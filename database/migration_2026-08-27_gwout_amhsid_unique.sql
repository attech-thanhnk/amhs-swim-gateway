-- ============================================================
-- Migration: chặn trùng bản tin ở tầng cơ sở dữ liệu
-- Ngày: 27/08/2026
-- ============================================================
--
-- VẤN ĐỀ
-- AmhsToGwoutSyncScheduler chống trùng bằng điều kiện trong câu SELECT:
--
--   AND NOT EXISTS (SELECT 1 FROM gwout g WHERE g.amhsid = t.messageId)
--
-- Cách này chỉ an toàn khi có MỘT instance. Với hai instance cùng đọc mtcu_tmp, cả hai
-- có thể qua được NOT EXISTS trước khi bên nào kịp commit, và cùng INSERT -> hai dòng
-- gwout cho một bản tin AMHS. Với bản tin hợp lệ, hậu quả là PUBLISH TRÙNG lên AMQP.
--
-- Tình huống này đã xảy ra thật ngày 26/08 khi chạy song song bản local và bản trên server.
--
-- CÁCH XỬ LÝ
-- Ràng buộc UNIQUE ở tầng CSDL chặn chắc chắn, không phụ thuộc thời điểm commit.
--
-- Lưu ý: MySQL cho phép NHIỀU giá trị NULL trong index UNIQUE. Điều này đúng ý muốn —
-- 88 dòng gwout hiện có amhsid = NULL (sinh từ công cụ mô phỏng, không qua đường đồng bộ)
-- vẫn tồn tại bình thường.

-- ------------------------------------------------------------
-- Bước 1: dọn dữ liệu thử nghiệm
-- ------------------------------------------------------------
-- Dòng dưới đã bị vô hiệu: bảng `gwout_report` được xoá ngày 2026-09-09,
-- xem migration_2026-09-09_drop_gwout_report.sql
-- DELETE FROM `gwout_report` WHERE `mts_id` LIKE 'TEST-CTSW%';
DELETE FROM `gwout`        WHERE `amhsid` LIKE 'TEST-CTSW%';
DELETE FROM `mtcu_to`      WHERE `receiveMessage_id` IN
       (SELECT id FROM (SELECT id FROM `mtcu_tmp` WHERE `messageId` LIKE 'TEST-CTSW%') x);
DELETE FROM `mtcu_tmp`     WHERE `messageId` LIKE 'TEST-CTSW%';

-- ------------------------------------------------------------
-- Bước 2: dọn mọi dòng trùng còn sót (an toàn cho môi trường khác)
-- ------------------------------------------------------------
-- Giữ dòng có msgid NHỎ NHẤT của mỗi amhsid, xoá các dòng sinh sau.
DELETE g1 FROM `gwout` g1
  JOIN `gwout` g2
    ON g1.`amhsid` = g2.`amhsid`
   AND g1.`msgid`  > g2.`msgid`
 WHERE g1.`amhsid` IS NOT NULL;

-- ------------------------------------------------------------
-- Bước 3: tạo ràng buộc
-- ------------------------------------------------------------
ALTER TABLE `gwout`
    ADD UNIQUE KEY `uk_gwout_amhsid` (`amhsid`);

-- ------------------------------------------------------------
-- HÀNH VI SAU KHI ÁP
-- ------------------------------------------------------------
-- Khi hai instance cùng chạy và cùng cố ghi một bản tin:
--   - một bên INSERT thành công;
--   - bên kia nhận lỗi "Duplicate entry ... for key uk_gwout_amhsid".
--
-- Vòng lặp đồng bộ đã có try/catch cho từng bản tin nên không sập, chỉ ghi log lỗi.
-- Tuy nhiên vì saveAndFlush nằm trong một @Transactional, vi phạm ràng buộc sẽ đánh dấu
-- transaction rollback-only, khiến các bản tin CÒN LẠI trong cùng lô đó cũng bị huỷ.
-- Chúng được đồng bộ lại ở lượt quét sau (2 giây), lúc đó NOT EXISTS đã lọc bản tin
-- vừa bị bên kia ghi -> tự phục hồi, chỉ tốn một lượt.
--
-- Nói cách khác: ràng buộc này là LƯỚI AN TOÀN, không phải cơ chế chính. Cách xử lý gốc
-- vẫn là chỉ chạy một instance gateway-swim trên mỗi cơ sở dữ liệu.
