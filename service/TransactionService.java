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
     *    - Throw ValidationException if insufficient stock.
     * 4. Deduct quantity from product stock quantity.
     * 5. Record stock movement in inventoryService with Reason.SALE and negative change.
     * 6. Create, store, and return a new Transaction object with status COMPLETED.
     */
    public Transaction processSale(User user, String productId, double quantityKg) throws ValidationException {
        // 1. Input Validations
        if (Double.isNaN(quantityKg) || Double.isInfinite(quantityKg) || quantityKg <= 0) {
            throw new ValidationException("Quantity must be a valid positive number.");
        }
        if (user == null) {
            throw new ValidationException("No user is signed in.");
        }

        // 2. Find product
        Product product = productService.findById(productId);
        if (product == null) {
            throw new ValidationException("Product not found: " + productId);
        }

        // InventoryService applies its own quantity rules and reports insufficient stock.
        // Use the same rounded quantity in the transaction that it records in stock history.
        double soldQuantity = Math.round(quantityKg * 100.0) / 100.0;
        inventoryService.ensureAvailable(productId, soldQuantity);
        double pricePerKg = product.getPricePerKg();
        double totalAmount = Math.round(soldQuantity * pricePerKg * 100.0) / 100.0;

        inventoryService.deductStock(user, productId, soldQuantity, "Sale");

        // 6. create a transaction object
        Transaction transaction = new Transaction(generateTransactionId(),
                    LocalDateTime.now().withNano(0), 
                    TransactionType.SALE,
                    product.getProductId(), 
                    product.getName(), 
                    soldQuantity,
                    pricePerKg, 
                    totalAmount, 
                    user.getUsername());
        transactions.add(transaction);
        return transaction;
    }

    /**
     * TASK 2: Process Cash Adjustments (CASH_IN / CASH_OUT)
     * 1. Validate that amount > 0.
     * 2. Validate that type is CASH_IN or CASH_OUT
     * 3. Create a Transaction object without product binding.
     * 4. Add to transaction list and return the record.
     * @throws ValidationException 
     */
    public Transaction processCashTransaction(User user, TransactionType type, double amount, String note) throws ValidationException {
        // 1-2
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            throw new ValidationException("Amount must be a valid positive number.");
        }
        if (user == null) {
            throw new ValidationException("No user is signed in.");
        }

        if (type == null || (type != TransactionType.CASH_IN && type != TransactionType.CASH_OUT)) {
            throw new ValidationException("Transaction must be CASH IN or CASH OUT");
        }

        // 3.
        double rounded = Math.round(amount * 100) / 100;
        Transaction transaction = new Transaction(
                    generateTransactionId(),
                    LocalDateTime.now().withNano(0),
                    type, 
                    null,
                    note,
                    0.0,
                    0.0,
                    rounded, 
                    user.getUsername());
    
        // 4.
        transactions.add(transaction);
        return transaction;
    }

    /**
     * TASK 3: Request a Transaction Void (Clerk Action)
     * 1. Verify user permission: check user.canRequestVoid() or role
     * 2. Retrieve transaction by ID.
     * 3. Check if transaction is currently COMPLETED (cannot request void on already voided transactions)
     * 4. Update transaction status to VOID_REQUESTED
     * 5. Record voidRequestedBy username and voidReason.
     */
    public void requestVoid(User clerk, String transactionId, String reason) throws ValidationException {
        // 1. Check if the user is allowed to request a void.
        if (clerk == null) {
            throw new ValidationException("No user is signed in.");
        }
        if (!clerk.canRequestVoid()) {
            throw new ValidationException("Only a clerk can request a transaction void.");
        }

        // Validate the reason before changing the transaction.
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Void reason is required.");
        }

        // 2. Find the transaction.
        Transaction transaction = findTransaction(transactionId);

        // 3. Only completed transactions can be requested for void.
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ValidationException("Only completed transactions can be requested for void.");
        }

        // 4-5. Save the request details and update its status.
        transaction.setStatus(TransactionStatus.VOID_REQUESTED);
        transaction.setVoidRequestedBy(clerk.getUsername());
        transaction.setVoidReason(reason.trim());
    }

    /**
     * TASK 4: Approve Void Request (Manager Action)
     * 1. Verify user permission: check manager.canApproveVoids()
     * 2. Retrieve transaction by ID.
     * 3. Confirm transaction status is VOID_REQUESTED
     * 4. If transaction type is SALE
     *    - Fetch product using transaction.getProductId()
     *    - Restore stock: product.setStockQuantity(product.getStockQuantity() + transaction.getQuantityKg())
     *    - Log inventory movement: inventoryService.logMovement(..., StockMovement.Reason.VOID_RETURN, +quantity)
     * 5. Update transaction status to VOIDED
     * 6. Record manager.getUsername() in voidApprovedBy
     */
    public void approveVoid(User manager, String transactionId) throws ValidationException {
        // 1. Check if the user is allowed to approve void requests.
        if (manager == null) {
            throw new ValidationException("No user is signed in.");
        }
        if (!manager.canApproveVoids()) {
            throw new ValidationException("Only a manager can approve void requests.");
        }

        // 2-3. Find the transaction and check its status.
        Transaction transaction = findTransaction(transactionId);
        if (transaction.getStatus() != TransactionStatus.VOID_REQUESTED) {
            throw new ValidationException("Transaction has no pending void request.");
        }

        // 4. Return the shrimp to inventory when a sale is voided.
        // InventoryService also saves the updated product stock and logs VOID_RETURN.
        if (transaction.getType() == TransactionType.SALE) {
            inventoryService.returnStock(manager, transaction.getProductId(),
                    transaction.getQuantityKg(), "Approved void: " + transaction.getTransactionId());
        }

        // 5-6. Mark the transaction as voided and record the approving manager.
        transaction.setStatus(TransactionStatus.VOIDED);
        transaction.setVoidApprovedBy(manager.getUsername());
    }

    /**
     * TASK 5: Reject Void Request (Manager Action)
     * 1. Verify manager.canApproveVoids()
     * 2. Retrieve transaction by ID.
     * 3. Reset transaction status back to COMPLETED
     * 4. Clear voidRequestedBy and voidReason fields.
     */
    public void rejectVoid(User manager, String transactionId) throws ValidationException {
        // 1. Check if the user is allowed to reject void requests.
        if (manager == null) {
            throw new ValidationException("No user is signed in.");
        }
        if (!manager.canApproveVoids()) {
            throw new ValidationException("Only a manager can reject void requests.");
        }

        // 2. Find the transaction and make sure it has a pending request.
        Transaction transaction = findTransaction(transactionId);
        if (transaction.getStatus() != TransactionStatus.VOID_REQUESTED) {
            throw new ValidationException("Transaction has no pending void request.");
        }

        // 3-4. Reject the request and clear its request details.
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setVoidRequestedBy(null);
        transaction.setVoidReason(null);
    }

    // Find a transaction using its ID.
    private Transaction findTransaction(String transactionId) throws ValidationException {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new ValidationException("Transaction ID is required.");
        }

        for (Transaction transaction : transactions) {
            if (transaction.getTransactionId().equalsIgnoreCase(transactionId.trim())) {
                return transaction;
            }
        }

        throw new ValidationException("Transaction not found: " + transactionId);
    }

    // Return all transactions (Manager view)
    public List<Transaction> getAllTransactions() {
        return new ArrayList<>(transactions);
    }

    // Return transactions executed by a specific user (Clerk view)
    public List<Transaction> getTransactionsByUser(String username) {
        return transactions.stream()
                .filter(t -> t.getPerformedBy().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    // Return all transactions currently waiting for manager approval (Status == VOID_REQUESTED)
    public List<Transaction> getPendingVoidRequests() {
        return transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.VOID_REQUESTED)
                .collect(Collectors.toList());
    }

    // Calculate total net cash balance: Sum(SALE completed) + Sum(CASH_IN completed) - Sum(CASH_OUT completed)
    public double calculateTotalCashBalance() {
        double balance = 0.0;
        for (Transaction t : transactions) {
            if (t.getStatus() == TransactionStatus.COMPLETED) { // Exclude VOIDED and VOID_REQUESTED
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
