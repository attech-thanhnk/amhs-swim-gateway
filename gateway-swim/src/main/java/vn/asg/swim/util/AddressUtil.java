package vn.asg.swim.util;

/**
 * Rút gọn địa chỉ X.400 O/R đầy đủ (vd. "/CN=VVTSAAAA/OU=VVTS/O=VVTS/.../")
 * về mã AFTN ngắn (vd. "VVTSAAAA") bằng cách lấy giá trị của thành phần CN
 * (hoặc OU nếu không có CN).
 */
public class AddressUtil {

    public static String getShort(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        int index = address.indexOf("CN=");
        if (index >= 0) {
            index += 3;
            int end = address.indexOf("/", index);
            if (end < index) {
                return address.substring(index);
            }
            return address.substring(index, end);
        }

        index = address.indexOf("OU=");
        if (index >= 0) {
            index += 3;
            int end = address.indexOf("/", index);
            if (end < index) {
                return address.substring(index);
            }
            return address.substring(index, end);
        }

        return null;
    }
}
