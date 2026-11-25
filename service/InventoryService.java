package service;

import model.Product;
import model.StockMovement;
import model.User;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages stock levels (restocking, sale deductions, void returns, low-stock reports)
 * and persists an immutable audit log to {@code data/inventory_log.txt}.
 */
public class InventoryService {

    /** Products at or below this threshold (in kg) are flagged as low stock. */
    public static final double LOW_STOCK_LEVEL = 25;

    private static final double MAX_QUANTITY = 100000;
    private static final int MAX_NOTE_LENGTH = 120;
    private static final String FILE_NAME = "data/inventory_log.txt";
    private static final String SEPARATOR = "|";

    private final ProductService productService;
    private final List<StockMovement> movements = new ArrayList<>();
    private final String filePath;

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

    public boolean isLowStock(Product p) {
        return p.getStockQuantity() <= LOW_STOCK_LEVEL;
    }

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
        List<Product> result = new ArrayList<>();
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

    // ---------- RESTOCK (Manager) ----------

    public void restock(User by, String productId, double kg, String note) {
        requireUser(by);
        if (!by.canRestock()) {
            throw new ValidationException("Only a manager can restock products.");
        }
        Product p = requireProduct(productId);
        double qty = checkQuantity(kg);
        String cleanNote = checkNote(note);

        changeStock(p, qty, StockMovement.Reason.RESTOCK, by,
                cleanNote.isEmpty() ? "Restock" : cleanNote);
    }

    // ---------- LOW-STOCK REPORT (Clerk) ----------

    public void requestRestock(User by, String productId) {
        requireUser(by);
        if (by.canRestock()) {
            throw new ValidationException("Managers can restock directly without filing a low-stock report.");
        }
        Product p = requireProduct(productId);

        if (!isLowStock(p)) {
            throw new ValidationException(p.getName() + " still has enough stock ("
                    + String.format("%.2f", p.getStockQuantity()) + " kg).");
        }
        if (hasRestockRequest(p.getProductId())) {
            throw new ValidationException("A low-stock report for " + p.getName()
                    + " is already waiting for the manager.");
        }
        addMovement(new StockMovement(now(), p.getProductId(), p.getName(), 0,
                StockMovement.Reason.LOW_STOCK_REPORT, by.getFullName(),
                "Reported as low on stock", p.getStockQuantity()));
    }

    /**
     * Returns {@code true} if the product currently has an unresolved low-stock report
     * and is still at or below {@link #LOW_STOCK_LEVEL}.
     */
    public boolean hasRestockRequest(String productId) {
        Product p = productService.findById(productId);
        if (p == null || !isLowStock(p)) {
            return false;
        }
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

    // ---------- USED BY SALES ----------

    public void ensureAvailable(String productId, double kg) {
        Product p = requireProduct(productId);
        if (!p.isActive()) {
            throw new ValidationException(p.getName() + " is archived and cannot be sold.");
        }
        double qty = checkQuantity(kg);
        if (qty > p.getStockQuantity()) {
            throw new InsufficientStockException(p.getName(), p.getStockQuantity(), qty);
        }
    }

    public void deductStock(User by, String productId, double kg, String note) {
        requireUser(by);
        ensureAvailable(productId, kg);
        Product p = requireProduct(productId);
        changeStock(p, -checkQuantity(kg), StockMovement.Reason.SALE, by, checkNote(note));
    }

    public void returnStock(User by, String productId, double kg, String note) {
        requireUser(by);
        Product p = requireProduct(productId);
        changeStock(p, checkQuantity(kg), StockMovement.Reason.VOID_RETURN, by, checkNote(note));
    }

    // ---------- OPENING STOCK ----------

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

    /** @return all stock movements in reverse chronological order (newest first) */
    public List<StockMovement> getMovements() {
        List<StockMovement> copy = new ArrayList<>(movements);
        Collections.reverse(copy);
        return copy;
    }

    /** @return stock movements for a specific product (newest first) */
    public List<StockMovement> getMovementsFor(String productId) {
        List<StockMovement> result = new ArrayList<>();
        for (StockMovement m : getMovements()) {
            if (m.getProductId().equals(productId)) {
                result.add(m);
            }
        }
        return result;
    }

    /** @return {@code true} if the product has ever been referenced in stock movements */
    public boolean hasMovementsFor(String productId) {
        for (StockMovement m : movements) {
            if (m.getProductId().equalsIgnoreCase(productId)) {
                return true;
            }
        }
        return false;
    }

    // ---------- HELPER METHODS ----------

    private void changeStock(Product p, double change, StockMovement.Reason reason,
                             User by, String note) {
        double oldStock = p.getStockQuantity();
        double newStock = round2(oldStock + change);

        p.setStockQuantity(newStock);
        try {
            productService.saveProducts();
            addMovement(new StockMovement(now(), p.getProductId(), p.getName(), change,
                    reason, by.getFullName(), note, newStock));
        } catch (RuntimeException e) {
            p.setStockQuantity(oldStock);
            try {
                productService.saveProducts();
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
    }

    private void addMovement(StockMovement m) {
        movements.add(m);
        try {
            appendToFile(m);
        } catch (RuntimeException e) {
            movements.remove(m);
            throw e;
        }
    }

    private Product requireProduct(String productId) {
        Product p = productService.findById(productId);
        if (p == null) {
            throw new ValidationException("Product not found: " + productId);
        }
        return p;
    }

    private void requireUser(User by) {
        if (by == null) {
            throw new ValidationException("No user is signed in.");
        }
    }

    private double checkQuantity(double kg) {
        if (Double.isNaN(kg) || Double.isInfinite(kg)) {
            throw new ValidationException("Quantity must be a valid number.");
        }
        double qty = round2(kg);
        if (qty < 0.01) {
            throw new ValidationException("Quantity must be at least 0.01 kg.");
        }
        if (qty > MAX_QUANTITY) {
            throw new ValidationException("Quantity cannot be more than 100,000 kg.");
        }
        return qty;
    }

    private String checkNote(String note) {
        if (note == null) {
            return "";
        }
        String cleaned = clean(note.trim());
        if (cleaned.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Note must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        return cleaned;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    // ---------- SAVE AND LOAD FILE ----------

    private synchronized void appendToFile(StockMovement m) {
        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(m.getDateTime() + SEPARATOR
                    + m.getProductId() + SEPARATOR
                    + clean(m.getProductName()) + SEPARATOR
                    + m.getChange() + SEPARATOR
                    + m.getReason().name() + SEPARATOR
                    + clean(m.getPerformedBy()) + SEPARATOR
                    + clean(m.getNote()) + SEPARATOR
                    + m.getStockAfter());
            if (writer.checkError()) {
                throw new IOException("Write failed while appending stock history.");
            }
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

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                if (parts.length < 8) {
                    continue;
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
                    // skip broken lines
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load the stock history: " + e.getMessage());
        }
    }

    private static String clean(String text) {
        return text.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }
}