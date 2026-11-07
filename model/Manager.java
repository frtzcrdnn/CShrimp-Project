package model;

public class Manager extends User {

	public Manager(String username, String password) {
		super(username, password);
	}
	
	public String getRoleTitle() { return "Manager"; }
}
