package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import model.Product;
import model.StockMovement;
import model.User;

public class InventoryService {
    private static final double MAX_RESTOCK_KG = 100000;

    private final ProductService productService;
    private final List<StockMovement> movements;
    private final Runnable saver;
    
    public InventoryService(ProductService productService, List<StockMovement> movements, Runnable saver) {
        this.productService = productService;
        this.movements = movements;
        this.saver = saver;
    }
    
    // stock movements, newest first
    public List<StockMovement> getMovements() {
        List<StockMovement> copy = new ArrayList<>(movements);
        Collections.reverse(copy);
        return copy;
    }
    
    public List<Product> getLowStockProducts() {
    	
    }
    
    
	
}
