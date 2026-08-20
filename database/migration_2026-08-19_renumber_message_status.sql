-- Đánh số lại MessageStatus cho liên tục sau khi xóa các giá trị chết
-- (IN_PROCESSING/IN_TRANSFORMED/IN_SENT, OUT_PROCESSING/OUT_PUBLISHING — chưa từng
-- được gán cho bất kỳ bản tin nào, xem MessageStatus.java để biết lý do).
--
-- !!! QUAN TRỌNG: PHẢI dừng gateway-swim TRƯỚC khi chạy file này, và chỉ khởi động
-- lại SAU KHI migration chạy xong (dùng bản build mới nhất). Chạy migration này
-- trong lúc gateway-swim (bản build cũ) vẫn đang chạy sẽ làm dữ liệu ghi trong
-- lúc đó bị lẫn giữa 2 cách đánh số, gây sai lệch status.
--
-- Thứ tự UPDATE dưới đây đã được tính toán để không đè chéo lên nhau (xử lý theo
-- thứ tự tăng dần của giá trị cũ, giá trị đích luôn nhỏ hơn giá trị nguồn nên không
-- có va chạm giữa các bước).

-- ========== Gwin / GwinHistory ==========
-- Cũ: PENDING=0, FAILED=4, UNROUTED=5, RESOLVED=6, CANCELLED=7
-- Mới: PENDING=0, FAILED=1, UNROUTED=2, RESOLVED=3, CANCELLED=4
UPDATE `gwin` SET `status` = 1 WHERE `status` = 4;
UPDATE `gwin` SET `status` = 2 WHERE `status` = 5;
UPDATE `gwin` SET `status` = 3 WHERE `status` = 6;
UPDATE `gwin` SET `status` = 4 WHERE `status` = 7;

UPDATE `gwin_history` SET `status` = 1 WHERE `status` = 4;
UPDATE `gwin_history` SET `status` = 2 WHERE `status` = 5;
UPDATE `gwin_history` SET `status` = 3 WHERE `status` = 6;
UPDATE `gwin_history` SET `status` = 4 WHERE `status` = 7;

-- ========== Gwout / GwoutHistory ==========
-- Cũ: PENDING=0, TRANSFORMED=2, PUBLISHED=4, FAILED=5, RESOLVED=6, CANCELLED=7
-- Mới: PENDING=0, TRANSFORMED=1, PUBLISHED=2, FAILED=3, RESOLVED=4, CANCELLED=5
UPDATE `gwout` SET `status` = 1 WHERE `status` = 2;
UPDATE `gwout` SET `status` = 2 WHERE `status` = 4;
UPDATE `gwout` SET `status` = 3 WHERE `status` = 5;
UPDATE `gwout` SET `status` = 4 WHERE `status` = 6;
UPDATE `gwout` SET `status` = 5 WHERE `status` = 7;

UPDATE `gwout_history` SET `status` = 1 WHERE `status` = 2;
UPDATE `gwout_history` SET `status` = 2 WHERE `status` = 4;
UPDATE `gwout_history` SET `status` = 3 WHERE `status` = 5;
UPDATE `gwout_history` SET `status` = 4 WHERE `status` = 6;
UPDATE `gwout_history` SET `status` = 5 WHERE `status` = 7;
