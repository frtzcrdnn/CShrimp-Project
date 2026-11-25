package model;

import java.time.LocalDateTime;

/**
 * Represents a financial transaction (shrimp sale, cash-in, or cash-out),
 * including customer, payment details, and void-approval audit metadata.
 */
public class Transaction {

    private final String transactionId;
    private final LocalDateTime dateTime;
    private final TransactionType type;
    private TransactionStatus status;

    private final String productId;
    private final String productName;
    private final double quantityKg;
    private final double pricePerKg;
    private final double totalAmount;

    private final String customerName;
    private final String paymentMethod;
    private final double amountTendered;
    private final double changeAmount;

    private final String performedBy;
    private String voidRequestedBy;
    private String voidReason;
    private String voidApprovedBy;

    /** Creates a completed transaction with default walk-in cash payment. */
    public Transaction(String transactionId, LocalDateTime dateTime, TransactionType type,
                       String productId, String productName, double quantityKg,
                       double pricePerKg, double totalAmount, String performedBy) {
        this(transactionId, dateTime, type, productId, productName, quantityKg,
                pricePerKg, totalAmount, "Walk-in", "Cash", totalAmount, 0.0, performedBy);
    }

    /** Creates a completed transaction with explicit customer and payment details. */
    public Transaction(String transactionId, LocalDateTime dateTime, TransactionType type,
                       String productId, String productName, double quantityKg,
                       double pricePerKg, double totalAmount, String customerName,
                       String paymentMethod, double amountTendered, double changeAmount,
                       String performedBy) {
        this.transactionId = transactionId;
        this.dateTime = dateTime;
        this.type = type;
        this.status = TransactionStatus.COMPLETED;
        this.productId = productId;
        this.productName = productName;
        this.quantityKg = quantityKg;
        this.pricePerKg = pricePerKg;
        this.totalAmount = totalAmount;
        this.customerName = (customerName == null || customerName.trim().isEmpty())
                ? "Walk-in" : customerName.trim();
        this.paymentMethod = (paymentMethod == null || paymentMethod.trim().isEmpty())
                ? "Cash" : paymentMethod.trim();
        this.amountTendered = amountTendered;
        this.changeAmount = changeAmount;
        this.performedBy = performedBy;
    }

    /** Marks this transaction as awaiting manager void approval. */
    public void requestVoid(String requestedBy, String reason) {
        this.status = TransactionStatus.VOID_REQUESTED;
        this.voidRequestedBy = requestedBy;
        this.voidReason = reason;
    }

    /** Marks a pending void request as approved by a manager. */
    public void approveVoid(String approvedBy) {
        this.status = TransactionStatus.VOIDED;
        this.voidApprovedBy = approvedBy;
    }

    /** Directly voids a completed transaction (Manager action). */
    public void directVoid(String approvedBy, String reason) {
        this.status = TransactionStatus.VOIDED;
        this.voidRequestedBy = approvedBy;
        this.voidReason = reason;
        this.voidApprovedBy = approvedBy;
    }

    /** Rejects a pending void request and returns status to COMPLETED. */
    public void rejectVoid() {
        this.status = TransactionStatus.COMPLETED;
        this.voidRequestedBy = null;
        this.voidReason = null;
        this.voidApprovedBy = null;
    }

    /** Restores persisted void metadata when loading from file. */
    public void restoreVoidState(TransactionStatus status, String voidRequestedBy,
                                 String voidReason, String voidApprovedBy) {
        this.status = status;
        this.voidRequestedBy = voidRequestedBy;
        this.voidReason = voidReason;
        this.voidApprovedBy = voidApprovedBy;
    }

    public String getTransactionId() { return transactionId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public double getQuantityKg() { return quantityKg; }
    public double getPricePerKg() { return pricePerKg; }
    public double getTotalAmount() { return totalAmount; }
    public String getCustomerName() { return customerName; }
    public String getPaymentMethod() { return paymentMethod; }
    public double getAmountTendered() { return amountTendered; }
    public double getChangeAmount() { return changeAmount; }
    public String getPerformedBy() { return performedBy; }
    public String getVoidRequestedBy() { return voidRequestedBy; }
    public String getVoidReason() { return voidReason; }
    public String getVoidApprovedBy() { return voidApprovedBy; }
}