package service;

import model.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * The shrimp inventory: every product with its stock, seen as one whole.
 * The dashboards and the inventory screen use it to ask questions such as
 * "how many items are low on stock?".
 *
 * Inventory only READS. Changing the stock (restock, sale, void) and writing
 * the stock history is done by InventoryService, so every change is recorded.
 */
public class Inventory {

    private final ProductService productService;
    private final InventoryService inventoryService;

    public Inventory(ProductService productService, InventoryService inventoryService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    public List<Product> getItems() {
        return new ArrayList<>(productService.getAllProducts());
    }

    public Product findItem(String productId) {
        return productService.findById(productId);
    }

    public int getItemCount() {
        return productService.getAllProducts().size();
    }

    public double getTotalStockKg() {
        double total = 0;
        for (Product p : productService.getAllProducts()) {
            total += p.getStockQuantity();
        }
        return Math.round(total * 100.0) / 100.0;
    }

    // what all the stock on hand is worth at the current prices
    public double getTotalStockValue() {
        double total = 0;
        for (Product p : productService.getAllProducts()) {
            total += p.getStockQuantity() * p.getPricePerKg();
        }
        return Math.round(total * 100.0) / 100.0;
    }

    public String getStatus(Product p) {
        return inventoryService.getStatus(p);
    }

    // items at or below the low-stock level (out-of-stock items are included)
    public List<Product> getLowStockItems() {
        return inventoryService.getLowStockProducts();
    }

    // low on stock but not yet empty
    public int getLowStockCount() {
        int count = 0;
        for (Product p : productService.getAllProducts()) {
            if (inventoryService.isLowStock(p) && !inventoryService.isOutOfStock(p)) {
                count++;
            }
        }
        return count;
    }

    public int getOutOfStockCount() {
        int count = 0;
        for (Product p : productService.getAllProducts()) {
            if (inventoryService.isOutOfStock(p)) {
                count++;
            }
        }
        return count;
    }

    // true if a clerk reported this product and the manager has not restocked it yet
    public boolean hasRestockRequest(Product p) {
        return inventoryService.hasRestockRequest(p.getProductId());
    }
}
