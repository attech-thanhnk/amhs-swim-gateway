-- ============================================================
-- Migration: đồng nhất mtcu_tmp trên 163 với schema của server 188
-- Ngày: 27/08/2026
-- ============================================================
--
-- BỐI CẢNH
-- Server 188 chạy DB xử lý AMHS, sau đó chuyển dữ liệu sang 163 để ITCU đọc.
-- So sánh hai bên cho thấy 163 thiếu 4 cột mà 188 đang có:
--
--   status      int(11)     -- trạng thái xử lý phía 188
--   data        longblob    -- NỘI DUNG NHỊ PHÂN của file đính kèm (FTBP)
--   file_name   varchar(255)-- tên file đính kèm
--   error_code  int(11)     -- mã lỗi phía 188
--
-- Cột `data` chính là mắt xích còn thiếu khiến file đính kèm chưa bao giờ đi được
-- sang SWIM: nội dung file vẫn nằm trên 188 nhưng không được mang sang 163.
--
-- Sau migration này, bước chuyển 188 -> 163 mới có chỗ để đổ `data` và `file_name` vào.

ALTER TABLE `mtcu_tmp`
    ADD COLUMN `status` int(11) DEFAULT NULL
        COMMENT 'Trạng thái xử lý phía server 188',
    ADD COLUMN `data` longblob DEFAULT NULL
        COMMENT 'Nội dung nhị phân của file đính kèm FTBP - nguồn cho AMQP body section data (§4.4.3.5.1)',
    ADD COLUMN `file_name` varchar(255) DEFAULT NULL
        COMMENT 'Tên file đính kèm - nguồn cho amhs_ftbp_file_name (§4.4.3.4.2)',
    ADD COLUMN `error_code` int(11) DEFAULT NULL
        COMMENT 'Mã lỗi phía server 188';

-- ------------------------------------------------------------
-- Ghi chú về ba cột ftbp* chỉ có trên 163
-- ------------------------------------------------------------
-- `ftbpFileName`, `ftbpObjectSize`, `ftbpLastMod` được thêm vào 163 trước đây cho gateway,
-- nhưng server 188 không có nguồn tương ứng nên cả ba luôn NULL (kiểm tra 53.525 dòng: 0 dòng
-- có giá trị).
--
-- Sau khi ITCU chuyển sang đọc `file_name` và tự tính kích thước bằng LENGTH(`data`),
-- ba cột này không còn được dùng và có thể xoá. CHƯA xoá trong migration này để tránh
-- rủi ro, xoá riêng sau khi bản build mới đã chạy ổn định:
--
--   ALTER TABLE `mtcu_tmp` DROP COLUMN `ftbpFileName`,
--                          DROP COLUMN `ftbpObjectSize`,
--                          DROP COLUMN `ftbpLastMod`;
--
-- Riêng `ftbpLastMod` (date-and-time-of-last-modification) không có nguồn ở cả hai server.
-- Điều này HỢP LỆ: §4.4.3.4.2 ghi rõ ba thuộc tính FTBP "All of them are optional", mã T1,
-- vắng mặt thì không gán application property tương ứng.
