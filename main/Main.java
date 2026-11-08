package main;

import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import model.Clerk;
import model.Manager;
import model.Product;
import model.StockMovement;
import model.User;
import service.InventoryService;
import service.ProductService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Temporary entry point with two tabs (Products and Inventory), so
 * ProductService and InventoryService can be tested before Login and the
 * Dashboards exist. Replace with the real login flow later.
 *
 * There is no login yet, so the Inventory tab has a "Testing as" box to
 * switch between a Manager and a Clerk and check the permissions.
 *
 * Adding a product is done in its own window (Add New Product button).
 * The panel on the right is only for editing a product that is selected.
 */
public class Main extends Application {

    private ProductService productService = new ProductService();
    private InventoryService inventoryService = new InventoryService(productService);
    private ObservableList<Product> tableData = FXCollections.observableArrayList();

    // fake users for testing (no login screen yet)
    private Manager manager = new Manager("manager", "manager123", "Maria Santos", true);
    private Clerk clerk = new Clerk("clerk1", "clerk123", "Juan Dela Cruz", true);
    private User currentUser = manager;

    private TableView<Product> table;
    private TextField searchField;

    // fields for the edit panel (right side)
    private TextField editIdField, editNameField, editPriceField, editStockField;
    private TextArea editDescArea;
    private Button updateBtn, deleteBtn, clearBtn;

    // inventory tab
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");
    private ObservableList<Product> inventoryData = FXCollections.observableArrayList();
    private ObservableList<StockMovement> logData = FXCollections.observableArrayList();
    private TableView<Product> inventoryTable;
    private TableView<StockMovement> logTable;
    private ComboBox<String> roleBox;
    private Label summaryLabel, selectedLabel;
    private TextField restockQtyField, restockNoteField;
    private Button restockBtn, reportBtn;
    private CheckBox onlySelectedBox;

    @Override
    public void start(Stage stage) {
        Label title = new Label("Product Management");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");
        HBox header = new HBox(title);
        header.setPadding(new Insets(14));
        header.setStyle("-fx-background-color: #d9534f;");

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(buildTablePane());
        root.setRight(buildEditPane());

        refreshTable();
        setEditEnabled(false);

        Tab productsTab = new Tab("Products", root);
        Tab inventoryTab = new Tab("Inventory", buildInventoryPane());
        productsTab.setClosable(false);
        inventoryTab.setClosable(false);

        // stock can change in the other tab, so reload the table when a tab is opened
        productsTab.setOnSelectionChanged(e -> {
            if (productsTab.isSelected()) {
                refreshTable();
                table.refresh();
            }
        });
        inventoryTab.setOnSelectionChanged(e -> {
            if (inventoryTab.isSelected()) {
                refreshInventory();
            }
        });
        refreshInventory();

        stage.setTitle("SHRIMP - Products and Inventory");
        stage.setScene(new Scene(new TabPane(productsTab, inventoryTab), 1100, 700));
        stage.show();
    }

    // ---------- SCREEN PARTS ----------

    private VBox buildTablePane() {
        // search bar + separate Add button
        searchField = new TextField();
        searchField.setPromptText("Search by ID, name or description...");
        searchField.textProperty().addListener((o, a, b) -> refreshTable());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button addBtn = new Button("+ Add New Product");
        addBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: white; -fx-font-weight: bold;");
        addBtn.setOnAction(e -> showAddDialog());

        HBox topBar = new HBox(10, searchField, addBtn);

        table = new TableView<>(tableData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Product, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductId()));
        idCol.setMaxWidth(80);

        TableColumn<Product, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(240);

        TableColumn<Product, Number> priceCol = new TableColumn<>("Price / kg");
        priceCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getPricePerKg()));
        priceCol.setCellFactory(col -> new TableCell<Product, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format("Php %.2f", value.doubleValue()));
                }
            }
        });
        priceCol.setMaxWidth(120);

        TableColumn<Product, Number> stockCol = new TableColumn<>("Stock (kg)");
        stockCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getStockQuantity()));
        stockCol.setCellFactory(col -> new TableCell<Product, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", value.doubleValue()));
                }
            }
        });
        stockCol.setMaxWidth(110);

        TableColumn<Product, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));

        table.getColumns().addAll(idCol, nameCol, priceCol, stockCol, descCol);
        table.getSelectionModel().selectedItemProperty().addListener((o, old, p) -> fillEditPanel(p));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox box = new VBox(10, topBar, table);
        box.setPadding(new Insets(12));
        return box;
    }

    // right side: only for editing the product that is selected in the table
    private VBox buildEditPane() {
        Label heading = new Label("Edit Selected Product");
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label hint = new Label("Click a product in the table to edit it.");
        hint.setStyle("-fx-text-fill: gray;");

        editIdField = new TextField();
        editIdField.setEditable(false);
        editIdField.setStyle("-fx-background-color: #e0e0e0;");
        editNameField = new TextField();
        editPriceField = new TextField();
        editStockField = new TextField();
        editStockField.setEditable(false); // stock is changed in the Inventory tab, so it is logged
        editStockField.setStyle("-fx-background-color: #e0e0e0;");
        editDescArea = new TextArea();
        editDescArea.setPrefRowCount(4);
        editDescArea.setWrapText(true);

        updateBtn = new Button("Update");
        deleteBtn = new Button("Delete");
        clearBtn = new Button("Clear");
        updateBtn.setOnAction(e -> onUpdate());
        deleteBtn.setOnAction(e -> onDelete());
        clearBtn.setOnAction(e -> clearEditPanel());

        HBox buttons = new HBox(8, updateBtn, deleteBtn, clearBtn);

        VBox form = new VBox(8,
                heading, hint,
                new Label("Product ID (cannot be changed)"), editIdField,
                new Label("Name"), editNameField,
                new Label("Price per kg (Php)"), editPriceField,
                new Label("Stock (kg) - change it in the Inventory tab"), editStockField,
                new Label("Description"), editDescArea,
                buttons);
        form.setPadding(new Insets(12));
        form.setPrefWidth(280);
        form.setStyle("-fx-background-color: #f4f4f4;");
        return form;
    }

    // ---------- ADD (separate window) ----------

    private void showAddDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add New Product");
        dialog.setHeaderText("Fill in the details of the new product.");

        // shows the ID the product WILL get, so the user knows it is automatic
        TextField idField = new TextField(productService.getNextId());
        idField.setEditable(false);
        idField.setStyle("-fx-background-color: #e0e0e0;");
        TextField nameField = new TextField();
        TextField priceField = new TextField();
        priceField.setPromptText("e.g. 650");
        TextField stockField = new TextField("0");
        TextArea descArea = new TextArea();
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.add(new Label("Product ID (automatic):"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("Name:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("Price per kg (Php):"), 0, 2);
        grid.add(priceField, 1, 2);
        grid.add(new Label("Starting stock (kg):"), 0, 3);
        grid.add(stockField, 1, 3);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(descArea, 1, 4);

        dialog.getDialogPane().setContent(grid);
        ButtonType saveType = new ButtonType("Save Product", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // when Save is pressed: try to add. If something is wrong, show the
        // error and keep the window open so the user doesn't lose what they typed
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                double price = parseNumber(priceField.getText(), "Price");
                double stock = parseNumber(stockField.getText(), "Stock");
                Product p = productService.addProduct(nameField.getText(), price,
                        descArea.getText(), stock);
                inventoryService.logOpeningStock(manager, p); // writes the "opening stock" history line
                refreshTable();
                info("Added " + p.getName() + " (" + p.getProductId() + ").");
            } catch (IllegalArgumentException ex) {
                error(ex.getMessage());
                event.consume(); // stops the window from closing
            }
        });

        dialog.showAndWait();
    }

    // ---------- UPDATE / DELETE / CLEAR ----------

    private void onUpdate() {
        try {
            double price = parseNumber(editPriceField.getText(), "Price");
            // the 4-argument version keeps the stock as it is
            productService.updateProduct(editIdField.getText(), editNameField.getText(),
                    price, editDescArea.getText());
            refreshTable();
            info("Product updated.");
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    private void onDelete() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            error("Select a product from the table first.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getName() + "\"? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            productService.removeProduct(selected.getProductId());
            refreshTable();
            clearEditPanel();
        }
    }

    private void clearEditPanel() {
        table.getSelectionModel().clearSelection();
        editIdField.clear();
        editNameField.clear();
        editPriceField.clear();
        editStockField.clear();
        editDescArea.clear();
        setEditEnabled(false);
    }

    // ---------- INVENTORY TAB ----------

    private VBox buildInventoryPane() {
        Label heading = new Label("Inventory");
        heading.setFont(Font.font("System", FontWeight.BOLD, 20));

        roleBox = new ComboBox<>();
        roleBox.getItems().addAll("Manager", "Clerk");
        roleBox.setValue("Manager");
        roleBox.setOnAction(e -> {
            currentUser = "Clerk".equals(roleBox.getValue()) ? clerk : manager;
            updateRoleControls();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, heading, spacer, new Label("Testing as:"), roleBox);
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
        boolean canRestock = currentUser.canRestock();
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
            inventoryService.restock(currentUser, selected.getProductId(), kg,
                    restockNoteField.getText());
            restockQtyField.clear();
            restockNoteField.clear();
            refreshInventory();
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
            inventoryService.requestRestock(currentUser, selected.getProductId());
            refreshInventory();
            info("Reported. The manager can now see that " + selected.getName() + " needs restocking.");
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    // ---------- HELPERS ----------

    private double parseNumber(String text, String fieldName) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    private void refreshTable() {
        String keyword = "";
        if (searchField != null) {
            keyword = searchField.getText();
        }
        tableData.setAll(productService.searchByName(keyword));
    }

    private void fillEditPanel(Product p) {
        if (p == null) {
            return;
        }
        editIdField.setText(p.getProductId());
        editNameField.setText(p.getName());
        editPriceField.setText(String.valueOf(p.getPricePerKg()));
        editStockField.setText(String.valueOf(p.getStockQuantity()));
        editDescArea.setText(p.getDescription());
        setEditEnabled(true);
    }

    // the edit panel is greyed out until a product is selected
    private void setEditEnabled(boolean enabled) {
        editNameField.setDisable(!enabled);
        editPriceField.setDisable(!enabled);
        editStockField.setDisable(!enabled);
        editDescArea.setDisable(!enabled);
        updateBtn.setDisable(!enabled);
        deleteBtn.setDisable(!enabled);
        clearBtn.setDisable(!enabled);
    }

    private void info(String message) {
        new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
    }

    private void error(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}