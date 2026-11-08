package model;

import java.time.LocalDateTime;

/**
 * One line of the inventory history. Every time stock goes up or down
 * (restock, sale, void) InventoryService records one of these.
 */

public class StockMovement {

    public enum Reason {
        OPENING("Opening Stock"),
        RESTOCK("Restock"),
        SALE("Sale"),
        VOID_RETURN("Void Return"),
        LOW_STOCK_REPORT("Low-Stock Report");

        private final String label;

        Reason(String label) { this.label = label; }

        public String getLabel() { return label; }

        @Override
        public String toString() { return label; }
    }

    private final LocalDateTime dateTime;
    private final String productId;
    private final String productName;
    private final double change;       // +kg added, -kg removed, 0 for a low-stock report
    private final Reason reason;
    private final String performedBy;  // full name of the user
    private final String note;
    private final double stockAfter;

    public StockMovement(LocalDateTime dateTime, String productId, String productName,
                         double change, Reason reason, String performedBy,
                         String note, double stockAfter) {
        this.dateTime = dateTime;
        this.productId = productId;
        this.productName = productName;
        this.change = change;
        this.reason = reason;
        this.performedBy = performedBy;
        this.note = note;
        this.stockAfter = stockAfter;
    }

    public LocalDateTime getDateTime() { return dateTime; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public double getChange() { return change; }
    public Reason getReason() { return reason; }
    public String getPerformedBy() { return performedBy; }
    public String getNote() { return note; }
    public double getStockAfter() { return stockAfter; }
}
