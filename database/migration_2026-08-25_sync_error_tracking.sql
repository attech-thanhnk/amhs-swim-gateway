-- =======================================================================
-- Migration: Điền dữ liệu nguồn lỗi (rejection_source) cho dữ liệu cũ
-- Date: 2026-08-25
-- Tables affected: gwin, gwout, message_conversion_log
-- =======================================================================

-- 1. Điền nguồn lỗi rejection_source cho bảng message_conversion_log
UPDATE `message_conversion_log`
SET `rejection_source` = 'SWIM'
WHERE `rejection_source` IS NULL 
  AND (`category` = 'IN' OR `type` = 'SWIM');

UPDATE `message_conversion_log`
SET `rejection_source` = 'AMHS'
WHERE `rejection_source` IS NULL 
  AND (`category` = 'OUT' OR `type` = 'AMHS');

-- 2. Điền nguồn lỗi rejection_source cho gwin và gwout
UPDATE `gwin`
SET `rejection_source` = 'SWIM'
WHERE `rejection_source` IS NULL;

UPDATE `gwout`
SET `rejection_source` = 'AMHS'
WHERE `rejection_source` IS NULL;
