package vn.asg.cp.exception;

/**
 * Exception ném ra khi resource không tìm thấy.
 * HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object id) {
        super(String.format("%s not found with id: %s", resourceName, id));
    }
}
