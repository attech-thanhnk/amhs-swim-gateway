-- =============================================================================
-- Migration: thêm cột gwin.atsmhs_service_level
-- Ngày: 2026-08-22
-- Lý do: EUR Doc 047 §3.3.3 + §4.5.2.10.1 / §4.5.3.7-9 (test case CTSW103)
--
-- Mức dịch vụ ATSMHS (BASIC / EXTENDED) được gateway phân giải cho TỪNG bản tin
-- (theo cấu hình ATSMHS_SERVICE_LEVEL: EXTENDED / BASIC / CONTENT_BASED /
-- RECIPIENTS_BASED). Trước đây giá trị này được tính rồi chỉ ghi log và bỏ đi,
-- nên thành phần dựng IPM phía sau không biết phải map theo kiểu nào:
--
--   BASIC    : amhs_ats_ft  -> ATS-message-Filing-Time
--              amhs_ats_ohi -> ATS-message-Optional-Heading-Info
--   EXTENDED : amhs_ats_ft  -> authorization-time
--              amhs_ats_ohi -> originators-reference
--              (kèm precedence-policy-identifier)
--
-- Cột này chỉ LƯU quyết định của gateway; việc dựng IPM vẫn do thành phần AMHS
-- (amss) đảm nhiệm.
-- =============================================================================

ALTER TABLE `gwin`
  ADD COLUMN `atsmhs_service_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL
  COMMENT 'Muc dich vu ATSMHS da phan giai: BASIC / EXTENDED (EUR Doc 047 §3.3.3)'
  AFTER `addressing_source`;

-- -----------------------------------------------------------------------------
-- LƯU Ý cấu hình (không phải thay đổi schema, nhưng cần cho CTSW103):
-- Chế độ RECIPIENTS_BASED đối chiếu từng người nhận với danh sách
-- gateway_config.ATSMHS_EXTENDED_CAPABLE_ADDRESSES. Danh sách này hiện đang
-- RỖNG. Theo §3.3.3.6, chỉ map extended khi TẤT CẢ người nhận hỗ trợ extended,
-- "Otherwise" -> basic; danh sách rỗng nghĩa là không có bằng chứng nào, nên
-- gateway phân giải ra BASIC (và ghi log cảnh báo chưa cấu hình).
--
-- => Muốn chạy CTSW103 bản tin 6 (tất cả hỗ trợ extended -> EXTENDED) và
--    bản tin 7 (tất cả TRỪ MỘT -> BASIC), BẮT BUỘC phải điền danh sách này.
--    Thay các địa chỉ ví dụ dưới đây bằng địa chỉ AFTN thật:
--
--   UPDATE `gateway_config`
--      SET `config_value` = 'VVHHZTZX,VVTSZDYX'
--    WHERE `config_key` = 'ATSMHS_EXTENDED_CAPABLE_ADDRESSES';
-- -----------------------------------------------------------------------------
