package main;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;
import model.Product;
import model.Transaction;
import model.TransactionStatus;
import model.TransactionType;
import model.User;
import service.InsufficientStockException;
import service.InventoryService;
import service.ProductService;
import service.TransactionService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Milestone 3 - Transaction Management screen (reusable component).
 * Sales are the cash transactions of this system.
 *
 * It only talks to TransactionService. The screen decides what to SHOW from
 * the user's permissions (canRequestVoid, canApproveVoids, canViewAllTransactions),
 * and the service enforces the same rules again, so the rules can't be skipped.
 *
 *  - Both roles : record a sale, view history
 *  - Clerk      : sees only their own transactions, can request a void
 *  - Manager    : sees every transaction, approves or rejects void requests
 */
public class TransactionPane extends VBox {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");
    private static final String ALL = "All";

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;
    private final Supplier<User> currentUser;   // always returns the user who is signed in right now
    private final Runnable onDataChanged;       // lets other tabs know stock changed

    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();
    private TableView<Transaction> table;

    // summary
    private Label balanceLabel, salesLabel, todayLabel, pendingLabel;

    // sale form
    private ComboBox<Product> productBox;
    private TextField qtyField;
    private Label stockInfoLabel, totalPreviewLabel;
    private Button saleBtn;

    // filters
    private TextField searchField;
    private ComboBox<String> statusFilter;

    // void controls
    private Label detailLabel;
    private Button requestVoidBtn, approveBtn, rejectBtn;

    public TransactionPane(ProductService productService, InventoryService inventoryService,
                           TransactionService transactionService, Supplier<User> currentUser,
                           Runnable onDataChanged) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.transactionService = transactionService;
        this.currentUser = currentUser;
        this.onDataChanged = onDataChanged;

        Label heading = new Label("Transactions");
        heading.setFont(Font.font("System", FontWeight.BOLD, 20));

        getChildren().addAll(heading, buildSummaryBar(),
                buildSalePane(),
                buildHistoryPane());
        setSpacing(10);
        setPadding(new Insets(12));
        refresh();
    }

    // ---------- BUILDING THE SCREEN ----------

    private HBox buildSummaryBar() {
        balanceLabel = new Label();
        salesLabel = new Label();
        todayLabel = new Label();
        pendingLabel = new Label();

        HBox bar = new HBox(10,
                card("Cash Balance", balanceLabel, "#d9534f"),
                card("Total Sales", salesLabel, "#5cb85c"),
                card("Today's Sales", todayLabel, "#5bc0de"),
                card("Pending Voids", pendingLabel, "#f0ad4e"));
        return bar;
    }

    // one small summary box
    private VBox card(String title, Label value, String color) {
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        value.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        VBox box = new VBox(2, t, value);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setStyle("-fx-background-color: #f4f4f4; -fx-background-radius: 6;");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // new sale form
    private VBox buildSalePane() {
        Label title = new Label("New Sale");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        productBox = new ComboBox<>();
        productBox.setPromptText("Choose a product");
        productBox.setMaxWidth(Double.MAX_VALUE);
        productBox.setConverter(new StringConverter<Product>() {
            @Override
            public String toString(Product p) {
                if (p == null) {
                    return "";
                }
                return p.getName() + " - Php " + String.format("%.2f", p.getPricePerKg()) + "/kg";
            }

            @Override
            public Product fromString(String s) {
                return null; // combo box is not editable
            }
        });
        productBox.valueProperty().addListener((o, a, b) -> updateSalePreview());

        qtyField = new TextField();
        qtyField.setPromptText("kg, e.g. 2.5");
        qtyField.textProperty().addListener((o, a, b) -> updateSalePreview());

        stockInfoLabel = new Label();
        stockInfoLabel.setStyle("-fx-text-fill: gray;");
        totalPreviewLabel = new Label("Total: Php 0.00");
        totalPreviewLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        saleBtn = new Button("Process Sale");
        saleBtn.setStyle("-fx-background-color: #5cb85c; -fx-text-fill: white; -fx-font-weight: bold;");
        saleBtn.setOnAction(e -> onSale());

        VBox box = new VBox(8, title, new Label("Product"), productBox,
                new Label("Quantity (kg)"), qtyField, stockInfoLabel, totalPreviewLabel, saleBtn);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 6;");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // history table + filters + void buttons
    private VBox buildHistoryPane() {
        Label title = new Label("Transaction History");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        searchField = new TextField();
        searchField.setPromptText("Search ID, product or user...");
        searchField.textProperty().addListener((o, a, b) -> reloadRows());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        statusFilter = new ComboBox<>();
        statusFilter.getItems().add(ALL);
        for (TransactionStatus s : TransactionStatus.values()) {
            statusFilter.getItems().add(s.getLabel());
        }
        statusFilter.setValue(ALL);
        statusFilter.setOnAction(e -> reloadRows());

        HBox filters = new HBox(10, title, searchField, new Label("Status:"), statusFilter);
        filters.setAlignment(Pos.CENTER_LEFT);

        table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transactions to show."));

        TableColumn<Transaction, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionId()));
        idCol.setPrefWidth(80);

        TableColumn<Transaction, String> timeCol = new TableColumn<>("Date & Time");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDateTime().format(TIME)));
        timeCol.setPrefWidth(160);

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType().getLabel()));
        typeCol.setPrefWidth(80);

        TableColumn<Transaction, String> itemCol = new TableColumn<>("Product / Note");
        itemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        itemCol.setPrefWidth(220);

        TableColumn<Transaction, String> qtyCol = new TableColumn<>("Qty (kg)");
        qtyCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getType() == TransactionType.SALE
                        ? String.format("%.2f", c.getValue().getQuantityKg()) : "-"));
        qtyCol.setPrefWidth(80);

        TableColumn<Transaction, String> totalCol = new TableColumn<>("Amount");
        totalCol.setCellValueFactory(c -> new SimpleStringProperty(
                (c.getValue().getType().getSign() < 0 ? "- " : "+ ")
                        + "Php " + String.format("%,.2f", c.getValue().getTotalAmount())));
        totalCol.setPrefWidth(120);

        TableColumn<Transaction, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerformedBy()));
        byCol.setPrefWidth(90);

        TableColumn<Transaction, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().getLabel()));
        statusCol.setPrefWidth(120);
        statusCol.setCellFactory(col -> new TableCell<Transaction, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(status);
                if (status.equals(TransactionStatus.VOIDED.getLabel())) {
                    setTextFill(Color.RED);
                } else if (status.equals(TransactionStatus.VOID_REQUESTED.getLabel())) {
                    setTextFill(Color.DARKORANGE);
                } else {
                    setTextFill(Color.DARKGREEN);
                }
            }
        });

        TableColumn<Transaction, String> reasonCol = new TableColumn<>("Void reason");
        reasonCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getVoidReason() == null ? "" : c.getValue().getVoidReason()));

        table.getColumns().addAll(idCol, timeCol, typeCol, itemCol, qtyCol, totalCol, byCol, statusCol, reasonCol);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateVoidControls());
        VBox.setVgrow(table, Priority.ALWAYS);

        detailLabel = new Label();
        detailLabel.setWrapText(true);
        requestVoidBtn = new Button("Request Void");
        requestVoidBtn.setOnAction(e -> onRequestVoid());
        approveBtn = new Button("Approve Void");
        approveBtn.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white;");
        approveBtn.setOnAction(e -> onApproveVoid());
        rejectBtn = new Button("Reject Void");
        rejectBtn.setOnAction(e -> onRejectVoid());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, detailLabel, spacer, requestVoidBtn, approveBtn, rejectBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, filters, table, actions);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    // ---------- REFRESH ----------

    /** Reloads everything. Call this whenever the tab is opened or the user changes. */
    public void refresh() {
        // product list: stock may have changed in the Inventory tab, so rebuild and keep the selection
        Product selected = productBox.getValue();
        String selectedId = selected == null ? null : selected.getProductId();
        productBox.getItems().setAll(productService.getAllProducts());
        productBox.setValue(null);
        if (selectedId != null) {
            for (Product p : productBox.getItems()) {
                if (p.getProductId().equals(selectedId)) {
                    productBox.setValue(p);
                    break;
                }
            }
        }

        reloadRows();
        updateSummary();
        updateSalePreview();
        updateVoidControls();
    }

    private void updateSummary() {
        balanceLabel.setText(peso(transactionService.calculateTotalCashBalance()));
        salesLabel.setText(peso(transactionService.calculateTotalSales()));
        todayLabel.setText(peso(transactionService.calculateTodaySales()));
        pendingLabel.setText(String.valueOf(transactionService.getPendingVoidRequests().size()));
    }

    // applies the role (clerk = own only) and the search / filters, newest first
    private void reloadRows() {
        if (table == null) {
            return;
        }
        User user = currentUser.get();
        List<Transaction> source = user.canViewAllTransactions()
                ? transactionService.getAllTransactions()
                : transactionService.getTransactionsByUser(user.getUsername());

        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String status = statusFilter.getValue();

        String selectedId = null;
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedId = selected.getTransactionId();
        }

        List<Transaction> shown = new ArrayList<>();
        for (int i = source.size() - 1; i >= 0; i--) { // newest first
            Transaction t = source.get(i);
            if (!ALL.equals(status) && !t.getStatus().getLabel().equals(status)) {
                continue;
            }
            if (!keyword.isEmpty() && !matches(t, keyword)) {
                continue;
            }
            shown.add(t);
        }
        rows.setAll(shown);
        table.refresh();

        if (selectedId != null) {
            for (Transaction t : rows) {
                if (t.getTransactionId().equals(selectedId)) {
                    table.getSelectionModel().select(t);
                    break;
                }
            }
        }
    }

    private boolean matches(Transaction t, String keyword) {
        return t.getTransactionId().toLowerCase().contains(keyword)
                || (t.getProductName() != null && t.getProductName().toLowerCase().contains(keyword))
                || t.getPerformedBy().toLowerCase().contains(keyword);
    }

    // live "available stock" and "total" under the sale form
    private void updateSalePreview() {
        Product p = productBox.getValue();
        if (p == null) {
            stockInfoLabel.setText("Select a product to see its stock.");
            totalPreviewLabel.setText("Total: Php 0.00");
            return;
        }
        stockInfoLabel.setText("Available: " + String.format("%.2f", p.getStockQuantity()) + " kg   ("
                + inventoryService.getStatus(p) + ")");
        try {
            double qty = Double.parseDouble(qtyField.getText().trim());
            if (qty > 0 && !Double.isInfinite(qty)) {
                totalPreviewLabel.setText("Total: " + peso(Math.round(qty * p.getPricePerKg() * 100.0) / 100.0));
                return;
            }
        } catch (NumberFormatException | NullPointerException e) {
            // not a number yet, show zero below
        }
        totalPreviewLabel.setText("Total: Php 0.00");
    }

    // shows or hides the void buttons depending on the user's role and the selected row
    private void updateVoidControls() {
        User user = currentUser.get();
        Transaction t = table.getSelectionModel().getSelectedItem();

        requestVoidBtn.setVisible(user.canRequestVoid());
        requestVoidBtn.setManaged(user.canRequestVoid());
        approveBtn.setVisible(user.canApproveVoids());
        approveBtn.setManaged(user.canApproveVoids());
        rejectBtn.setVisible(user.canApproveVoids());
        rejectBtn.setManaged(user.canApproveVoids());

        if (t == null) {
            detailLabel.setText(user.canApproveVoids()
                    ? "Select a transaction. Void requests waiting for approval show \"Void Requested\"."
                    : "Select a transaction to request a void.");
            requestVoidBtn.setDisable(true);
            approveBtn.setDisable(true);
            rejectBtn.setDisable(true);
            return;
        }

        boolean pending = t.getStatus() == TransactionStatus.VOID_REQUESTED;
        requestVoidBtn.setDisable(t.getStatus() != TransactionStatus.COMPLETED);
        approveBtn.setDisable(!pending);
        rejectBtn.setDisable(!pending);

        String text = t.getTransactionId() + " - " + t.getStatus().getLabel();
        if (pending) {
            text += ". Requested by " + t.getVoidRequestedBy() + ": " + t.getVoidReason();
        } else if (t.getStatus() == TransactionStatus.VOIDED) {
            text += ". Approved by " + t.getVoidApprovedBy() + ". Reason: " + t.getVoidReason();
        }
        detailLabel.setText(text);
    }

    // ---------- ACTIONS ----------

    private void onSale() {
        Product p = productBox.getValue();
        if (p == null) {
            error("Choose a product first.");
            return;
        }
        double qty;
        try {
            qty = Double.parseDouble(qtyField.getText().trim());
        } catch (NumberFormatException | NullPointerException e) {
            error("Quantity must be a valid number.");
            return;
        }
        // sale can't be undone without a manager, so confirm first
        double total = Math.round(qty * p.getPricePerKg() * 100.0) / 100.0;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Sell " + String.format("%.2f", qty) + " kg of " + p.getName() + "\nTotal: " + peso(total),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Confirm sale");
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }

        try {
            Transaction t = transactionService.processSale(currentUser.get(), p.getProductId(), qty);
            qtyField.clear();
            afterChange();
            info("Sale " + t.getTransactionId() + " recorded: " + String.format("%.2f", t.getQuantityKg())
                    + " kg of " + t.getProductName() + " = " + peso(t.getTotalAmount()));
        } catch (InsufficientStockException ex) {
            warn(ex.getMessage()); // special stock warning
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    private void onRequestVoid() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            error("Select a transaction first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Request Void");
        dialog.setHeaderText("Void " + t.getTransactionId() + " (" + peso(t.getTotalAmount()) + ")?");
        dialog.setContentText("Reason:");
        Optional<String> reason = dialog.showAndWait();
        if (reason.isEmpty()) {
            return;
        }
        try {
            transactionService.requestVoid(currentUser.get(), t.getTransactionId(), reason.get());
            afterChange();
            info("Void request sent to the manager.");
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    private void onApproveVoid() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            error("Select a transaction first.");
            return;
        }
        String extra = t.getType() == TransactionType.SALE
                ? "\n" + String.format("%.2f", t.getQuantityKg()) + " kg will be returned to stock." : "";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Approve void of " + t.getTransactionId() + " (" + peso(t.getTotalAmount()) + ")?\n"
                        + "Reason: " + t.getVoidReason() + extra,
                ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.YES) {
            return;
        }
        try {
            transactionService.approveVoid(currentUser.get(), t.getTransactionId());
            afterChange();
            info(t.getTransactionId() + " has been voided.");
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    private void onRejectVoid() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            error("Select a transaction first.");
            return;
        }
        try {
            transactionService.rejectVoid(currentUser.get(), t.getTransactionId());
            afterChange();
            info("Void request for " + t.getTransactionId() + " was rejected.");
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    // after any change: reload this screen and tell the other tabs that stock may have changed
    private void afterChange() {
        refresh();
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }

    // ---------- HELPERS ----------

    private static String peso(double amount) {
        return "Php " + String.format("%,.2f", amount);
    }

    private void info(String message) {
        new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
    }

    private void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText("Insufficient stock");
        alert.showAndWait();
    }

    private void error(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}
