package service;

import model.Product;
import model.StockMovement;
import model.User;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles everything about stock: restocking, taking stock out for a sale,
 * putting it back for a voided sale, low-stock checks, and the stock history.
 *
 * It works on top of ProductService: the stock itself lives in
 * Product.stockQuantity and is saved by productService.saveProducts().
 * Every change is also written to a history file so there is a record of
 * who changed what and when.
 *
 * History file (data/inventory_log.txt), one line per movement, separated by "|":
 * dateTime|productId|productName|change|reason|performedBy|note|stockAfter
 */

public class InventoryService {

    // a product at or below this many kg is "low stock"
    public static final double LOW_STOCK_LEVEL = 25;

    private static final double MAX_QUANTITY = 100000;
    private static final int MAX_NOTE_LENGTH = 120;
    private static final String FILE_NAME = "data/inventory_log.txt";
    private static final String SEPARATOR = "|";

    private ProductService productService;
    private ArrayList<StockMovement> movements = new ArrayList<>();
    private String filePath;

    public InventoryService(ProductService productService) {
        this(productService, FILE_NAME);
    }

    public InventoryService(ProductService productService, String filePath) {
        this.productService = productService;
        this.filePath = filePath;
        loadMovements();
    }

    // ---------- STOCK STATUS ----------

    public boolean isOutOfStock(Product p) {
        return p.getStockQuantity() <= 0;
    }

    // true when stock is at or below the low-stock level (out of stock counts too)
    public boolean isLowStock(Product p) {
        return p.getStockQuantity() <= LOW_STOCK_LEVEL;
    }

    // "OUT OF STOCK", "LOW STOCK" or "IN STOCK"
    public String getStatus(Product p) {
        if (isOutOfStock(p)) {
            return "OUT OF STOCK";
        }
        if (isLowStock(p)) {
            return "LOW STOCK";
        }
        return "IN STOCK";
    }

    public List<Product> getLowStockProducts() {
        ArrayList<Product> result = new ArrayList<>();
        for (Product p : productService.getAllProducts()) {
            if (isLowStock(p)) {
                result.add(p);
            }
        }
        return result;
    }

    public double getStock(String productId) {
        return requireProduct(productId).getStockQuantity();
    }

    // ---------- RESTOCK (manager) ----------

    public void restock(User by, String productId, double kg, String note) {
        requireUser(by);
        if (!by.canRestock()) {
            throw new IllegalArgumentException("Only a manager can restock products.");
        }
        Product p = requireProduct(productId);
        double qty = checkQuantity(kg);
        String cleanNote = checkNote(note);

        changeStock(p, qty, StockMovement.Reason.RESTOCK, by,
                cleanNote.isEmpty() ? "Restock" : cleanNote);
        // restocking also clears any low-stock report, see hasRestockRequest()
    }

    // ---------- LOW-STOCK REPORT (clerk) ----------

    // a clerk tells the manager that a product is running low
    public void requestRestock(User by, String productId) {
        requireUser(by);
        Product p = requireProduct(productId);

        if (!isLowStock(p)) {
            throw new IllegalArgumentException(p.getName() + " still has enough stock ("
                    + String.format("%.2f", p.getStockQuantity()) + " kg).");
        }
        if (hasRestockRequest(p.getProductId())) {
            throw new IllegalArgumentException("A low-stock report for " + p.getName()
                    + " is already waiting for the manager.");
        }
        addMovement(new StockMovement(now(), p.getProductId(), p.getName(), 0,
                StockMovement.Reason.LOW_STOCK_REPORT, by.getFullName(),
                "Reported as low on stock", p.getStockQuantity()));
    }

    // true if the newest "report" for this product is newer than its newest restock
    public boolean hasRestockRequest(String productId) {
        for (int i = movements.size() - 1; i >= 0; i--) {
            StockMovement m = movements.get(i);
            if (!m.getProductId().equals(productId)) {
                continue;
            }
            if (m.getReason() == StockMovement.Reason.LOW_STOCK_REPORT) {
                return true;
            }
            if (m.getReason() == StockMovement.Reason.RESTOCK) {
                return false;
            }
        }
        return false;
    }

    // ---------- USED BY SALES (SalesService will call these) ----------

    // throws InsufficientStockException if there is not enough stock
    public void ensureAvailable(String productId, double kg) {
        Product p = requireProduct(productId);
        double qty = checkQuantity(kg);
        if (qty > p.getStockQuantity()) {
            throw new InsufficientStockException(p.getName(), p.getStockQuantity(), qty);
        }
    }

    // takes stock out for a sale
    public void deductStock(User by, String productId, double kg, String note) {
        requireUser(by);
        ensureAvailable(productId, kg);
        Product p = requireProduct(productId);
        changeStock(p, -checkQuantity(kg), StockMovement.Reason.SALE, by, checkNote(note));
    }

    // puts stock back when a sale is voided
    public void returnStock(User by, String productId, double kg, String note) {
        requireUser(by);
        Product p = requireProduct(productId);
        changeStock(p, checkQuantity(kg), StockMovement.Reason.VOID_RETURN, by, checkNote(note));
    }

    // ---------- OPENING STOCK ----------

    // Call this right after a product is added with a starting stock above 0.
    // It does NOT change the stock, it only writes the "opening stock" line in the history.
    public void logOpeningStock(User by, Product p) {
        requireUser(by);
        if (p.getStockQuantity() <= 0) {
            return;
        }
        addMovement(new StockMovement(now(), p.getProductId(), p.getName(),
                p.getStockQuantity(), StockMovement.Reason.OPENING, by.getFullName(),
                "Opening stock", p.getStockQuantity()));
    }

    // ---------- HISTORY ----------

    // newest first
    public List<StockMovement> getMovements() {
        ArrayList<StockMovement> copy = new ArrayList<>(movements);
        Collections.reverse(copy);
        return copy;
    }

    // newest first
    public List<StockMovement> getMovementsFor(String productId) {
        ArrayList<StockMovement> result = new ArrayList<>();
        for (StockMovement m : getMovements()) {
            if (m.getProductId().equals(productId)) {
                result.add(m);
            }
        }
        return result;
    }

    // ---------- HELPER METHODS ----------

    // changes the stock, saves the products file, and records the movement
    private void changeStock(Product p, double change, StockMovement.Reason reason,
                             User by, String note) {
        double oldStock = p.getStockQuantity();
        double newStock = round2(oldStock + change);

        p.setStockQuantity(newStock);
        try {
            productService.saveProducts();
        } catch (RuntimeException e) {
            p.setStockQuantity(oldStock); // saving failed, so undo the change
            throw e;
        }
        addMovement(new StockMovement(now(), p.getProductId(), p.getName(), change,
                reason, by.getFullName(), note, newStock));
    }

    private void addMovement(StockMovement m) {
        movements.add(m);
        appendToFile(m);
    }

    private Product requireProduct(String productId) {
        Product p = productService.findById(productId);
        if (p == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        return p;
    }

    private void requireUser(User by) {
        if (by == null) {
            throw new IllegalArgumentException("No user is signed in.");
        }
    }

    private double checkQuantity(double kg) {
        if (Double.isNaN(kg) || Double.isInfinite(kg)) {
            throw new IllegalArgumentException("Quantity must be a valid number.");
        }
        double qty = round2(kg);
        if (qty < 0.01) {
            throw new IllegalArgumentException("Quantity must be at least 0.01 kg.");
        }
        if (qty > MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity cannot be more than 100,000 kg.");
        }
        return qty;
    }

    private String checkNote(String note) {
        if (note == null) {
            return "";
        }
        String cleaned = clean(note.trim());
        if (cleaned.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Note must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        return cleaned;
    }

    // rounds to 2 decimals so stock never shows things like 139.99999999
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    // ---------- SAVE AND LOAD FILE ----------

    // adds one line at the end of the history file
    private void appendToFile(StockMovement m) {
        try {
            File file = new File(filePath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            PrintWriter writer = new PrintWriter(new FileWriter(file, true));
            writer.println(m.getDateTime() + SEPARATOR
                    + m.getProductId() + SEPARATOR
                    + clean(m.getProductName()) + SEPARATOR
                    + m.getChange() + SEPARATOR
                    + m.getReason().name() + SEPARATOR
                    + clean(m.getPerformedBy()) + SEPARATOR
                    + clean(m.getNote()) + SEPARATOR
                    + m.getStockAfter());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException("Could not save the stock history: " + e.getMessage());
        }
    }

    private void loadMovements() {
        movements.clear();

        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                if (parts.length < 8) {
                    continue; // skip broken lines
                }
                try {
                    movements.add(new StockMovement(
                            LocalDateTime.parse(parts[0]),
                            parts[1],
                            parts[2],
                            Double.parseDouble(parts[3]),
                            StockMovement.Reason.valueOf(parts[4]),
                            parts[5],
                            parts[6],
                            Double.parseDouble(parts[7])));
                } catch (RuntimeException e) {
                    // skip lines with wrong values
                }
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException("Could not load the stock history: " + e.getMessage());
        }
    }

    // removes "|" and line breaks so they don't break the file format
    private String clean(String text) {
        return text.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }
}