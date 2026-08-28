-- ============================================================
-- Migration: cắt tiền tố "ID:" khỏi amqp_message_id đã lưu
-- Ngày: 28/08/2026
-- ============================================================
--
-- VẤN ĐỀ
-- Chuẩn JMS bắt buộc mọi JMSMessageID bắt đầu bằng "ID:". Chiều VÀO
-- (AMQPSubscriberService) đã cắt tiền tố này trước khi lưu, chiều RA
-- (OutboundDispatchService) thì giữ nguyên. Kết quả là hai cột lệch dạng nhau -
--
--   gwout.amqp_message_id   286/286 có tiền tố "ID-"
--   gwin.message_id           0/261 có tiền tố
--
-- Màn hình Control Position tìm kiếm bằng LIKE trên cả hai cột
-- (MessagesController dòng 207), nên cùng một chuỗi người dùng gõ sẽ khớp ở
-- bảng này mà trượt ở bảng kia.
--
-- CÁCH XỬ LÝ
-- Phần mã- OutboundDispatchService nay gọi AmqpMessageIdUtil.clean() giống chiều vào.
-- Phần dữ liệu- migration này chuẩn hoá các dòng đã lưu từ trước.
--
-- Chỉ cắt đúng tiền tố ở ĐẦU chuỗi. "ID-" xuất hiện giữa chuỗi được giữ nguyên.
-- Phép biến đổi đảo ngược được hoàn toàn bằng CONCAT('ID:', cột) - xem cuối tệp.

SET SQL_SAFE_UPDATES = 0;

-- ------------------------------------------------------------
-- Bước 1: gwout.amqp_message_id  (286 dòng)
-- ------------------------------------------------------------
UPDATE `gwout`
   SET `amqp_message_id` = SUBSTRING(`amqp_message_id`, 4)
 WHERE `amqp_message_id` LIKE 'ID:%';

-- ------------------------------------------------------------
-- Bước 2: message_conversion_log.amqp_message_id  (514 dòng)
-- ------------------------------------------------------------
-- Cắt tiền tố kiểu dữ liệu của Qpid trước, vì chúng dài hơn và cũng bắt đầu bằng "ID-".
UPDATE `message_conversion_log`
   SET `amqp_message_id` = SUBSTRING(`amqp_message_id`, 19)
 WHERE `amqp_message_id` LIKE 'ID:AMQP_NO_PREFIX:%';

UPDATE `message_conversion_log`
   SET `amqp_message_id` = SUBSTRING(`amqp_message_id`, 16)
 WHERE `amqp_message_id` LIKE 'ID:AMQP_STRING:%';

UPDATE `message_conversion_log`
   SET `amqp_message_id` = SUBSTRING(`amqp_message_id`, 16)
 WHERE `amqp_message_id` LIKE 'ID:AMQP_BINARY:%';

UPDATE `message_conversion_log`
   SET `amqp_message_id` = SUBSTRING(`amqp_message_id`, 15)
 WHERE `amqp_message_id` LIKE 'ID:AMQP_ULONG:%';

UPDATE `message_conversion_log`
   SET `amqp_message_id` = SUBSTRING(`amqp_message_id`, 14)
 WHERE `amqp_message_id` LIKE 'ID:AMQP_UUID:%';

UPDATE `message_conversion_log`
   SET `amqp_message_id` = SUBSTRING(`amqp_message_id`, 4)
 WHERE `amqp_message_id` LIKE 'ID:%';

SET SQL_SAFE_UPDATES = 1;

-- ------------------------------------------------------------
-- KHÔNG đụng tới bảng sao lưu
-- ------------------------------------------------------------
-- msg_conver_bak.message_id còn 43/261 dòng có tiền tố. Đây là ảnh chụp dữ liệu
-- tại một thời điểm, giữ nguyên hiện trạng mới đúng vai trò của bản sao lưu.
-- gwin_bk cũng vậy (0 dòng dính, không cần làm gì).

-- ------------------------------------------------------------
-- KIỂM TRA SAU KHI ÁP
-- ------------------------------------------------------------
-- Cả ba con số dưới đây phải bằng 0 -
--
--   SELECT SUM(amqp_message_id LIKE 'ID:%') FROM gwout;
--   SELECT SUM(amqp_message_id LIKE 'ID:%') FROM message_conversion_log;
--   SELECT SUM(message_id      LIKE 'ID:%') FROM gwin;

-- ------------------------------------------------------------
-- CÁCH ĐẢO NGƯỢC (nếu cần)
-- ------------------------------------------------------------
-- Tiền tố "ID:" là cố định nên phục hồi được chính xác -
--
--   SET SQL_SAFE_UPDATES = 0;
--   UPDATE `gwout` SET `amqp_message_id` = CONCAT('ID:', `amqp_message_id`)
--    WHERE `amqp_message_id` IS NOT NULL;
--   SET SQL_SAFE_UPDATES = 1;
--
-- Lưu ý- với message_conversion_log thì phục hồi được tiền tố "ID:" nhưng KHÔNG
-- phân biệt lại được tiền tố kiểu dữ liệu (AMQP_STRING, AMQP_UUID...) đã bị cắt.
