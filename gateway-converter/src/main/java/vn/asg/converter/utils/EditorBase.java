/*
 */
package vn.asg.converter.utils;
 
import java.awt.Color;
import javax.swing.JComponent;

/**
 *
 * @author ThanhNk
 */
public abstract class EditorBase extends MessageEditorPanel {

    public static final Color COLOR_TEXT_ERROR = Color.decode("#FFCCCC");
    public static final Color COLOR_TEXT_NORMAL = Color.decode("#FFFFFF");

    private String errMessage;

    public abstract boolean validateForm();

    public abstract void actionCopy();

    public abstract void actionSent();

    /**
     * @return the errMessage
     */
    public String getErrMessage() {
        return errMessage;
    }

    /**
     * @param errMessage the errMessage to set
     */
    protected void setErrMessage(String errMessage) {
        this.errMessage = errMessage;
    }

    public void setError(JComponent component, String error) {
        component.setBackground(COLOR_TEXT_ERROR);
//        component.setToolTipText(error);
        component.requestFocus();
        this.errMessage = error;
    }

    public void setError(String error) {
        this.errMessage = "Please insert the correct value in the: " + error;
    }

    public void clearError(JComponent component) {
        component.setBackground(COLOR_TEXT_NORMAL);
        this.errMessage = null;
//        component.setToolTipText("");
    }

}

