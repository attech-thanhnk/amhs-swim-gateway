-- ============================================================
-- Migration: chiều AMHS -> SWIM định tuyến theo ĐỊA CHỈ recipient
-- Ngày: 04/09/2026
-- ============================================================
--
-- VẤN ĐỀ
-- Chiều AMHS -> SWIM đang chọn topic bằng cách ĐOÁN LOẠI BẢN TIN từ nội dung điện văn:
-- MessageDetectService khớp routing.detect_pattern trên thân bản tin, ra message_type,
-- rồi RoutingService.findBestMatchOut(message_type) tra ngược lại chính bảng routing để
-- lấy send_topic.
--
-- Cách này không có cơ sở trong đặc tả. EUR Doc 047 Appendix A không hề nhắc tới "topic",
-- "publish" hay "message type"; toàn bộ tài liệu kiểm thử định tuyến theo ĐỊA CHỈ:
--
--   §2.2  "the SWIM producers and consumers are considered as configuration parameters
--          which are jointly set up in the IUT, SWIM Test Tool and AMHS Test Tool"
--   CTSW001 "send a sequence of ATS messages (IPMs) over AMHS to the IUT addressing an
--          AMQP consumer"
--   CTSW009 "two primary recipients, which are one AMHS user and one AMQP consumer ...
--          the first message contains the addressee indicators of both AMQP consumers"
--
-- Hệ quả thực tế của cách đoán nội dung: mọi bản tin không khớp detect_pattern nào đều rơi
-- vào rule catch-all UNKNOWN -> ats/generic/unknown. Ngày 04/09 có 114 dispatch SENT và 10
-- DEAD nằm ở topic này, trong khi bên SWIM subscribe topic nghiệp vụ nên không nhận được gì.
--
-- CÁCH XỬ LÝ
-- Tra send_topic thẳng từ cột routing.recipients theo địa chỉ AFTN của từng người nhận.
-- Cột recipients đã có sẵn trong schema và có sẵn ô nhập trên Control Position, nhưng chưa
-- từng được engine đọc (RoutingService không gọi getRecipients()) nên dùng lại được ngay.
--
-- Mỗi recipient tra đích riêng, nên một IPM gửi cho nhiều người nhận ở các đích khác nhau sẽ
-- sinh nhiều dispatch mang topic khác nhau. OutboundDispatchService.processDispatch() vốn đã
-- gom dispatch theo (gwout, topic) nên mỗi topic vẫn chỉ nhận ĐÚNG MỘT message AMQP với
-- amhs_recipients liệt kê đủ người nhận - giữ nguyên §4.4.3.4.4.
--
-- THỨ TỰ TRIỂN KHAI - QUAN TRỌNG
-- Bản build cũ vẫn dùng detect_pattern. Chạy migration này TRƯỚC khi deploy code mới sẽ làm
-- bản cũ không tra được rule cho bất kỳ bản tin nào. Bắt buộc:
--   1. Deploy gateway-swim bản mới
--   2. Chạy migration này
--
-- ============================================================

START TRANSACTION;

-- ------------------------------------------------------------
-- 1. Bỏ các rule OUT định tuyến theo loại bản tin
-- ------------------------------------------------------------
-- 30 rule cũ ánh xạ detect_pattern -> message_type -> send_topic. Từ đây send_topic được tra
-- theo địa chỉ nên các rule này không còn chỗ dùng. Xoá thay vì set active=0 để bảng routing
-- không còn hai lược đồ song song gây nhầm lẫn cho người vận hành.
DELETE FROM `routing` WHERE `direction` = 'OUT';

-- ------------------------------------------------------------
-- 2. Rule OUT theo địa chỉ - TÊN TOPIC TẠM THỜI
-- ------------------------------------------------------------
-- Tên topic dưới đây là ĐẶT TẠM để hệ thống chạy được ngay, CHƯA thống nhất với bên SWIM.
-- Khi hai bên chốt xong tên thật thì chỉ cần sửa cột send_topic (trên Control Position hoặc
-- bằng UPDATE) - không phải sửa code, không phải restart, RoutingService đọc thẳng DB.
--
-- Danh sách địa chỉ lấy từ dữ liệu thật trong gwout_dispatch tính tới 04/09/2026.
--
-- KHÔNG có rule catch-all: địa chỉ không khai báo ở đây sẽ nhận NDR unrecognised-OR-name theo
-- §4.4.8, để cấu hình thiếu lộ ra ngay thay vì chìm vào một topic rác như ats/generic/unknown.
INSERT INTO `routing`
    (`direction`, `recipients`, `send_topic`, `priority`, `active`, `created_by`, `note`)
VALUES
    ('OUT', 'VVTSSWIM', 'ats/consumer/vvtsswim', 10, 1, 'migration-2026-09-04',
     'Địa chỉ AMHS của chính gateway. AmhsToGwoutSyncScheduler chỉ điền địa chỉ này vào gwout.address khi IPM không có recipient thật nào khác (nhánh fallback §4.4.3.4.4) - chủ yếu gặp ở bản tin kiểm thử.'),
    ('OUT', 'VVTSZTZX', 'ats/consumer/vvtsztzx', 10, 1, 'migration-2026-09-04', 'TÊN TOPIC TẠM - chờ thống nhất với bên SWIM'),
    ('OUT', 'VVNBZTZX', 'ats/consumer/vvnbztzx', 10, 1, 'migration-2026-09-04', 'TÊN TOPIC TẠM - chờ thống nhất với bên SWIM'),
    ('OUT', 'VVHHZTZX', 'ats/consumer/vvhhztzx', 10, 1, 'migration-2026-09-04', 'TÊN TOPIC TẠM - chờ thống nhất với bên SWIM'),
    ('OUT', 'VVHHZPZX', 'ats/consumer/vvhhzpzx', 10, 1, 'migration-2026-09-04', 'TÊN TOPIC TẠM - chờ thống nhất với bên SWIM'),
    ('OUT', 'VVTSOPTB', 'ats/consumer/vvtsoptb', 10, 1, 'migration-2026-09-04', 'TÊN TOPIC TẠM - chờ thống nhất với bên SWIM'),
    ('OUT', 'VVTSMHSA', 'ats/consumer/vvtsmhsa', 10, 1, 'migration-2026-09-04', 'TÊN TOPIC TẠM - chờ thống nhất với bên SWIM');

COMMIT;

-- ============================================================
-- 3. Dọn các cột không còn (hoặc chưa bao giờ) được đọc
-- ============================================================
-- Đã rà toàn bộ gateway-swim, gateway-cp và Control Position frontend. Các cột dưới đây không
-- có bất kỳ chỗ nào đọc giá trị - giữ lại chỉ gây hiểu nhầm cho người vận hành, vì chúng vẫn
-- hiện ra trên UI/Swagger và sửa được nhưng sửa xong không có tác dụng gì.
--
--   routing.detect_pattern  Mẫu nhận diện loại bản tin. MessageDetectService - nơi duy nhất đọc
--                           cột này - đã bị xoá cùng migration này.
--   routing.originator      Chỉ được RoutingController ghi vào, không logic nào đọc ra. Originator
--                           chiều SWIM -> AMHS lấy từ property amhs_originator của bản tin AMQP,
--                           thiếu/sai thì lùi về gateway_config.DEFAULT_ORIGINATOR (§4.5.2.12).
--   routing.message_filter  Không xuất hiện ở bất kỳ file nào trong cả ba codebase.
--   routing.convert_to_json Không xuất hiện ở bất kỳ file nào. Doc 047 yêu cầu chuyển tiếp nguyên
--                           văn, không convert theo chiều nào.
--   routing.priority_amhs   Không xuất hiện ở bất kỳ file nào. Độ ưu tiên lấy từ chính bản tin
--   routing.priority_swim   (mtcu_to.precedence / ATS-message-priority), không lấy từ rule.
--
--   gwout_dispatch.message_type  Được ghi lúc tạo dispatch nhưng không nơi nào đọc, kể cả
--                           Control Position. Đích publish nay tra theo địa chỉ nên trường này
--                           cũng hết nguồn dữ liệu.
--
-- GIỮ LẠI routing.message_type: engine KHÔNG đọc, nhưng Control Position hiển thị nó ở tab S2A
-- làm nhãn cho biết topic đó chở loại bản tin gì. Đây là dữ liệu cho người đọc, không phải cho
-- máy chạy - đã ghi rõ điều đó vào @Schema của entity và DTO.

ALTER TABLE `routing`
    DROP COLUMN `detect_pattern`,
    DROP COLUMN `originator`,
    DROP COLUMN `message_filter`,
    DROP COLUMN `convert_to_json`,
    DROP COLUMN `priority_amhs`,
    DROP COLUMN `priority_swim`;

ALTER TABLE `gwout_dispatch`
    DROP COLUMN `message_type`;


-- ============================================================
-- GHI CHÚ VỀ CÚ PHÁP CỘT recipients
-- ============================================================
-- Một rule khai báo được nhiều địa chỉ, phân cách bằng dấu phẩy hoặc khoảng trắng:
--
--   'VVTSZTZX,VVNBZTZX'   hai địa chỉ cùng đi về một topic
--   'VVTS*'               mọi địa chỉ bắt đầu bằng VVTS
--   '*'                   mọi địa chỉ (catch-all, KHÔNG khai báo ở trên - xem lý do mục 2)
--
-- Thứ tự ưu tiên trong RoutingService.findTopicForRecipient():
--   khớp chính xác  >  wildcard tiền tố dài hơn  >  wildcard tiền tố ngắn hơn  >  priority nhỏ hơn
