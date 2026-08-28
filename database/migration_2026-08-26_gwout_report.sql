-- ============================================================
-- Migration: hàng đợi AMHS report (DR/NDR) giữa ITCU và AMHS Component
-- Ngày: 2026-08-26
-- Phạm vi: EUR Doc 047 §4.4.8 - Generation of AMHS reports
--          Appendix A: CTSW003 (DR) và 13 case sinh NDR
-- ============================================================

-- Phương án B của hợp đồng ITCU-amss: ITCU quyết định và xếp report vào hàng đợi,
-- AMHS Component (amss) đọc hàng đợi rồi dựng và phát report ra đường truyền X.400.
--
-- MỖI DÒNG LÀ KẾT QUẢ CỦA MỘT RECIPIENT, vì report X.400 mang per-recipient-fields.
-- Nhờ vậy CTSW012 biểu diễn được combined report: cùng một gwout_id có thể vừa có
-- dòng DR (recipient tra được địa chỉ AF) vừa có dòng NDR (recipient không tra được).
--
-- QUY TRÌNH PHÍA amss:
--   1. SELECT * FROM gwout_report WHERE status = 'PENDING' ORDER BY gwout_id, id;
--   2. Gom theo gwout_id -> dựng MỘT report chứa đủ per-recipient-fields của nhóm;
--   3. Phát report ra X.400;
--   4. UPDATE status = 'SENT', sent_at = NOW() cho toàn bộ dòng của nhóm
--      (hoặc status = 'FAILED' kèm last_error nếu phát lỗi).
--
-- non-delivery-reason-code luôn là 'unable-to-transfer' với mọi NDR chiều AMHS -> SWIM.
-- Chuỗi supplementary_info phải giữ NGUYÊN VĂN khi đưa vào NDR: test tool so sánh chính xác.

CREATE TABLE IF NOT EXISTS `gwout_report` (
  `id`                 bigint(20)   NOT NULL AUTO_INCREMENT,
  `gwout_id`           bigint(20)   NOT NULL
                       COMMENT 'FK trỏ tới bản tin gốc trong gwout',
  `mts_id`             varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL
                       COMMENT 'MTS-Identifier bản tin gốc, để amss đối chiếu trên X.400',
  `report_type`        varchar(3)   COLLATE utf8mb4_unicode_ci NOT NULL
                       COMMENT 'DR = delivery-report, NDR = non-delivery-report',
  `recipient`          varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL
                       COMMENT 'Địa chỉ AFTN của recipient mà dòng này áp dụng (per-recipient-fields)',
  `reason_code`        varchar(32)  COLLATE utf8mb4_unicode_ci DEFAULT NULL
                       COMMENT 'non-delivery-reason-code - luôn là unable-to-transfer với NDR',
  `diagnostic_code`    varchar(64)  COLLATE utf8mb4_unicode_ci DEFAULT NULL
                       COMMENT 'non-delivery-diagnostic-code',
  `supplementary_info` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL
                       COMMENT 'supplementary-information - phải giữ nguyên văn theo Appendix A',
  `status`             varchar(16)  COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING'
                       COMMENT 'PENDING -> SENT hoặc FAILED - amss cập nhật sau khi phát',
  `created_at`         datetime(6)  NOT NULL,
  `sent_at`            datetime(6)  DEFAULT NULL,
  `last_error`         text         COLLATE utf8mb4_unicode_ci
                       COMMENT 'Lỗi do amss ghi lại nếu phát report thất bại',
  PRIMARY KEY (`id`),
  -- Idempotency hai chiều: ITCU chạy lại không sinh dòng trùng, amss không phát trùng report
  UNIQUE KEY `uk_report` (`gwout_id`, `recipient`, `report_type`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_report_gwout` FOREIGN KEY (`gwout_id`)
      REFERENCES `gwout` (`msgid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
