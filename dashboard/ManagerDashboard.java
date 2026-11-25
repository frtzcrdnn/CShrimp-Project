package dashboard;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Clerk;
import model.Product;
import model.Transaction;
import model.User;
import service.ShrimpSystem;
import service.TransactionService;
import ui.AccountPane;
import ui.InventoryPane;
import ui.ProductPane;
import ui.Theme;
import ui.TransactionPane;

/**
 * Manager Dashboard. Tabs: Overview, Products, Inventory, Transactions, Accounts.
 */
public class ManagerDashboard extends Dashboard {

    private Label totalSalesLabel, salesCountLabel, balanceLabel, lowStockLabel, outOfStockLabel, pendingLabel;

    private final ObservableList<Product> lowStockData = FXCollections.observableArrayList();
    private final ObservableList<Transaction> recentData = FXCollections.observableArrayList();
    private final ObservableList<ClerkSale> clerkData = FXCollections.observableArrayList();
    private TableView<ClerkSale> clerkTable;

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
        super(system, user, "Manager Console", true, onLogout);
        buildTabs();
        refreshAll();
    }

    @Override
    protected void buildTabs() {
        Runnable changed = this::refreshAll;

        ProductPane productPane = new ProductPane(system.getProductService(), system.getInventoryService(),
                () -> user, changed);
        InventoryPane inventoryPane = new InventoryPane(system.getProductService(), system.getInventoryService(),
                () -> user, changed);
        TransactionPane transactionPane = new TransactionPane(system.getProductService(),
                system.getInventoryService(), system.getTransactionService(), () -> user, changed);
        AccountPane accountPane = new AccountPane(system.getAuthenticationService(), () -> user, changed);

        addTab("Overview", "Sales, stock and clerk activity at a glance.", buildOverview(), this::refreshOverview);
        addTab("Products", "Browse the product list. Click a product for its details.", productPane, productPane::refresh);
        addTab("Inventory", "Stock levels, restocking and the stock history.", inventoryPane, inventoryPane::refresh);
        addTab("Transactions", "Record sales, manage cash wallet and approve/reject voids.",
                transactionPane, transactionPane::refresh);
        addTab("Accounts", "Approve, create and manage clerk employee accounts.", accountPane, accountPane::refresh);
    }

    private Node buildOverview() {
        totalSalesLabel = new Label();
        salesCountLabel = new Label();
        balanceLabel = new Label();
        lowStockLabel = new Label();
        outOfStockLabel = new Label();
        pendingLabel = new Label();

        GridPane cards = Theme.statGrid(3,
                statCard("Total sales", totalSalesLabel, Theme.OK),
                statCard("Completed sales", salesCountLabel, Theme.DEEP),
                statCard("Cash balance", balanceLabel, Theme.CRIMSON),
                statCard("Low-stock items", lowStockLabel, Theme.WARN),
                statCard("Out of stock", outOfStockLabel, Theme.BAD),
                statCard("Pending voids", pendingLabel, Theme.WARN));

        TableView<Product> lowStockTable = buildLowStockTable(lowStockData);
        TableView<Transaction> recentTable = buildTransactionTable(recentData);
        VBox left = Theme.panel("Low-stock items", lowStockTable);
        VBox right = Theme.panel("Recent transactions", recentTable);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(340);
        right.setPrefWidth(540);
        HBox middle = new HBox(16, left, right);

        VBox root = new VBox(18, cards, middle, buildClerkSection());
        root.setPadding(new Insets(0, 4, 4, 0));
        return Theme.scroll(root);
    }

    private VBox buildClerkSection() {
        clerkTable = new TableView<>(clerkData);
        clerkTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        clerkTable.setPlaceholder(new Label("No clerks yet."));
        clerkTable.setPrefHeight(150);

        TableColumn<ClerkSale, String> nameCol = new TableColumn<>("Clerk");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().clerk.getFullName()));
        nameCol.setPrefWidth(200);

        TableColumn<ClerkSale, String> userCol = new TableColumn<>("Username");
        userCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().clerk.getUsername()));
        userCol.setPrefWidth(120);

        TableColumn<ClerkSale, Integer> countCol = Theme.intColumn(
                "Completed sales", c -> c.salesCount, 130);
        TableColumn<ClerkSale, Double> totalCol = Theme.doubleColumn(
                "Total sales", c -> c.salesTotal, Theme::peso, 140);
        TableColumn<ClerkSale, Integer> pendingCol = Theme.intColumn(
                "Pending voids", c -> c.pendingVoids, 130);

        clerkTable.getColumns().addAll(nameCol, userCol, countCol, totalCol, pendingCol);

        Button viewBtn = new Button("View clerk dashboard");
        viewBtn.getStyleClass().add("btn-secondary");
        viewBtn.setOnAction(e -> openClerkDashboard());
        viewBtn.disableProperty().bind(clerkTable.getSelectionModel().selectedItemProperty().isNull());

        Label hint = Theme.mutedLabel("Select a clerk to open their dashboard (view only).");
        HBox actions = Theme.spacerRow(hint, viewBtn);

        VBox body = new VBox(10, clerkTable, actions);
        return Theme.panel("Clerk sales history", body);
    }

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
        Scene viewScene = new Scene(view, 1050, 650);
        if (!Theme.stylesheet().isEmpty()) {
            viewScene.getStylesheets().add(Theme.stylesheet());
        }
        window.setScene(viewScene);
        registerChildWindow(window);
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