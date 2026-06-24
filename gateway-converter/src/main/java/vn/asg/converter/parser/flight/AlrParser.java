package vn.asg.converter.parser.flight;

import vn.asg.converter.model.flight.FplMessage;

/**
 * Parser chuyên biệt cho bản tin Khẩn nguy (ALR).
 * Kế thừa FplParser để sử dụng lại logic bóc tách các trường ICAO chuẩn.
 */
public class AlrParser extends FplParser {

    @Override
    public FplMessage parse(String body) throws Exception {
        // Gọi logic parse của lớp cha nhưng với loại tin ALR
        // Logic trong FplParser sẽ tự động xử lý offset cho ALR
        FplMessage msg = super.parse(body);
        msg.setMessageType("ALR");
        return msg;
    }
}
