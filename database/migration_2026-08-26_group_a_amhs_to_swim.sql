-- ============================================================
-- Migration: hoàn thiện các kiểm tra chiều AMHS -> SWIM (nhóm A)
-- Ngày: 2026-08-26
-- Phạm vi: Appendix A EUR Doc 047 v3.0 - CTSW001, CTSW006, CTSW011,
--          CTSW012, CTSW013, CTSW017, CTSW019
-- ============================================================

-- ------------------------------------------------------------
-- 1. gwout.content_length
-- ------------------------------------------------------------
-- Tham số content-length của X.400 Probe (§4.4.6.2 / CTSW011 Probe 3).
-- Probe khai báo content-length vượt "Maximum message data size" phải bị từ chối
-- bằng NDR "content-too-long" trước khi chuyển đổi sang AMQP.
--
-- LƯU Ý VỀ NGUỒN DỮ LIỆU: cột này do AMHS Component (amss) điền khi chuyển probe
-- sang ITCU. Bảng nguồn mtcu_tmp hiện KHÔNG có cột tương ứng, cũng như chưa có cờ
-- phân biệt probe với IPM thường -> cần amss bổ sung. Khi giá trị là NULL, ITCU bỏ
-- qua bước kiểm tra (cùng quy ước với gwout.x400_content_type).
ALTER TABLE `gwout`
    ADD COLUMN `content_length` int(11) DEFAULT NULL
    COMMENT 'Content-length của Probe (§4.4.6.2, CTSW011); NULL = không có dữ liệu, bỏ qua kiểm tra'
    AFTER `x400_content_type`;

-- ------------------------------------------------------------
-- 2. Cấu hình chính sách repertoire (CTSW019)
-- ------------------------------------------------------------
-- EUR Doc 047 §4.4.2.3: general-text-body-part có repertoire khác ISO 646
-- (ISO 8859-x, Cyrillic, Arabic, Greek, Hebrew, CJK...) được chuyển đổi hay bị từ
-- chối là tuỳ chính sách nội bộ của AMHS Management Domain.
--   true  = vẫn chuyển đổi sang AMQP (mặc định)
--   false = từ chối, sinh NDR content-syntax-error kèm supplementary-information
--           "unable to convert to AMQP due to unsupported encoded-information-types"
INSERT IGNORE INTO `gateway_config` (`config_key`, `config_value`, `description`, `updated_at`) VALUES
('ALLOW_NON_ISO646_REPERTOIRE', 'true',
 'CTSW019: cho phép general-text-body-part có repertoire khác ISO 646 (true=chuyển đổi, false=từ chối)',
 '2026-08-26 00:00:00');

-- ------------------------------------------------------------
-- 3. Ghi chú: gwout.subject đã tồn tại, không cần thay đổi schema
-- ------------------------------------------------------------
-- CTSW001 (§4.4.3.4.8) yêu cầu AMQP application property amhs_subject.
-- Cột gwout.subject đã có sẵn và mtcu_tmp.subject cũng đã có sẵn; trước migration này
-- AmhsToGwoutSyncScheduler không select cột đó nên giá trị luôn NULL.
-- Đã sửa ở phía ứng dụng, không cần DDL.
