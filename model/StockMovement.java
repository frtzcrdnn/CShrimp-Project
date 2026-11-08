package model;

import java.time.LocalDateTime;

// One line of the inventory audit trail: every stock increase or decrease is logged here.
public class StockMovement {
    public enum Reason {
        INITIAL("Opening Stock"),
        RESTOCK("Restock"),
        SALE("Sale"),
        VOID_RETURN("Void Return");

        private final String label;
        Reason(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final LocalDateTime dateTime;
    private final String productCode;
    private final String productName;
    private final double changeKg;
    private final Reason reason;
    private final String performedBy;
    private final String note;
    private final double stockAfter;

    public StockMovement(LocalDateTime dateTime, String productCode, String productName, double changeKg,
                         Reason reason, String performedBy, String note, double stockAfter) {
        this.dateTime = dateTime;
        this.productCode = productCode;
        this.productName = productName;
        this.changeKg = changeKg;
        this.reason = reason;
        this.performedBy = performedBy == null ? "" : performedBy;
        this.note = note == null ? "" : note;
        this.stockAfter = stockAfter;
    }

    public LocalDateTime getDateTime() { return dateTime; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public double getChangeKg() { return changeKg; }
    public Reason getReason() { return reason; }
    public String getPerformedBy() { return performedBy; }
    public String getNote() { return note; }
    public double getStockAfter() { return stockAfter; }
}
