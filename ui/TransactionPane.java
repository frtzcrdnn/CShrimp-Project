package ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
import service.ValidationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Transaction & Wallet screen (reusable across Manager and Clerk dashboards).
 * Supports recording shrimp sales (with customer & payment details), Cash-In/Cash-Out
 * wallet adjustments, viewing transaction history, and role-based void workflows.
 */
public class TransactionPane extends VBox {

    private static final String ALL = "All";

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;
    private final Supplier<User> currentUser;
    private final Runnable onDataChanged;

    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();
    private TableView<Transaction> table;

    // summary
    private Label balanceLabel, salesLabel, todayLabel, pendingLabel;

    // sale form
    private ComboBox<Product> productBox;
    private TextField qtyField, customerField, tenderedField;
    private ComboBox<String> paymentMethodBox;
    private Label stockInfoLabel, totalPreviewLabel;

    // wallet cash-in / cash-out form
    private ComboBox<TransactionType> cashTypeBox;
    private TextField cashAmountField, cashPartyField, cashNoteField;

    // filters
    private TextField searchField;
    private ComboBox<String> statusFilter;

    // void controls
    private Label detailLabel;
    private Button requestVoidBtn, approveBtn, rejectBtn, directVoidBtn;

    public TransactionPane(ProductService productService, InventoryService inventoryService,
                           TransactionService transactionService, Supplier<User> currentUser,
                           Runnable onDataChanged) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.transactionService = transactionService;
        this.currentUser = currentUser;
        this.onDataChanged = onDataChanged;

        VBox formsCol = new VBox(14, buildSalePane(), buildCashPane());
        ScrollPane formsScroll = Theme.scroll(formsCol);
        formsScroll.setPrefWidth(330);
        formsScroll.setMinWidth(330);
        formsScroll.setMaxWidth(330);

        VBox history = buildHistoryPane();
        HBox.setHgrow(history, Priority.ALWAYS);
        HBox body = new HBox(16, formsScroll, history);
        body.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(buildSummaryBar(), body);
        setSpacing(16);
        refresh();
    }

    private GridPane buildSummaryBar() {
        balanceLabel = new Label();
        salesLabel = new Label();
        todayLabel = new Label();
        pendingLabel = new Label();

        boolean allView = currentUser.get().canViewAllTransactions();
        return Theme.statGrid(4,
                Theme.statCard(allView ? "Cash balance" : "My net cash", balanceLabel, Theme.CRIMSON),
                Theme.statCard(allView ? "Total sales" : "My total sales", salesLabel, Theme.OK),
                Theme.statCard(allView ? "Today's sales" : "My today's sales", todayLabel, Theme.DEEP),
                Theme.statCard("Pending voids", pendingLabel, Theme.WARN));
    }

    private VBox buildSalePane() {
        Label title = new Label("New sale");
        title.getStyleClass().add("h2");

        productBox = new ComboBox<>();
        productBox.setPromptText("Choose a product");
        productBox.setMaxWidth(Double.MAX_VALUE);
        productBox.setConverter(new StringConverter<Product>() {
            @Override
            public String toString(Product p) {
                if (p == null) {
                    return "";
                }
                return Theme.codeOf(p) + "  -  " + Theme.peso(p.getPricePerKg()) + "/kg";
            }

            @Override
            public Product fromString(String s) {
                return null;
            }
        });
        productBox.valueProperty().addListener((o, a, b) -> updateSalePreview());

        qtyField = new TextField();
        qtyField.setPromptText("e.g. 2.5");
        qtyField.textProperty().addListener((o, a, b) -> updateSalePreview());

        customerField = new TextField();
        customerField.setPromptText("Walk-in (optional)");

        paymentMethodBox = new ComboBox<>();
        paymentMethodBox.getItems().addAll("Cash", "GCash", "Bank Transfer");
        paymentMethodBox.setValue("Cash");
        paymentMethodBox.setMaxWidth(Double.MAX_VALUE);

        tenderedField = new TextField();
        tenderedField.setPromptText("Exact amount if blank");
        tenderedField.textProperty().addListener((o, a, b) -> updateSalePreview());

        stockInfoLabel = Theme.mutedLabel("");
        totalPreviewLabel = new Label("Total: Php 0.00 | Change: Php 0.00");
        totalPreviewLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + Theme.DEEP + ";");
        totalPreviewLabel.setWrapText(true);

        Button saleBtn = new Button("Process sale");
        saleBtn.getStyleClass().add("btn-primary");
        saleBtn.setMaxWidth(Double.MAX_VALUE);
        saleBtn.setOnAction(e -> onSale());

        VBox box = new VBox(6, title,
                Theme.fieldLabel("Product"), productBox,
                Theme.fieldLabel("Quantity (kg)"), qtyField,
                Theme.fieldLabel("Customer"), customerField,
                Theme.fieldLabel("Payment method"), paymentMethodBox,
                Theme.fieldLabel("Amount tendered (Php)"), tenderedField,
                stockInfoLabel, new Separator(), totalPreviewLabel, saleBtn);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(16));
        return box;
    }

    private VBox buildCashPane() {
        Label title = new Label("Wallet (Cash In / Cash Out)");
        title.getStyleClass().add("h2");

        cashTypeBox = new ComboBox<>();
        cashTypeBox.getItems().addAll(TransactionType.CASH_IN, TransactionType.CASH_OUT);
        cashTypeBox.setValue(TransactionType.CASH_IN);
        cashTypeBox.setMaxWidth(Double.MAX_VALUE);

        cashAmountField = new TextField();
        cashAmountField.setPromptText("e.g. 1000");

        cashPartyField = new TextField();
        cashPartyField.setPromptText("Customer / Supplier (optional)");

        cashNoteField = new TextField();
        cashNoteField.setPromptText("e.g. Opening float, ice & supplies");

        Button cashBtn = new Button("Record cash entry");
        cashBtn.getStyleClass().add("btn-secondary");
        cashBtn.setMaxWidth(Double.MAX_VALUE);
        cashBtn.setOnAction(e -> onCashTransaction());

        VBox box = new VBox(6, title,
                Theme.fieldLabel("Type"), cashTypeBox,
                Theme.fieldLabel("Amount (Php)"), cashAmountField,
                Theme.fieldLabel("Customer / Source"), cashPartyField,
                Theme.fieldLabel("Note"), cashNoteField,
                cashBtn);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(16));
        return box;
    }

    private VBox buildHistoryPane() {
        Label title = new Label("Transaction history");
        title.getStyleClass().add("h2");

        searchField = new TextField();
        searchField.setPromptText("Search ID, customer, product/note or user...");
        searchField.textProperty().addListener((o, a, b) -> reloadRows());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        statusFilter = new ComboBox<>();
        statusFilter.getItems().add(ALL);
        for (TransactionStatus s : TransactionStatus.values()) {
            statusFilter.getItems().add(s.getLabel());
        }
        statusFilter.setValue(ALL);
        statusFilter.setOnAction(e -> reloadRows());

        Label statusLabel = Theme.fieldLabel("Status");
        HBox filters = new HBox(10, searchField, statusLabel, statusFilter);
        filters.setAlignment(Pos.CENTER_LEFT);

        table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transactions to show."));

        TableColumn<Transaction, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionId()));
        idCol.setPrefWidth(80);

        TableColumn<Transaction, LocalDateTime> timeCol = Theme.dateTimeColumn(
                "Date & Time", Transaction::getDateTime, 150);

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType().getLabel()));
        typeCol.setPrefWidth(75);

        TableColumn<Transaction, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        customerCol.setPrefWidth(105);

        TableColumn<Transaction, String> itemCol = new TableColumn<>("Product / Note");
        itemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        itemCol.setPrefWidth(180);

        TableColumn<Transaction, Double> qtyCol = Theme.doubleColumn(
                "Qty (kg)",
                t -> t.getType() == TransactionType.SALE ? t.getQuantityKg() : 0.0,
                v -> v <= 0 ? "-" : String.format("%.2f", v),
                75);

        TableColumn<Transaction, Double> totalCol = Theme.doubleColumn(
                "Amount",
                t -> t.getType().getSign() * t.getTotalAmount(),
                v -> (v < 0 ? "- " : "+ ") + Theme.peso(Math.abs(v)),
                115);

        TableColumn<Transaction, String> payCol = new TableColumn<>("Payment");
        payCol.setCellValueFactory(c -> {
            Transaction t = c.getValue();
            if (t.getType() != TransactionType.SALE) {
                return new SimpleStringProperty(t.getPaymentMethod());
            }
            return new SimpleStringProperty(t.getPaymentMethod() + " (Chg " + Theme.peso(t.getChangeAmount()) + ")");
        });
        payCol.setPrefWidth(130);

        TableColumn<Transaction, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerformedBy()));
        byCol.setPrefWidth(80);

        TableColumn<Transaction, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().getLabel()));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> new TableCell<Transaction, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                if (status.equals(TransactionStatus.VOIDED.getLabel())) {
                    setStyle(Theme.statusStyle(Theme.BAD));
                } else if (status.equals(TransactionStatus.VOID_REQUESTED.getLabel())) {
                    setStyle(Theme.statusStyle(Theme.WARN));
                } else {
                    setStyle(Theme.statusStyle(Theme.OK));
                }
            }
        });

        table.getColumns().addAll(idCol, timeCol, typeCol, customerCol, itemCol,
                qtyCol, totalCol, payCol, byCol, statusCol);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateVoidControls());
        VBox.setVgrow(table, Priority.ALWAYS);

        detailLabel = new Label();
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("muted");

        requestVoidBtn = new Button("Request void");
        requestVoidBtn.getStyleClass().add("btn-danger");
        requestVoidBtn.setOnAction(e -> onRequestVoid());

        approveBtn = new Button("Approve void");
        approveBtn.getStyleClass().add("btn-primary");
        approveBtn.setOnAction(e -> onApproveVoid());

        rejectBtn = new Button("Reject void");
        rejectBtn.getStyleClass().add("btn-secondary");
        rejectBtn.setOnAction(e -> onRejectVoid());

        directVoidBtn = new Button("Void directly");
        directVoidBtn.getStyleClass().add("btn-danger");
        directVoidBtn.setOnAction(e -> onDirectVoid());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(detailLabel, Priority.SOMETIMES);
        HBox actions = new HBox(8, detailLabel, spacer, requestVoidBtn, directVoidBtn, approveBtn, rejectBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(12, title, filters, table, actions);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    /** Reloads products, transaction rows, summary cards, and void buttons. */
    public void refresh() {
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
        User user = currentUser.get();
        if (user.canViewAllTransactions()) {
            balanceLabel.setText(Theme.peso(transactionService.calculateTotalCashBalance()));
            salesLabel.setText(Theme.peso(transactionService.calculateTotalSales()));
            todayLabel.setText(Theme.peso(transactionService.calculateTodaySales()));
            pendingLabel.setText(String.valueOf(transactionService.getPendingVoidRequests().size()));
        } else {
            String uname = user.getUsername();
            balanceLabel.setText(Theme.peso(transactionService.calculateBalanceByUser(uname)));
            salesLabel.setText(Theme.peso(transactionService.calculateSalesByUser(uname)));
            todayLabel.setText(Theme.peso(transactionService.calculateTodaySalesByUser(uname)));
            long ownPending = transactionService.getTransactionsByUser(uname).stream()
                    .filter(t -> t.getStatus() == TransactionStatus.VOID_REQUESTED)
                    .count();
            pendingLabel.setText(String.valueOf(ownPending));
        }
    }

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

        Transaction selected = table.getSelectionModel().getSelectedItem();
        String selectedId = selected == null ? null : selected.getTransactionId();

        List<Transaction> shown = new ArrayList<>();
        for (int i = source.size() - 1; i >= 0; i--) {
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
                || t.getCustomerName().toLowerCase().contains(keyword)
                || (t.getProductName() != null && t.getProductName().toLowerCase().contains(keyword))
                || t.getPerformedBy().toLowerCase().contains(keyword);
    }

    private void updateSalePreview() {
        Product p = productBox.getValue();
        if (p == null) {
            stockInfoLabel.setText("Select a product to see its stock.");
            totalPreviewLabel.setText("Total: Php 0.00 | Change: Php 0.00");
            return;
        }
        stockInfoLabel.setText("Available: " + String.format("%.2f", p.getStockQuantity()) + " kg   ("
                + inventoryService.getStatus(p) + ")");
        try {
            double rawQty = Double.parseDouble(qtyField.getText().trim());
            double qty = Theme.round2(rawQty);
            if (qty >= 0.01 && !Double.isInfinite(rawQty) && !Double.isNaN(rawQty)) {
                double total = Theme.round2(qty * p.getPricePerKg());
                double change = 0.0;
                String tText = tenderedField.getText().trim();
                if (!tText.isEmpty()) {
                    double tendered = Theme.round2(Double.parseDouble(tText));
                    change = Theme.round2(tendered - total);
                }
                String changeText = change < 0
                        ? "Short " + Theme.peso(Math.abs(change))
                        : Theme.peso(change);
                totalPreviewLabel.setText("Total: " + Theme.peso(total) + " | Change: " + changeText);
                return;
            }
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        totalPreviewLabel.setText("Total: Php 0.00 | Change: Php 0.00");
    }

    private void updateVoidControls() {
        User user = currentUser.get();
        Transaction t = table.getSelectionModel().getSelectedItem();

        requestVoidBtn.setVisible(user.canRequestVoid());
        requestVoidBtn.setManaged(user.canRequestVoid());
        approveBtn.setVisible(user.canApproveVoids());
        approveBtn.setManaged(user.canApproveVoids());
        rejectBtn.setVisible(user.canApproveVoids());
        rejectBtn.setManaged(user.canApproveVoids());
        directVoidBtn.setVisible(user.canApproveVoids());
        directVoidBtn.setManaged(user.canApproveVoids());

        if (t == null) {
            detailLabel.setText(user.canApproveVoids()
                    ? "Select a transaction to void directly or approve/reject a pending void."
                    : "Select one of your completed transactions to request a void.");
            requestVoidBtn.setDisable(true);
            approveBtn.setDisable(true);
            rejectBtn.setDisable(true);
            directVoidBtn.setDisable(true);
            return;
        }

        boolean completed = t.getStatus() == TransactionStatus.COMPLETED;
        boolean pending = t.getStatus() == TransactionStatus.VOID_REQUESTED;
        boolean ownTxn = t.getPerformedBy().equalsIgnoreCase(user.getUsername());

        requestVoidBtn.setDisable(!(completed && ownTxn));
        directVoidBtn.setDisable(!completed);
        approveBtn.setDisable(!pending);
        rejectBtn.setDisable(!pending);

        String text = t.getTransactionId() + " [" + t.getCustomerName() + "] - " + t.getStatus().getLabel();
        if (pending) {
            text += ". Requested by " + t.getVoidRequestedBy() + ": " + t.getVoidReason();
        } else if (t.getStatus() == TransactionStatus.VOIDED) {
            text += ". Voided by " + t.getVoidApprovedBy() + ". Reason: " + t.getVoidReason();
        }
        detailLabel.setText(text);
    }

    private void onSale() {
        Product p = productBox.getValue();
        if (p == null) {
            Theme.error("Choose a product first.");
            return;
        }
        try {
            double rawQty = Theme.parseNumber(qtyField.getText(), "Quantity");
            double qty = Theme.round2(rawQty);
            if (qty < 0.01) {
                throw new ValidationException("Quantity must be at least 0.01 kg.");
            }
            double total = Theme.round2(qty * p.getPricePerKg());
            double tendered = tenderedField.getText().trim().isEmpty()
                    ? total
                    : Theme.round2(Theme.parseNumber(tenderedField.getText(), "Amount tendered"));
            if (tendered < total) {
                throw new ValidationException("Insufficient payment. Total is " + Theme.peso(total) + ".");
            }
            double change = Theme.round2(tendered - total);
            String customer = customerField.getText().trim().isEmpty() ? "Walk-in" : customerField.getText().trim();
            String method = paymentMethodBox.getValue();

            String summary = String.format(
                    "Customer: %s\nSell %.2f kg of %s\nTotal: %s\nPayment: %s (%s) | Change: %s",
                    customer, qty, p.getName(), Theme.peso(total), method,
                    Theme.peso(tendered), Theme.peso(change));
            if (!Theme.confirm("Confirm sale", summary)) {
                return;
            }

            Transaction t = transactionService.processSale(
                    currentUser.get(), p.getProductId(), qty, customer, method, tendered);
            qtyField.clear();
            customerField.clear();
            tenderedField.clear();
            afterChange();
            Theme.info("Sale " + t.getTransactionId() + " recorded: " + String.format("%.2f", t.getQuantityKg())
                    + " kg of " + t.getProductName() + " = " + Theme.peso(t.getTotalAmount())
                    + " (Change: " + Theme.peso(t.getChangeAmount()) + ")");
        } catch (InsufficientStockException ex) {
            Theme.warn("Insufficient stock", ex.getMessage());
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onCashTransaction() {
        TransactionType type = cashTypeBox.getValue();
        try {
            double amount = Theme.round2(Theme.parseNumber(cashAmountField.getText(), "Amount"));
            String party = cashPartyField.getText();
            String note = cashNoteField.getText();

            if (!Theme.confirm("Confirm " + type.getLabel(),
                    type.getLabel() + " of " + Theme.peso(amount)
                            + "\nNote: " + (note.trim().isEmpty() ? type.getLabel() : note.trim()))) {
                return;
            }
            Transaction t = transactionService.processCashTransaction(
                    currentUser.get(), type, amount, party, note);
            cashAmountField.clear();
            cashPartyField.clear();
            cashNoteField.clear();
            afterChange();
            Theme.info(t.getType().getLabel() + " " + t.getTransactionId() + " recorded: "
                    + Theme.peso(t.getTotalAmount()));
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onRequestVoid() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            Theme.error("Select a transaction first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Request Void");
        dialog.setHeaderText("Void " + t.getTransactionId() + " (" + Theme.peso(t.getTotalAmount()) + ")?");
        dialog.setContentText("Reason:");
        Theme.style(dialog.getDialogPane());
        Optional<String> reason = dialog.showAndWait();
        if (reason.isEmpty()) {
            return;
        }
        try {
            transactionService.requestVoid(currentUser.get(), t.getTransactionId(), reason.get());
            afterChange();
            Theme.info("Void request sent to the manager.");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onDirectVoid() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            Theme.error("Select a transaction first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Direct Void");
        dialog.setHeaderText("Void " + t.getTransactionId() + " (" + Theme.peso(t.getTotalAmount()) + ") directly?");
        dialog.setContentText("Reason:");
        Theme.style(dialog.getDialogPane());
        Optional<String> reason = dialog.showAndWait();
        if (reason.isEmpty()) {
            return;
        }
        try {
            transactionService.voidTransaction(currentUser.get(), t.getTransactionId(), reason.get());
            afterChange();
            Theme.info(t.getTransactionId() + " has been voided.");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onApproveVoid() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            Theme.error("Select a transaction first.");
            return;
        }
        String extra = t.getType() == TransactionType.SALE
                ? "\n" + String.format("%.2f", t.getQuantityKg()) + " kg will be returned to stock." : "";
        if (!Theme.confirm("Approve Void",
                "Approve void of " + t.getTransactionId() + " (" + Theme.peso(t.getTotalAmount()) + ")?\n"
                        + "Reason: " + t.getVoidReason() + extra)) {
            return;
        }
        try {
            transactionService.approveVoid(currentUser.get(), t.getTransactionId());
            afterChange();
            Theme.info(t.getTransactionId() + " has been voided.");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onRejectVoid() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) {
            Theme.error("Select a transaction first.");
            return;
        }
        try {
            transactionService.rejectVoid(currentUser.get(), t.getTransactionId());
            afterChange();
            Theme.info("Void request for " + t.getTransactionId() + " was rejected.");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void afterChange() {
        refresh();
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }
}