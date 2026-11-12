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
  
}
