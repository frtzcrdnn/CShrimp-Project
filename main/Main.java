package main;

import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.Product;
import service.ProductService;

import java.util.Optional;

/**
 * Temporary entry point that shows ONLY the Product Management screen,
 * so ProductService can be tested before Login, Inventory and the
 * Dashboards exist. Replace with the real login flow later.
 *
 * Adding a product is done in its own window (Add New Product button).
 * The panel on the right is only for editing a product that is selected.
 */
public class Main extends Application {

    private ProductService productService = new ProductService();
    private ObservableList<Product> tableData = FXCollections.observableArrayList();

    private TableView<Product> table;
    private TextField searchField;

    // fields for the edit panel (right side)
    private TextField editIdField, editNameField, editPriceField, editStockField;
    private TextArea editDescArea;
    private Button updateBtn, deleteBtn, clearBtn;

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

        stage.setTitle("SHRIMP - Product Management");
        stage.setScene(new Scene(root, 1000, 560));
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
                new Label("Stock (kg)"), editStockField,
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
            double stock = parseNumber(editStockField.getText(), "Stock");
            productService.updateProduct(editIdField.getText(), editNameField.getText(),
                    price, editDescArea.getText(), stock);
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