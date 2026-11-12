package model;

import java.time.LocalDateTime;

public class Transaction {
  // attributes
  
  // transaction attributes
  private final String transactionId;
  private final LocalDateTime dateTime;
  private final TransactionType type;
  private TransactionStatus status;

  // product attributes
  private final String productId;
  private final String productName;
  private final double quantityKg;
  private final double pricePerKg;
  private final double totalAmount;

  private final String performedBy;
  private String voidRequestedBy;
  private String voidReason;
  private String voidApprovedBy;

  // constructor
  public Transaction(String transactionId, LocalDateTime dateTime, TransactionType type,
                       String productId, String productName, double quantityKg, 
                       double pricePerKg, double totalAmount, String performedBy) {
  this.transactionId = transactionId;
  this.dateTime = dateTime;
  this.type = type;
  this.status = TransactionStatus.COMPLETED; // default = completed
  this.productId = productId;
  this.productName = productName;
  this.quantityKg = quantityKg;
  this.pricePerKg = pricePerKg;
  this.totalAmount = totalAmount;
  this.performedBy = performedBy;
  }

  // getters & setters
  public String getTransactionId() { return transactionId; }
  public LocalDateTime getDateTime() { return dateTime; }
  public TransactionType getType() { return type; }
  public TransactionStatus getStatus() { return status; }
  public void setStatus(TransactionStatus status) { this.status = status; }
  public String getProductId() { return productId; }
  public String getProductName() { return productName; }
  public double getQuantityKg() { return quantityKg; }
  public double getPricePerKg() { return pricePerKg; }
  public double getTotalAmount() { return totalAmount; }
  public String getPerformedBy() { return performedBy; }
  public String getVoidRequestedBy() { return voidRequestedBy; }
  public void setVoidRequestedBy(String voidRequestedBy) { this.voidRequestedBy = voidRequestedBy; }
  public String getVoidReason() { return voidReason; }
  public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
  public String getVoidApprovedBy() { return voidApprovedBy; }
  public void setVoidApprovedBy(String voidApprovedBy) { this.voidApprovedBy = voidApprovedBy; }
  
}