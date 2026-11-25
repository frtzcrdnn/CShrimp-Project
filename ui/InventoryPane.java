package ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.Product;
import model.StockMovement;
import model.User;
import service.InventoryService;
import service.ProductService;
import service.ValidationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Inventory screen shared by Manager and Clerk dashboards.
 * Shows current stock levels, stock history, manager restocking, and clerk low-stock reporting.
 */
public class InventoryPane extends VBox {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final Supplier<User> currentUser;
    private final Runnable onDataChanged;

    private final ObservableList<Product> inventoryData = FXCollections.observableArrayList();
    private final ObservableList<StockMovement> logData = FXCollections.observableArrayList();
    private TableView<Product> inventoryTable;
    private TableView<StockMovement> logTable;
    private VBox historyBox;
    private Label productsLabel, lowLabel, outLabel;
    private Label summaryLabel, selectedNameLabel, selectedLabel;
    private TextField restockQtyField, restockNoteField;
    private Button restockBtn, reportBtn;
    private CheckBox onlySelectedBox;
    private VBox managerControls, clerkControls;

    public InventoryPane(ProductService productService, InventoryService inventoryService,
                         Supplier<User> currentUser, Runnable onDataChanged) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.currentUser = currentUser;
        this.onDataChanged = onDataChanged;

        setSpacing(16);
        productsLabel = new Label();
        lowLabel = new Label();
        outLabel = new Label();
        GridPane stats = Theme.statGrid(3,
                Theme.statCard("Products", productsLabel, Theme.DEEP),
                Theme.statCard("Low stock", lowLabel, Theme.WARN),
                Theme.statCard("Out of stock", outLabel, Theme.BAD));

        HBox body = new HBox(16, buildStockPanel(), buildActionPanel());
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(stats, body);
        refresh();
    }

    /** Reloads the stock table, history log, and role controls. */
    public void refresh() {
        refreshInventory();
    }

    private VBox buildStockPanel() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton stockTab = new ToggleButton("Current stock");
        ToggleButton historyTab = new ToggleButton("History");
        for (ToggleButton b : new ToggleButton[]{stockTab, historyTab}) {
            b.setToggleGroup(group);
            b.getStyleClass().add("seg");
        }
        stockTab.setSelected(true);

        Label heading = new Label("Stock");
        heading.getStyleClass().add("h2");
        HBox top = Theme.spacerRow(heading, new HBox(4, stockTab, historyTab));

        buildInventoryTable();
        buildHistory();

        StackPane tables = new StackPane(inventoryTable, historyBox);
        historyBox.setVisible(false);
        historyBox.setManaged(false);
        VBox.setVgrow(tables, Priority.ALWAYS);

        group.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) {
                was.setSelected(true);
                return;
            }
            boolean history = now == historyTab;
            inventoryTable.setVisible(!history);
            inventoryTable.setManaged(!history);
            historyBox.setVisible(history);
            historyBox.setManaged(history);
        });

        summaryLabel = Theme.mutedLabel("");

        VBox panel = new VBox(12, top, tables, summaryLabel);
        panel.getStyleClass().add("card");
        panel.setPadding(new Insets(16));
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private void buildInventoryTable() {
        inventoryTable = new TableView<>(inventoryData);
        inventoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        inventoryTable.setPlaceholder(new Label("No products yet."));

        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(280);

        TableColumn<Product, Double> stockCol = Theme.doubleColumn(
                "Stock (kg)", Product::getStockQuantity, v -> String.format("%.2f", v), 115);
        stockCol.setMaxWidth(125);

        TableColumn<Product, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                inventoryService.getStatus(c.getValue())));
        statusCol.setMaxWidth(140);
        statusCol.setCellFactory(col -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                if (status.equals("OUT OF STOCK")) {
                    setStyle(Theme.statusStyle(Theme.BAD));
                } else if (status.equals("LOW STOCK")) {
                    setStyle(Theme.statusStyle(Theme.WARN));
                } else {
                    setStyle(Theme.statusStyle(Theme.OK));
                }
            }
        });

        TableColumn<Product, String> reportCol = new TableColumn<>("Low-stock report");
        reportCol.setCellValueFactory(c -> new SimpleStringProperty(
                inventoryService.hasRestockRequest(c.getValue().getProductId())
                        ? "Reported - needs restock" : ""));

        inventoryTable.getColumns().addAll(nameCol, stockCol, statusCol, reportCol);
        inventoryTable.getSelectionModel().selectedItemProperty().addListener((o, old, p) -> {
            updateSelectedLabel();
            refreshLog();
        });
    }

    private void buildHistory() {
        onlySelectedBox = new CheckBox("Only show the selected product");
        onlySelectedBox.setOnAction(e -> refreshLog());

        logTable = new TableView<>(logData);
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        logTable.setPlaceholder(new Label("No stock changes yet."));

        TableColumn<StockMovement, LocalDateTime> timeCol = Theme.dateTimeColumn(
                "Date & Time", StockMovement::getDateTime, 150);

        TableColumn<StockMovement, String> logProductCol = new TableColumn<>("Product");
        logProductCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        logProductCol.setPrefWidth(150);

        TableColumn<StockMovement, Double> changeCol = Theme.doubleColumn(
                "Change (kg)",
                StockMovement::getChange,
                v -> (v > 0 ? "+" : "") + String.format("%.2f", v),
                105);
        changeCol.setMaxWidth(115);

        TableColumn<StockMovement, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReason().getLabel()));
        reasonCol.setPrefWidth(100);

        TableColumn<StockMovement, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerformedBy()));
        byCol.setPrefWidth(90);

        TableColumn<StockMovement, Double> afterCol = Theme.doubleColumn(
                "Stock after", StockMovement::getStockAfter, v -> String.format("%.2f", v), 100);
        afterCol.setMaxWidth(110);

        TableColumn<StockMovement, String> noteCol = new TableColumn<>("Note");
        noteCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNote()));

        logTable.getColumns().addAll(timeCol, logProductCol, changeCol, reasonCol, byCol, afterCol, noteCol);
        VBox.setVgrow(logTable, Priority.ALWAYS);

        historyBox = new VBox(8, onlySelectedBox, logTable);
    }

    private VBox buildActionPanel() {
        Label heading = new Label("Update stock");
        heading.getStyleClass().add("h2");

        selectedNameLabel = new Label();
        selectedNameLabel.setWrapText(true);
        selectedNameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + Theme.DEEP + ";");
        selectedLabel = Theme.mutedLabel("");

        restockQtyField = new TextField();
        restockQtyField.setPromptText("kg to add");
        restockNoteField = new TextField();
        restockNoteField.setPromptText("e.g. delivery from supplier");
        restockBtn = new Button("Restock");
        restockBtn.getStyleClass().add("btn-primary");
        restockBtn.setMaxWidth(Double.MAX_VALUE);
        restockBtn.setOnAction(e -> onRestock());
        managerControls = new VBox(6,
                Theme.fieldLabel("Quantity to add (kg)"), restockQtyField,
                Theme.fieldLabel("Note (optional)"), restockNoteField,
                restockBtn);
        VBox.setMargin(restockBtn, new Insets(8, 0, 0, 0));

        reportBtn = new Button("Report low stock");
        reportBtn.getStyleClass().add("btn-primary");
        reportBtn.setMaxWidth(Double.MAX_VALUE);
        reportBtn.setOnAction(e -> onReportLowStock());
        clerkControls = new VBox(10,
                Theme.mutedLabel("Running out of this product? Tell the manager so it gets restocked."),
                reportBtn);

        VBox panel = new VBox(12, heading, selectedNameLabel, selectedLabel,
                new Separator(), managerControls, clerkControls);
        panel.getStyleClass().add("card");
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(290);
        panel.setMinWidth(290);
        panel.setMaxWidth(290);
        panel.setAlignment(Pos.TOP_LEFT);
        return panel;
    }

    private void refreshInventory() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        String selectedId = selected == null ? null : selected.getProductId();

        inventoryData.setAll(productService.getAllProducts());
        inventoryTable.refresh();

        if (selectedId != null) {
            for (Product p : inventoryData) {
                if (p.getProductId().equals(selectedId)) {
                    inventoryTable.getSelectionModel().select(p);
                    break;
                }
            }
        }

        int low = 0;
        int out = 0;
        for (Product p : productService.getAllProducts()) {
            if (inventoryService.isOutOfStock(p)) {
                out++;
            } else if (inventoryService.isLowStock(p)) {
                low++;
            }
        }
        productsLabel.setText(String.valueOf(productService.getAllProducts().size()));
        lowLabel.setText(String.valueOf(low));
        outLabel.setText(String.valueOf(out));
        summaryLabel.setText("A product is marked low stock at "
                + String.format("%.0f", InventoryService.LOW_STOCK_LEVEL) + " kg or less.");

        updateSelectedLabel();
        refreshLog();
        updateRoleControls();
    }

    private void refreshLog() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        List<StockMovement> rows = (onlySelectedBox.isSelected() && selected != null)
                ? inventoryService.getMovementsFor(selected.getProductId())
                : inventoryService.getMovements();
        if (rows.size() > 300) {
            rows = rows.subList(0, 300);
        }
        logData.setAll(rows);
    }

    private void updateSelectedLabel() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            selectedNameLabel.setText("No product selected");
            selectedLabel.setText("Click a product in the table to update its stock.");
        } else {
            selectedNameLabel.setText(Theme.codeOf(selected));
            selectedLabel.setText(Theme.meaningOf(selected) + "\nCurrent stock: "
                    + String.format("%.2f", selected.getStockQuantity()) + " kg");
        }
        boolean none = selected == null;
        restockBtn.setDisable(none || !currentUser.get().canRestock());
        reportBtn.setDisable(none || currentUser.get().canRestock());
    }

    private void updateRoleControls() {
        boolean canRestock = currentUser.get().canRestock();
        managerControls.setVisible(canRestock);
        managerControls.setManaged(canRestock);
        clerkControls.setVisible(!canRestock);
        clerkControls.setManaged(!canRestock);
    }

    private void onRestock() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Theme.error("Select a product from the table first.");
            return;
        }
        try {
            double kg = Theme.parseNumber(restockQtyField.getText(), "Quantity");
            inventoryService.restock(currentUser.get(), selected.getProductId(), kg,
                    restockNoteField.getText());
            restockQtyField.clear();
            restockNoteField.clear();
            refreshInventory();
            notifyChanged();
            Theme.info(selected.getName() + " now has "
                    + String.format("%.2f", selected.getStockQuantity()) + " kg in stock.");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onReportLowStock() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Theme.error("Select a product from the table first.");
            return;
        }
        try {
            inventoryService.requestRestock(currentUser.get(), selected.getProductId());
            refreshInventory();
            notifyChanged();
            Theme.info("Reported. The manager can now see that " + selected.getName() + " needs restocking.");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void notifyChanged() {
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }
}