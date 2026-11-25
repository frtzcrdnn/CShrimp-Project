package dashboard;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Manager;
import model.Product;
import model.Transaction;
import model.TransactionStatus;
import model.TransactionType;
import model.User;
import service.ShrimpSystem;
import service.ValidationException;
import ui.Theme;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class of the two dashboards (Clerk & Manager)
 * Provides the role-specific sidebar or topbar chrome, tab navigation, refresh hooks, and secondary window cleanup on logout.
 */
public abstract class Dashboard extends BorderPane {

    protected final ShrimpSystem system;
    protected final User user;

    private final boolean sidebar;
    private final Pane navBox;
    private final StackPane pages = new StackPane();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final Map<String, ToggleButton> navButtons = new LinkedHashMap<>();
    private final List<Stage> childWindows = new ArrayList<>();

    protected Dashboard(ShrimpSystem system, User user, String consoleLabel, boolean sidebar, Runnable onLogout) {
        this.system = system;
        this.user = user;
        this.sidebar = sidebar;
        this.navBox = sidebar ? new VBox(6) : new HBox(8);

        navGroup.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null && was != null) {
                was.setSelected(true);
            }
        });

        if (sidebar) {
            setLeft(buildSidebar(consoleLabel, onLogout));
        } else {
            setTop(buildTopBar(consoleLabel, onLogout));
        }

        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-sub");
        VBox header = new VBox(2, pageTitle, pageSubtitle);
        VBox.setVgrow(pages, Priority.ALWAYS);

        VBox main = new VBox(16, header, pages);
        main.getStyleClass().add("app-bg");
        main.setPadding(new Insets(sidebar ? 28 : 22, 28, 24, 28));
        setCenter(main);
    }

    // ---------- CHROME ----------

    private VBox buildSidebar(String consoleLabel, Runnable onLogout) {
        Label console = new Label(consoleLabel);
        console.setStyle("-fx-text-fill: #F6DCD8; -fx-font-size: 13px; -fx-font-weight: bold;");
        VBox brand = new VBox(6, Theme.logo(80), console);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 0, 18, 4));

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        VBox side = new VBox(6, brand, navBox, grow, buildProfile(true));
        if (onLogout != null) {
            Button pwdBtn = new Button("Change Password");
            pwdBtn.getStyleClass().add("btn-light");
            pwdBtn.setMaxWidth(Double.MAX_VALUE);
            pwdBtn.setOnAction(e -> showChangePasswordDialog());

            Button logout = new Button("Log Out");
            logout.getStyleClass().add("btn-light");
            logout.setMaxWidth(Double.MAX_VALUE);
            logout.setOnAction(e -> onLogout.run());

            side.getChildren().addAll(pwdBtn, logout);
        }
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(26, 16, 20, 16));
        side.setPrefWidth(240);
        side.setMinWidth(240);
        side.setMaxWidth(240);
        return side;
    }

    private HBox buildTopBar(String consoleLabel, Runnable onLogout) {
        Label console = new Label(consoleLabel);
        console.setStyle("-fx-text-fill: #FFE9E6; -fx-font-size: 12px; -fx-font-weight: bold;");
        HBox brand = new HBox(10, Theme.logo(48), console);
        brand.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (navBox instanceof HBox navRow) {
            navRow.setAlignment(Pos.CENTER_LEFT);
        }
        HBox bar = new HBox(18, brand, navBox, spacer, buildProfile(false));
        bar.setAlignment(Pos.CENTER_LEFT);
        if (onLogout != null) {
            Button pwdBtn = new Button("Password");
            pwdBtn.getStyleClass().add("btn-light");
            pwdBtn.setOnAction(e -> showChangePasswordDialog());

            Button logout = new Button("Log Out");
            logout.getStyleClass().add("btn-light");
            logout.setOnAction(e -> onLogout.run());

            bar.getChildren().addAll(pwdBtn, logout);
        }
        bar.getStyleClass().add("topbar");
        bar.setPadding(new Insets(12, 24, 12, 24));
        return bar;
    }

    private HBox buildProfile(boolean leftAligned) {
        Label fullName = new Label(user.getFullName());
        fullName.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label role = new Label(user.getRole());
        role.setStyle("-fx-text-fill: #F6DCD8; -fx-font-size: 11px;");
        VBox text = new VBox(0, fullName, role);
        text.setAlignment(leftAligned ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);

        Node avatar = Theme.avatar(user, 44);
        HBox box = leftAligned ? new HBox(10, avatar, text) : new HBox(10, text, avatar);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 4, 10, 4));
        return box;
    }

    /** Picks the right dashboard for the authenticated user. */
    public static Dashboard create(ShrimpSystem system, User user, Runnable onLogout) {
        if (user instanceof Manager) {
            return new ManagerDashboard(system, user, onLogout);
        }
        return new ClerkDashboard(system, user, onLogout, false);
    }

    // ---------- PAGES & CHILD WINDOWS ----------

    protected abstract void buildTabs();

    protected void addTab(String title, Node content, Runnable refresher) {
        addTab(title, "", content, refresher);
    }

    protected void addTab(String title, String subtitle, Node content, Runnable refresher) {
        ToggleButton button = new ToggleButton(title);
        button.setToggleGroup(navGroup);
        button.getStyleClass().add(sidebar ? "nav-side" : "nav-top");
        if (sidebar) {
            button.setMaxWidth(Double.MAX_VALUE);
        }

        content.setVisible(false);
        content.setManaged(false);
        pages.getChildren().add(content);
        navButtons.put(title, button);
        navBox.getChildren().add(button);
        if (refresher != null) {
            refreshers.add(refresher);
        }

        button.selectedProperty().addListener((o, was, selected) -> {
            if (selected) {
                showPage(content, title, subtitle, refresher);
            }
        });
        if (navGroup.getSelectedToggle() == null) {
            button.setSelected(true);
        }
    }

    protected void openTab(String title) {
        ToggleButton button = navButtons.get(title);
        if (button != null) {
            button.setSelected(true);
        }
    }

    private void showPage(Node content, String title, String subtitle, Runnable refresher) {
        for (Node n : pages.getChildren()) {
            boolean shown = n == content;
            n.setVisible(shown);
            n.setManaged(shown);
        }
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
        boolean hasSubtitle = subtitle != null && !subtitle.isEmpty();
        pageSubtitle.setVisible(hasSubtitle);
        pageSubtitle.setManaged(hasSubtitle);
        if (refresher != null) {
            refresher.run();
        }
    }

    /** Updates every registered page after a data change. */
    public void refreshAll() {
        for (Runnable r : refreshers) {
            r.run();
        }
    }

    /** Registers a child window so it is closed automatically upon logout. */
    protected void registerChildWindow(Stage window) {
        childWindows.add(window);
        window.setOnHidden(e -> childWindows.remove(window));
    }

    /** Closes all open secondary windows owned by this dashboard. */
    public void closeChildWindows() {
        for (Stage window : new ArrayList<>(childWindows)) {
            if (window.isShowing()) {
                window.close();
            }
        }
        childWindows.clear();
    }

    private void showChangePasswordDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Update password for " + user.getUsername());

        PasswordField currentField = new PasswordField();
        PasswordField newField = new PasswordField();

        VBox content = new VBox(8,
                Theme.fieldLabel("Current Password"), currentField,
                Theme.fieldLabel("New Password (min 6 chars)"), newField);
        content.setPadding(new Insets(10, 6, 6, 6));
        content.setPrefWidth(320);

        dialog.getDialogPane().setContent(content);
        Theme.style(dialog.getDialogPane());
        ButtonType saveType = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.getStyleClass().add("btn-primary");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).getStyleClass().add("btn-secondary");

        saveBtn.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                system.getAuthenticationService().changeOwnPassword(
                        user, currentField.getText(), newField.getText());
                Theme.info("Your password has been updated.");
            } catch (ValidationException ex) {
                Theme.error(ex.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    // ---------- SHARED BUILDING BLOCKS ----------

    protected static String peso(double amount) {
        return Theme.peso(amount);
    }

    protected VBox statCard(String title, Label value, String color) {
        return Theme.statCard(title, value, color);
    }

    protected TableView<Product> buildLowStockTable(ObservableList<Product> data) {
        TableView<Product> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("All products have enough stock."));
        table.setPrefHeight(260);

        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(Theme.codeOf(c.getValue())));
        nameCol.setPrefWidth(100);

        TableColumn<Product, Double> stockCol = Theme.doubleColumn(
                "Stock (kg)", Product::getStockQuantity, v -> String.format("%.2f", v), 90);

        TableColumn<Product, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(system.getInventory().getStatus(c.getValue())));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                setStyle(Theme.statusStyle(status.equals("OUT OF STOCK") ? Theme.BAD : Theme.WARN));
            }
        });

        TableColumn<Product, String> reportCol = new TableColumn<>("Report");
        reportCol.setCellValueFactory(c -> new SimpleStringProperty(
                system.getInventory().hasRestockRequest(c.getValue()) ? "Reported" : ""));

        table.getColumns().addAll(nameCol, stockCol, statusCol, reportCol);
        return table;
    }

    protected TableView<Transaction> buildTransactionTable(ObservableList<Transaction> data) {
        TableView<Transaction> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transactions yet."));
        table.setPrefHeight(260);

        TableColumn<Transaction, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionId()));
        idCol.setPrefWidth(75);

        TableColumn<Transaction, LocalDateTime> timeCol = Theme.dateTimeColumn(
                "Date & Time", Transaction::getDateTime, 145);

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType().getLabel()));
        typeCol.setPrefWidth(70);

        TableColumn<Transaction, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        customerCol.setPrefWidth(95);

        TableColumn<Transaction, String> itemCol = new TableColumn<>("Item / Note");
        itemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        itemCol.setPrefWidth(140);

        TableColumn<Transaction, Double> qtyCol = Theme.doubleColumn(
                "Qty (kg)",
                t -> t.getType() == TransactionType.SALE ? t.getQuantityKg() : 0.0,
                v -> v <= 0 ? "-" : String.format("%.2f", v),
                70);

        TableColumn<Transaction, Double> totalCol = Theme.doubleColumn(
                "Amount",
                t -> t.getType().getSign() * t.getTotalAmount(),
                v -> (v < 0 ? "- " : "+ ") + Theme.peso(Math.abs(v)),
                110);

        TableColumn<Transaction, String> byCol = new TableColumn<>("By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerformedBy()));
        byCol.setPrefWidth(75);

        TableColumn<Transaction, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().getLabel()));
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

        table.getColumns().addAll(idCol, timeCol, typeCol, customerCol, itemCol, qtyCol, totalCol, byCol, statusCol);
        return table;
    }
}