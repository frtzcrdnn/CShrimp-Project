package service;

import model.Clerk;
import model.Manager;
import model.User;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages employee accounts, credential verification, and persistent storage
 * in {@code data/users.txt}.
 */
public class AuthenticationService {

    private static final String FILE_NAME = "data/users.txt";
    private static final String SEPARATOR = "|";
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final List<User> users = new ArrayList<>();
    private final String filePath;

    public AuthenticationService() {
        this(FILE_NAME);
    }

    public AuthenticationService(String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        if (!file.exists()) {
            seedDefaultAccounts();
            saveUsers();
        } else {
            loadUsers();
        }
    }

    /**
     * Authenticates an employee by username and password.
     *
     * @param username login username
     * @param password account password
     * @return authenticated {@link User}
     * @throws ValidationException if credentials are invalid or the account is unapproved
     */
    public User login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Please enter your username.");
        }
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Please enter your password.");
        }

        User user = findByUsername(username);
        if (user == null || !user.getPassword().equals(password)) {
            throw new ValidationException("Incorrect username or password.");
        }
        if (!user.isApproved()) {
            throw new ValidationException("This account is not approved or is inactive. Please ask the manager.");
        }
        return user;
    }

    public User findByUsername(String username) {
        if (username == null) {
            return null;
        }
        String target = username.trim();
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(target)) {
                return u;
            }
        }
        return null;
    }

    /** @return all approved clerks (used for sales reporting) */
    public List<Clerk> getClerks() {
        List<Clerk> clerks = new ArrayList<>();
        for (User u : users) {
            if (u instanceof Clerk && u.isApproved()) {
                clerks.add((Clerk) u);
            }
        }
        return clerks;
    }

    /** @return all clerks, including pending/deactivated accounts (for Manager account management) */
    public List<Clerk> getAllClerks() {
        List<Clerk> clerks = new ArrayList<>();
        for (User u : users) {
            if (u instanceof Clerk) {
                clerks.add((Clerk) u);
            }
        }
        return clerks;
    }

    /**
     * Registers a new clerk account in a pending (unapproved) state for manager review.
     */
    public Clerk registerClerk(String username, String password, String fullName) {
        return createClerkInternal(username, password, fullName, false);
    }

    /**
     * Allows a manager to create a new clerk account directly.
     */
    public Clerk createClerk(User manager, String username, String password,
                             String fullName, boolean approved) {
        requireAccountManager(manager);
        return createClerkInternal(username, password, fullName, approved);
    }

    /**
     * Approves or deactivates a clerk account.
     */
    public void setClerkApproved(User manager, String username, boolean approved) {
        requireAccountManager(manager);
        User target = findByUsername(username);
        if (!(target instanceof Clerk)) {
            throw new ValidationException("Clerk account not found: " + username);
        }
        boolean previous = target.isApproved();
        target.setApproved(approved);
        try {
            saveUsers();
        } catch (RuntimeException e) {
            target.setApproved(previous);
            throw e;
        }
    }

    /**
     * Resets a clerk's password (Manager action).
     */
    public void resetClerkPassword(User manager, String username, String newPassword) {
        requireAccountManager(manager);
        User target = findByUsername(username);
        if (!(target instanceof Clerk)) {
            throw new ValidationException("Clerk account not found: " + username);
        }
        validatePassword(newPassword);
        String oldPassword = target.getPassword();
        target.setPassword(newPassword);
        try {
            saveUsers();
        } catch (RuntimeException e) {
            target.setPassword(oldPassword);
            throw e;
        }
    }

    /**
     * Allows a signed-in user to change their own password.
     */
    public void changeOwnPassword(User user, String currentPassword, String newPassword) {
        if (user == null) {
            throw new ValidationException("No user is signed in.");
        }
        if (!user.getPassword().equals(currentPassword)) {
            throw new ValidationException("Current password is incorrect.");
        }
        validatePassword(newPassword);
        String oldPassword = user.getPassword();
        user.setPassword(newPassword);
        try {
            saveUsers();
        } catch (RuntimeException e) {
            user.setPassword(oldPassword);
            throw e;
        }
    }

    private Clerk createClerkInternal(String username, String password,
                                      String fullName, boolean approved) {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Username is required.");
        }
        String cleanUser = clean(username.trim());
        if (!cleanUser.matches("[a-zA-Z0-9._-]{3,30}")) {
            throw new ValidationException("Username must be 3-30 characters (letters, numbers, ., _, -).");
        }
        if (findByUsername(cleanUser) != null) {
            throw new ValidationException("Username \"" + cleanUser + "\" is already taken.");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new ValidationException("Full name is required.");
        }
        validatePassword(password);

        Clerk clerk = new Clerk(cleanUser, password, clean(fullName.trim()), approved);
        users.add(clerk);
        try {
            saveUsers();
        } catch (RuntimeException e) {
            users.remove(clerk);
            throw e;
        }
        return clerk;
    }

    private void requireAccountManager(User manager) {
        if (manager == null || !manager.canManageAccounts()) {
            throw new ValidationException("Only a manager can manage employee accounts.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.contains("|") || password.contains("\n") || password.contains("\r")) {
            throw new ValidationException("Password cannot contain '|' or line breaks.");
        }
    }

    private void seedDefaultAccounts() {
        users.clear();
        users.add(new Manager("manager", "manager123", "Maria Santos", true));
        users.add(new Clerk("clerk1", "clerk123", "Juan Dela Cruz", true));
        users.add(new Clerk("clerk2", "clerk123", "Ana Reyes", true));
    }

    private synchronized void saveUsers() {
        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        File temp = new File(filePath + ".tmp");
        try {
            try (PrintWriter writer = new PrintWriter(new FileWriter(temp))) {
                for (User u : users) {
                    String role = (u instanceof Manager) ? "MANAGER" : "CLERK";
                    writer.println(role + SEPARATOR
                            + clean(u.getUsername()) + SEPARATOR
                            + u.getPassword() + SEPARATOR
                            + clean(u.getFullName()) + SEPARATOR
                            + u.isApproved());
                }
                if (writer.checkError()) {
                    throw new IOException("Write failed while saving users.");
                }
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            temp.delete();
            throw new RuntimeException("Could not save employee accounts: " + e.getMessage());
        }
    }

    private void loadUsers() {
        users.clear();
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                if (parts.length < 5) {
                    continue;
                }
                String role = parts[0].trim();
                String username = parts[1].trim();
                String password = parts[2];
                String fullName = parts[3].trim();
                boolean approved = Boolean.parseBoolean(parts[4].trim());

                if ("MANAGER".equalsIgnoreCase(role)) {
                    users.add(new Manager(username, password, fullName, approved));
                } else if ("CLERK".equalsIgnoreCase(role)) {
                    users.add(new Clerk(username, password, fullName, approved));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load employee accounts: " + e.getMessage());
        }
    }

    private static String clean(String text) {
        return text.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }
}