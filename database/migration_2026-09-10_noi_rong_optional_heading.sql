-- ============================================================
-- Migration: Nới rộng gwout.optional_heading và message_conversion_log.ohi
-- Ngày: 10/09/2026
-- ============================================================
-- VẤN ĐỀ:
-- Cột gwout.optional_heading bị giới hạn varchar(60) và AmhsToGwoutSyncScheduler cắt bớt
-- substring(0, 60). EUR Doc 047 §4.4.3.4.6 yêu cầu amhs_ats_ohi phải chứa nguyên văn
-- giá trị của phần tử optional-heading-information (Basic IPM) hoặc originators-reference (Extended IPM),
-- không được tự ý cắt xén làm mất dữ liệu gốc của AMHS khi chuyển sang AMQP.
--
-- CÁCH XỬ LÝ:
-- 1. Nới cột gwout.optional_heading lên varchar(255).
-- 2. Nới cột message_conversion_log.ohi lên varchar(255).
-- 3. Bỏ việc cắt cụt substring(0, 60) trong AmhsToGwoutSyncScheduler.
-- ============================================================

ALTER TABLE `gwout`
    MODIFY COLUMN `optional_heading` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'ATS-message-optional-heading-info hoặc originators-reference (Doc 047 §4.4.3.4.6)';

ALTER TABLE `message_conversion_log`
    MODIFY COLUMN `ohi` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'Optional heading information / originators-reference';
