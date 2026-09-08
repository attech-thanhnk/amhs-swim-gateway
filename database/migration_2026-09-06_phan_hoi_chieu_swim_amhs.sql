-- ============================================================
-- Migration: phản hồi bay ngược về ở chiều SWIM → AMHS
-- Ngày: 2026-09-06 (cập nhật 2026-09-07)
-- Phạm vi: Appendix A EUR Doc 047 v3.0 — CTSW014, CTSW015, CTSW113, CTSW114
-- ============================================================
--
-- BỐI CẢNH
--
-- Chiều SWIM → AMHS: ITCU nhận điện văn AMQP, dựng IPM rồi bàn giao cho amss phát ra X.400
-- (bảng `gwin`). Sau khi phát, có hai loại phản hồi bay ngược về cổng AMHS:
--
--   a) IPN — RN/NRN, §4.4.1.2 b) → §4.4.7     do NGƯỜI NHẬN sinh ra
--   b) Report — DR/NDR, §4.4.1.3               do MTA sinh ra
--
-- Cả bốn loại nằm chung bảng `cp` do AMHS Component tạo, phân biệt bằng cột `ipnType`.
-- Với ITCU chúng là cùng một việc: tra điện văn gốc, ghi log, báo Control Position.
--
-- Đây KHÔNG phải `gwout_report` — bảng đó là hàng đợi report ITCU TỰ SINH cho chiều ngược lại
-- (AMHS → SWIM), ITCU ghi và amss đọc. Hoàn toàn khác việc.
--
-- Ghi chú lịch sử: bản đầu của migration này tạo bảng riêng `mtcu_report` cho DR/NDR. Sau khi
-- rà soát thấy AMHS Component đã có bảng `cp` cho IPN, hai bên chốt gộp cả bốn loại vào `cp`.
-- `mtcu_report` và `mtcu_ipn` đã được xoá ngày 2026-09-07 (cả hai đều rỗng, chưa từng dùng).

-- ------------------------------------------------------------
-- 1. gwin: ba cột để đối chiếu phản hồi về đúng điện văn gốc  [ĐÃ ÁP 2026-09-06]
-- ------------------------------------------------------------
-- §4.4.7.1 a) yêu cầu xác định "điện văn gốc đã được ITCU sinh ra trước đó hay chưa".
-- Trước đây ITCU tra nhầm sang `gwout.ipm_id` — đó là IPM ITCU NHẬN từ AMHS, không phải IPM
-- ITCU DỰNG RA. Ba cột dưới đây là khoá đối chiếu đúng chiều.
ALTER TABLE `gwin`
    ADD COLUMN `ipm_id` varchar(255) DEFAULT NULL
        COMMENT 'IPM-Identifier của IPM do ITCU dựng - đối chiếu cp.subjectIPM (RN/NRN)'
        AFTER `message_id`,
    ADD COLUMN `mts_id` varchar(255) DEFAULT NULL
        COMMENT 'MTS-Identifier do MTA cấp - AMSS GHI NGƯỢC vào, đối chiếu cp.subjectMTS (DR/NDR)'
        AFTER `ipm_id`,
    ADD COLUMN `ats_priority` varchar(2) DEFAULT NULL
        COMMENT 'ATS-message-priority SS/DD/FF/GG/KK - ITCU tự ghi, §4.4.7.2 cần để lọc RN';

CREATE INDEX `idx_gwin_ipm_id` ON `gwin` (`ipm_id`);
CREATE INDEX `idx_gwin_mts_id` ON `gwin` (`mts_id`);

-- Ai ghi cột nào:
--   * ipm_id, ats_priority -> ITCU tự ghi lúc nhận điện văn AMQP. KHÔNG phụ thuộc amss.
--   * mts_id               -> chỉ amss biết (MTA cấp lúc submit). Là khoá CHÍNH của CTSW114 vì
--                             report tham chiếu điện văn gốc qua MTS-Identifier: MTA không giải
--                             mã nội dung nên không thể biết IPM-Identifier.

-- ------------------------------------------------------------
-- 2. cp: ba cột cho nhánh DR/NDR  [CHỜ AMHS COMPONENT ÁP]
-- ------------------------------------------------------------
-- Bảng `cp` do AMHS Component tạo ngày 2026-09-07, ánh xạ 1:1 với IPN của X.420 — đủ cho
-- RN/NRN nhưng chưa có cột nào của report. Ba cột dưới đây bổ sung nhánh DR/NDR.
--
-- ITCU tự dò sự tồn tại của ba cột này lúc chạy và degrade an toàn: thiếu thì nhánh RN/NRN vẫn
-- chạy đủ, riêng NDR thiếu mã lý do. Nên có thể áp lúc nào cũng được, không cần đồng bộ thời điểm.
ALTER TABLE `cp`
    ADD COLUMN `reasonCode` varchar(64) DEFAULT NULL
        COMMENT 'non-delivery-reason-code, vd unable-to-transfer (CTSW114)',
    ADD COLUMN `diagnosticCode` varchar(64) DEFAULT NULL
        COMMENT 'non-delivery-diagnostic-code. ĐỂ TRỐNG LÀ HỢP LỆ - CTSW114 yêu cầu đúng như vậy',
    ADD COLUMN `subjectMTS` varchar(255) DEFAULT NULL
        COMMENT 'MTS-Identifier điện văn gốc - khoá đối chiếu của DR/NDR, tra về gwin.mts_id';

CREATE INDEX `idx_cp_status` ON `cp` (`status`);
CREATE INDEX `idx_cp_ipn_type` ON `cp` (`ipnType`);

-- `ipnType` nhận thêm hai giá trị `DR` và `NDR`, ngoài `RN`/`NRN` đang dùng.
--
-- Lưu ý về `supplementaryInfomation`: cột này hiện mang nghĩa suppl-receipt-info (trường của RN).
-- Với NDR nó là supplementary-information — một khái niệm khác của X.400. Dùng chung được vì mỗi
-- dòng chỉ thuộc một loại và `ipnType` đã phân biệt, nhưng phải nhớ nghĩa phụ thuộc `ipnType`.
