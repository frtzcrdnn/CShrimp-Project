package model;

public class Manager extends User {

    public Manager(String username, String fullName, String passwordHash, boolean approved) {
        super(username, fullName, passwordHash, approved);
	}
	
    @Override
	public String getRole() { return "Manager"; }
    @Override 
    public boolean canManageProducts() { return true; }
    @Override 
    public boolean canRestock() { return true; }
    @Override 
    public boolean canApproveVoids() { return true; }
    @Override 
    public boolean canViewAllTransactions() { return true; }
    @Override 
    public boolean canManageAccounts() { return true; }
    @Override 
    public boolean canRequestVoid() { return false; }
}
