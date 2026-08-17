-- Gộp bảng message_type_registry vào routing (chỉ còn 1 bảng cho cả detect + route).
-- Chạy thủ công trên DB (không tự động thi hành).
-- LƯU Ý: routing.detect_pattern được MessageDetectService đọc ngay lúc app khởi động
-- (@PostConstruct) — nếu chưa chạy migration này, app vẫn chạy được (lỗi được log,
-- không crash) nhưng toàn bộ nhận diện loại bản tin chiều AMHS->SWIM sẽ không hoạt động.

ALTER TABLE `routing`
  ADD COLUMN `detect_pattern` varchar(255) DEFAULT NULL
  COMMENT 'Mẫu nhận diện loại bản tin từ nội dung thô (chỉ áp dụng direction=OUT)'
  AFTER `message_type`;

-- Điền detect_pattern cho các rule OUT đã có sẵn trong DB (map 1:1 theo message_type,
-- lấy từ dữ liệu cũ của message_type_registry — sửa lại danh sách này nếu DB thật có
-- thêm rule khác với seed.sql).
UPDATE `routing` SET `detect_pattern` = 'METAR '   WHERE `direction` = 'OUT' AND `message_type` = 'METAR_TEXT';
UPDATE `routing` SET `detect_pattern` = 'SPECI '   WHERE `direction` = 'OUT' AND `message_type` = 'SPECI_TEXT';
UPDATE `routing` SET `detect_pattern` = 'TAF '     WHERE `direction` = 'OUT' AND `message_type` = 'TAF_TEXT';
UPDATE `routing` SET `detect_pattern` = 'SIGMET '  WHERE `direction` = 'OUT' AND `message_type` = 'SIGMET_TEXT';
UPDATE `routing` SET `detect_pattern` = 'AIRMET '  WHERE `direction` = 'OUT' AND `message_type` = 'AIRMET_TEXT';
UPDATE `routing` SET `detect_pattern` = 'GAMET '   WHERE `direction` = 'OUT' AND `message_type` = 'GAMET_TEXT';
UPDATE `routing` SET `detect_pattern` = '(SNOWTAM' WHERE `direction` = 'OUT' AND `message_type` = 'SNOWTAM_TEXT';
UPDATE `routing` SET `detect_pattern` = 'ASHTAM '  WHERE `direction` = 'OUT' AND `message_type` = 'ASHTAM_TEXT';
UPDATE `routing` SET `detect_pattern` = 'VAA '     WHERE `direction` = 'OUT' AND `message_type` = 'VAA_TEXT';
UPDATE `routing` SET `detect_pattern` = 'TCA '     WHERE `direction` = 'OUT' AND `message_type` = 'TCA_TEXT';
UPDATE `routing` SET `detect_pattern` = 'AAXX'     WHERE `direction` = 'OUT' AND `message_type` = 'SYNOP_TEXT';
UPDATE `routing` SET `detect_pattern` = '(FPL-'    WHERE `direction` = 'OUT' AND `message_type` = 'FPL_TEXT';
UPDATE `routing` SET `detect_pattern` = '(CHG-'    WHERE `direction` = 'OUT' AND `message_type` = 'CHG_TEXT';
UPDATE `routing` SET `detect_pattern` = '(CNL-'    WHERE `direction` = 'OUT' AND `message_type` = 'CNL_TEXT';
UPDATE `routing` SET `detect_pattern` = '(DLA-'    WHERE `direction` = 'OUT' AND `message_type` = 'DLA_TEXT';
UPDATE `routing` SET `detect_pattern` = '(DEP-'    WHERE `direction` = 'OUT' AND `message_type` = 'DEP_TEXT';
UPDATE `routing` SET `detect_pattern` = '(ARR-'    WHERE `direction` = 'OUT' AND `message_type` = 'ARR_TEXT';
UPDATE `routing` SET `detect_pattern` = '(SPL-'    WHERE `direction` = 'OUT' AND `message_type` = 'SPL_TEXT';
UPDATE `routing` SET `detect_pattern` = '(RQP-'    WHERE `direction` = 'OUT' AND `message_type` = 'RQP_TEXT';
UPDATE `routing` SET `detect_pattern` = '(RQS-'    WHERE `direction` = 'OUT' AND `message_type` = 'RQS_TEXT';
UPDATE `routing` SET `detect_pattern` = 'DFPL'     WHERE `direction` = 'OUT' AND `message_type` = 'DFPL_TEXT';
UPDATE `routing` SET `detect_pattern` = '(ALR-'    WHERE `direction` = 'OUT' AND `message_type` = 'ALR_TEXT';
UPDATE `routing` SET `detect_pattern` = '(EST-'    WHERE `direction` = 'OUT' AND `message_type` = 'EST_TEXT';
UPDATE `routing` SET `detect_pattern` = '(CDN-'    WHERE `direction` = 'OUT' AND `message_type` = 'CDN_TEXT';
UPDATE `routing` SET `detect_pattern` = '(ACP-'    WHERE `direction` = 'OUT' AND `message_type` = 'ACP_TEXT';
UPDATE `routing` SET `detect_pattern` = '(CPL-'    WHERE `direction` = 'OUT' AND `message_type` = 'CPL_TEXT';
UPDATE `routing` SET `detect_pattern` = '('        WHERE `direction` = 'OUT' AND `message_type` = 'NOTAM_TEXT';
UPDATE `routing` SET `detect_pattern` = '(ARP-'    WHERE `direction` = 'OUT' AND `message_type` = 'ARP_TEXT';
UPDATE `routing` SET `detect_pattern` = '(ARS-'    WHERE `direction` = 'OUT' AND `message_type` = 'ARS_TEXT';
-- UNKNOWN: cố tình để trống detect_pattern (dùng đúng fallback có sẵn của MessageDetectService)

-- Bảng message_type_registry không còn được code đọc/ghi (đã gộp vào routing ở trên).
-- Xóa hẳn khi đã xác nhận không còn phụ thuộc nào khác (ví dụ báo cáo/dashboard riêng
-- đang query trực tiếp bảng này ngoài code trong repo này).
-- DROP TABLE IF EXISTS `message_type_registry`;
