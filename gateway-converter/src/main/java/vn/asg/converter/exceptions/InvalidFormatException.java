/*
 */
package vn.asg.converter.exceptions;

/**
 *
 * @author ThanhNk
 */
public class InvalidFormatException extends Exception {

    public InvalidFormatException() {
        super();
    }

    public InvalidFormatException(String message) {
        super(message);
    }

    public InvalidFormatException(Throwable ex) {
        super(ex);
    }

    public InvalidFormatException(String message, Throwable ex) {
        super(message, ex);
    }

}

