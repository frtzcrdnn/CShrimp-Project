package model;

public class Clerk extends User {
	
	public Clerk(String username, String password) {
		super(username, password);
	}
	
	@Override
	public String getRoleTitle() { return "Clerk"; }
	
}
