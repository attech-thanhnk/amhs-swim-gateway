-- ============================================================
-- Migration: các phát hiện rà soát chiều AMHS → SWIM
-- Ngày: 29/08/2026
-- Phạm vi: EUR Doc 047 §4.4.3.4.4, §4.4.2.5, §4.4.6.3
--          Appendix A: CTSW004, CTSW010, CTSW011
-- ============================================================
--
-- Gộp bốn thay đổi độc lập, áp theo thứ tự nào cũng được, áp lại nhiều lần không sao.

-- MySQL Workbench mặc định bật safe update mode, chặn mọi DELETE/UPDATE không có WHERE trên cột
-- KEY (Error 1175). Câu DELETE dọn dòng trùng ở mục 1 ghép bảng với chính nó nên rơi vào diện bị
-- chặn. Tắt trong phạm vi session rồi trả lại đúng giá trị cũ ở cuối file, để script chạy được
-- cả trên Workbench lẫn mysql CLI mà không đổi cấu hình của người chạy.
SET @old_safe_updates := @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ------------------------------------------------------------
-- 1. Chặn publish trùng một IPM: UNIQUE (gwout_id, recipient)
-- ------------------------------------------------------------
-- VẤN ĐỀ
-- gwout giữ nguyên status = 1 (TRANSFORMED) cho tới khi MỌI dòng gwout_dispatch kết thúc, còn
-- findPendingPublishBatch chỉ lọc theo status. Khi publish lỗi, dispatch chuyển FAILED kèm
-- next_retry_at ở tương lai nhưng gwout vẫn = 1, nên lượt poll kế tiếp (POLL_INTERVAL_MS = 500ms)
-- chọn lại chính bản tin đó và tạo THÊM một bộ dispatch đầy đủ. Với retry lần 1 chờ 30 giây, một
-- bản tin kẹt sinh khoảng 60 bộ dòng thừa; các dòng cũ đến hạn retry sau đó publish lại cùng một
-- IPM, vi phạm §4.4.3.4.4 ("1 IPM AMHS chỉ sinh ra 1 message AMQP duy nhất").
--
-- CÁCH XỬ LÝ
-- Điều kiện NOT EXISTS trong findPendingPublishBatch là cơ chế chính. Ràng buộc dưới đây là lưới
-- đỡ ở tầng CSDL, không phụ thuộc thời điểm commit của từng instance.

-- Bước 0 (chỉ để XEM, không sửa gì): kiểm tra phạm vi trước khi xoá.
-- Chạy riêng câu này trước nếu muốn biết sẽ mất bao nhiêu dòng.
--   SELECT gwout_id, recipient, COUNT(*) AS so_dong
--     FROM `gwout_dispatch`
--    GROUP BY gwout_id, recipient
--   HAVING COUNT(*) > 1;

-- Bước 1: dọn dòng trùng đã sinh ra từ trước.
-- Giữ MỘT dòng cho mỗi (gwout_id, recipient): ưu tiên dòng đã SENT (đó là dòng phản ánh lần
-- publish thật sự xảy ra), trong số đó lấy id nhỏ nhất; nếu không có dòng nào SENT thì lấy id
-- nhỏ nhất của cả nhóm.
-- `WHERE d1.id > 0` dùng khoá chính, cần cho safe update mode dù đã tắt ở trên.
DELETE d1 FROM `gwout_dispatch` d1
  JOIN (
      SELECT `gwout_id`, `recipient`,
             COALESCE(
                 MIN(CASE WHEN `status` = 'SENT' THEN `id` END),
                 MIN(`id`)
             ) AS keep_id
        FROM `gwout_dispatch`
       GROUP BY `gwout_id`, `recipient`
  ) k
    ON d1.`gwout_id`  = k.`gwout_id`
   AND d1.`recipient` = k.`recipient`
   AND d1.`id`       <> k.`keep_id`
 WHERE d1.`id` > 0;

-- Bước 2: tạo ràng buộc (bỏ qua nếu đã tồn tại)
SET @exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'gwout_dispatch'
     AND INDEX_NAME   = 'uk_dispatch'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `gwout_dispatch` ADD UNIQUE KEY `uk_dispatch` (`gwout_id`, `recipient`)',
  'SELECT ''uk_dispatch da ton tai, bo qua''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. Nới gwout.filing_time để giữ được giá trị SAI KHUÔN
-- ------------------------------------------------------------
-- VẤN ĐỀ
-- Cột rộng đúng 6 ký tự nên AmhsToGwoutSyncScheduler phải cắt bớt trước khi ghi. Việc cắt xảy ra
-- TRƯỚC bước validateAtsMessageHeader (yêu cầu ^\d{6}$), nên một filing-time hỏng như
-- "0704301234" biến thành "070430" hợp lệ và CTSW004 mất một ca kiểm thử.
--
-- CÁCH XỬ LÝ
-- Nới cột lên varchar(32) và bỏ hẳn bước cắt về 6, để giá trị thô đi tới được bước từ chối.
-- Bản tin đúng khuôn vẫn chỉ chiếm 6 ký tự như trước.
ALTER TABLE `gwout`
    MODIFY COLUMN `filing_time` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'ATS-message-filing-time (DDhhmm). Rộng 32 để giá trị sai khuôn cũng lưu nguyên vẹn cho CTSW004';

-- ------------------------------------------------------------
-- 3. MAX_MESSAGE_RECIPIENTS: 20 -> 512
-- ------------------------------------------------------------
-- Appendix A CTSW010 và CTSW011 đều giả định "Maximum message number of recipients" = 512.
-- Giá trị 20 hiện tại sẽ làm cả hai case từ chối nhầm bản tin hợp lệ.
UPDATE `gateway_config`
   SET `config_value` = '512'
 WHERE `config_key` = 'MAX_MESSAGE_RECIPIENTS';

-- ------------------------------------------------------------
-- 4. Tách key địa chỉ AMHS của gateway
-- ------------------------------------------------------------
-- VẤN ĐỀ
-- Chiều AMHS → SWIM đang mượn DEFAULT_ORIGINATOR_AFTN làm địa chỉ gateway để loại khỏi
-- amhs_recipients (§4.4.3.4.4). Nhưng ý nghĩa thật của key đó là AFTN originator mà ITCU dùng khi
-- dựng bản tin ở chiều SWIM → AMHS (xem AMQPSubscriberService). Hai khái niệm khác nhau: đổi
-- originator của chiều kia sang giá trị khác sẽ làm địa chỉ gateway không còn bị loại, và
-- amhs_recipients mang sai giá trị.
INSERT INTO `gateway_config` (`config_key`, `config_value`, `description`, `updated_at`)
VALUES ('GATEWAY_AMHS_ADDRESS', 'VVTSSWIM',
        'Địa chỉ AFTN gateway dùng để NHẬN bản tin từ AMHS; bị loại khỏi amhs_recipients (§4.4.3.4.4)',
        NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;

-- ------------------------------------------------------------
-- Trả safe update mode về đúng giá trị trước khi chạy script
-- ------------------------------------------------------------
SET SQL_SAFE_UPDATES = @old_safe_updates;

-- ------------------------------------------------------------
-- HÀNH VI SAU KHI ÁP
-- ------------------------------------------------------------
-- 1. Một (gwout_id, recipient) chỉ còn đúng một dòng dispatch. Nếu code cũ vẫn chạy song song,
--    lần chèn thừa sẽ nhận lỗi "Duplicate entry ... for key uk_dispatch" thay vì publish trùng.
-- 2. filing_time sai khuôn đi được tới bước kiểm tra và sinh NDR content-syntax-error (CTSW004).
-- 3. Bản tin tới 512 recipient không còn bị từ chối nhầm (CTSW010, CTSW011).
-- 4. Có thể đổi DEFAULT_ORIGINATOR_AFTN độc lập mà không ảnh hưởng chiều AMHS → SWIM.
