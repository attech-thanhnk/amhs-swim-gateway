-- ============================================================
-- ASG — Seed Data
-- ============================================================
USE asg_db;

SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;

-- Tắt kiểm tra khóa ngoại để thực hiện nạp dữ liệu an toàn
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. gateway_config defaults
-- ============================================================
INSERT INTO `gateway_config` (`config_key`, `config_value`, `description`, `updated_at`) VALUES
('MAX_MESSAGE_RECIPIENTS',  '20',    'Max recipients per message (0 = unlimited)', NOW()),
('POLL_INTERVAL_MS',        '500',   'Tần suất poll tin tức (ms)', NOW()),
('INBOUND_BATCH_SIZE',      '10',    'Số lượng tin AMHS xử lý mỗi lô', NOW()),
('OUTBOUND_BATCH_SIZE',     '10',    'Số lượng tin SWIM xử lý mỗi lô', NOW()),
('RETRY_MAX_COUNT',         '3',     'Số lần retry tối đa khi lỗi gửi tin', NOW()),
('RETRY_DELAY_1ST_SECONDS', '30',    'Thời gian chờ retry lần 1 (s)', NOW()),
('RETRY_DELAY_2ND_SECONDS', '120',   'Thời gian chờ retry lần 2 (s)', NOW()),
('RETRY_DELAY_3RD_SECONDS', '300',   'Thời gian chờ retry lần 3 (s)', NOW()),
('JWT_SECRET', 'asgGatewaySecretKey2026ChangeInProduction!', 'Khóa bí mật tạo JWT', NOW()),
('JWT_EXPIRATION_MS', '3600000', 'Thời hạn Token (ms) - Mặc định 1 giờ', NOW()),
('LOG_RETENTION_DAYS',      '30',    'Số ngày giữ log hệ thống', NOW()),
('DEFAULT_ORIGINATOR_AFTN', 'VVHHZPZX', 'AFTN originator cho bản tin', NOW()),
('CONVERSION_DIRECTION',    'BOTH',  'BOTH / AMHS_TO_SWIM / SWIM_TO_AMHS', NOW()),
('ATSMHS_SERVICE_LEVEL', 'CONTENT_BASED', 'EXTENDED / BASIC / CONTENT_BASED / RECIPIENTS_BASED', NOW()),
('AUTHORIZED_AMHS_USERS', 'ALL', 'ALL / BY_LIST / BY_PRMD', NOW()),
('AUTHORIZED_SWIM_USERS', 'ALL', 'ALL / BY_LIST / BY_ENTERPRISE', NOW()),
('AUTHORIZED_AMHS_ADDRESSES', '', 'Whitelist địa chỉ AMHS (nếu dùng BY_LIST)', NOW()),
('AUTHORIZED_AMHS_PRMDS', '', 'Whitelist PRMD (nếu dùng BY_PRMD)', NOW()),
('AUTHORIZED_SWIM_ENTERPRISES', '', 'Whitelist Enterprise SWIM (nếu dùng BY_ENTERPRISE)', NOW()),
('ATSMHS_EXTENDED_CAPABLE_ADDRESSES', '', 'Danh sách địa chỉ hỗ trợ Extended ATSMHS', NOW()),
('STRICT_COMPLIANCE_MODE', 'false', 'Bật chế độ kiểm tra EUR Doc 047 (S-06)', NOW()),
('MAX_MSG_DATA_SIZE', '2097152', 'Kích thước tin tối đa (bytes) - Mặc định 2MB', NOW()),
('ALLOWED_ORIGINS', 'http://192.168.22.159:5173,http://localhost:5173,http://localhost:3000', 'CORS Allowed Origins (Frontend trên máy 159)', NOW()),
('GATEWAY_IP', '0.0.0.0', 'IP lắng nghe (0.0.0.0 để nghe mọi card mạng LAN)', NOW()),
('SERVER_PORT_CP', '8180', 'Cổng dịch vụ Dashboard (CP)', NOW()),
('SERVER_PORT_SWIM', '8181', 'Cổng dịch vụ SWIM Component', NOW());

-- ============================================================
-- 2. message_type_registry — nhận diện điện văn
-- ============================================================
INSERT INTO `message_type_registry` (`message_type`, `detect_pattern`, `difficulty`, `phase`, `active`, `note`) VALUES
('METAR', '<iwxxm:METAR', 'easy', 1, 1, 'IWXXM METAR'),
('FPL', '<fx:FlightPlan', 'easy', 1, 1, 'FIXM Flight Plan'),
('NOTAM', '<aixm:Event', 'medium', 1, 1, 'AIXM NOTAM'),
('TAF', '<iwxxm:TAF', 'easy', 1, 1, 'IWXXM TAF'),
('SIGMET', '<iwxxm:SIGMET', 'medium', 1, 1, 'IWXXM SIGMET'),
('SPECI', '<iwxxm:SPECI', 'easy', 1, 1, 'IWXXM SPECI'),
('METAR_TEXT', 'METAR ', 'easy', 1, 1, 'Legacy TAC METAR'),
('SPECI_TEXT', 'SPECI ', 'easy', 1, 1, 'Legacy TAC SPECI'),
('FPL_TEXT', '(FPL-', 'easy', 1, 1, 'Legacy TAC FPL'),
('DEP_TEXT', '(DEP-', 'easy', 1, 1, 'Legacy TAC DEP'),
('ARR_TEXT', '(ARR-', 'easy', 1, 1, 'Legacy TAC ARR'),
('CHG_TEXT', '(CHG-', 'easy', 1, 1, 'Legacy TAC CHG'),
('CNL_TEXT', '(CNL-', 'easy', 1, 1, 'Legacy TAC CNL'),
('DLA_TEXT', '(DLA-', 'easy', 1, 1, 'Legacy TAC DLA'),
('NOTAM_TEXT', 'NOTAM ', 'easy', 1, 1, 'Legacy TAC NOTAM'),
('TAF_TEXT', 'TAF ', 'easy', 1, 1, 'Legacy TAC TAF'),
('SIGMET_TEXT', 'SIGMET ', 'easy', 1, 1, 'Legacy TAC SIGMET'),
('AIRMET_TEXT', 'AIRMET ', 'easy', 1, 1, 'Legacy TAC AIRMET'),
('GAMET_TEXT', 'GAMET ', 'easy', 1, 1, 'Legacy TAC GAMET'),
('ARS_TEXT', '(ARS-', 'easy', 1, 1, 'Legacy TAC AIREP AIREP SPECIAL'),
('ARP_TEXT', '(ARP-', 'easy', 1, 1, 'Legacy TAC AIREP'),
('VAA_TEXT', 'VAA ', 'easy', 1, 1, 'Legacy TAC Volcanic Ash Advisory'),
('TCA_TEXT', 'TCA ', 'easy', 1, 1, 'Legacy TAC Tropical Cyclone Advisory'),
('ASHTAM_TEXT', 'ASHTAM ', 'easy', 1, 1, 'Legacy TAC ASHTAM');

-- ============================================================
-- 3. cp_users — admin mặc định (CẦN ĐỊNH NGHĨA TRƯỚC ROUTING)
-- ============================================================
INSERT INTO `cp_users` (`uuid`, `username`, `password`, `role`, `created_at`) VALUES
('11111111-1111-1111-1111-111111111111', 'admin',
 '$2a$10$9rwGdXi0PX2nRAEVfQ3zKe0Y/8t2Dx6uxE4HOCjiuvA7.IofHGJzC',
 'ADMIN', NOW());

-- ============================================================
-- 4. accounts — mẫu kết nối (CẦN ĐỊNH NGHĨA TRƯỚC DISPATCH)
-- ============================================================
INSERT INTO `accounts` (`account_name`, `protocol`, `host`, `port`, `config_json`, `status`, `bind_status`, `sasl_mechanism`, `tls_enabled`, `signed_messages_action`, `unsigned_messages_action`) VALUES
('solace-broker-primary', 'AMQP', '127.0.0.1', 5672,
 '{"username":"admin","password":"admin","vpn":"default","client-id":"asg-gw-01"}',
 'ACTIVE', 'DISCONNECTED', 'PLAIN', 0, 'KEEP', 'ACCEPT'),
('isode-mswitch-mta', 'X400', '192.168.1.100', 102,
 '{"username":"mta-user","password":"mta-password","mta-name":"HAN-MTA-01"}',
 'ACTIVE', 'DISCONNECTED', 'PLAIN', 0, 'KEEP', 'ACCEPT');

-- ============================================================
-- 5. routing — dữ liệu mẫu (Sử dụng 'admin' từ cp_users)
-- ============================================================
-- ========== CHIỀU IN (SWIM → AMHS) ==========
INSERT INTO `routing` (`direction`, `receive_topic`, `message_filter`, `recipients`, `originator`, `priority`, `active`, `note`, `created_by`) VALUES
('IN', 'ats/met/metar', NULL, 'VVHHZQZX VVHHZTZX VVHHZDZX', 'VVHHZPZX', 10, 1, 'Tất cả METAR gửi cho Hanoi MET + ACC', 'admin'),
('IN', 'ats/met/taf', NULL, 'VVHHZQZX', 'VVHHZPZX', 10, 1, 'Tất cả TAF gửi cho Hanoi MET', 'admin'),
('IN', 'nm/b2b/flights', 'FPL', 'VVHHZDZX VVTSZDZX', 'VVHHZPZX', 10, 1, 'FPL gửi cho cả 2 ACC Vietnam', 'admin'),
('IN', 'ats/notam/vvhf', NULL, 'VVHHZDZX', 'VVHHZPZX', 10, 1, 'NOTAM VVHF cho Hanoi ACC', 'admin'),
('IN', 'ats/met', NULL, 'VVHHZMZX', 'VVHHZPZX', 999, 1, 'Catch-all cho MET messages', 'admin');

-- ========== CHIỀU OUT (AMHS → SWIM) ==========
INSERT INTO `routing` (`direction`, `message_type`, `send_topic`, `priority`, `active`, `note`, `created_by`) VALUES
('OUT', 'METAR', 'ats/met/metar', 10, 1, 'Gửi IWXXM METAR', 'admin'),
('OUT', 'METAR_TEXT', 'ats/met/metar', 10, 1, 'Gửi TAC METAR', 'admin'),
('OUT', 'TAF', 'ats/met/taf', 10, 1, 'Gửi IWXXM TAF', 'admin'),
('OUT', 'TAF_TEXT', 'ats/met/taf', 10, 1, 'Gửi TAC TAF', 'admin'),
('OUT', 'FPL', 'ats/fpl/vietnam', 10, 1, 'Gửi FIXM FPL', 'admin'),
('OUT', 'FPL_TEXT', 'ats/fpl/vietnam', 10, 1, 'Gửi TAC FPL', 'admin'),
('OUT', 'NOTAM', 'ats/notam', 10, 1, 'Gửi AIXM NOTAM', 'admin'),
('OUT', 'NOTAM_TEXT', 'ats/notam', 10, 1, 'Gửi TAC NOTAM', 'admin');

-- ============================================================
-- 6. gwout — vài bản ghi mẫu để test
-- ============================================================
INSERT INTO `gwout` (`priority`, `time`, `TEXT`, `origin`, `address`, `amhsid`, `ipm_id`, `filing_time`, `priority2`, `status`, `body_part_type`, `content_type`) VALUES
(4, NOW(), '<?xml version="1.0" encoding="UTF-8"?><iwxxm:METAR xmlns:iwxxm="http://icao.int/iwxxm/3.0" xmlns:aixm="http://www.aixm.aero/schema/5.1.1" xmlns:gml="http://www.opengis.net/gml/3.2" status="NORMAL"><iwxxm:aerodrome><aixm:AirportHeliport gml:id="ah-vvhh"><aixm:timeSlice><aixm:AirportHeliportTimeSlice gml:id="ahts-vvhh"><aixm:locationIndicatorICAO>VVHH</aixm:locationIndicatorICAO></aixm:AirportHeliportTimeSlice></aixm:timeSlice></aixm:AirportHeliport></iwxxm:aerodrome></iwxxm:METAR>',
 'VVHHZQZX', 'VVHHYNYX VVTSYNYX', 'VN/HAN/20260407/000001', 'VVHH.20260407.001',
 '070000', 4, 0, 'ia5-text', 'text/plain'),

(5, NOW(), '<?xml version="1.0" encoding="UTF-8"?><fx:FlightPlan xmlns:fx="http://www.fixm.aero/flight/4.3" xmlns:fb="http://www.fixm.aero/base/4.3"><fx:departure><fx:departureAerodrome><fb:locationIndicator>VVCS</fb:locationIndicator></fx:departureAerodrome></fx:departure><fx:arrival><fx:destinationAerodrome><fb:locationIndicator>VVPQ</fb:locationIndicator></fx:destinationAerodrome></fx:arrival><fx:routeTrajectory><fx:route><fx:routeText>VVCS DCT VVDN DCT VVPQ</fx:routeText></fx:route></fx:routeTrajectory></fx:FlightPlan>',
 'VVTSZQZX', 'VVTSYNYX', 'VN/SGN/20260407/000002', 'VVTS.20260407.001',
 '070030', 5, 0, 'ia5-text', 'text/plain');

-- Bật lại kiểm tra khóa ngoại
SET FOREIGN_KEY_CHECKS = 1;
