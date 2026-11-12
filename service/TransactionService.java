package service;

import model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TransactionService {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final List<Transaction> transactions = new ArrayList<>();
    private int transactionCounter = 1;

    // Connect existing Product and Inventory services via dependency injection
    public TransactionService(ProductService productService, InventoryService inventoryService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    /**
     * Generates a unique, formatted transaction ID (e.g., TXN-0001, TXN-0002).
     */
    private synchronized String generateTransactionId() {
        return String.format("TXN-%04d", transactionCounter++);
    }

    /**
     * TASK 1: Process a Shrimp Sale
     * 1. Check if quantity is positive (> 0).
     * 2. Find the product via productService.
     * 3. Check if available stock (product.getStockQuantity()) is sufficient.
     *    - Throw IllegalArgumentException if insufficient stock[cite: 1, 11].
     * 4. Deduct quantity from product stock quantity[cite: 4, 11].
     * 5. Record stock movement in inventoryService with Reason.SALE and negative change.
     * 6. Create, store, and return a new Transaction object with status COMPLETED[cite: 7, 8].
     */
    public Transaction processSale(User user, String productId, double quantityKg) {
        /* TODO: Implement logic according to steps 1-6 above */    	
        return null;
    }

    /**
     * TASK 2: Process Cash Adjustments (CASH_IN / CASH_OUT)[cite: 8, 12]
     * 1. Validate that amount > 0.
     * 2. Validate that type is CASH_IN or CASH_OUT[cite: 8].
     * 3. Create a Transaction object without product binding.
     * 4. Add to transaction list and return the record.
     */
    public Transaction processCashTransaction(User user, TransactionType type, double amount, String note) {
        /* TODO: Implement logic according to steps 1-4 above */
        return null;
    }

    /**
     * TASK 3: Request a Transaction Void (Clerk Action)[cite: 2, 7, 11, 12]
     * 1. Verify user permission: check user.canRequestVoid() or role[cite: 2, 9, 11].
     * 2. Retrieve transaction by ID.
     * 3. Check if transaction is currently COMPLETED (cannot request void on already voided transactions)[cite: 7].
     * 4. Update transaction status to VOID_REQUESTED[cite: 7].
     * 5. Record voidRequestedBy username and voidReason[cite: 11].
     */
    public void requestVoid(User clerk, String transactionId, String reason) {
        /* TODO: Implement logic according to steps 1-5 above */
    }

    /**
     * TASK 4: Approve Void Request (Manager Action)[cite: 3, 7, 11, 12]
     * 1. Verify user permission: check manager.canApproveVoids()[cite: 3, 9, 11].
     * 2. Retrieve transaction by ID.
     * 3. Confirm transaction status is VOID_REQUESTED[cite: 7].
     * 4. If transaction type is SALE[cite: 8]:
     *    - Fetch product using transaction.getProductId()[cite: 4].
     *    - Restore stock: product.setStockQuantity(product.getStockQuantity() + transaction.getQuantityKg())[cite: 4, 11].
     *    - Log inventory movement: inventoryService.logMovement(..., StockMovement.Reason.VOID_RETURN, +quantity)[cite: 5, 11].
     * 5. Update transaction status to VOIDED[cite: 7, 11].
     * 6. Record manager.getUsername() in voidApprovedBy[cite: 11].
     */
    public void approveVoid(User manager, String transactionId) {
        /* TODO: Implement logic according to steps 1-6 above */
    }

    /**
     * TASK 5: Reject Void Request (Manager Action)[cite: 3, 7, 11, 12]
     * 1. Verify manager.canApproveVoids()[cite: 3, 9].
     * 2. Retrieve transaction by ID.
     * 3. Reset transaction status back to COMPLETED[cite: 7].
     * 4. Clear voidRequestedBy and voidReason fields.
     */
    public void rejectVoid(User manager, String transactionId) {
        /* TODO: Implement logic according to steps 1-4 above */
    }

    /**
     * TASK 6: Query and Reporting Methods[cite: 11, 12]
     */

    // Return all transactions (Manager view)[cite: 3, 11, 12]
    public List<Transaction> getAllTransactions() {
        return new ArrayList<>(transactions);
    }

    // Return transactions executed by a specific user (Clerk view)[cite: 2, 11, 12]
    public List<Transaction> getTransactionsByUser(String username) {
        return transactions.stream()
                .filter(t -> t.getPerformedBy().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    // Return all transactions currently waiting for manager approval (Status == VOID_REQUESTED)[cite: 7, 11, 12]
    public List<Transaction> getPendingVoidRequests() {
        return transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.VOID_REQUESTED)
                .collect(Collectors.toList());
    }

    // Calculate total net cash balance: Sum(SALE completed) + Sum(CASH_IN completed) - Sum(CASH_OUT completed)[cite: 8, 12]
    public double calculateTotalCashBalance() {
        double balance = 0.0;
        for (Transaction t : transactions) {
            if (t.getStatus() == TransactionStatus.COMPLETED) { // Exclude VOIDED and VOID_REQUESTED[cite: 7]
                if (t.getType() == TransactionType.SALE || t.getType() == TransactionType.CASH_IN) {
                    balance += t.getTotalAmount();
                } else if (t.getType() == TransactionType.CASH_OUT) {
                    balance -= t.getTotalAmount();
                }
            }
        }
        return balance;
    }
}