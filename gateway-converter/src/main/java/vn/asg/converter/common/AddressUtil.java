/*
 */
package vn.asg.converter.common;

import java.io.IOException;

/**
 *
 * @author ThanhNk
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

    public static String getPriority(int priority) {

        switch (priority) {
            case 2:
                return "Urgent";
            case 1:
                return "Non Urgent";
            case 0:
                return "Normal";
            default:
                return "Unknown";
        }

    }

//    /**
//     * Get address scheme
//     * @param address
//     * @return address type
//     */
//    public static String getAddressScheme(String address) {
//        return Address.parse(address).getScheme();
//    }
    public static String initDatabasePathFromAddress(String account) throws IOException {
        String shortAddress = AddressUtil.getShort(account);
        return String.format("./databases/%s", shortAddress);
    }

    public static String initDatabasePathFromAddressBackup(String account) throws IOException {
        String shortAddress = AddressUtil.getShort(account);
        return String.format("./Bak/%s", shortAddress);
    }

    public static void main(String[] args) {
        test("/CN=VVTSAAAA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        test("/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        test("/OU=VVTS");
        test("CN=VVTS");
        test("CN=");
    }

    private static void test(String address) {
        String shrt = AddressUtil.getShort(address);
    }
}

