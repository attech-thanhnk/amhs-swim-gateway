/*
 */
package vn.asg.converter.reverter.iwxxm;

/**
 *
 * @author ThanhNk
 */
public class IWXXMParsingException extends Exception {

    private static final long serialVersionUID = 6194555760488113942L;

    public IWXXMParsingException(String string) {
        super(string);
    }

    public IWXXMParsingException(Exception ex) {
        super(ex);
    }

    public IWXXMParsingException(String message, Exception ex) {
        super(message, ex);
    }

}

