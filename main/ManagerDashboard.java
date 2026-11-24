package main;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.Clerk;
import model.Product;
import model.Transaction;
import model.User;
import service.ShrimpSystem;
import service.TransactionService;

/**
 * Manager Dashboard. Tabs: Overview, Products, Inventory, Transactions.
 *
 * The Overview shows total sales, low-stock items, recent transactions and the
 * sales history of every clerk. The manager can open any clerk's dashboard
 * (view only) from there.
 */
public class ManagerDashboard extends Dashboard {

    private Label totalSalesLabel, salesCountLabel, balanceLabel, lowStockLabel, outOfStockLabel, pendingLabel;

    private final ObservableList<Product> lowStockData = FXCollections.observableArrayList();
    private final ObservableList<Transaction> recentData = FXCollections.observableArrayList();
    private final ObservableList<ClerkSale> clerkData = FXCollections.observableArrayList();
    private TableView<ClerkSale> clerkTable;

    /** One row of the "Clerk Sales History" table. */
    private static class ClerkSale {
        final Clerk clerk;
        final int salesCount;
        final double salesTotal;
        final int pendingVoids;

        ClerkSale(Clerk clerk, int salesCount, double salesTotal, int pendingVoids) {
            this.clerk = clerk;
            this.salesCount = salesCount;
            this.salesTotal = salesTotal;
            this.pendingVoids = pendingVoids;
        }
    }

    public ManagerDashboard(ShrimpSystem system, User user, Runnable onLogout) {
        super(system, user, "SHRIMP - Manager Dashboard", onLogout);
        buildTabs();
        refreshAll();
    }

    @Override
    protected void buildTabs() {
        // every pane asks this dashboard to refresh the other tabs when it changes something
        Runnable changed = this::refreshAll;

        ProductPane productPane = new ProductPane(system.getProductService(), system.getInventoryService(),
                () -> user, changed);
        InventoryPane inventoryPane = new InventoryPane(system.getProductService(), system.getInventoryService(),
                () -> user, changed);
        TransactionPane transactionPane = new TransactionPane(system.getProductService(),
                system.getInventoryService(), system.getTransactionService(), () -> user, changed);

        addTab("Overview", buildOverview(), this::refreshOverview);
        addTab("Products", productPane, productPane::refresh);
        addTab("Inventory", inventoryPane, inventoryPane::refresh);
        addTab("Transactions", transactionPane, transactionPane::refresh);
    }

    // ---------- OVERVIEW TAB ----------

    private VBox buildOverview() {
        totalSalesLabel = new Label();
        salesCountLabel = new Label();
        balanceLabel = new Label();
        lowStockLabel = new Label();
        outOfStockLabel = new Label();
        pendingLabel = new Label();

        HBox cards = new HBox(10,
                statCard("Total Sales", totalSalesLabel, "#5cb85c"),
                statCard("Completed Sales", salesCountLabel, "#5bc0de"),
                statCard("Cash Balance", balanceLabel, "#d9534f"),
                statCard("Low-Stock Items", lowStockLabel, "#f0ad4e"),
                statCard("Out of Stock", outOfStockLabel, "#d9534f"),
                statCard("Pending Voids", pendingLabel, "#f0ad4e"));

        TableView<Product> lowStockTable = buildLowStockTable(lowStockData);
        TableView<Transaction> recentTable = buildTransactionTable(recentData);
        VBox left = new VBox(6, sectionTitle("Low-Stock Items"), lowStockTable);
        VBox right = new VBox(6, sectionTitle("Recent Transactions"), recentTable);
        VBox.setVgrow(lowStockTable, Priority.ALWAYS);
        VBox.setVgrow(recentTable, Priority.ALWAYS);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(400);
        right.setPrefWidth(600);
        HBox middle = new HBox(12, left, right);

        VBox root = new VBox(12, cards, middle, buildClerkSection());
        VBox.setVgrow(middle, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        return root;
    }

    // the clerk sales history + the button that opens a clerk's dashboard
    private VBox buildClerkSection() {
        clerkTable = new TableView<>(clerkData);
        clerkTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        clerkTable.setPlaceholder(new Label("No clerks yet."));
        clerkTable.setPrefHeight(130);

        TableColumn<ClerkSale, String> nameCol = new TableColumn<>("Clerk");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().clerk.getFullName()));
        nameCol.setPrefWidth(200);

        TableColumn<ClerkSale, String> userCol = new TableColumn<>("Username");
        userCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().clerk.getUsername()));
        userCol.setPrefWidth(120);

        TableColumn<ClerkSale, String> countCol = new TableColumn<>("Completed sales");
        countCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().salesCount)));
        countCol.setPrefWidth(130);

        TableColumn<ClerkSale, String> totalCol = new TableColumn<>("Total sales");
        totalCol.setCellValueFactory(c -> new SimpleStringProperty(peso(c.getValue().salesTotal)));
        totalCol.setPrefWidth(140);

        TableColumn<ClerkSale, String> pendingCol = new TableColumn<>("Pending void requests");
        pendingCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().pendingVoids)));

        clerkTable.getColumns().addAll(nameCol, userCol, countCol, totalCol, pendingCol);

        Button viewBtn = new Button("View Clerk Dashboard");
        viewBtn.setOnAction(e -> openClerkDashboard());
        viewBtn.disableProperty().bind(clerkTable.getSelectionModel().selectedItemProperty().isNull());

        HBox top = new HBox(12, sectionTitle("Clerk Sales History"), viewBtn);
        return new VBox(6, top, clerkTable);
    }

    // opens the selected clerk's dashboard in its own window (view only)
    private void openClerkDashboard() {
        ClerkSale selected = clerkTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ClerkDashboard view = new ClerkDashboard(system, selected.clerk, null, true);
        Stage window = new Stage();
        window.setTitle("Clerk Dashboard - " + selected.clerk.getFullName());
        if (getScene() != null) {
            window.initOwner(getScene().getWindow());
        }
        window.setScene(new Scene(view, 950, 600));
        window.show();
    }

    private void refreshOverview() {
        TransactionService transactions = system.getTransactionService();

        totalSalesLabel.setText(peso(transactions.calculateTotalSales()));
        salesCountLabel.setText(String.valueOf(system.getCompletedSalesCount()));
        balanceLabel.setText(peso(transactions.calculateTotalCashBalance()));
        lowStockLabel.setText(String.valueOf(system.getInventory().getLowStockCount()));
        outOfStockLabel.setText(String.valueOf(system.getInventory().getOutOfStockCount()));
        pendingLabel.setText(String.valueOf(transactions.getPendingVoidRequests().size()));

        lowStockData.setAll(system.getInventory().getLowStockItems());
        recentData.setAll(system.newestFirst(transactions.getAllTransactions(), 10));

        // keep the selected clerk selected after the table reloads
        String selectedUsername = null;
        ClerkSale selected = clerkTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedUsername = selected.clerk.getUsername();
        }
        clerkData.clear();
        for (Clerk c : system.getAuthenticationService().getClerks()) {
            clerkData.add(new ClerkSale(c,
                    system.getCompletedSalesCount(c.getUsername()),
                    system.getCompletedSalesTotal(c.getUsername()),
                    system.getPendingVoidCount(c.getUsername())));
        }
        if (selectedUsername != null) {
            for (ClerkSale row : clerkData) {
                if (row.clerk.getUsername().equals(selectedUsername)) {
                    clerkTable.getSelectionModel().select(row);
                    break;
                }
            }
        }
    }
}
