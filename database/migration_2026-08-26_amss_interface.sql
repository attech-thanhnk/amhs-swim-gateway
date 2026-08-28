-- ============================================================
-- Migration: dữ liệu giao diện với AMHS Component (nhóm B)
-- Ngày: 2026-08-26
-- Phạm vi: Appendix A EUR Doc 047 v3.0
--          CTSW001, CTSW009, CTSW014, CTSW015, CTSW020
-- ============================================================
--
-- CÁC CỘT/BẢNG DƯỚI ĐÂY DO AMHS COMPONENT (amss) GHI, ITCU CHỈ ĐỌC.
-- Nguyên tắc: amss ghi GIÁ TRỊ THÔ của X.400, ITCU diễn giải. Ví dụ precedence giữ
-- nguyên 14/28/57/71/107; việc ánh xạ sang SS/DD/FF/GG/KK theo Table 5 là của ITCU.
--
-- Mọi cột đều cho phép NULL và ITCU degrade an toàn khi thiếu dữ liệu, nên có thể áp
-- migration này trước rồi amss điền dần.

-- ------------------------------------------------------------
-- 1. mtcu_to: ba thuộc tính theo TỪNG recipient
-- ------------------------------------------------------------
-- Cả ba đều là thuộc tính của per-recipient-fields / recipient-extensions nên không thể
-- suy ra từ cấp bản tin.
ALTER TABLE `mtcu_to`
    ADD COLUMN `precedence` int(11) DEFAULT NULL
        COMMENT 'IPM recipient-extensions precedence: 14/28/57/71/107. NULL = Basic IPM (CTSW001, CTSW020)',
    ADD COLUMN `responsibility` bit(1) DEFAULT NULL
        COMMENT 'MTE per-recipient-fields responsibility: 1=responsible, 0=not-responsible (CTSW001)',
    ADD COLUMN `recipientType` varchar(10) DEFAULT NULL
        COMMENT 'Loại recipient trong IPM heading: primary | copy | blind-copy (CTSW009)';

-- Ghi chú về cách ITCU dùng:
--   * responsibility  -> lọc gwout.address (amhs_recipients) chỉ còn recipient "responsible"
--                        theo §4.4.3.4.4. NULL ở TẤT CẢ các dòng của một bản tin = chưa có dữ
--                        liệu -> ITCU giữ nguyên hành vi cũ (nhận mọi recipient).
--   * precedence      -> lấy giá trị CAO NHẤT trong các recipient "responsible", ánh xạ sang
--                        amhs_ats_pri và AMQP priority theo Table 5 (§4.4.3.4.3).
--   * recipientType   -> phục vụ CTSW009. Lưu ý: kết quả phía AMQP chủ yếu phụ thuộc việc
--                        địa chỉ có nằm trong MTE hay không; cột này dùng để đối chiếu và
--                        phục vụ phần relay sang AMHS của amss.

-- ------------------------------------------------------------
-- 2. gwout.precedence (ITCU tự ghi, dẫn xuất từ mtcu_to)
-- ------------------------------------------------------------
ALTER TABLE `gwout`
    ADD COLUMN `precedence` int(11) DEFAULT NULL
    COMMENT 'Precedence cao nhất trong các recipient responsible (Table 5) - NULL = Basic IPM'
    AFTER `content_length`;

-- ------------------------------------------------------------
-- 3. mtcu_ipn: IPN (RN/NRN) nhận từ AMHS
-- ------------------------------------------------------------
-- CTSW014 và CTSW015. amss ghi vào, ITCU quét và xử lý theo §4.4.7:
--   - không tìm thấy bản tin chủ đề -> NDR invalid-arguments + supplementary
--     "unable to notify RN to SWIM due to misrouted RN", báo Control Position;
--   - tìm thấy nhưng priority khác SS -> log + báo Control Position, không NDR;
--   - tìm thấy và priority SS        -> log + báo Control Position.
-- IPN KHÔNG được chuyển sang môi trường SWIM (§2.2.1.1).
CREATE TABLE IF NOT EXISTS `mtcu_ipn` (
  `id`               bigint(20)   NOT NULL AUTO_INCREMENT,
  `notification_type` varchar(3)   NOT NULL
                     COMMENT 'RN = receipt-notification, NRN = non-receipt-notification',
  `subject_ipm_id`    varchar(255) DEFAULT NULL
                     COMMENT 'IPM-Identifier của bản tin chủ đề - ITCU tra ngược gwout.ipm_id',
  `subject_mts_id`    varchar(255) DEFAULT NULL
                     COMMENT 'MTS-Identifier bản tin chủ đề, dùng khi thiếu IPM-Identifier',
  `or_address`        varchar(255) DEFAULT NULL
                     COMMENT 'O/R address của bên phát IPN',
  `receipt_time`      varchar(255) DEFAULT NULL
                     COMMENT 'receipt-time trong RN',
  `non_receipt_reason`int(11)      DEFAULT NULL
                     COMMENT 'non-receipt-reason, chỉ với NRN',
  `received_at`       datetime     DEFAULT NULL
                     COMMENT 'Thời điểm amss ghi bản ghi',
  `status`           varchar(16)  DEFAULT 'PENDING'
                     COMMENT 'PENDING -> PROCESSED, do ITCU cập nhật',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_subject_ipm` (`subject_ipm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
