-- =============================================================================
-- Migration: thêm cột gwout.number_of_attachment
-- Ngày: 2026-08-22
-- Lý do: EUR Doc 047 §4.4.2.2 / §4.4.2.4 (test case CTSW007)
--
-- Số body part của IPM gốc. Bảng nguồn mtcu_tmp ĐÃ CÓ sẵn cột numberOfAttachment,
-- nhưng AmhsToGwoutSyncScheduler trước đây không select nó, nên gateway không hề
-- biết bản tin có mấy body part và luôn cho qua.
--
-- Quy tắc theo tài liệu:
--   1 body part   -> xử lý bình thường
--   2 body part   -> chỉ hợp lệ khi là cặp text + file-transfer-body-part (§4.4.2.4a)
--                    ngược lại từ chối, diagnostic "content-syntax-error",
--                    supplementary "unable to convert to AMQP due to unsupported
--                    combination of body part types"
--   > 2 body part -> từ chối, supplementary "unable to convert to AMQP due to
--                    multiple body parts" (§4.4.2.2c)
--
-- Kiểm chứng trên DB thật (192.168.22.163/asg_db) tại thời điểm viết migration:
--   numberOfAttachment = 1 -> 53.518 bản tin
--   numberOfAttachment = 2 -> 5 bản tin (id 208112-208116), bodyPartType=401
--                             (ia5-text), KHÔNG có thuộc tính FTBP
--   => 5 bản tin này đúng là trường hợp "hai body part text" mà §4.4.2.4 yêu cầu
--      từ chối, nhưng trước khi có thay đổi này chúng vẫn được chuyển sang SWIM.
-- =============================================================================

ALTER TABLE `gwout`
  ADD COLUMN `number_of_attachment` int(11) DEFAULT NULL
  COMMENT 'So body part cua IPM goc (EUR Doc 047 4.4.2.2/4.4.2.4)'
  AFTER `body_type`;

-- Lưu ý: các bản tin đã nằm sẵn trong gwout sẽ có giá trị NULL. Code coi NULL là
-- "không rõ" và bỏ qua kiểm tra này (giữ nguyên hành vi cũ cho dữ liệu cũ), chỉ
-- áp dụng cho bản tin đồng bộ mới từ mtcu_tmp.

-- =============================================================================
-- Bổ sung cùng đợt: cột gwout.origin_eit
-- Lý do: EUR Doc 047 §4.4.2.1 (test case CTSW016)
--
-- current encoded-information-types của IPM. Bảng nguồn mtcu_tmp đã có sẵn cột
-- originEncodeInformationType nhưng scheduler không select, nên §4.4.2.1 (chỉ
-- chấp nhận các EIT được liệt kê, còn lại sinh NDR với diagnostic
-- "encoded-information-types-unsupported") hoàn toàn chưa được kiểm.
--
-- Kiểm chứng trên DB thật: cả 53.523 bản tin đều có originEncodeInformationType
-- = 'ia5-text' (giá trị HỢP LỆ), nên thay đổi này không làm đổi hành vi với dữ
-- liệu hiện có. Nếu về sau amss ghi giá trị khác mà chính tả không khớp danh
-- sách trong MessageValidationService.isAllowedEit(), bản tin sẽ bị từ chối -
-- cần theo dõi log "rejected by EIT check" sau khi triển khai.
-- =============================================================================

ALTER TABLE `gwout`
  ADD COLUMN `origin_eit` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
  COMMENT 'current encoded-information-types cua IPM (EUR Doc 047 4.4.2.1)'
  AFTER `number_of_attachment`;
