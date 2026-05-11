package vn.asg.converter.core;

/**
 * Kết quả trả về của quá trình chuyển đổi TAC → XML.
 */
public class ConversionResult {

    public enum Status { SUCCESS, PARSE_ERROR, UNSUPPORTED, SYSTEM_ERROR }

    private final Status status;
    private final String xml;
    private final String outputType;  // "IWXXM", "AIXM", "FIXM"
    private final String messageId;   // Tên file output, ví dụ "METAR_VVNB_221000Z.xml"
    private final String errorMessage;
    private final String originalTac;

    private ConversionResult(Status status, String xml, String outputType,
                             String messageId, String errorMessage, String originalTac) {
        this.status = status;
        this.xml = xml;
        this.outputType = outputType;
        this.messageId = messageId;
        this.errorMessage = errorMessage;
        this.originalTac = originalTac;
    }

    public static ConversionResult success(String xml, String outputType, String messageId) {
        return new ConversionResult(Status.SUCCESS, xml, outputType, messageId, null, null);
    }

    public static ConversionResult parseError(String error, String originalTac) {
        return new ConversionResult(Status.PARSE_ERROR, null, null, null, error, originalTac);
    }

    public static ConversionResult unsupported(String originalTac) {
        return new ConversionResult(Status.UNSUPPORTED, null, null, null,
            "Message type not supported", originalTac);
    }

    public static ConversionResult systemError(String error, String originalTac) {
        return new ConversionResult(Status.SYSTEM_ERROR, null, null, null, error, originalTac);
    }

    public boolean isSuccess()     { return status == Status.SUCCESS; }
    public Status getStatus()      { return status; }
    public String getXml()         { return xml; }
    public String getOutputType()  { return outputType; }
    public String getMessageId()   { return messageId; }
    public String getErrorMessage(){ return errorMessage; }
    public String getOriginalTac() { return originalTac; }
}
