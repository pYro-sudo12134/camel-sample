package by.losik.exception;

public class SaveFailedException extends RuntimeException {
    public SaveFailedException(Throwable throwable) {
        super("Save failed", throwable);
    }
}
