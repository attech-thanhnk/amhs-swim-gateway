-- ============================================================
-- Migration: mở rộng sức chứa danh sách recipient (CTSW010)
-- Ngày: 2026-08-26
-- Phạm vi: Appendix A EUR Doc 047 v3.0 - CTSW010
-- ============================================================

-- ------------------------------------------------------------
-- 1. gwout.address: varchar(1000) -> MEDIUMTEXT
-- ------------------------------------------------------------
-- CTSW010 ca (a): IPM với 512 recipient (khi "Maximum message number of recipients"
-- được đặt là 512) phải được chuyển đổi và publish THÀNH CÔNG.
--
-- Mỗi địa chỉ AFTN là 8 ký tự + 1 dấu phân cách = 9 ký tự, nên 512 recipient cần
-- khoảng 4608 ký tự. Cột varchar(1000) cũ chỉ chứa được ~111 recipient; phần dư bị
-- AmhsToGwoutSyncScheduler cắt bỏ âm thầm khiến bản tin được chuyển đi THIẾU người
-- nhận thay vì bị từ chối đúng cách.
--
-- Lưu ý: việc vượt ngưỡng vẫn phải sinh NDR "too-many-recipients" (§4.4.2.7) — do
-- MessageValidationService.validateAmhsToSwim đảm nhiệm, không phải do giới hạn cột.
ALTER TABLE `gwout`
    MODIFY COLUMN `address` MEDIUMTEXT COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'Danh sách địa chỉ AFTN người nhận, phân cách dấu phẩy (CTSW010: tới 512 recipient)';

-- ------------------------------------------------------------
-- 2. Cấu hình cho conformance test
-- ------------------------------------------------------------
-- Appendix A/CTSW010 và CTSW011 (Probe 4/5) đều giả định
-- "Maximum message number of recipients" = 512. Giá trị đang seed là 20 (phù hợp vận
-- hành thường ngày). TRƯỚC KHI chạy conformance test, đặt lại thành 512:
--
--   UPDATE `gateway_config` SET `config_value` = '512'
--    WHERE `config_key` = 'MAX_MESSAGE_RECIPIENTS';
--
-- Không tự động đổi ở đây để tránh nới lỏng giới hạn của môi trường đang chạy thật.
