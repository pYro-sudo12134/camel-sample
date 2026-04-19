package by.losik.exception;

public class QueueMessageReceiveException extends RuntimeException {
    public QueueMessageReceiveException(Throwable throwable) {
        super("Failed to receive messages from queue", throwable);
    }
}
