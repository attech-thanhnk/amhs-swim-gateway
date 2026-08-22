package vn.asg.swim.model;

/**
 * Result of the AMHS originator + recipients extraction for a SWIM→AMHS message.
 *
 * @param originator AFTN originator address (8 characters). Example:
 *                   "VVHHZPZX". Never null — falls back to the configured
 *                   default originator per EUR Doc 047 §4.5.2.12(b).
 * @param recipients Space-separated AFTN addresses. Example: "VVHHZTZX
 *                   VVTSZDYX". Blank if UNRESOLVED.
 * @param source     Resolution source:
 *                   - "AMQP_PROPERTY" : Extracted from AMQP application
 *                   properties (amhs_recipients)
 *                   - "UNRESOLVED" : amhs_recipients missing/invalid — message
 *                   is rejected per §4.5.1.5(b)
 */
public record ResolvedAddressing(
        String originator,
        String recipients,
        String source) {
    public static final String SOURCE_AMQP_PROPERTY = "AMQP_PROPERTY";
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
