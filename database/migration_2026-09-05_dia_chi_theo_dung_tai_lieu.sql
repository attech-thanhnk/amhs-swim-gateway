-- ============================================================
-- Migration: bỏ vai trò "địa chỉ nhận" của VVTSSWIM ở chiều AMHS -> SWIM
-- Ngày: 05/09/2026
-- ============================================================
--
-- VẤN ĐỀ
-- Địa chỉ AMHS của gateway (VVTSSWIM) đang gánh hai vai trò ngược nhau:
--
--   1. Bản tin PHẢI ghi VVTSSWIM trong danh sách người nhận thì AmhsToGwoutSyncScheduler
--      mới nhặt (điều kiện cũ: mtcu_to.address LIKE '%VVTSSWIM%').
--   2. Ngay sau đó chính địa chỉ đó lại bị gạt khỏi amhs_recipients.
--
-- EUR Doc 047 không có cả hai luật này. Theo Appendix A, IPM được đánh địa chỉ THẲNG tới
-- địa chỉ AFTN của AMQP consumer, còn địa chỉ của gateway không bao giờ là recipient:
--
--   CTSW001  "send a sequence of ATS messages (IPMs) over AMHS to the IUT addressing an
--            AMQP consumer"
--   CTSW009  "the first message contains the addressee indicators of both AMQP consumers"
--   CTSW012  "the AMHS address of the second AMQP consumer is unknown and the MTCU cannot
--            find a match in its address look-up table"
--
-- Hệ quả của luật cũ, đo trên bench ngày 05/09/2026 khi chạy CTSW001: 11/11 bản tin phải
-- ghi kèm VVTSSWIM; 11 bản tin rơi vào nhánh fallback và mang amhs_recipients = 'VVTSSWIM'
-- (chính code cũng ghi chú giá trị đó là SAI so với §4.4.3.4.4, kèm 11 cảnh báo trong
-- gw_alert). Bài CTSW001 điện văn 11 (hai AMQP recipient) không thể đạt vì recipient thứ hai
-- luôn là VVTSSWIM và bị gạt.
--
-- CÁCH XỬ LÝ (đi kèm thay đổi code trong AmhsToGwoutSyncScheduler)
--   - Điều kiện nhặt bản tin: có ít nhất một recipient tra được trong bảng rule OUT đang
--     active — đúng "address look-up table" mà CTSW012 nói tới.
--   - amhs_recipients: giữ nguyên mọi recipient "responsible", không gạt địa chỉ nào.
--
-- THỨ TỰ TRIỂN KHAI - QUAN TRỌNG
-- Chạy migration này TRƯỚC khi deploy code mới sẽ làm bản cũ không nhặt được bản tin nào
-- (bản cũ vẫn lọc theo VVTSSWIM). Bắt buộc:
--   1. Deploy gateway-swim bản mới
--   2. Chạy migration này
--   3. Đổi AMHS Test Tool: đánh địa chỉ tới consumer thật, KHÔNG ghi kèm VVTSSWIM nữa
--
-- ============================================================

START TRANSACTION;

-- ------------------------------------------------------------
-- 1. Bỏ rule OUT cho chính địa chỉ gateway
-- ------------------------------------------------------------
-- VVTSSWIM là địa chỉ gateway dùng để đấu nối AMHS, không phải một AMQP consumer. Giữ rule
-- này lại thì bản tin gửi tới VVTSSWIM vẫn được nhặt và từ nay (không còn bộ lọc gạt) sẽ
-- mang amhs_recipients = 'VVTSSWIM' — sai §4.4.3.4.4 mà không có gì báo.
--
-- Sau khi xoá, bản tin chỉ đánh địa chỉ tới VVTSSWIM sẽ KHÔNG được nhặt nữa. Đó là hành vi
-- mong muốn: cấu hình/địa chỉ sai lộ ra ngay thay vì chạy tiếp với dữ liệu sai.
DELETE FROM `routing` WHERE `direction` = 'OUT' AND `recipients` = 'VVTSSWIM';

-- ------------------------------------------------------------
-- 2. Khai báo AMQP consumer thứ hai: VVTSOPTC
-- ------------------------------------------------------------
-- CTSW001 điện văn 11 và CTSW009 cần HAI AMQP consumer thật. Bench mới chỉ khai báo
-- VVTSOPTB; ngày 05/09 bản tin gửi tới VVTSOPTC bị NDR unrecognised-OR-name (gwout#58978).
--
-- CÙNG send_topic VỚI VVTSOPTB — có chủ đích:
-- processDispatch() gom dispatch theo (gwout, topic), nên hai recipient khác topic sẽ thành
-- HAI message AMQP, mỗi cái amhs_recipients chỉ một tên. CTSW009 lại yêu cầu MỘT message
-- "contains the addressee indicators of both AMQP consumers", tức phải chung một topic thì
-- amhs_recipients mới ra 'VVTSOPTB,VVTSOPTC'.
--
-- Tên topic ở đây mượn lại tên cũ của VVTSOPTB để không làm gián đoạn bên SWIM đang
-- subscribe. Khi hai bên chốt được tên chung cho nhóm consumer này thì chỉ cần UPDATE cột
-- send_topic của cả hai dòng — không phải sửa code, không phải restart.
INSERT INTO `routing`
    (`direction`, `recipients`, `send_topic`, `priority`, `active`, `created_by`, `note`)
VALUES
    ('OUT', 'VVTSOPTC', 'ats/consumer/vvtsoptb', 10, 1, 'migration-2026-09-05',
     'AMQP consumer thứ hai của bộ kiểm thử. Dùng CHUNG send_topic với VVTSOPTB để một IPM gửi cho cả hai chỉ sinh MỘT message AMQP mang đủ hai addressee indicator (CTSW001 điện văn 11, CTSW009). TÊN TOPIC TẠM - chờ thống nhất với bên SWIM.');

-- ------------------------------------------------------------
-- 3. Ghi chú lại ý nghĩa của GATEWAY_AMHS_ADDRESS
-- ------------------------------------------------------------
-- Key này không còn được dùng để lọc recipient nữa. Giữ lại làm thông tin đấu nối, nhưng
-- sửa mô tả để người vận hành không hiểu nhầm là nó còn ảnh hưởng tới amhs_recipients.
UPDATE `gateway_config`
   SET `description` = 'Địa chỉ AFTN của gateway trên mạng AMHS (thông tin đấu nối). Từ 05/09/2026 KHÔNG còn dùng để lọc bản tin hay để gạt khỏi amhs_recipients.',
       `updated_at` = NOW()
 WHERE `config_key` = 'GATEWAY_AMHS_ADDRESS';

COMMIT;

-- ------------------------------------------------------------
-- HÀNH VI SAU KHI ÁP
-- ------------------------------------------------------------
-- 1. Bản tin được nhặt khi có ít nhất một recipient khai báo trong rule OUT active.
-- 2. amhs_recipients liệt kê đủ recipient "responsible", không gạt địa chỉ nào.
-- 3. Recipient không tra được rule vẫn nhận NDR unrecognised-OR-name theo §4.4.8 (CTSW012).
-- 4. Bản tin chỉ đánh địa chỉ tới VVTSSWIM sẽ không còn được chuyển sang SWIM.
--
-- KIỂM TRA NHANH SAU KHI ÁP
--   SELECT recipients, send_topic FROM routing WHERE direction = 'OUT' ORDER BY recipients;
--   -- không còn dòng VVTSSWIM; VVTSOPTB và VVTSOPTC cùng send_topic
