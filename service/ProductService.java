package service;

import model.Product;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all the product operations (add, edit, delete, search)
 * and saves them to a text file. It does not need InventoryService,
 * so it still works even if the inventory part is empty.
 *
 * Each product is saved as one line, separated by "|":
 * id|name|pricePerKg|description|stockQuantity
 */
public class ProductService {

    private static final String FILE_NAME = "data/products.txt";
    private static final String SEPARATOR = "|";

    private ArrayList<Product> products = new ArrayList<>();
    private String filePath;

    public ProductService() {
        this(FILE_NAME);
    }

    public ProductService(String filePath) {
        this.filePath = filePath;
        loadProducts();

        // if there is no saved data yet, use the default shrimp products
        if (products.isEmpty()) {
            addDefaultProducts();
            saveProducts();
        }
    }

    // ---------- GET / SEARCH ----------

    public List<Product> getAllProducts() {
        return products;
    }

    public Product findById(String productId) {
        if (productId == null) {
            return null;
        }
        for (Product p : products) {
            if (p.getProductId().equalsIgnoreCase(productId.trim())) {
                return p;
            }
        }
        return null;
    }

    public List<Product> searchByName(String keyword) {
        ArrayList<Product> results = new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            results.addAll(products);
            return results;
        }

        String k = keyword.trim().toLowerCase();
        for (Product p : products) {
            if (p.getName().toLowerCase().contains(k)
                    || p.getProductId().toLowerCase().contains(k)
                    || p.getDescription().toLowerCase().contains(k)) {
                results.add(p);
            }
        }
        return results;
    }

    public boolean nameExists(String name) {
        for (Product p : products) {
            if (p.getName().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    // ---------- ADD ----------

    public Product addProduct(String name, double pricePerKg, String description, double initialStock) {
        checkInputs(name, pricePerKg, description);

        if (initialStock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }
        if (nameExists(name)) {
            throw new IllegalArgumentException("A product named \"" + name.trim() + "\" already exists.");
        }

        Product p = new Product(getNextId(), name.trim(), pricePerKg, description.trim(), initialStock);
        products.add(p);
        saveProducts();
        return p;
    }

    public Product addProduct(String name, double pricePerKg, String description) {
        return addProduct(name, pricePerKg, description, 0);
    }

    // ---------- UPDATE ----------

    // updates name, price and description (stock stays the same)
    public Product updateProduct(String productId, String name, double pricePerKg, String description) {
        Product p = findById(productId);
        if (p == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        return updateProduct(productId, name, pricePerKg, description, p.getStockQuantity());
    }

    // same as above, but also sets the stock (used to enter the initial stock)
    public Product updateProduct(String productId, String name, double pricePerKg,
                                 String description, double stockQuantity) {
        Product p = findById(productId);
        if (p == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }

        checkInputs(name, pricePerKg, description);
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }

        // the name must not be used by a different product
        for (Product other : products) {
            if (other != p && other.getName().equalsIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("Another product is already named \"" + name.trim() + "\".");
            }
        }

        p.setName(name.trim());
        p.setPricePerKg(pricePerKg);
        p.setDescription(description.trim());
        p.setStockQuantity(stockQuantity);
        saveProducts();
        return p;
    }

    // ---------- DELETE ----------

    public void removeProduct(String productId) {
        Product p = findById(productId);
        if (p == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        products.remove(p);
        saveProducts();
    }

    // ---------- HELPER METHODS ----------

    private void checkInputs(String name, double price, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price per kg must be greater than 0.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required.");
        }
    }

    // makes IDs like P001, P002, P003...
    // public so the Add Product window can show the ID before saving
    public String getNextId() {
        int highest = 0;
        for (Product p : products) {
            try {
                int number = Integer.parseInt(p.getProductId().substring(1));
                if (number > highest) {
                    highest = number;
                }
            } catch (Exception e) {
                // ignore IDs that are not in the P### format
            }
        }
        return String.format("P%03d", highest + 1);
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
        products.add(new Product(getNextId(), name, price, description, 0));
    }

    // ---------- SAVE AND LOAD FILE ----------

    // saves all products to the file (call this again after the stock changes)
    public void saveProducts() {
        try {
            File file = new File(filePath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            PrintWriter writer = new PrintWriter(new FileWriter(file));
            for (Product p : products) {
                String line = p.getProductId() + SEPARATOR
                        + clean(p.getName()) + SEPARATOR
                        + p.getPricePerKg() + SEPARATOR
                        + clean(p.getDescription()) + SEPARATOR
                        + p.getStockQuantity();
                writer.println(line);
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException("Could not save products: " + e.getMessage());
        }
    }

    private void loadProducts() {
        products.clear();

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

                // "\\|" because split() treats | as a special symbol
                String[] parts = line.split("\\|", -1);
                if (parts.length < 5) {
                    continue; // skip broken lines
                }

                try {
                    String id = parts[0];
                    String name = parts[1];
                    double price = Double.parseDouble(parts[2]);
                    String description = parts[3];
                    double stock = Double.parseDouble(parts[4]);
                    products.add(new Product(id, name, price, description, stock));
                } catch (NumberFormatException e) {
                    // skip lines with wrong numbers
                }
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException("Could not load products: " + e.getMessage());
        }
    }

    // removes "|" and line breaks so they don't break the file format
    private String clean(String text) {
        return text.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }
}