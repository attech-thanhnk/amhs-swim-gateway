-- ============================================================
-- Migration: phản hồi bay ngược về ở chiều SWIM → AMHS
-- Ngày: 2026-09-06
-- Phạm vi: Appendix A EUR Doc 047 v3.0 — CTSW014, CTSW015, CTSW113, CTSW114
-- ============================================================
--
-- BỐI CẢNH
--
-- Chiều SWIM → AMHS: ITCU nhận bản tin AMQP, dựng IPM rồi bàn giao cho amss phát ra
-- X.400 (bảng `gwin`). Sau khi phát, có HAI loại phản hồi bay ngược về cổng AMHS:
--
--   a) IPN — RN/NRN, §4.4.1.2 b) → §4.4.7    → bảng `mtcu_ipn` (đã có từ 26/08)
--   b) Report — DR/NDR, §4.4.1.3 Report reception → bảng `mtcu_report` (tạo ở đây)
--
-- Cả hai đều nói về bản tin ITCU ĐÃ GỬI ĐI, tức bản ghi nằm ở `gwin`. Đây KHÔNG phải
-- `gwout_report` — bảng đó là hàng đợi report ITCU tự sinh cho chiều ngược lại
-- (AMHS → SWIM), hoàn toàn khác việc.
--
-- Mọi cột đều cho phép NULL và ITCU degrade an toàn khi thiếu dữ liệu, nên có thể áp
-- migration này trước rồi amss điền dần.

-- ------------------------------------------------------------
-- 1. gwin: ba cột để đối chiếu phản hồi về đúng bản tin gốc
-- ------------------------------------------------------------
-- §4.4.7.1 a) yêu cầu xác định "bản tin chủ đề đã được ITCU sinh ra trước đó hay chưa".
-- Trước đây ITCU tra nhầm sang `gwout.ipm_id` — đó là IPM ITCU NHẬN từ AMHS, không phải
-- IPM ITCU DỰNG RA. Ba cột dưới đây là khoá đối chiếu đúng chiều.
ALTER TABLE `gwin`
    ADD COLUMN `ipm_id` varchar(255) DEFAULT NULL
        COMMENT 'IPM-Identifier của IPM do ITCU dựng - ITCU tự ghi từ property amhs_ipm_id (CTSW014/113)'
        AFTER `message_id`,
    ADD COLUMN `mts_id` varchar(255) DEFAULT NULL
        COMMENT 'MTS-Identifier do MTA cấp lúc submit - AMSS GHI NGƯỢC vào sau khi phát (CTSW114)'
        AFTER `ipm_id`,
    ADD COLUMN `ats_priority` varchar(2) DEFAULT NULL
        COMMENT 'ATS-message-priority SS/DD/FF/GG/KK - ITCU tự ghi, §4.4.7.2 cần để lọc RN';

CREATE INDEX `idx_gwin_ipm_id` ON `gwin` (`ipm_id`);
CREATE INDEX `idx_gwin_mts_id` ON `gwin` (`mts_id`);

-- Ai ghi cột nào:
--   * ipm_id, ats_priority -> ITCU tự ghi lúc nhận bản tin AMQP. KHÔNG phụ thuộc amss.
--   * mts_id               -> chỉ amss biết (MTA cấp lúc submit). Là khoá dự phòng khi
--                             SWIM không gửi kèm amhs_ipm_id, và là khoá CHÍNH của
--                             CTSW114 vì NDR tham chiếu bản tin qua MTS-Identifier.

-- ------------------------------------------------------------
-- 2. mtcu_report: DR/NDR nhận từ AMHS (§4.4.1.3 Report reception)
-- ------------------------------------------------------------
-- CTSW114. Cùng khuôn `mtcu_ipn`: amss INSERT với status PENDING, ITCU quét rồi chuyển
-- sang PROCESSED. Tiêu chí chấm của CTSW114 chỉ là "logs and reports to the Control
-- Position", nên ITCU không cần dựng lại report — chỉ cần biết là nó đã về.
--
-- Tên cột dùng snake_case như `mtcu_ipn` (ITCU đọc bằng JPA entity, Spring Boot áp naming
-- strategy chuyển camelCase thành snake_case kể cả khi khai báo tường minh).
CREATE TABLE IF NOT EXISTS `mtcu_report` (
  `id`              bigint(20)   NOT NULL AUTO_INCREMENT,
  `report_type`     varchar(3)   NOT NULL
                    COMMENT 'DR = delivery-report, NDR = non-delivery-report',
  `gwin_id`         bigint(20)   DEFAULT NULL
                    COMMENT 'gwin.msgid của bản tin gốc, nếu amss map được. NULL thì ITCU tra theo mts_id/ipm_id',
  `subject_mts_id`  varchar(255) DEFAULT NULL
                    COMMENT 'MTS-Identifier bản tin gốc mà report này nói tới - khoá đối chiếu chính',
  `subject_ipm_id`  varchar(255) DEFAULT NULL
                    COMMENT 'IPM-Identifier bản tin gốc, khoá đối chiếu dự phòng',
  `recipient`       varchar(255) DEFAULT NULL
                    COMMENT 'O/R address của recipient mà per-recipient-fields này nói tới',
  `reason_code`     varchar(64)  DEFAULT NULL
                    COMMENT 'non-delivery-reason-code, vd unable-to-transfer. Chỉ với NDR',
  `diagnostic_code` varchar(64)  DEFAULT NULL
                    COMMENT 'non-delivery-diagnostic-code. ĐỂ TRỐNG LÀ HỢP LỆ - CTSW114 yêu cầu đúng như vậy',
  `supplementary_info` varchar(512) DEFAULT NULL
                    COMMENT 'supplementary-information, nếu report có mang',
  `report_time`     varchar(255) DEFAULT NULL
                    COMMENT 'Thời điểm ghi trong report (nguyên văn từ X.400)',
  `received_at`     datetime     DEFAULT NULL
                    COMMENT 'Thời điểm amss ghi bản ghi',
  `status`          varchar(16)  DEFAULT 'PENDING'
                    COMMENT 'PENDING -> PROCESSED, do ITCU cập nhật',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_subject_mts` (`subject_mts_id`),
  KEY `idx_gwin_id` (`gwin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Không đặt khoá ngoại gwin_id -> gwin(msgid): report có thể về sau khi dòng gwin đã bị
-- dọn, và CTSW114 vẫn phải log + báo Control Position kể cả khi không tra ra bản tin gốc.
