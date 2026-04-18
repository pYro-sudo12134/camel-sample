package by.losik.exception;

public class NullEntityException extends IllegalStateException {
    public NullEntityException () {
        super("Entity cannot be null");
    }
}
