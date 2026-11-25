package ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.Clerk;
import model.User;
import service.AuthenticationService;
import service.ValidationException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Clerk Account Management screen (Manager only).
 * For adding clerks, approving/deactivating accounts, and resetting passwords.
 */
public class AccountPane extends VBox {

    private final AuthenticationService authService;
    private final Supplier<User> currentUser;
    private final Runnable onDataChanged;

    private final ObservableList<Clerk> clerkData = FXCollections.observableArrayList();
    private TableView<Clerk> table;
    private Button approveBtn, deactivateBtn, resetPwdBtn;

    public AccountPane(AuthenticationService authService, Supplier<User> currentUser,
                       Runnable onDataChanged) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.onDataChanged = onDataChanged;

        Label heading = new Label("Clerk accounts");
        heading.getStyleClass().add("h2");

        Button addBtn = new Button("+ Add clerk account");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> showAddClerkDialog());

        HBox top = Theme.spacerRow(heading, addBtn);

        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        approveBtn = new Button("Approve / Activate");
        approveBtn.getStyleClass().add("btn-primary");
        approveBtn.setOnAction(e -> onSetApproved(true));

        deactivateBtn = new Button("Deactivate");
        deactivateBtn.getStyleClass().add("btn-danger");
        deactivateBtn.setOnAction(e -> onSetApproved(false));

        resetPwdBtn = new Button("Reset password");
        resetPwdBtn.getStyleClass().add("btn-secondary");
        resetPwdBtn.setOnAction(e -> onResetPassword());

        Label hint = Theme.mutedLabel("Approved clerks can sign in and appear in Clerk Sales History.");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, hint, spacer, approveBtn, deactivateBtn, resetPwdBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, top, table, actions);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        VBox.setVgrow(card, Priority.ALWAYS);

        getChildren().add(card);
        refresh();
    }

    private TableView<Clerk> buildTable() {
        TableView<Clerk> tv = new TableView<>(clerkData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPlaceholder(new Label("No clerk accounts yet."));

        TableColumn<Clerk, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        nameCol.setPrefWidth(220);

        TableColumn<Clerk, String> userCol = new TableColumn<>("Username");
        userCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        userCol.setPrefWidth(160);

        TableColumn<Clerk, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isApproved() ? "APPROVED" : "PENDING / INACTIVE"));
        statusCol.setCellFactory(col -> new TableCell<Clerk, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                setStyle(Theme.statusStyle("APPROVED".equals(status) ? Theme.OK : Theme.WARN));
            }
        });

        tv.getColumns().addAll(nameCol, userCol, statusCol);
        tv.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateButtons());
        return tv;
    }

    /** Reloads clerk accounts while keeping the selected row selected. */
    public void refresh() {
        Clerk selected = table.getSelectionModel().getSelectedItem();
        String selectedUsername = selected == null ? null : selected.getUsername();

        clerkData.setAll(authService.getAllClerks());
        table.refresh();

        if (selectedUsername != null) {
            for (Clerk c : clerkData) {
                if (c.getUsername().equalsIgnoreCase(selectedUsername)) {
                    table.getSelectionModel().select(c);
                    break;
                }
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        Clerk c = table.getSelectionModel().getSelectedItem();
        boolean canManage = currentUser.get().canManageAccounts();
        approveBtn.setDisable(!canManage || c == null || c.isApproved());
        deactivateBtn.setDisable(!canManage || c == null || !c.isApproved());
        resetPwdBtn.setDisable(!canManage || c == null);
    }

    private void showAddClerkDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Clerk Account");
        dialog.setHeaderText("Create a new clerk employee account.");

        TextField fullNameField = new TextField();
        fullNameField.setPromptText("e.g. Carlo Mendoza");
        TextField usernameField = new TextField();
        usernameField.setPromptText("e.g. clerk3");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Minimum 6 characters");
        CheckBox approvedBox = new CheckBox("Approve immediately");
        approvedBox.setSelected(true);

        VBox content = new VBox(8,
                Theme.fieldLabel("Full Name"), fullNameField,
                Theme.fieldLabel("Username"), usernameField,
                Theme.fieldLabel("Password"), passwordField,
                approvedBox);
        content.setPadding(new Insets(10, 6, 6, 6));
        content.setPrefWidth(340);

        dialog.getDialogPane().setContent(content);
        Theme.style(dialog.getDialogPane());
        ButtonType createType = new ButtonType("Create Clerk", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        Button createBtn = (Button) dialog.getDialogPane().lookupButton(createType);
        createBtn.getStyleClass().add("btn-primary");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).getStyleClass().add("btn-secondary");

        createBtn.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                Clerk created = authService.createClerk(currentUser.get(),
                        usernameField.getText(), passwordField.getText(),
                        fullNameField.getText(), approvedBox.isSelected());
                refresh();
                notifyChanged();
                Theme.info("Created clerk account: " + created.getFullName()
                        + " (" + created.getUsername() + ").");
            } catch (ValidationException ex) {
                Theme.error(ex.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    private void onSetApproved(boolean approved) {
        Clerk selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            authService.setClerkApproved(currentUser.get(), selected.getUsername(), approved);
            refresh();
            notifyChanged();
            Theme.info(selected.getFullName() + " is now " + (approved ? "approved." : "deactivated."));
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onResetPassword() {
        Clerk selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset Clerk Password");
        dialog.setHeaderText("New password for " + selected.getFullName() + " (" + selected.getUsername() + ")");
        dialog.setContentText("New Password (min 6 chars):");
        Theme.style(dialog.getDialogPane());
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        try {
            authService.resetClerkPassword(currentUser.get(), selected.getUsername(), result.get());
            Theme.info("Password updated for " + selected.getUsername() + ".");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void notifyChanged() {
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }
}