package model;

public class Clerk extends User {
    public Clerk(String username, String fullName, String passwordHash, boolean approved) {
        super(username, fullName, passwordHash, approved);
    }

    @Override public String getRole() { return "Clerk"; }
    @Override public boolean canManageProducts() { return false; }
    @Override public boolean canRestock() { return false; }
    @Override public boolean canApproveVoids() { return false; }
    @Override public boolean canViewAllTransactions() { return false; }
    @Override public boolean canManageAccounts() { return false; }
    @Override public boolean canRequestVoid() { return true; }	
}
