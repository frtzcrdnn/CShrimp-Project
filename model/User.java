package model;

public abstract class User {
	// attributes
	private String username;
	private String password;
	
	// constructor
	public User(String username, String password) {
		this.username = username;
		this.password = password;
	}
	
	// getters & setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public abstract String getRoleTitle();

	
}
