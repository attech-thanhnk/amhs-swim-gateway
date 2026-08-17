-- Đề xuất gửi đội quản lý hệ thống "amss" (chủ sở hữu bảng mtcu_tmp).
-- KHÔNG chạy trên DB của gateway-swim/gateway-cp — đây là bảng của hệ thống khác.
--
-- Mục đích: cho phép AMHS/SWIM Gateway lấy đúng tên file, kích thước và thời gian
-- sửa đổi cuối của file đính kèm (FTBP) khi chuyển bản tin AMHS -> SWIM, thay vì
-- phải bỏ trống các property amhs_ftbp_file_name / amhs_ftbp_object_size /
-- amhs_ftbp_last_mod (EUR Doc 047 §4.4.3.4.2 Table 4).
--
-- gateway-swim đã sẵn sàng đọc 3 cột này (tên cột giả định bên dưới, đội amss có thể
-- đổi tên nếu cần — chỉ cần báo lại để cập nhật câu SELECT trong AmhsToGwoutSyncScheduler).

ALTER TABLE `mtcu_tmp`
  ADD COLUMN `ftbpFileName` varchar(255) DEFAULT NULL,
  ADD COLUMN `ftbpObjectSize` varchar(20) DEFAULT NULL,
  ADD COLUMN `ftbpLastMod` varchar(20) DEFAULT NULL;
