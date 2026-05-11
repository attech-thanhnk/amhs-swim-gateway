/*
 */
package vn.asg.converter.utils;

import javax.swing.JPanel;

/**
 *
 * @author ThanhNk
 */
public abstract class MessageEditorPanel extends JPanel {
    
    public abstract String getText();
    
    public abstract void setText(String text);
    
}

