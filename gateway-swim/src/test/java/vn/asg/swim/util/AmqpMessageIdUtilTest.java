package vn.asg.swim.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AmqpMessageIdUtilTest {

    @Test
    @DisplayName("Cắt tiền tố ID: của message-id do Qpid JMS sinh ở chiều ra")
    void catTienToIdCuaQpid() {
        assertEquals("b3fe5138-5898-4bbf-9454-993ba058e813:1:44:1-1",
                AmqpMessageIdUtil.clean("ID:b3fe5138-5898-4bbf-9454-993ba058e813:1:44:1-1"));
    }

    @Test
    @DisplayName("Cắt tiền tố kiểu dữ liệu mà Qpid chèn khi message-id không phải chuỗi")
    void catTienToKieuDuLieu() {
        assertEquals("IPM.CTSW110.1787827641684",
                AmqpMessageIdUtil.clean("ID:AMQP_STRING:IPM.CTSW110.1787827641684"));
        assertEquals("7a9c0e11-2f3d-4b56-8c90-1de2f3a4b5c6",
                AmqpMessageIdUtil.clean("ID:AMQP_UUID:7a9c0e11-2f3d-4b56-8c90-1de2f3a4b5c6"));
        assertEquals("12345", AmqpMessageIdUtil.clean("ID:AMQP_ULONG:12345"));
    }

    @Test
    @DisplayName("Chuỗi không có tiền tố thì giữ nguyên")
    void giuNguyenKhiKhongCoTienTo() {
        assertEquals("IPM.GW.1787825384462", AmqpMessageIdUtil.clean("IPM.GW.1787825384462"));
    }

    @Test
    @DisplayName("Chỉ cắt tiền tố ở đầu chuỗi, không đụng vào phần thân")
    void chiCatODau() {
        // "ID:" xuất hiện giữa chuỗi phải được giữ lại
        assertEquals("abc:ID:def", AmqpMessageIdUtil.clean("ID:abc:ID:def"));
    }

    @Test
    @DisplayName("Cắt xong mà rỗng, hoặc đầu vào rỗng/null, thì trả về null")
    void traVeNullKhiKhongConGiDinhDanh() {
        assertNull(AmqpMessageIdUtil.clean(null));
        assertNull(AmqpMessageIdUtil.clean(""));
        assertNull(AmqpMessageIdUtil.clean("   "));
        assertNull(AmqpMessageIdUtil.clean("ID:"));
        assertNull(AmqpMessageIdUtil.clean("ID:   "));
        assertNull(AmqpMessageIdUtil.clean("null"));
        assertNull(AmqpMessageIdUtil.clean("ID:null"));
    }

    @Test
    @DisplayName("Cắt xong hai chiều cho ra cùng một dạng — điều kiện để CP tìm kiếm nhất quán")
    void haiChieuCungDang() {
        String chieuRa = AmqpMessageIdUtil.clean("ID:AMQP_STRING:IPM.CTSW110.999");
        String chieuVao = AmqpMessageIdUtil.clean("ID:IPM.CTSW110.999");
        assertEquals(chieuRa, chieuVao);
    }
}
