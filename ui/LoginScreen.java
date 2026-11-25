package ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.User;
import service.ShrimpSystem;
import service.ValidationException;

import java.util.function.Consumer;

/**
 * Welcome / Login screen. Authenticates credentials via {@link ShrimpSystem}
 * and allows prospective clerks to register an account for Manager approval.
 */
public class LoginScreen extends StackPane {

    private static final double FRAME_SIZE = 490;

    private final ShrimpSystem system;
    private final Consumer<User> onLoginSuccess;

    private final TextField usernameField;
    private final PasswordField passwordField;
    private final Label errorLabel;

    public LoginScreen(ShrimpSystem system, Consumer<User> onLoginSuccess) {
        this.system = system;
        this.onLoginSuccess = onLoginSuccess;

        applyBackground();

        Label subtitle = new Label("Shrimp Processing Management");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);
        HBox brand = new HBox(14, Theme.logo(80), subtitle);
        brand.setAlignment(Pos.CENTER_LEFT);

        usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: " + Theme.BAD + "; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);
        errorLabel.setMinHeight(Region.USE_PREF_SIZE);

        Button loginBtn = new Button("Log In");
        loginBtn.getStyleClass().add("btn-primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setDefaultButton(true);
        loginBtn.setOnAction(e -> onLogin());

        Button registerBtn = new Button("Register New Clerk Account");
        registerBtn.getStyleClass().add("btn-secondary");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setOnAction(e -> showRegisterDialog());

        VBox form = new VBox(6, brand,
                spacer(10),
                Theme.fieldLabel("Username"), usernameField,
                spacer(4),
                Theme.fieldLabel("Password"), passwordField,
                errorLabel, loginBtn, registerBtn);
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPadding(new Insets(34));
        form.setStyle("-fx-border-color: #E8B7B7; -fx-border-width: 1; -fx-border-insets: 10;");

        StackPane frame = new StackPane(form);
        frame.setMinSize(FRAME_SIZE, FRAME_SIZE);
        frame.setPrefSize(FRAME_SIZE, FRAME_SIZE);
        frame.setMaxSize(FRAME_SIZE, FRAME_SIZE);
        frame.setStyle("-fx-background-color: rgba(255,255,255,0.96);"
                + "-fx-border-color: " + Theme.CRIMSON + "; -fx-border-width: 3;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 30, 0, 0, 8);");

        setAlignment(Pos.CENTER);
        getChildren().add(frame);
        setPadding(new Insets(20));
    }

    private void applyBackground() {
        Image bg = Theme.loadImage(Theme.LOGIN_BG);
        if (bg != null) {
            setBackground(new Background(new BackgroundImage(bg,
                    BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER,
                    new BackgroundSize(100, 100, true, true, false, true))));
        } else {
            setStyle("-fx-background-color: linear-gradient(to bottom right, #6B1D2A, #B8394B 55%, #F0A39B);");
        }
    }

    private Region spacer(double height) {
        Region r = new Region();
        r.setMinHeight(height);
        r.setPrefHeight(height);
        return r;
    }

    private void onLogin() {
        try {
            User user = system.login(usernameField.getText(), passwordField.getText());
            errorLabel.setText("");
            passwordField.clear();
            onLoginSuccess.accept(user);
        } catch (ValidationException ex) {
            errorLabel.setText(ex.getMessage());
            passwordField.clear();
        }
    }

    private void showRegisterDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Register Clerk Account");
        dialog.setHeaderText("New clerk accounts require Manager approval before first login.");

        TextField fullNameField = new TextField();
        fullNameField.setPromptText("e.g. Carlo Mendoza");
        TextField userField = new TextField();
        userField.setPromptText("e.g. clerk3");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Minimum 6 characters");

        VBox content = new VBox(8,
                Theme.fieldLabel("Full Name"), fullNameField,
                Theme.fieldLabel("Username"), userField,
                Theme.fieldLabel("Password"), passField);
        content.setPadding(new Insets(10, 6, 6, 6));
        content.setPrefWidth(340);

        dialog.getDialogPane().setContent(content);
        Theme.style(dialog.getDialogPane());
        ButtonType submitType = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitType, ButtonType.CANCEL);

        Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submitType);
        submitBtn.getStyleClass().add("btn-primary");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).getStyleClass().add("btn-secondary");

        submitBtn.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                system.getAuthenticationService().registerClerk(
                        userField.getText(), passField.getText(), fullNameField.getText());
                Theme.info("Registration submitted! Please ask a manager to approve your account.");
            } catch (ValidationException ex) {
                Theme.error(ex.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }
}