package service;

import model.Clerk;
import model.Manager;
import model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the list of employee accounts and checks the username and password
 * when someone logs in. Only employees (Manager and Clerk) can use the system.
 *
 * The accounts are created here for now (there is no registration screen yet).
 */
public class AuthenticationService {

    private final List<User> users = new ArrayList<>();

    public AuthenticationService() {
        users.add(new Manager("manager", "manager123", "Maria Santos", true));
        users.add(new Clerk("clerk1", "clerk123", "Juan Dela Cruz", true));
        users.add(new Clerk("clerk2", "clerk123", "Ana Reyes", true));
    }

    /**
     * Checks the username and password and returns the user.
     * @throws ValidationException with a message that is safe to show on the login screen
     */
    public User login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Please enter your username.");
        }
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Please enter your password.");
        }

        User user = findByUsername(username);
        // same message for a wrong username and a wrong password, so it does not reveal which one exists
        if (user == null || !user.getPassword().equals(password)) {
            throw new ValidationException("Incorrect username or password.");
        }
        if (!user.isApproved()) {
            throw new ValidationException("This account is not approved yet. Please ask the manager.");
        }
        return user;
    }

    public User findByUsername(String username) {
        if (username == null) {
            return null;
        }
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username.trim())) {
                return u;
            }
        }
        return null;
    }

    // every approved clerk (used by the Manager Dashboard)
    public List<Clerk> getClerks() {
        List<Clerk> clerks = new ArrayList<>();
        for (User u : users) {
            if (u instanceof Clerk && u.isApproved()) {
                clerks.add((Clerk) u);
            }
        }
        return clerks;
    }
}
