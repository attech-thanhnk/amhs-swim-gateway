-- EUR Doc 047 §4.4.3.4.9: repertoire (ISO-646 / ISO-8859-1) cho general-text-body-part
-- Chạy thủ công trên DB (không tự động thi hành).

ALTER TABLE `gwout`
  ADD COLUMN `body_part_charset` varchar(20) DEFAULT NULL
  COMMENT 'Repertoire general-text-body-part: ISO-646 hoặc ISO-8859-1 (EUR Doc 047 §4.4.3.4.9)'
  AFTER `body_part_type`;

ALTER TABLE `gwout_history`
  ADD COLUMN `body_part_charset` varchar(20) DEFAULT NULL
  COMMENT 'Repertoire general-text-body-part: ISO-646 hoặc ISO-8859-1 (EUR Doc 047 §4.4.3.4.9)'
  AFTER `body_part_type`;

-- EUR Doc 047 §4.5.2.3: subject được phép dài tới 128 ký tự trước khi trim,
-- nhưng cột hiện tại chỉ varchar(100) -> có thể lỗi khi subject dài 101-128 ký tự.
ALTER TABLE `gwin` MODIFY COLUMN `subject` varchar(128) DEFAULT NULL;

-- EUR Doc 047 §4.4.3.4.2 Table 4: file-attribute của FTBP (AMHS -> SWIM)
ALTER TABLE `gwout`
  ADD COLUMN `ftbp_file_name` varchar(255) DEFAULT NULL AFTER `body_part_charset`,
  ADD COLUMN `ftbp_object_size` varchar(20) DEFAULT NULL AFTER `ftbp_file_name`,
  ADD COLUMN `ftbp_last_mod` varchar(20) DEFAULT NULL AFTER `ftbp_object_size`;

ALTER TABLE `gwout_history`
  ADD COLUMN `ftbp_file_name` varchar(255) DEFAULT NULL AFTER `body_part_charset`,
  ADD COLUMN `ftbp_object_size` varchar(20) DEFAULT NULL AFTER `ftbp_file_name`,
  ADD COLUMN `ftbp_last_mod` varchar(20) DEFAULT NULL AFTER `ftbp_object_size`;
