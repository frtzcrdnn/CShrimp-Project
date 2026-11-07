package model;

public enum TransactionType {
    SALE("Sale", 1),
    CASH_IN("Cash In", 1),
    CASH_OUT("Cash Out", -1);

    private final String label;
    private final int sign;

    TransactionType(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String getLabel() { return label; }
    public int getSign() { return sign; }

    @Override
    public String toString() { return label; }
}