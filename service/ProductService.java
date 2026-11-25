package service;

import model.Product;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Manages the shrimp product catalog (add, update, archive/delete, search)
 * and persists records to {@code data/products.txt}.
 */
public class ProductService {

    private static final String FILE_NAME = "data/products.txt";
    private static final String SEPARATOR = "|";
    private static final double MAX_PRICE = 10_000_000;
    private static final double MAX_STOCK = 100_000;

    private final List<Product> products = new ArrayList<>();
    private final String filePath;
    private int highestIdNumber = 0;
    private Predicate<String> historyCheck;

    public ProductService() {
        this(FILE_NAME);
    }

    public ProductService(String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        if (!file.exists()) {
            addDefaultProducts();
            saveProducts();
        } else {
            loadProducts();
        }
    }

    /**
     * Registers a callback used to check whether a product has historical
     * stock movements or transactions before deleting it.
     */
    public void setHistoryCheck(Predicate<String> historyCheck) {
        this.historyCheck = historyCheck;
    }

    /**
     * Ensures auto-generated product IDs remain strictly higher than any ID
     * referenced in historical logs or transactions.
     */
    public void ensureNextIdAbove(String productId) {
        int num = parseIdNumber(productId);
        if (num > highestIdNumber) {
            highestIdNumber = num;
        }
    }

    // ---------- GET / SEARCH ----------

    /** @return defensive copy of all active products in the catalog */
    public List<Product> getAllProducts() {
        List<Product> active = new ArrayList<>();
        for (Product p : products) {
            if (p.isActive()) {
                active.add(p);
            }
        }
        return active;
    }

    /** @return defensive copy of all products, including archived items */
    public List<Product> getAllProductsIncludingArchived() {
        return new ArrayList<>(products);
    }

    public Product findById(String productId) {
        if (productId == null) {
            return null;
        }
        String target = productId.trim();
        for (Product p : products) {
            if (p.getProductId().equalsIgnoreCase(target)) {
                return p;
            }
        }
        return null;
    }

    public List<Product> searchByName(String keyword) {
        List<Product> results = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllProducts();
        }

        String k = keyword.trim().toLowerCase();
        for (Product p : products) {
            if (!p.isActive()) {
                continue;
            }
            if (p.getName().toLowerCase().contains(k)
                    || p.getProductId().toLowerCase().contains(k)
                    || p.getDescription().toLowerCase().contains(k)) {
                results.add(p);
            }
        }
        return results;
    }

    public boolean nameExists(String name) {
        if (name == null) {
            return false;
        }
        String cleaned = clean(name.trim());
        for (Product p : products) {
            if (p.isActive() && p.getName().equalsIgnoreCase(cleaned)) {
                return true;
            }
        }
        return false;
    }

    // ---------- ADD ----------

    public Product addProduct(String name, double pricePerKg, String description, double initialStock) {
        checkInputs(name, pricePerKg, description);
        double cleanPrice = validatePrice(pricePerKg);
        double cleanStock = validateStock(initialStock);

        String cleanName = clean(name.trim());
        String cleanDesc = clean(description.trim());

        if (nameExists(cleanName)) {
            throw new ValidationException("A product named \"" + cleanName + "\" already exists.");
        }

        String newId = getNextId();
        Product p = new Product(newId, cleanName, cleanPrice, cleanDesc, cleanStock, true);
        highestIdNumber = Math.max(highestIdNumber, parseIdNumber(newId));
        products.add(p);
        try {
            saveProducts();
        } catch (RuntimeException e) {
            products.remove(p);
            throw e;
        }
        return p;
    }

    public Product addProduct(String name, double pricePerKg, String description) {
        return addProduct(name, pricePerKg, description, 0);
    }

    // ---------- UPDATE ----------

    public Product updateProduct(String productId, String name, double pricePerKg, String description) {
        Product p = findById(productId);
        if (p == null) {
            throw new ValidationException("Product not found: " + productId);
        }
        return updateProduct(productId, name, pricePerKg, description, p.getStockQuantity());
    }

    public Product updateProduct(String productId, String name, double pricePerKg,
                                 String description, double stockQuantity) {
        Product p = findById(productId);
        if (p == null) {
            throw new ValidationException("Product not found: " + productId);
        }

        checkInputs(name, pricePerKg, description);
        double cleanPrice = validatePrice(pricePerKg);
        double cleanStock = validateStock(stockQuantity);

        String cleanName = clean(name.trim());
        String cleanDesc = clean(description.trim());

        for (Product other : products) {
            if (other != p && other.isActive() && other.getName().equalsIgnoreCase(cleanName)) {
                throw new ValidationException("Another product is already named \"" + cleanName + "\".");
            }
        }

        String oldName = p.getName();
        double oldPrice = p.getPricePerKg();
        String oldDesc = p.getDescription();
        double oldStock = p.getStockQuantity();

        p.setName(cleanName);
        p.setPricePerKg(cleanPrice);
        p.setDescription(cleanDesc);
        p.setStockQuantity(cleanStock);
        try {
            saveProducts();
        } catch (RuntimeException e) {
            p.setName(oldName);
            p.setPricePerKg(oldPrice);
            p.setDescription(oldDesc);
            p.setStockQuantity(oldStock);
            throw e;
        }
        return p;
    }

    // ---------- DELETE / ARCHIVE ----------

    /**
     * Permanently removes a product only if it has zero stock and no historical
     * transactions or stock movements. Products with history should be archived instead.
     */
    public void removeProduct(String productId) {
        Product p = findById(productId);
        if (p == null) {
            throw new ValidationException("Product not found: " + productId);
        }
        if (p.getStockQuantity() > 0) {
            throw new ValidationException("Cannot delete \"" + p.getName()
                    + "\" while " + String.format("%.2f", p.getStockQuantity())
                    + " kg is still in stock.");
        }
        if (historyCheck != null && historyCheck.test(p.getProductId())) {
            throw new ValidationException("Cannot delete \"" + p.getName()
                    + "\" because it has past sales or inventory history. Archive it instead.");
        }

        int index = products.indexOf(p);
        products.remove(p);
        try {
            saveProducts();
        } catch (RuntimeException e) {
            products.add(index, p);
            throw e;
        }
    }

    /**
     * Archives a zero-stock product so it is hidden from active sales while preserving
     * historical transactions, pending voids, and inventory logs.
     */
    public void archiveProduct(String productId) {
        Product p = findById(productId);
        if (p == null) {
            throw new ValidationException("Product not found: " + productId);
        }
        if (p.getStockQuantity() > 0) {
            throw new ValidationException("Cannot archive \"" + p.getName()
                    + "\" while " + String.format("%.2f", p.getStockQuantity())
                    + " kg is still in stock.");
        }
        boolean oldActive = p.isActive();
        p.setActive(false);
        try {
            saveProducts();
        } catch (RuntimeException e) {
            p.setActive(oldActive);
            throw e;
        }
    }

    // ---------- HELPER METHODS ----------

    private void checkInputs(String name, double price, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Product name is required.");
        }
        if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0) {
            throw new ValidationException("Price per kg must be a valid number greater than 0.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new ValidationException("Description is required.");
        }
    }

    private double validatePrice(double price) {
        if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0) {
            throw new ValidationException("Price per kg must be a valid number greater than 0.");
        }
        double rounded = Math.round(price * 100.0) / 100.0;
        if (rounded < 0.01) {
            throw new ValidationException("Price per kg must be at least Php 0.01.");
        }
        if (rounded > MAX_PRICE) {
            throw new ValidationException("Price per kg cannot exceed Php 10,000,000.");
        }
        return rounded;
    }

    private double validateStock(double stock) {
        if (Double.isNaN(stock) || Double.isInfinite(stock) || stock < 0) {
            throw new ValidationException("Stock must be a valid number (0 or higher).");
        }
        double rounded = Math.round(stock * 100.0) / 100.0;
        if (rounded > MAX_STOCK) {
            throw new ValidationException("Stock cannot exceed 100,000 kg.");
        }
        return rounded;
    }

    /** Returns the next monotonic product ID (e.g., P001, P009) without reusing deleted IDs. */
    public String getNextId() {
        int highest = highestIdNumber;
        for (Product p : products) {
            int num = parseIdNumber(p.getProductId());
            if (num > highest) {
                highest = num;
            }
        }
        return String.format("P%03d", highest + 1);
    }

    private static int parseIdNumber(String id) {
        if (id == null || id.length() < 2) {
            return 0;
        }
        try {
            return Integer.parseInt(id.trim().substring(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void addDefaultProducts() {
        addDefault("CHLSO - Cooked Head-Less Shell-On", 700, "Cooked shrimp with head removed and shell retained");
        addDefault("CHOSO - Cooked Head-On Shell-On", 700, "Cooked shrimp with head and shell retained");
        addDefault("FHOSO - Fresh Head-On Shell-On", 570, "Raw whole shrimp with head and shell intact");
        addDefault("FHLSO - Fresh Head-Less Shell-On", 590, "Raw shrimp with head removed but shell kept on");
        addDefault("NOBASHI", 610, "Peeled and deveined, tail-on, stretched shrimp");
        addDefault("FPD - Fresh Peeled & Deveined", 520, "Raw shrimp fully peeled and deveined");
        addDefault("FPDTO - Fresh Peeled & Deveined Tail On", 500, "Raw shrimp peeled and deveined with the tail left on");
        addDefault("FPU - Fresh Peeled & Undeveined", 490, "Raw shrimp peeled but not deveined");
    }

    private void addDefault(String name, double price, String description) {
        String id = getNextId();
        highestIdNumber = parseIdNumber(id);
        products.add(new Product(id, name, price, description, 0, true));
    }

    // ---------- SAVE AND LOAD FILE ----------

    public synchronized void saveProducts() {
        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        File temp = new File(filePath + ".tmp");
        try {
            try (PrintWriter writer = new PrintWriter(new FileWriter(temp))) {
                for (Product p : products) {
                    writer.println(p.getProductId() + SEPARATOR
                            + clean(p.getName()) + SEPARATOR
                            + p.getPricePerKg() + SEPARATOR
                            + clean(p.getDescription()) + SEPARATOR
                            + p.getStockQuantity() + SEPARATOR
                            + p.isActive());
                }
                if (writer.checkError()) {
                    throw new IOException("Write failed while saving products.");
                }
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            temp.delete();
            throw new RuntimeException("Could not save products: " + e.getMessage());
        }
    }

    private void loadProducts() {
        products.clear();
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
                if (parts.length < 5) {
                    continue;
                }
                try {
                    String id = parts[0].trim();
                    String name = parts[1];
                    double price = Double.parseDouble(parts[2]);
                    String description = parts[3];
                    double stock = Double.parseDouble(parts[4]);
                    boolean active = parts.length < 6 || Boolean.parseBoolean(parts[5].trim());

                    products.add(new Product(id, name, price, description, stock, active));
                    highestIdNumber = Math.max(highestIdNumber, parseIdNumber(id));
                } catch (NumberFormatException e) {
                    // skip corrupted row without wiping valid rows
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load products: " + e.getMessage());
        }
    }

    private static String clean(String text) {
        return text.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }
}