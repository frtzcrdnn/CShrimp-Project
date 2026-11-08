package service;

/**
 * Thrown when a sale asks for more kilograms than are in stock.
 * It extends IllegalArgumentException, so a normal
 * "catch (IllegalArgumentException e)" still catches it, but the Sale screen
 * can catch it first to show a special stock warning.
 */


public class InsufficientStockException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String productName;
    private final double available;
    private final double requested;

    public InsufficientStockException(String productName, double available, double requested) {
        super(String.format("Not enough stock for %s. Available: %.2f kg, requested: %.2f kg.",
                productName, available, requested));
        this.productName = productName;
        this.available = available;
        this.requested = requested;
    }

    public String getProductName() { return productName; }
    public double getAvailable() { return available; }
    public double getRequested() { return requested; }
}
