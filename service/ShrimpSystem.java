package service;

import model.StockMovement;
import model.Transaction;
import model.TransactionStatus;
import model.TransactionType;
import model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central application facade that initializes, connects, and coordinates all
 * domain services and active user session state.
 */
public class ShrimpSystem {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;
    private final AuthenticationService authenticationService;
    private final Inventory inventory;

    private User currentUser;

    public ShrimpSystem() {
        productService = new ProductService();
        inventoryService = new InventoryService(productService);
        transactionService = new TransactionService(productService, inventoryService);
        authenticationService = new AuthenticationService();
        inventory = new Inventory(productService, inventoryService);

        // Prevent deleted product IDs from ever being reused across restarts
        for (StockMovement m : inventoryService.getMovements()) {
            productService.ensureNextIdAbove(m.getProductId());
        }
        for (Transaction t : transactionService.getAllTransactions()) {
            if (t.getProductId() != null) {
                productService.ensureNextIdAbove(t.getProductId());
            }
        }

        // Prevent hard deletion of products referenced in stock logs or transactions
        productService.setHistoryCheck(productId ->
                inventoryService.hasMovementsFor(productId)
                        || transactionService.hasTransactionsForProduct(productId));
    }

    // ---------- SESSION ----------

    public User login(String username, String password) {
        currentUser = authenticationService.login(username, password);
        return currentUser;
    }

    public void logout() {
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    // ---------- SERVICES ----------

    public ProductService getProductService() {
        return productService;
    }

    public InventoryService getInventoryService() {
        return inventoryService;
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }

    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    public Inventory getInventory() {
        return inventory;
    }

    // ---------- DASHBOARD METRICS ----------

    public int getCompletedSalesCount() {
        return countSales(transactionService.getAllTransactions());
    }

    public int getCompletedSalesCount(String username) {
        return countSales(transactionService.getTransactionsByUser(username));
    }

    public double getCompletedSalesTotal(String username) {
        return transactionService.calculateSalesByUser(username);
    }

    public double getTodaySalesTotal(String username) {
        return transactionService.calculateTodaySalesByUser(username);
    }

    public int getPendingVoidCount(String username) {
        int count = 0;
        for (Transaction t : transactionService.getTransactionsByUser(username)) {
            if (t.getStatus() == TransactionStatus.VOID_REQUESTED) {
                count++;
            }
        }
        return count;
    }

    public List<Transaction> newestFirst(List<Transaction> list, int limit) {
        List<Transaction> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy.size() > limit ? new ArrayList<>(copy.subList(0, limit)) : copy;
    }

    private int countSales(List<Transaction> list) {
        int count = 0;
        for (Transaction t : list) {
            if (isActiveSale(t)) {
                count++;
            }
        }
        return count;
    }

    private boolean isActiveSale(Transaction t) {
        return t.getType() == TransactionType.SALE && t.getStatus() != TransactionStatus.VOIDED;
    }
}