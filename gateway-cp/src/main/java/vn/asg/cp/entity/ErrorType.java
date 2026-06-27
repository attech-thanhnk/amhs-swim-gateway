package vn.asg.cp.entity;

/**
 * Enum đại diện cho các kiểu lỗi của Gwin và Gwout.
 * Phục vụ cho thống kê trên Dashboard Control Panel.
 */
public enum ErrorType {
    UNDEFINED(0),
    CONVERT_FAILED(1),
    SEND_FAILED(2);

    private final int value;

    ErrorType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ErrorType fromValue(int val) {
        for (ErrorType type : values()) {
            if (type.value == val) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ErrorType value: " + val);
    }
}
