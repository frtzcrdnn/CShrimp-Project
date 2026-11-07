package model;

public abstract class User {
	// attributes
	private String username;
	private String password;
	private String fullName;
	private boolean approved;
	
	// constructor
	public User(String username, String password, String fullName, boolean approved) {
		this.username = username;
		this.password = password;
		this.fullName = fullName;
		this.approved = approved;
	}
	
	// getters & setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    
    public abstract String getRole();
    public abstract boolean canManageProducts();
    public abstract boolean canRestock();
    public abstract boolean canApproveVoids();
    public abstract boolean canViewAllTransactions();
    public abstract boolean canManageAccounts();
    public abstract boolean canRequestVoid();
    
    @Override
    public String toString() {
        return fullName + " (" + getRole() + ")";
    }

	
}
