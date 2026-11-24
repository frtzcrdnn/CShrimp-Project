package model;

/**
 * Abstract base class representing a system employee (Manager or Clerk).
 * Encapsulates account credentials and role-based permissions.
 */
public abstract class User {

    private String username;
    private String password;
    private String fullName;
    private boolean approved;

    /**
     * Creates a new employee account.
     *
     * @param username unique login username
     * @param password account password
     * @param fullName employee's full display name
     * @param approved whether the account is approved to log in
     */
    public User(String username, String password, String fullName, boolean approved) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.approved = approved;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    /** @return human-readable role title (e.g., "Manager" or "Clerk") */
    public abstract String getRole();

    /** @return {@code true} if the user can add, edit, or archive products */
    public abstract boolean canManageProducts();

    /** @return {@code true} if the user can add restock quantities to inventory */
    public abstract boolean canRestock();

    /** @return {@code true} if the user can approve, reject, or directly execute voids */
    public abstract boolean canApproveVoids();

    /** @return {@code true} if the user can view all employees' transactions */
    public abstract boolean canViewAllTransactions();

    /** @return {@code true} if the user can approve or manage employee accounts */
    public abstract boolean canManageAccounts();

    /** @return {@code true} if the user can submit void requests for manager approval */
    public abstract boolean canRequestVoid();

    @Override
    public String toString() {
        return fullName + " (" + getRole() + ")";
    }
}