package vn.asg.swim.exception;

/**
 * Base exception cho gateway, các exception khác extend từ đây.
 */
public class GatewayException extends Exception {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
