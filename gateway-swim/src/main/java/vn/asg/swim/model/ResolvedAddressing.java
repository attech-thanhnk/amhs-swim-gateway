package vn.asg.swim.model;

/**
 * Kết quả phân giải địa chỉ originator và recipients cho bản tin SWIM → AMHS.
 */
public record ResolvedAddressing(
        String originator,
        String recipients,
        String source) {
    public static final String SOURCE_AMQP_PROPERTY = "AMQP_PROPERTY";
    public static final String SOURCE_UNRESOLVED = "UNRESOLVED";

    public boolean isResolved() {
        return !SOURCE_UNRESOLVED.equals(source)
                && recipients != null
                && !recipients.isBlank();
    }
}
