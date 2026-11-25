package main;

import dashboard.Dashboard;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import model.User;
import service.ShrimpSystem;
import ui.LoginScreen;
import ui.Theme;

/**
 * The Starting Point. Main only starts the system and switches between screens:
 * Login -> (Manager or Clerk) Dashboard -> Log Out -> Login.
 */
public class Main extends Application {

    private ShrimpSystem system;
    private Scene scene;

    @Override
    public void start(Stage stage) {
        system = new ShrimpSystem();

        scene = new Scene(new Region(), 1200, 750);
        if (!Theme.stylesheet().isEmpty()) {
            scene.getStylesheets().add(Theme.stylesheet());
        }
        showLogin();

        stage.setTitle("SHRIMP - Shrimp Processing Management System");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    // window switching: only the content of the scene changes, the window stays
    private void showLogin() {
        scene.setRoot(new LoginScreen(system, this::showDashboard));
    }

    private void showDashboard(User user) {
        scene.setRoot(Dashboard.create(system, user, this::logout));
    }

    private void logout() {
        system.logout();
        showLogin();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
