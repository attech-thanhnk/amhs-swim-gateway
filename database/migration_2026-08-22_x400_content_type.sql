-- =============================================================================
-- Migration: thêm cột gwout.x400_content_type
-- Ngày: 2026-08-22
-- Lý do: EUR Doc 047 §4.4.1.1 (test case CTSW008)
--
-- content-type nằm trong Message Transfer Envelope. Theo CTSW008, gateway chỉ được
-- chấp nhận interpersonal-messaging-1988(22); các giá trị khác phải bị từ chối và
-- trả NDR gồm:
--   - non-delivery-reason-code     = "unable-to-transfer"
--   - non-delivery-diagnostic-code = "content-type-not-supported"
-- Các giá trị mà CTSW008 dùng để thử: 22 (nhận), 2 (interpersonal-messaging-1984),
-- 35 (edi-messaging), 0 (unidentified) — cả ba giá trị sau đều phải bị từ chối.
--
-- Bảng nguồn mtcu_tmp ĐÃ CÓ sẵn cột contentType (int) nhưng AmhsToGwoutSyncScheduler
-- trước đây không select, nên §4.4.1.1 hoàn toàn chưa được kiểm.
--
-- Kiểm chứng trên DB thật (192.168.22.163/asg_db) tại thời điểm viết migration:
--   contentType = 22 -> 53.523 bản tin (toàn bộ)
--   => không có bản tin thật nào bị ảnh hưởng bởi thay đổi này.
--
-- Vì sao cần cột mới: gwout.content_type đã tồn tại nhưng chứa MIME type
-- (text/plain, application/octet-stream) dùng cho phía AMQP, không phải
-- abstract-value của X.400 — không dùng lại được.
-- =============================================================================

ALTER TABLE `gwout`
  ADD COLUMN `x400_content_type` int(11) DEFAULT NULL
  COMMENT 'content-type abstract-value cua MTE (EUR Doc 047 4.4.1.1)'
  AFTER `origin_eit`;

-- Lưu ý: các bản tin đã nằm sẵn trong gwout sẽ có giá trị NULL. Code coi NULL là
-- "không rõ" và bỏ qua kiểm tra này (giữ nguyên hành vi cũ cho dữ liệu cũ), chỉ
-- áp dụng cho bản tin đồng bộ mới từ mtcu_tmp.
