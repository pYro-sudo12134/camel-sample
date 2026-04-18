package by.losik.exception;

public class UnknownStatusException extends RuntimeException {
    public UnknownStatusException() {
        super();
    }

    public UnknownStatusException(String message) {
        super(String.format("Unknown status: %s", message));
    }
}
