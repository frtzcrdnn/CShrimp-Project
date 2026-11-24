package main;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.Product;
import model.Transaction;
import model.User;
import service.ShrimpSystem;

/**
 * Clerk Dashboard. Tabs: Overview, Inventory, Transactions (sales and void requests).
 *
 * The Overview shows the clerk's own sales, the low-stock alerts and the
 * clerk's sales history. When a manager opens it from the Manager Dashboard
 * (readOnly = true), only the Overview is shown, so the manager can look but
 * can't record anything in the clerk's name.
 */
public class ClerkDashboard extends Dashboard {

    private final boolean readOnly;

    private Label todayLabel, totalLabel, countLabel, pendingLabel, lowStockLabel;
    private final ObservableList<Product> lowStockData = FXCollections.observableArrayList();
    private final ObservableList<Transaction> historyData = FXCollections.observableArrayList();

    public ClerkDashboard(ShrimpSystem system, User user, Runnable onLogout, boolean readOnly) {
        super(system, user, readOnly ? "Clerk Dashboard (view only)" : "SHRIMP - Clerk Dashboard",
                readOnly ? null : onLogout);
        this.readOnly = readOnly;
        buildTabs();
        refreshAll();
    }

    @Override
    protected void buildTabs() {
        addTab("Overview", buildOverview(), this::refreshOverview);
        if (readOnly) {
            return;
        }

        Runnable changed = this::refreshAll;
        InventoryPane inventoryPane = new InventoryPane(system.getProductService(), system.getInventoryService(),
                () -> user, changed);
        TransactionPane transactionPane = new TransactionPane(system.getProductService(),
                system.getInventoryService(), system.getTransactionService(), () -> user, changed);

        addTab("Inventory", inventoryPane, inventoryPane::refresh);
        addTab("Transactions", transactionPane, transactionPane::refresh);
    }

    // ---------- OVERVIEW TAB ----------

    private VBox buildOverview() {
        todayLabel = new Label();
        totalLabel = new Label();
        countLabel = new Label();
        pendingLabel = new Label();
        lowStockLabel = new Label();

        HBox cards = new HBox(10,
                statCard("Sales Today", todayLabel, "#5cb85c"),
                statCard("Total Sales", totalLabel, "#5bc0de"),
                statCard("Completed Sales", countLabel, "#5bc0de"),
                statCard("Pending Voids", pendingLabel, "#f0ad4e"),
                statCard("Low-Stock Alerts", lowStockLabel, "#d9534f"));

        TableView<Product> lowStockTable = buildLowStockTable(lowStockData);
        TableView<Transaction> historyTable = buildTransactionTable(historyData);
        VBox left = new VBox(6, sectionTitle("Low-Stock Alerts"), lowStockTable);
        VBox right = new VBox(6, sectionTitle("Sales History"), historyTable);
        VBox.setVgrow(lowStockTable, Priority.ALWAYS);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(400);
        right.setPrefWidth(600);
        HBox middle = new HBox(12, left, right);
        VBox.setVgrow(middle, Priority.ALWAYS);

        VBox root = new VBox(12, cards, middle);
        root.setPadding(new Insets(12));
        return root;
    }

    private void refreshOverview() {
        String username = user.getUsername();

        todayLabel.setText(peso(system.getTodaySalesTotal(username)));
        totalLabel.setText(peso(system.getCompletedSalesTotal(username)));
        countLabel.setText(String.valueOf(system.getCompletedSalesCount(username)));
        pendingLabel.setText(String.valueOf(system.getPendingVoidCount(username)));
        lowStockLabel.setText(String.valueOf(system.getInventory().getLowStockItems().size()));

        lowStockData.setAll(system.getInventory().getLowStockItems());
        historyData.setAll(system.newestFirst(system.getTransactionService().getTransactionsByUser(username), 50));
    }
}
