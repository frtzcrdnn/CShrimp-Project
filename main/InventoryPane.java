package main;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import model.Product;
import model.StockMovement;
import model.User;
import service.InventoryService;
import service.ProductService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Inventory screen (stock table, restock / low-stock report, stock history).
 * Reusable component: the Manager and the Clerk dashboards both use it, and the
 * buttons that are shown depend on what the signed-in user is allowed to do.
 */
public class InventoryPane extends VBox {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final Supplier<User> currentUser;   // the user who is signed in right now
    private final Runnable onDataChanged;       // tells the dashboard that stock changed

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");
    private ObservableList<Product> inventoryData = FXCollections.observableArrayList();
    private ObservableList<StockMovement> logData = FXCollections.observableArrayList();
    private TableView<Product> inventoryTable;
    private TableView<StockMovement> logTable;
    private Label summaryLabel, selectedLabel;
    private TextField restockQtyField, restockNoteField;
    private Button restockBtn, reportBtn;
    private CheckBox onlySelectedBox;

    public InventoryPane(ProductService productService, InventoryService inventoryService,
                         Supplier<User> currentUser, Runnable onDataChanged) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.currentUser = currentUser;
        this.onDataChanged = onDataChanged;

        VBox content = buildInventoryPane();
        VBox.setVgrow(content, Priority.ALWAYS);
        getChildren().add(content);
        refreshInventory();
    }

    /** Reloads the stock table, the history and the buttons. */
    public void refresh() {
        refreshInventory();
    }

    private VBox buildInventoryPane() {
        Label heading = new Label("Inventory");
        heading.setFont(Font.font("System", FontWeight.BOLD, 20));

        HBox top = new HBox(10, heading);
        top.setAlignment(Pos.CENTER_LEFT);

        summaryLabel = new Label();

        // --- stock table ---
        inventoryTable = new TableView<>(inventoryData);
        inventoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Product, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductId()));
        idCol.setMaxWidth(80);

        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(300);

        TableColumn<Product, String> stockCol = new TableColumn<>("Stock (kg)");
        stockCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.2f", c.getValue().getStockQuantity())));
        stockCol.setMaxWidth(120);

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
                    return;
                }
                setText(status);
                if (status.equals("OUT OF STOCK")) {
                    setTextFill(Color.RED);
                } else if (status.equals("LOW STOCK")) {
                    setTextFill(Color.DARKORANGE);
                } else {
                    setTextFill(Color.DARKGREEN);
                }
            }
        });

        TableColumn<Product, String> reportCol = new TableColumn<>("Low-stock report");
        reportCol.setCellValueFactory(c -> new SimpleStringProperty(
                inventoryService.hasRestockRequest(c.getValue().getProductId())
                        ? "Reported - needs restock" : ""));

        inventoryTable.getColumns().addAll(idCol, nameCol, stockCol, statusCol, reportCol);
        inventoryTable.getSelectionModel().selectedItemProperty().addListener((o, old, p) -> {
            updateSelectedLabel();
            refreshLog();
        });
        VBox.setVgrow(inventoryTable, Priority.ALWAYS);

        // --- restock (manager) / report low stock (clerk) ---
        selectedLabel = new Label();
        restockQtyField = new TextField();
        restockQtyField.setPromptText("kg to add");
        restockQtyField.setPrefWidth(110);
        restockNoteField = new TextField();
        restockNoteField.setPromptText("Optional note, e.g. delivery from supplier");
        HBox.setHgrow(restockNoteField, Priority.ALWAYS);

        restockBtn = new Button("Restock");
        restockBtn.setOnAction(e -> onRestock());
        reportBtn = new Button("Report Low Stock");
        reportBtn.setOnAction(e -> onReportLowStock());

        HBox actions = new HBox(10, new Label("Quantity (kg):"), restockQtyField,
                new Label("Note:"), restockNoteField, restockBtn, reportBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        // --- history table ---
        onlySelectedBox = new CheckBox("Only show the selected product");
        onlySelectedBox.setOnAction(e -> refreshLog());
        Label logHeading = new Label("Stock History");
        logHeading.setFont(Font.font("System", FontWeight.BOLD, 14));
        HBox logTop = new HBox(15, logHeading, onlySelectedBox);
        logTop.setAlignment(Pos.CENTER_LEFT);

        logTable = new TableView<>(logData);
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<StockMovement, String> timeCol = new TableColumn<>("Date & Time");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDateTime().format(LOG_TIME)));
        timeCol.setPrefWidth(160);

        TableColumn<StockMovement, String> logProductCol = new TableColumn<>("Product");
        logProductCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        logProductCol.setPrefWidth(230);

        TableColumn<StockMovement, String> changeCol = new TableColumn<>("Change (kg)");
        changeCol.setCellValueFactory(c -> new SimpleStringProperty(
                (c.getValue().getChange() > 0 ? "+" : "") + String.format("%.2f", c.getValue().getChange())));
        changeCol.setMaxWidth(110);

        TableColumn<StockMovement, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReason().getLabel()));
        reasonCol.setPrefWidth(120);

        TableColumn<StockMovement, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerformedBy()));
        byCol.setPrefWidth(120);

        TableColumn<StockMovement, String> afterCol = new TableColumn<>("Stock after");
        afterCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.2f", c.getValue().getStockAfter())));
        afterCol.setMaxWidth(110);

        TableColumn<StockMovement, String> noteCol = new TableColumn<>("Note");
        noteCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNote()));

        logTable.getColumns().addAll(timeCol, logProductCol, changeCol, reasonCol, byCol, afterCol, noteCol);
        VBox.setVgrow(logTable, Priority.ALWAYS);

        VBox box = new VBox(10, top, summaryLabel, inventoryTable, selectedLabel, actions, logTop, logTable);
        box.setPadding(new Insets(12));
        return box;
    }

    // reloads the stock table, the history and the buttons
    private void refreshInventory() {
        // remember the selected product, because setAll() clears the selection
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        String selectedId = null;
        if (selected != null) {
            selectedId = selected.getProductId();
        }

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
        summaryLabel.setText(productService.getAllProducts().size() + " products   |   "
                + low + " low stock   |   " + out + " out of stock   (low-stock level: "
                + String.format("%.0f", InventoryService.LOW_STOCK_LEVEL) + " kg)");

        updateSelectedLabel();
        refreshLog();
        updateRoleControls();
    }

    private void refreshLog() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        List<StockMovement> rows;
        if (onlySelectedBox.isSelected() && selected != null) {
            rows = inventoryService.getMovementsFor(selected.getProductId());
        } else {
            rows = inventoryService.getMovements();
        }
        if (rows.size() > 300) {
            rows = rows.subList(0, 300); // newest 300 only
        }
        logData.setAll(rows);
    }

    private void updateSelectedLabel() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            selectedLabel.setText("Select a product in the table above.");
        } else {
            selectedLabel.setText("Selected: " + selected.getName() + "   (current stock: "
                    + String.format("%.2f", selected.getStockQuantity()) + " kg)");
        }
    }

    // only a manager can restock, only a clerk needs the "report" button
    private void updateRoleControls() {
        boolean canRestock = currentUser.get().canRestock();
        restockQtyField.setDisable(!canRestock);
        restockNoteField.setDisable(!canRestock);
        restockBtn.setDisable(!canRestock);
        reportBtn.setDisable(canRestock);
    }

    private void onRestock() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            error("Select a product from the table first.");
            return;
        }
        try {
            double kg = parseNumber(restockQtyField.getText(), "Quantity");
            inventoryService.restock(currentUser.get(), selected.getProductId(), kg,
                    restockNoteField.getText());
            restockQtyField.clear();
            restockNoteField.clear();
            refreshInventory();
            notifyChanged();
            info(selected.getName() + " now has "
                    + String.format("%.2f", selected.getStockQuantity()) + " kg in stock.");
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    private void onReportLowStock() {
        Product selected = inventoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            error("Select a product from the table first.");
            return;
        }
        try {
            inventoryService.requestRestock(currentUser.get(), selected.getProductId());
            refreshInventory();
            notifyChanged();
            info("Reported. The manager can now see that " + selected.getName() + " needs restocking.");
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    private double parseNumber(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    private void info(String message) {
        new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
    }

    private void error(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }

    private void notifyChanged() {
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }
}
