package main;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import model.Manager;
import model.Product;
import model.Transaction;
import model.TransactionStatus;
import model.TransactionType;
import model.User;
import service.ShrimpSystem;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class of the two dashboards (ManagerDashboard and ClerkDashboard).
 *
 * It gives them the same look: a red header with the user's name and a Log Out
 * button, and a row of tabs under it. It also holds the pieces they share
 * (summary cards and the tables). Each subclass only decides WHICH tabs it has.
 *
 * Every tab can register a "refresher". When something changes in one tab
 * (for example a sale lowers the stock), refreshAll() updates all the others.
 */
public abstract class Dashboard extends BorderPane {

    protected static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");

    protected final ShrimpSystem system;
    protected final User user;

    private final TabPane tabs = new TabPane();
    private final List<Runnable> refreshers = new ArrayList<>();

    /**
     * @param onLogout what the Log Out button does; null hides the button
     *                 (used when a manager only looks at a clerk's dashboard)
     */
    protected Dashboard(ShrimpSystem system, User user, String title, Runnable onLogout) {
        this.system = system;
        this.user = user;

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label userLabel = new Label(user.getFullName() + "  (" + user.getRole() + ")");
        userLabel.setStyle("-fx-text-fill: white;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(14, titleLabel, spacer, userLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14));
        header.setStyle("-fx-background-color: #d9534f;");

        if (onLogout != null) {
            Button logoutBtn = new Button("Log Out");
            logoutBtn.setOnAction(e -> onLogout.run());
            header.getChildren().add(logoutBtn);
        }

        setTop(header);
        setCenter(tabs);
    }

    /** Picks the right dashboard for the user who just logged in. */
    public static Dashboard create(ShrimpSystem system, User user, Runnable onLogout) {
        if (user instanceof Manager) {
            return new ManagerDashboard(system, user, onLogout);
        }
        return new ClerkDashboard(system, user, onLogout, false);
    }

    /** Each dashboard adds its own tabs here, using addTab(). */
    protected abstract void buildTabs();

    /** Adds a tab. The refresher runs every time the tab is opened and on refreshAll(). */
    protected void addTab(String title, Node content, Runnable refresher) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        if (refresher != null) {
            refreshers.add(refresher);
            tab.setOnSelectionChanged(e -> {
                if (tab.isSelected()) {
                    refresher.run();
                }
            });
        }
        tabs.getTabs().add(tab);
    }

    /** Updates every tab (called after a sale, a restock, a void, a product change...). */
    public void refreshAll() {
        for (Runnable r : refreshers) {
            r.run();
        }
    }

    // ---------- SHARED BUILDING BLOCKS ----------

    protected static String peso(double amount) {
        return "Php " + String.format("%,.2f", amount);
    }

    // one small summary box with a title and a big number
    protected VBox statCard(String title, Label value, String color) {
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        VBox box = new VBox(2, t, value);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setStyle("-fx-background-color: #f4f4f4; -fx-background-radius: 6;");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    protected Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 14));
        return l;
    }

    // products that are low or out of stock
    protected TableView<Product> buildLowStockTable(ObservableList<Product> data) {
        TableView<Product> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("All products have enough stock."));
        table.setPrefHeight(200);

        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(200);

        TableColumn<Product, String> stockCol = new TableColumn<>("Stock (kg)");
        stockCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.2f", c.getValue().getStockQuantity())));
        stockCol.setPrefWidth(90);

        TableColumn<Product, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(system.getInventory().getStatus(c.getValue())));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(status);
                setTextFill(status.equals("OUT OF STOCK") ? Color.RED : Color.DARKORANGE);
            }
        });

        TableColumn<Product, String> reportCol = new TableColumn<>("Report");
        reportCol.setCellValueFactory(c -> new SimpleStringProperty(
                system.getInventory().hasRestockRequest(c.getValue()) ? "Reported" : ""));

        table.getColumns().addAll(nameCol, stockCol, statusCol, reportCol);
        return table;
    }

    // a list of transactions (sales history)
    protected TableView<Transaction> buildTransactionTable(ObservableList<Transaction> data) {
        TableView<Transaction> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transactions yet."));
        table.setPrefHeight(200);

        TableColumn<Transaction, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionId()));
        idCol.setPrefWidth(75);

        TableColumn<Transaction, String> timeCol = new TableColumn<>("Date & Time");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDateTime().format(TIME)));
        timeCol.setPrefWidth(150);

        TableColumn<Transaction, String> itemCol = new TableColumn<>("Product");
        itemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        itemCol.setPrefWidth(180);

        TableColumn<Transaction, String> qtyCol = new TableColumn<>("Qty (kg)");
        qtyCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getType() == TransactionType.SALE
                        ? String.format("%.2f", c.getValue().getQuantityKg()) : "-"));
        qtyCol.setPrefWidth(75);

        TableColumn<Transaction, String> totalCol = new TableColumn<>("Amount");
        totalCol.setCellValueFactory(c -> new SimpleStringProperty(peso(c.getValue().getTotalAmount())));
        totalCol.setPrefWidth(105);

        TableColumn<Transaction, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerformedBy()));
        byCol.setPrefWidth(80);

        TableColumn<Transaction, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().getLabel()));
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

        table.getColumns().addAll(idCol, timeCol, itemCol, qtyCol, totalCol, byCol, statusCol);
        return table;
    }
}
