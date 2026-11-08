/**
 * 
 */
/**
 * 
 */
module CShrimp {
    requires javafx.controls;
    requires javafx.fxml;

    // Allow JavaFX graphics module to access your main package via reflection
    opens main to javafx.graphics;

    exports main;
}