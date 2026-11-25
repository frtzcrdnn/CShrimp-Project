package model;

/**
 * Represents a shrimp product variant sold or tracked in inventory.
 */
public class Product {

    private final String productId;
    private String name;
    private double pricePerKg;
    private String description;
    private double stockQuantity;
    private boolean active;

    /**
     * Creates an active shrimp product.
     *
     * @param productId     unique product identifier (e.g., "P001")
     * @param name          product display name
     * @param pricePerKg    selling price per kilogram in Philippine Pesos
     * @param description   short product description
     * @param stockQuantity initial stock on hand in kilograms
     */
    public Product(String productId, String name, double pricePerKg,
                   String description, double stockQuantity) {
        this(productId, name, pricePerKg, description, stockQuantity, true);
    }

    /**
     * Creates a shrimp product with an explicit active/archived state.
     *
     * @param productId     unique product identifier
     * @param name          product display name
     * @param pricePerKg    selling price per kilogram
     * @param description   short product description
     * @param stockQuantity stock on hand in kilograms
     * @param active        {@code true} if available in active catalog; {@code false} if archived
     */
    public Product(String productId, String name, double pricePerKg,
                   String description, double stockQuantity, boolean active) {
        this.productId = productId;
        this.name = name;
        this.pricePerKg = pricePerKg;
        this.description = description;
        this.stockQuantity = stockQuantity;
        this.active = active;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(double pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(double stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}