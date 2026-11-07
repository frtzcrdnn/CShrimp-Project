package model;

public enum TransactionStatus {
    COMPLETED("Completed"),
    VOID_REQUESTED("Void Requested"),
    VOIDED("Voided");

    private final String label;

    TransactionStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}