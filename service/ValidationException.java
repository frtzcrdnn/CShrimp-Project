package service;

/** Just a class that throws msg when user input or a business rule is violated; the message is safe to show to the user. */
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
