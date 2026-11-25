package ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import model.Product;
import model.User;
import service.ValidationException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Centralized visual theme, asset loader, dialog styling, and shared UI helpers
 * for the entire CSHRIMP application.
 */
public final class Theme {

    private Theme() {
    }

    // ---------- FORMATTERS ----------
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");

    // ---------- PALETTE ----------
    public static final String DEEP = "#6B1D2A";     // deep burgundy (titles, sidebar)
    public static final String CRIMSON = "#A32B3A";  // main accent
    public static final String ROSE = "#C4495A";     // lighter accent
    public static final String TEXT = "#3A1F24";
    public static final String MUTED = "#8A6B70";
    public static final String OK = "#2E7D5B";
    public static final String WARN = "#C77700";
    public static final String BAD = "#B3261E";

    // ---------- PICTURE LOCATIONS ----------
    public static final String ASSETS = "assets";
    public static final String LOGO = ASSETS + "/logo";
    public static final String LOGIN_BG = ASSETS + "/login_bg";
    public static final String PRODUCT_DIR = ASSETS + "/products";
    public static final String PROFILE_DIR = ASSETS + "/profiles";

    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};

    // ---------- STYLE SHEET ----------
    private static final String CSS = """
            .root { -fx-accent: #B8394B; -fx-focus-color: #D9656F; -fx-faint-focus-color: transparent; -fx-font-size: 13px; }

            .app-bg { -fx-background-color: linear-gradient(to bottom right, #F6CFCF 0%, #FBE6E3 45%, #FFF6F3 100%); }
            .sidebar { -fx-background-color: linear-gradient(to bottom, #6B1D2A 0%, #8E2A3B 55%, #B8475A 100%); }
            .topbar { -fx-background-color: linear-gradient(to right, #B8394B, #DB7079);
                      -fx-effect: dropshadow(gaussian, rgba(107,29,42,0.25), 12, 0, 0, 2); }

            .card { -fx-background-color: white; -fx-background-radius: 14;
                    -fx-effect: dropshadow(gaussian, rgba(107,29,42,0.10), 14, 0, 0, 3); }
            .product-card { -fx-cursor: hand; }
            .product-card:hover { -fx-effect: dropshadow(gaussian, rgba(107,29,42,0.28), 20, 0, 0, 6); }

            .page-title { -fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #4A1520; }
            .page-sub { -fx-text-fill: #8A6B70; -fx-font-size: 13px; }
            .h2 { -fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #6B1D2A; }
            .muted { -fx-text-fill: #8A6B70; }
            .field-label { -fx-text-fill: #6B4A50; -fx-font-size: 12px; -fx-font-weight: bold; }
            .stat-title { -fx-text-fill: #8A6B70; -fx-font-size: 12px; }
            .stat-value { -fx-font-size: 20px; -fx-font-weight: bold; }
            .product-code { -fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #6B1D2A; }
            .more-link { -fx-text-fill: #A32B3A; -fx-font-weight: bold; -fx-font-size: 12px; }

            .nav-side { -fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 10;
                        -fx-text-fill: #F6DCD8; -fx-font-size: 14px; -fx-alignment: CENTER-LEFT;
                        -fx-padding: 11 16; -fx-cursor: hand; }
            .nav-side:hover { -fx-background-color: rgba(255,255,255,0.10); }
            .nav-side:selected { -fx-background-color: rgba(255,255,255,0.22); -fx-text-fill: white; -fx-font-weight: bold; }
            .nav-top { -fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 18;
                       -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 7 18; -fx-cursor: hand; }
            .nav-top:hover { -fx-background-color: rgba(255,255,255,0.18); }
            .nav-top:selected { -fx-background-color: white; -fx-text-fill: #A32B3A; -fx-font-weight: bold; }

            .btn-primary { -fx-background-color: linear-gradient(to bottom right, #C4495A, #A32B3A); -fx-background-insets: 0;
                           -fx-background-radius: 10; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 9 18; -fx-cursor: hand; }
            .btn-primary:hover { -fx-background-color: linear-gradient(to bottom right, #D4566A, #B8394B); }
            .btn-primary:disabled { -fx-opacity: 0.45; }
            .btn-secondary { -fx-background-color: white; -fx-background-insets: 0; -fx-background-radius: 10;
                             -fx-border-color: #D9B3B0; -fx-border-radius: 10; -fx-text-fill: #8E2A3B;
                             -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand; }
            .btn-secondary:hover { -fx-background-color: #FBECEA; }
            .btn-secondary:disabled { -fx-opacity: 0.45; }
            .btn-danger { -fx-background-color: white; -fx-background-insets: 0; -fx-background-radius: 10;
                          -fx-border-color: #E8B4AF; -fx-border-radius: 10; -fx-text-fill: #B3261E;
                          -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand; }
            .btn-danger:hover { -fx-background-color: #FDECEA; }
            .btn-danger:disabled { -fx-opacity: 0.45; }
            .btn-light { -fx-background-color: rgba(255,255,255,0.16); -fx-background-insets: 0; -fx-background-radius: 10;
                         -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand; }
            .btn-light:hover { -fx-background-color: rgba(255,255,255,0.28); }

            .seg { -fx-background-color: #F7E6E3; -fx-background-insets: 0; -fx-background-radius: 8;
                   -fx-text-fill: #8E2A3B; -fx-padding: 6 14; -fx-cursor: hand; }
            .seg:selected { -fx-background-color: #A32B3A; -fx-text-fill: white; -fx-font-weight: bold; }

            .text-field, .password-field { -fx-background-color: white; -fx-background-insets: 0; -fx-background-radius: 8;
                                           -fx-border-color: #E3C9C6; -fx-border-radius: 8; -fx-padding: 9 12;
                                           -fx-prompt-text-fill: #B39A9E; }
            .text-field:focused, .password-field:focused { -fx-border-color: #C4495A; }
            .text-field:disabled, .password-field:disabled { -fx-opacity: 0.6; }
            .text-area { -fx-background-color: #E3C9C6; -fx-background-insets: 0; -fx-background-radius: 8; -fx-padding: 0; }
            .text-area .content { -fx-background-color: white; -fx-background-insets: 1; -fx-background-radius: 7; }
            .text-area:focused { -fx-background-color: #C4495A; }
            .combo-box-base { -fx-background-color: white; -fx-background-insets: 0; -fx-background-radius: 8;
                              -fx-border-color: #E3C9C6; -fx-border-radius: 8; -fx-padding: 3 0; }
            .combo-box-base:focused { -fx-border-color: #C4495A; }

            .table-view { -fx-background-color: white; -fx-background-insets: 0; -fx-background-radius: 10; -fx-padding: 0;
                          -fx-border-color: #EBD3D0; -fx-border-radius: 10;
                          -fx-selection-bar: #F7D9D5; -fx-selection-bar-non-focused: #FBE8E5; }
            .table-view .column-header-background { -fx-background-color: #F7E6E3; -fx-background-radius: 10 10 0 0; }
            .table-view .column-header, .table-view .filler { -fx-background-color: transparent; -fx-size: 38; -fx-border-color: transparent; }
            .table-view .column-header .label { -fx-text-fill: #6B1D2A; -fx-font-weight: bold; -fx-alignment: CENTER-LEFT; }
            .table-view .placeholder .label { -fx-text-fill: #A98A8E; }
            .table-row-cell { -fx-background-color: white; -fx-background-insets: 0; -fx-cell-size: 38;
                              -fx-border-color: transparent transparent #F4E6E4 transparent; }
            .table-row-cell:odd { -fx-background-color: #FFFAF9; }
            .table-row-cell:selected { -fx-background-color: #F7D9D5; }
            .table-row-cell:selected .table-cell { -fx-text-fill: #3A1F24; }
            .table-cell { -fx-border-color: transparent; -fx-padding: 0 10; -fx-alignment: CENTER-LEFT; }

            .scroll-pane { -fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; }
            .scroll-pane > .viewport { -fx-background-color: transparent; }

            .dialog-pane { -fx-background-color: #FFF7F5; }
            .dialog-pane > .header-panel { -fx-background-color: linear-gradient(to right, #8E2A3B, #C4495A); }
            .dialog-pane > .header-panel .label { -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; }
            .dialog-pane .content.label { -fx-text-fill: #3A1F24; }
            """;

    private static String sheetUri;

    /** The URI of the style sheet (created once on first request). */
    public static synchronized String stylesheet() {
        if (sheetUri == null) {
            try {
                Path file = Files.createTempFile("shrimp-theme", ".css");
                Files.writeString(file, CSS);
                file.toFile().deleteOnExit();
                sheetUri = file.toUri().toString();
            } catch (IOException e) {
                sheetUri = "";
            }
        }
        return sheetUri;
    }

    /** Applies the application stylesheet to a dialog pane. */
    public static void style(DialogPane pane) {
        if (!stylesheet().isEmpty()) {
            pane.getStylesheets().add(stylesheet());
        }
    }

    // ---------- SHARED FORMATTERS, PARSERS & DIALOGS ----------

    public static String peso(double amount) {
        return "Php " + String.format("%,.2f", amount);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Parses a finite number from text or throws {@link ValidationException}. */
    public static double parseNumber(String text, String fieldName) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }
        try {
            double value = Double.parseDouble(text.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new ValidationException(fieldName + " must be a valid number.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a valid number.");
        }
    }

    public static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message);
        style(a.getDialogPane());
        a.showAndWait();
    }

    public static void warn(String header, String message) {
        Alert a = new Alert(Alert.AlertType.WARNING, message);
        a.setHeaderText(header);
        style(a.getDialogPane());
        a.showAndWait();
    }

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message);
        style(a.getDialogPane());
        a.showAndWait();
    }

    public static boolean confirm(String header, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(header);
        style(a.getDialogPane());
        Optional<ButtonType> result = a.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    // ---------- TYPED SORTABLE TABLE COLUMNS ----------

    /** Creates a chronologically sortable {@link LocalDateTime} column. */
    public static <T> TableColumn<T, LocalDateTime> dateTimeColumn(
            String title, Function<T, LocalDateTime> extractor, double prefWidth) {
        TableColumn<T, LocalDateTime> col = new TableColumn<>(title);
        col.setPrefWidth(prefWidth);
        col.setCellValueFactory(c -> new SimpleObjectProperty<>(extractor.apply(c.getValue())));
        col.setCellFactory(c -> new TableCell<T, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DATE_TIME));
            }
        });
        return col;
    }

    /** Creates a numerically sortable {@link Double} column with custom display formatting. */
    public static <T> TableColumn<T, Double> doubleColumn(
            String title, Function<T, Double> extractor, Function<Double, String> formatter, double prefWidth) {
        TableColumn<T, Double> col = new TableColumn<>(title);
        col.setPrefWidth(prefWidth);
        col.setCellValueFactory(c -> new SimpleObjectProperty<>(extractor.apply(c.getValue())));
        col.setCellFactory(c -> new TableCell<T, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatter.apply(item));
            }
        });
        return col;
    }

    /** Creates a numerically sortable {@link Integer} column. */
    public static <T> TableColumn<T, Integer> intColumn(
            String title, Function<T, Integer> extractor, double prefWidth) {
        TableColumn<T, Integer> col = new TableColumn<>(title);
        col.setPrefWidth(prefWidth);
        col.setCellValueFactory(c -> new SimpleObjectProperty<>(extractor.apply(c.getValue())));
        return col;
    }

    // ---------- IMAGES ----------

    private static List<File> searchRoots;

    private static List<File> searchRoots() {
        if (searchRoots != null) {
            return searchRoots;
        }
        LinkedHashSet<File> roots = new LinkedHashSet<>();
        List<File> starts = new ArrayList<>();
        starts.add(new File("").getAbsoluteFile());
        try {
            CodeSource cs = Theme.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                File classes = new File(cs.getLocation().toURI()).getAbsoluteFile();
                starts.add(classes.isFile() ? classes.getParentFile() : classes);
            }
        } catch (Exception ignored) {
        }
        for (File start : starts) {
            File dir = start;
            for (int up = 0; up < 4 && dir != null; up++) {
                roots.add(dir);
                roots.add(new File(dir, "src"));
                roots.add(new File(dir, "src/main/resources"));
                dir = dir.getParentFile();
            }
        }
        searchRoots = new ArrayList<>(roots);
        return searchRoots;
    }

    private static final Map<String, File> reported = new HashMap<>();

    private static File findFile(String pathWithoutExtension) {
        File newest = null;
        for (File root : searchRoots()) {
            for (String ext : EXTENSIONS) {
                File f = new File(root, pathWithoutExtension + ext);
                if (f.isFile() && (newest == null || f.lastModified() > newest.lastModified())) {
                    newest = f;
                }
            }
        }
        if (newest != null && !newest.equals(reported.get(pathWithoutExtension))) {
            reported.put(pathWithoutExtension, newest);
            System.out.println("[Theme] " + pathWithoutExtension + " -> " + newest.getAbsolutePath());
        }
        return newest;
    }

    public static Image loadImage(String pathWithoutExtension) {
        return loadImage(pathWithoutExtension, 0);
    }

    public static Image loadImage(String pathWithoutExtension, double height) {
        String url = null;
        File f = findFile(pathWithoutExtension);
        if (f != null) {
            url = f.toURI().toString();
        } else {
            for (String ext : EXTENSIONS) {
                URL res = Theme.class.getResource("/" + pathWithoutExtension + ext);
                if (res != null) {
                    url = res.toExternalForm();
                    break;
                }
            }
        }
        if (url == null) {
            return null;
        }
        Image img = height > 0 ? new Image(url, 0, height, true, true) : new Image(url);
        if (img.isError()) {
            return null;
        }
        return img;
    }

    public static ImageView coverView(Image img, double w, double h) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(false);
        iv.setSmooth(true);
        double imageRatio = img.getWidth() / img.getHeight();
        double boxRatio = w / h;
        double vw;
        double vh;
        if (imageRatio > boxRatio) {
            vh = img.getHeight();
            vw = vh * boxRatio;
        } else {
            vw = img.getWidth();
            vh = vw / boxRatio;
        }
        iv.setViewport(new Rectangle2D((img.getWidth() - vw) / 2, (img.getHeight() - vh) / 2, vw, vh));
        return iv;
    }

    /** Renders assets/logo.png or a styled CSHRIMP badge placeholder. */
    public static Node logo(double size) {
        double scale = 1;
        try {
            scale = Math.max(1, Screen.getPrimary().getOutputScaleY());
        } catch (Exception ignored) {
        }
        Image img = loadImage(LOGO, Math.ceil(size * scale));
        if (img != null) {
            double w = size * img.getWidth() / img.getHeight();
            ImageView iv = new ImageView(img);
            iv.setFitWidth(w);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            StackPane box = new StackPane(iv);
            box.setMinSize(w, size);
            box.setPrefSize(w, size);
            box.setMaxSize(w, size);
            return box;
        }
        Label l = new Label("CSHRIMP");
        l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + Math.max(10, size * 0.18) + "px;");
        StackPane p = new StackPane(l);
        p.setMinSize(size, size);
        p.setPrefSize(size, size);
        p.setMaxSize(size, size);
        p.setStyle("-fx-background-color: linear-gradient(to bottom right, #D9656F, #8E2A3B);"
                + "-fx-background-radius: 999; -fx-border-color: rgba(255,255,255,0.75);"
                + "-fx-border-radius: 999; -fx-border-width: 2;");
        return p;
    }

    /** Renders assets/profiles/USERNAME.png or the user's initials. */
    public static Node avatar(User u, double size) {
        StackPane holder = new StackPane();
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);

        Image img = loadImage(PROFILE_DIR + "/" + u.getUsername());
        if (img != null) {
            holder.getChildren().add(coverView(img, size, size));
        } else {
            Label initials = new Label(initials(u.getFullName()));
            initials.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + (size * 0.38) + "px;");
            holder.getChildren().add(initials);
            holder.setStyle("-fx-background-color: linear-gradient(to bottom right, #E58B94, #A32B3A);");
        }
        holder.setClip(new Circle(size / 2, size / 2, size / 2));

        StackPane ring = new StackPane(holder);
        ring.setPadding(new Insets(2));
        ring.setMinSize(size + 4, size + 4);
        ring.setPrefSize(size + 4, size + 4);
        ring.setMaxSize(size + 4, size + 4);
        ring.setStyle("-fx-background-color: rgba(255,255,255,0.9); -fx-background-radius: 999;");
        return ring;
    }

    private static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        String result = parts[0].substring(0, 1);
        if (parts.length > 1) {
            result += parts[parts.length - 1].substring(0, 1);
        }
        return result.toUpperCase();
    }

    // ---------- PRODUCT NAMES & PICTURES ----------

    public static String codeOf(Product p) {
        String name = p.getName();
        int i = name.indexOf(" - ");
        return (i < 0 ? name : name.substring(0, i)).trim();
    }

    public static String nameSuffix(String name) {
        int i = name.indexOf(" - ");
        return i < 0 ? "" : name.substring(i + 3).trim();
    }

    public static String meaningOf(Product p) {
        String suffix = nameSuffix(p.getName());
        return suffix.isEmpty() ? p.getDescription() : suffix;
    }

    public static File productFolder() {
        for (File root : searchRoots()) {
            File dir = new File(root, PRODUCT_DIR);
            if (dir.isDirectory()) {
                return dir;
            }
        }
        return new File(PRODUCT_DIR).getAbsoluteFile();
    }

    public static String pictureExtension(File f) {
        String n = f.getName().toLowerCase();
        for (String ext : EXTENSIONS) {
            if (n.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }

    public static void saveProductPicture(String code, File source) throws IOException {
        String ext = pictureExtension(source);
        if (ext == null) {
            throw new IOException("Only .png, .jpg and .jpeg pictures can be used.");
        }
        if (!source.isFile() || !source.canRead()) {
            throw new IOException("Could not open " + source.getName() + ".");
        }
        Image test = new Image(source.toURI().toString());
        if (test.isError() || test.getWidth() <= 0) {
            throw new IOException(source.getName() + " is not a picture the program can read.");
        }

        File dir = productFolder();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create the folder " + dir.getAbsolutePath() + ".");
        }
        File target = new File(dir, code + ext);
        if (target.getCanonicalFile().equals(source.getCanonicalFile())) {
            return;
        }
        try {
            Path tmp = Files.createTempFile(dir.toPath(), code, ext + ".tmp");
            Files.copy(source.toPath(), tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException e) {
            throw new IOException("Could not save the picture into " + dir.getAbsolutePath()
                    + ". Check that the folder is not read-only.");
        }
        target.setLastModified(System.currentTimeMillis());
        for (String other : EXTENSIONS) {
            if (!other.equals(ext)) {
                new File(dir, code + other).delete();
            }
        }
    }

    public static File productPictureFile(String code) {
        return findFile(PRODUCT_DIR + "/" + code);
    }

    public static Node productImage(Product p, double w, double h) {
        String code = codeOf(p);
        Image img = loadImage(PRODUCT_DIR + "/" + code);

        StackPane box = new StackPane();
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setMaxSize(w, h);
        if (img != null) {
            box.getChildren().add(coverView(img, w, h));
        } else {
            Label big = new Label(code);
            big.setStyle("-fx-text-fill: rgba(163,43,58,0.55); -fx-font-weight: bold; -fx-font-size: 22px;");
            Label hint = new Label("Photo placeholder\n" + PRODUCT_DIR + "/" + code + ".png");
            hint.setStyle("-fx-text-fill: rgba(107,29,42,0.55); -fx-font-size: 10px;");
            hint.setTextAlignment(TextAlignment.CENTER);
            VBox v = new VBox(4, big, hint);
            v.setAlignment(Pos.CENTER);
            box.getChildren().add(v);
            box.setStyle("-fx-background-color: linear-gradient(to bottom right, #FAD9D6, #F1B3B0);");
        }
        Rectangle clip = new Rectangle(w, h + 24);
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        box.setClip(clip);
        return box;
    }

    // ---------- BUILDING BLOCKS ----------

    public static VBox statCard(String title, Label value, String color) {
        Label t = new Label(title);
        t.getStyleClass().add("stat-title");
        value.getStyleClass().add("stat-value");
        value.setStyle("-fx-text-fill: " + color + ";");
        VBox box = new VBox(4, t, value);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(14, 16, 14, 16));
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    public static GridPane statGrid(int columns, Node... cards) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        for (int c = 0; c < columns; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / columns);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }
        for (int i = 0; i < cards.length; i++) {
            grid.add(cards[i], i % columns, i / columns);
            GridPane.setFillWidth(cards[i], true);
        }
        return grid;
    }

    public static VBox panel(String title, Node body) {
        Label h = new Label(title);
        h.getStyleClass().add("h2");
        VBox box = new VBox(12, h, body);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(16));
        VBox.setVgrow(body, Priority.ALWAYS);
        return box;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(16));
        return box;
    }

    public static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    public static Label mutedLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    public static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    public static String statusStyle(String color) {
        return "-fx-text-fill: " + color + "; -fx-font-weight: bold;";
    }

    public static HBox spacerRow(Node left, Node right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, left, spacer, right);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}