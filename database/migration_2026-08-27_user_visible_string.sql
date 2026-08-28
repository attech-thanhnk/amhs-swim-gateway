-- ============================================================
-- Migration: bổ sung amhs_user_visible_string (FTBP)
-- Ngày: 2026-08-27
-- Phạm vi: EUR Doc 047 v3.0 Table 2 - §4.4.3.4.11 và §4.4.4.6
-- ============================================================
--
-- Đối chiếu Table 2 "AMQP message generation" của EUR Doc 047 với code cho thấy thiếu một
-- application property bắt buộc có điều kiện:
--
--   application properties  amhs_user_visible_string  T1  FTBP data  See 4.4.3.4.11
--
-- §4.4.3.4.11: "The value of the element of the application property amhs_user_visible_string
-- shall be mapped from the element user-visible-string if present; otherwise the element is
-- absent."
--
-- §4.4.4.6: khi registered-identifier mang giá trị khác OID mặc định thì user-visible-string
-- BẮT BUỘC phải có kèm. ITCU vẫn chuyển bản tin đi nhưng ghi cảnh báo lên Control Position
-- nếu thiếu.
--
-- NGUỒN DỮ LIỆU: do AMHS Component (amss) cung cấp cùng với các tham số FTBP khác.
-- Bảng mtcu_tmp hiện chưa có cột tương ứng - xem tài liệu yêu cầu gửi amss.
-- NULL = không có dữ liệu, ITCU không gán property (đúng theo "otherwise the element is absent").

ALTER TABLE `gwout`
    ADD COLUMN `amhs_user_visible_string` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'FTBP user-visible-string (Table 2, §4.4.3.4.11) - bắt buộc khi registered-identifier khác OID mặc định'
    AFTER `amhs_registered_id`;
