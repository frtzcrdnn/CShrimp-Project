package main;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.User;
import service.ShrimpSystem;

import java.util.function.Consumer;

/**
 * Welcome / Login screen. It only checks the login through ShrimpSystem and then
 * tells Main who logged in; Main decides which dashboard to show.
 */
public class LoginScreen extends StackPane {

    private final ShrimpSystem system;
    private final Consumer<User> onLoginSuccess;

    private TextField usernameField;
    private PasswordField passwordField;
    private Label errorLabel;

    public LoginScreen(ShrimpSystem system, Consumer<User> onLoginSuccess) {
        this.system = system;
        this.onLoginSuccess = onLoginSuccess;

        Label title = new Label("SHRIMP");
        title.setStyle("-fx-font-size: 40px; -fx-font-weight: bold; -fx-text-fill: #d9534f;");
        Label subtitle = new Label("Shrimp Processing Management System");
        subtitle.setStyle("-fx-text-fill: gray;");

        usernameField = new TextField();
        usernameField.setPromptText("Username");
        passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setWrapText(true);

        Button loginBtn = new Button("Log In");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setDefaultButton(true);
        loginBtn.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white; -fx-font-weight: bold;");
        loginBtn.setOnAction(e -> onLogin());

        // handy while testing; delete this label when the project is finished
        Label hint = new Label("Test accounts:  manager / manager123   |   clerk1 / clerk123");
        hint.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        VBox form = new VBox(10, title, subtitle, new Separator(), usernameField, passwordField,
                errorLabel, loginBtn, hint);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(30));
        form.setMaxWidth(360);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        form.setStyle("-fx-background-color: #f4f4f4; -fx-background-radius: 8;");

        getChildren().add(form);
        setPadding(new Insets(20));
    }

    private void onLogin() {
        try {
            User user = system.login(usernameField.getText(), passwordField.getText());
            errorLabel.setText("");
            passwordField.clear();
            onLoginSuccess.accept(user);
        } catch (RuntimeException ex) {
            errorLabel.setText(ex.getMessage());
            passwordField.clear();
        }
    }
}
