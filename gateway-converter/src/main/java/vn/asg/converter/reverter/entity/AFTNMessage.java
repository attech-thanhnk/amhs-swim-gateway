/*
 */
package vn.asg.converter.reverter.entity;

/**
 *
 * @author ThanhNk
 */
public class AFTNMessage {

    private final StringBuilder builder = new StringBuilder();
    private final int LINE_LIMIT = 69;
    private String currentLine = "";
    private String endingSign = "";

    public AFTNMessage() {
    }

    public AFTNMessage(String endingChar) {
        this.endingSign = endingChar;
    }

    public AFTNMessage append(String content) {
        String normalizedString = normalize(content);
        String[] spitedContent = normalizedString.split(" ");

        for (String item : spitedContent) {
//            if (item.isEmpty())  {
//                continue;
//            }
//            
//            if (currentLine.length() + item.length() + 1 > LINE_LIMIT) {
//                if  (!builder.toString().isEmpty()) {
//                    builder.append("\n");
//                }
//                
//                builder.append(currentLine);
//                currentLine = item;
//                continue;
//            }
//            
//            if (!currentLine.isEmpty()) {
//                currentLine += " ";
//            }
//            currentLine += item;
            appendElement(item);
        }

        // builder.append(content);
        return this;
    }

    public AFTNMessage append(String content, boolean check) {
        String normalizedString = normalize(content, check);
        String[] spitedContent = normalizedString.split(" ");
        for (String item : spitedContent) {
            appendElement(item, check);
        }
        return this;
    }

    public AFTNMessage appendElement(String item) {
        if (item == null || item.isEmpty()) {
            return this;
        }

        if (currentLine.length() + item.length() + 1 > LINE_LIMIT) {
            if (!builder.toString().isEmpty()) {
                builder.append("\n");
            }

            builder.append(currentLine);
            currentLine = item;
            return this;
        }

        if (!currentLine.isEmpty()) {
            currentLine += " ";
        }
        currentLine += item;
        return this;
    }

    public AFTNMessage appendElement(String item, boolean check) {
        if (item == null || item.isEmpty()) {
            return this;
        }

        if (currentLine.length() + item.length() + 1 > LINE_LIMIT) {
            if (!builder.toString().isEmpty()) {
                builder.append("\n");
            }

            builder.append(currentLine);
            currentLine = item;
            return this;
        }
        if (!currentLine.isEmpty()) {
            if (!check) {
                currentLine += " ";
            }
        }
        currentLine += item;
        if (check) {
            if (!builder.toString().isEmpty()) {
                builder.append("\n");
            }
            builder.append(currentLine);
            currentLine = "";
        }
        return this;
    }

    public String flush() {
        if (currentLine.isEmpty()) {
            return this.builder.toString();
        }
        if (!builder.toString().isEmpty()) {
            builder.append("\n");
        }

        builder.append(currentLine);
        if (!builder.toString().endsWith(this.endingSign)) {
            builder.append(this.endingSign);
        }
        currentLine = "";

        return this.builder.toString();
    }

    private String normalize(String content, boolean check) {
        if (content == null) {
            return "";
        }
        String normalizedString = "";
        if (check) {
            normalizedString = content;
        } else {
            normalizedString = content.trim();
        }

        while (normalizedString.contains("  ")) {
            normalizedString = normalizedString.replace("  ", " ");
        }

        return normalizedString;
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        String normalizedString = content.trim();
        while (normalizedString.contains("  ")) {
            normalizedString = normalizedString.replace("  ", " ");
        }
        return normalizedString.replace("\n\r", " ").replace("\n", " ");
    }

    @Override
    public String toString() {
        // this.flush();
        return this.builder.toString();
    }
}

