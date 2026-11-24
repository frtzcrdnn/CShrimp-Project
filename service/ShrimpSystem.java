package service;

import model.Transaction;
import model.TransactionStatus;
import model.TransactionType;
import model.User;

import java.time.LocalDate;
import java.util.List;

/**
 * The "System" class: it starts the whole application's logic in one place.
 * It creates the services once, connects them to each other, and remembers
 * who is signed in. The screens only talk to this class, so they never create
 * services on their own. (Named ShrimpSystem because java.lang.System already exists.)
 */
public class ShrimpSystem {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final TransactionService transactionService;
    private final AuthenticationService authenticationService;
    private final Inventory inventory;

    private User currentUser; // null while nobody is signed in

    public ShrimpSystem() {
        productService = new ProductService();
        inventoryService = new InventoryService(productService);
        transactionService = new TransactionService(productService, inventoryService);
        authenticationService = new AuthenticationService();
        inventory = new Inventory(productService, inventoryService);
    }

    // ---------- SESSION ----------

    /** Checks the login and remembers the user. Throws ValidationException if it is wrong. */
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

    public ProductService getProductService() { return productService; }
    public InventoryService getInventoryService() { return inventoryService; }
    public TransactionService getTransactionService() { return transactionService; }
    public AuthenticationService getAuthenticationService() { return authenticationService; }
    public Inventory getInventory() { return inventory; }

    // ---------- FIGURES FOR THE DASHBOARDS ----------

    // number of completed sales by everyone
    public int getCompletedSalesCount() {
        return countSales(transactionService.getAllTransactions());
    }

    // number of completed sales by one user
    public int getCompletedSalesCount(String username) {
        return countSales(transactionService.getTransactionsByUser(username));
    }

    // total money of the completed sales of one user
    public double getCompletedSalesTotal(String username) {
        double total = 0;
        for (Transaction t : transactionService.getTransactionsByUser(username)) {
            if (isCompletedSale(t)) {
                total += t.getTotalAmount();
            }
        }
        return total;
    }

    // total money of the completed sales that one user made today
    public double getTodaySalesTotal(String username) {
        LocalDate today = LocalDate.now();
        double total = 0;
        for (Transaction t : transactionService.getTransactionsByUser(username)) {
            if (isCompletedSale(t) && t.getDateTime().toLocalDate().equals(today)) {
                total += t.getTotalAmount();
            }
        }
        return total;
    }

    // how many of one user's transactions are waiting for the manager to approve a void
    public int getPendingVoidCount(String username) {
        int count = 0;
        for (Transaction t : transactionService.getTransactionsByUser(username)) {
            if (t.getStatus() == TransactionStatus.VOID_REQUESTED) {
                count++;
            }
        }
        return count;
    }

    // newest first, at most "limit" rows
    public List<Transaction> newestFirst(List<Transaction> list, int limit) {
        java.util.ArrayList<Transaction> copy = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy.size() > limit ? new java.util.ArrayList<>(copy.subList(0, limit)) : copy;
    }

    private int countSales(List<Transaction> list) {
        int count = 0;
        for (Transaction t : list) {
            if (isCompletedSale(t)) {
                count++;
            }
        }
        return count;
    }

    private boolean isCompletedSale(Transaction t) {
        return t.getType() == TransactionType.SALE && t.getStatus() == TransactionStatus.COMPLETED;
    }
}
