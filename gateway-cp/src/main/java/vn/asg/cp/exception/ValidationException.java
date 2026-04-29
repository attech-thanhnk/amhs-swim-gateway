package vn.asg.cp.exception;

/**
 * Exception ném ra khi validation thất bại.
 * HTTP 400 Bad Request.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
