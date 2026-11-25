package dashboard;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Product;
import model.Transaction;
import model.User;
import service.ShrimpSystem;
import ui.InventoryPane;
import ui.Theme;
import ui.TransactionPane;

/**
 * Clerk Dashboard. Tabs: Overview, Inventory, Transactions.
 * When opened in read-only mode by a Manager, only the Overview tab is displayed.
 */
public class ClerkDashboard extends Dashboard {

    private final boolean readOnly;

    private Label todayLabel, totalLabel, countLabel, pendingLabel, lowStockLabel;
    private final ObservableList<Product> lowStockData = FXCollections.observableArrayList();
    private final ObservableList<Transaction> historyData = FXCollections.observableArrayList();

    public ClerkDashboard(ShrimpSystem system, User user, Runnable onLogout, boolean readOnly) {
        super(system, user, readOnly ? "Viewing clerk (read-only)" : "Clerk Counter", false,
                readOnly ? null : onLogout);
        this.readOnly = readOnly;
        buildTabs();
        refreshAll();
    }

    @Override
    protected void buildTabs() {
        addTab("Overview", "Hello, " + user.getFullName().split(" ")[0] + ". Here is your counter today.",
                buildOverview(), this::refreshOverview);
        if (readOnly) {
            return;
        }

        Runnable changed = this::refreshAll;
        InventoryPane inventoryPane = new InventoryPane(system.getProductService(), system.getInventoryService(),
                () -> user, changed);
        TransactionPane transactionPane = new TransactionPane(system.getProductService(),
                system.getInventoryService(), system.getTransactionService(), () -> user, changed);

        addTab("Inventory", "Check stock and report products that are running low.", inventoryPane, inventoryPane::refresh);
        addTab("Transactions", "Record sales, cash entries and see your history.", transactionPane, transactionPane::refresh);
    }

    private Node buildOverview() {
        todayLabel = new Label();
        totalLabel = new Label();
        countLabel = new Label();
        pendingLabel = new Label();
        lowStockLabel = new Label();

        GridPane cards = Theme.statGrid(5,
                statCard("Sales today", todayLabel, Theme.OK),
                statCard("Total sales", totalLabel, Theme.DEEP),
                statCard("Completed sales", countLabel, Theme.CRIMSON),
                statCard("Pending voids", pendingLabel, Theme.WARN),
                statCard("Low-stock alerts", lowStockLabel, Theme.BAD));

        TableView<Product> lowStockTable = buildLowStockTable(lowStockData);
        TableView<Transaction> historyTable = buildTransactionTable(historyData);
        VBox left = Theme.panel("Low-stock alerts", lowStockTable);
        VBox right = Theme.panel("My transaction history", historyTable);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(380);
        right.setPrefWidth(640);
        HBox middle = new HBox(16, left, right);
        VBox.setVgrow(middle, Priority.ALWAYS);

        VBox root = new VBox(16);
        if (!readOnly) {
            root.getChildren().add(buildNewSaleBanner());
        }
        root.getChildren().addAll(cards, middle);
        root.setPadding(new Insets(0, 4, 4, 0));
        return Theme.scroll(root);
    }

    private HBox buildNewSaleBanner() {
        Label title = new Label("Ready for the next customer?");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label text = new Label("Pick a product, enter the weight and the total is worked out for you.");
        text.setStyle("-fx-text-fill: #FFE9E6;");
        Button go = new Button("Start a new sale");
        go.getStyleClass().add("btn-secondary");
        go.setOnAction(e -> openTab("Transactions"));

        HBox banner = Theme.spacerRow(new VBox(2, title, text), go);
        banner.setPadding(new Insets(16, 20, 16, 20));
        banner.setStyle("-fx-background-color: linear-gradient(to right, #B8394B, #E08A8F);"
                + "-fx-background-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(107,29,42,0.20), 14, 0, 0, 3);");
        return banner;
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