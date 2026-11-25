package ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.Product;
import model.User;
import service.InventoryService;
import service.ProductService;
import service.ValidationException;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Product catalog screen (for Manager only). Displays visual product cards with photos,
 * search filtering, and add / edit / archive / delete dialogs.
 */
public class ProductPane extends BorderPane {

    private static final double CARD_WIDTH = 240;
    private static final double IMAGE_HEIGHT = 140;
    private static final double PREVIEW_WIDTH = 340;
    private static final double PREVIEW_HEIGHT = 140;

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final Supplier<User> currentUser;
    private final Runnable onDataChanged;

    private TextField searchField;
    private Label countLabel;
    private Button addBtn;
    private FlowPane grid;

    public ProductPane(ProductService productService, InventoryService inventoryService,
                       Supplier<User> currentUser, Runnable onDataChanged) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.currentUser = currentUser;
        this.onDataChanged = onDataChanged;

        setTop(buildTopBar());

        grid = new FlowPane(18, 18);
        grid.setPadding(new Insets(4, 8, 24, 4));
        ScrollPane scroll = Theme.scroll(grid);
        BorderPane.setMargin(scroll, new Insets(16, 0, 0, 0));
        setCenter(scroll);

        refresh();
    }

    /** Reloads product cards and updates manager permissions. */
    public void refresh() {
        addBtn.setDisable(!currentUser.get().canManageProducts());
        refreshTable();
    }

    private HBox buildTopBar() {
        searchField = new TextField();
        searchField.setPromptText("Search by code, name or description...");
        searchField.textProperty().addListener((o, a, b) -> refreshTable());
        searchField.setPrefWidth(380);

        countLabel = new Label();
        countLabel.getStyleClass().add("muted");

        addBtn = new Button("+ Add product");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> showProductForm(null));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(14, searchField, countLabel, spacer, addBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox buildCard(Product p) {
        Node picture = Theme.productImage(p, CARD_WIDTH, IMAGE_HEIGHT);

        String status = inventoryService.getStatus(p);
        StackPane imageBox = new StackPane(picture);
        if (!status.equals("IN STOCK")) {
            Label pill = new Label(status);
            pill.setStyle("-fx-background-color: " + (status.equals("OUT OF STOCK") ? Theme.BAD : Theme.WARN)
                    + "; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;"
                    + "-fx-padding: 3 9; -fx-background-radius: 10;");
            StackPane.setAlignment(pill, Pos.TOP_RIGHT);
            StackPane.setMargin(pill, new Insets(10));
            imageBox.getChildren().add(pill);
        }

        Label code = new Label(Theme.codeOf(p));
        code.getStyleClass().add("product-code");

        Label meaning = new Label(Theme.meaningOf(p));
        meaning.getStyleClass().add("muted");
        meaning.setWrapText(true);
        meaning.setAlignment(Pos.TOP_LEFT);
        meaning.setMinHeight(36);
        meaning.setPrefHeight(36);
        meaning.setMaxHeight(36);

        Label more = new Label("More details  >");
        more.getStyleClass().add("more-link");

        VBox text = new VBox(2, code, meaning, more);
        text.setPadding(new Insets(12, 16, 14, 16));

        VBox card = new VBox(imageBox, text);
        card.getStyleClass().addAll("card", "product-card");
        card.setMinWidth(CARD_WIDTH);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setOnMouseClicked(e -> showDetails(p));
        return card;
    }

    private void showDetails(Product p) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Product details");

        Label code = new Label(Theme.codeOf(p));
        code.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: " + Theme.DEEP + ";");
        Label meaning = new Label(Theme.meaningOf(p));
        meaning.setStyle("-fx-font-size: 15px; -fx-text-fill: " + Theme.MUTED + ";");
        meaning.setWrapText(true);

        String status = inventoryService.getStatus(p);
        String statusColor = status.equals("OUT OF STOCK") ? Theme.BAD
                : status.equals("LOW STOCK") ? Theme.WARN : Theme.OK;
        Label statusLabel = new Label(status);
        statusLabel.setStyle(Theme.statusStyle(statusColor));

        GridPane info = new GridPane();
        info.setHgap(18);
        info.setVgap(10);
        addInfoRow(info, 0, "Price per kg", new Label(Theme.peso(p.getPricePerKg())));
        addInfoRow(info, 1, "In stock", new Label(String.format("%.2f kg", p.getStockQuantity())));
        addInfoRow(info, 2, "Status", statusLabel);
        addInfoRow(info, 3, "Product ID", new Label(p.getProductId()));
        Label desc = new Label(p.getDescription());
        desc.setWrapText(true);
        desc.setMaxWidth(300);
        addInfoRow(info, 4, "Description", desc);

        VBox content = new VBox(12, Theme.productImage(p, 420, 210), code, meaning, info);
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        Theme.style(dialog.getDialogPane());

        ButtonType editType = new ButtonType("Edit", ButtonBar.ButtonData.LEFT);
        ButtonType archiveType = new ButtonType("Archive", ButtonBar.ButtonData.LEFT);
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);

        if (currentUser.get().canManageProducts()) {
            dialog.getDialogPane().getButtonTypes().addAll(editType, archiveType, deleteType, ButtonType.CLOSE);
            ((Button) dialog.getDialogPane().lookupButton(editType)).getStyleClass().add("btn-primary");
            ((Button) dialog.getDialogPane().lookupButton(archiveType)).getStyleClass().add("btn-secondary");
            ((Button) dialog.getDialogPane().lookupButton(deleteType)).getStyleClass().add("btn-danger");
        } else {
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        }
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).getStyleClass().add("btn-secondary");

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent()) {
            if (result.get() == editType) {
                showProductForm(p);
            } else if (result.get() == archiveType) {
                onArchive(p);
            } else if (result.get() == deleteType) {
                onDelete(p);
            }
        }
    }

    private void addInfoRow(GridPane grid, int row, String label, Label value) {
        grid.add(Theme.fieldLabel(label), 0, row);
        grid.add(value, 1, row);
    }

    private void showProductForm(Product existing) {
        if (!currentUser.get().canManageProducts()) {
            Theme.error("Only a manager can add or edit products.");
            return;
        }
        boolean editing = existing != null;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit product" : "Add product");
        dialog.setHeaderText(editing
                ? "Update the details of " + Theme.codeOf(existing) + "."
                : "Fill in the details of the new product.");

        TextField codeField = new TextField(editing ? Theme.codeOf(existing) : "");
        codeField.setPromptText("e.g. CHLSO");
        TextField meaningField = new TextField(editing ? Theme.nameSuffix(existing.getName()) : "");
        meaningField.setPromptText("e.g. Cooked Head-Less Shell-On");
        TextField priceField = new TextField(editing ? String.valueOf(existing.getPricePerKg()) : "");
        priceField.setPromptText("e.g. 650");
        TextField stockField = new TextField(editing ? String.format("%.2f", existing.getStockQuantity()) : "0");
        stockField.setDisable(editing);
        TextArea descArea = new TextArea(editing ? existing.getDescription() : "");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        File[] chosenPicture = new File[1];
        StackPane preview = new StackPane();
        preview.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        preview.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        preview.setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        if (editing) {
            preview.getChildren().add(Theme.productImage(existing, PREVIEW_WIDTH, PREVIEW_HEIGHT));
        } else {
            preview.getChildren().add(emptyPreview());
        }
        Label pictureName = Theme.mutedLabel(editing && Theme.productPictureFile(Theme.codeOf(existing)) != null
                ? "Current picture" : "No picture yet");
        pictureName.setMaxWidth(220);

        Button chooseBtn = new Button(editing ? "Change picture..." : "Choose picture...");
        chooseBtn.getStyleClass().add("btn-secondary");
        chooseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a product picture");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Pictures (png, jpg, jpeg)", "*.png", "*.jpg", "*.jpeg",
                            "*.PNG", "*.JPG", "*.JPEG"));
            File f = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (f == null) {
                return;
            }
            Image img = new Image(f.toURI().toString(), PREVIEW_WIDTH * 2, 0, true, true);
            if (img.isError() || img.getWidth() <= 0) {
                Theme.error(f.getName() + " could not be opened as a picture. Choose a .png or .jpg file.");
                return;
            }
            chosenPicture[0] = f;
            preview.getChildren().setAll(Theme.coverView(img, PREVIEW_WIDTH, PREVIEW_HEIGHT));
            pictureName.setText(f.getName());
        });
        HBox pictureRow = new HBox(10, chooseBtn, pictureName);
        pictureRow.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(6,
                Theme.fieldLabel("Picture"), preview, pictureRow,
                Theme.fieldLabel("Product ID" + (editing ? "" : " (automatic)")),
                idField(editing ? existing.getProductId() : productService.getNextId()),
                Theme.fieldLabel("Code"), codeField,
                Theme.fieldLabel("What it means"), meaningField,
                Theme.fieldLabel("Price per kg (Php)"), priceField,
                Theme.fieldLabel(editing ? "Stock (kg) - change it in the Inventory tab" : "Starting stock (kg)"), stockField,
                Theme.fieldLabel("Description"), descArea);
        form.setPadding(new Insets(10, 6, 6, 6));
        form.setPrefWidth(360);

        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.setPrefViewportHeight(480);
        formScroll.setPrefViewportWidth(380);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        dialog.getDialogPane().setContent(formScroll);
        Theme.style(dialog.getDialogPane());

        ButtonType saveType = new ButtonType(editing ? "Save changes" : "Save product", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.getStyleClass().add("btn-primary");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).getStyleClass().add("btn-secondary");

        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                String name = buildName(codeField.getText(), meaningField.getText());
                double price = Theme.parseNumber(priceField.getText(), "Price");
                if (editing) {
                    String oldCode = Theme.codeOf(existing);
                    productService.updateProduct(existing.getProductId(), name, price, descArea.getText());
                    String newCode = codeField.getText().trim();
                    String pictureProblem = savePicture(newCode, chosenPicture[0], oldCode);
                    refreshTable();
                    notifyChanged();
                    finished("Product updated.", pictureProblem);
                } else {
                    double stock = Theme.parseNumber(stockField.getText(), "Stock");
                    Product p = productService.addProduct(name, price, descArea.getText(), stock);
                    inventoryService.logOpeningStock(currentUser.get(), p);
                    String pictureProblem = savePicture(Theme.codeOf(p), chosenPicture[0], null);
                    refreshTable();
                    notifyChanged();
                    finished("Added " + Theme.codeOf(p) + " (" + p.getProductId() + ").", pictureProblem);
                }
            } catch (ValidationException ex) {
                Theme.error(ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private String savePicture(String code, File chosen, String oldCode) {
        try {
            if (chosen != null) {
                Theme.saveProductPicture(code, chosen);
            } else if (oldCode != null && !oldCode.equals(code)) {
                File old = Theme.productPictureFile(oldCode);
                if (old != null) {
                    Theme.saveProductPicture(code, old);
                }
            }
            return null;
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }

    private void finished(String message, String pictureProblem) {
        if (pictureProblem == null) {
            Theme.info(message);
        } else {
            Theme.error(message + "\n\nBut the picture was not saved: " + pictureProblem);
        }
    }

    private Node emptyPreview() {
        Label l = Theme.mutedLabel("No picture chosen");
        StackPane box = new StackPane(l);
        box.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        box.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        box.setStyle("-fx-background-color: linear-gradient(to bottom right, #FAD9D6, #F1B3B0);"
                + "-fx-background-radius: 12;");
        return box;
    }

    private TextField idField(String id) {
        TextField f = new TextField(id);
        f.setEditable(false);
        f.setFocusTraversable(false);
        f.setStyle("-fx-background-color: #F4E6E4;");
        return f;
    }

    private String buildName(String code, String meaning) {
        String c = code == null ? "" : code.trim();
        String m = meaning == null ? "" : meaning.trim();
        if (c.isEmpty()) {
            throw new ValidationException("Code is required, e.g. CHLSO.");
        }
        return m.isEmpty() ? c : c + " - " + m;
    }

    private void onArchive(Product p) {
        if (!Theme.confirm("Archive Product",
                "Archive \"" + Theme.codeOf(p) + "\"? It will be hidden from active sales while preserving history.")) {
            return;
        }
        try {
            productService.archiveProduct(p.getProductId());
            refreshTable();
            notifyChanged();
            Theme.info("\"" + Theme.codeOf(p) + "\" has been archived.");
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void onDelete(Product p) {
        if (!Theme.confirm("Delete Product",
                "Permanently delete \"" + Theme.codeOf(p) + "\"? This cannot be undone.")) {
            return;
        }
        try {
            productService.removeProduct(p.getProductId());
            refreshTable();
            notifyChanged();
        } catch (ValidationException ex) {
            Theme.error(ex.getMessage());
        }
    }

    private void refreshTable() {
        String keyword = searchField == null ? "" : searchField.getText();
        grid.getChildren().clear();
        int count = 0;
        for (Product p : productService.searchByName(keyword)) {
            grid.getChildren().add(buildCard(p));
            count++;
        }
        if (count == 0) {
            grid.getChildren().add(Theme.mutedLabel("No products match your search."));
        }
        countLabel.setText(count + (count == 1 ? " product" : " products"));
    }

    private void notifyChanged() {
        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }
}