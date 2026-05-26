package vn.asg.swim.model;

/**
 * Result of the AMHS originator + recipients resolution process.
 *
 * @param originator AFTN originator address (8 characters). Example:
 *                   "VVHHZPZX". Can be null.
 * @param recipients Space-separated AFTN addresses. Example: "VVHHZTZX
 *                   VVTSZDYX". Null if UNRESOLVED.
 * @param source     Resolution source:
 *                   - "AMQP_PROPERTY" : Extracted from AMQP application
 *                   properties
 *                   - "ROUTING_RULE" : Matched against the routing table (queue
 *                   + subject)
 *                   - "DEFAULT" : Fallback to gateway_config
 *                   DEFAULT_RECIPIENTS_INBOUND
 *                   - "UNRESOLVED" : Failed to resolve; message will transition
 *                   to STATUS_UNROUTED
 */
public record ResolvedAddressing(
        String originator,
        String recipients,
        String source) {
    public static final String SOURCE_AMQP_PROPERTY = "AMQP_PROPERTY";
    public static final String SOURCE_ROUTING_RULE = "ROUTING_RULE";
    public static final String SOURCE_UNRESOLVED = "UNRESOLVED";

    /**
     * Returns true if there are at least some recipients (enough to proceed with
     * dispatch).
     */
    public boolean isResolved() {
        return !SOURCE_UNRESOLVED.equals(source)
                && recipients != null
                && !recipients.isBlank();
    }
}
