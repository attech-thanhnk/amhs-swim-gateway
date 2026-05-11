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
-- MET Messages
('OUT', 'METAR', 'ats/met/metar', 10, 1, 'Gửi IWXXM METAR', 'admin'),
('OUT', 'METAR_TEXT', 'ats/met/metar', 10, 1, 'Gửi TAC METAR', 'admin'),
('OUT', 'SPECI', 'ats/met/speci', 10, 1, 'Gửi IWXXM SPECI', 'admin'),
('OUT', 'SPECI_TEXT', 'ats/met/speci', 10, 1, 'Gửi TAC SPECI', 'admin'),
('OUT', 'TAF', 'ats/met/taf', 10, 1, 'Gửi IWXXM TAF', 'admin'),
('OUT', 'TAF_TEXT', 'ats/met/taf', 10, 1, 'Gửi TAC TAF', 'admin'),
('OUT', 'SIGMET', 'ats/met/sigmet', 10, 1, 'Gửi IWXXM SIGMET', 'admin'),
('OUT', 'SIGMET_TEXT', 'ats/met/sigmet', 10, 1, 'Gửi TAC SIGMET', 'admin'),
('OUT', 'AIRMET_TEXT', 'ats/met/airmet', 10, 1, 'Gửi TAC AIRMET', 'admin'),
('OUT', 'GAMET_TEXT', 'ats/met/gamet', 10, 1, 'Gửi TAC GAMET', 'admin'),
('OUT', 'VAA_TEXT', 'ats/met/vaa', 10, 1, 'Gửi Volcanic Ash Advisory', 'admin'),
('OUT', 'TCA_TEXT', 'ats/met/tca', 10, 1, 'Gửi Tropical Cyclone Advisory', 'admin'),
-- Flight Plan Messages
('OUT', 'FPL', 'ats/fpl/vietnam', 10, 1, 'Gửi FIXM FPL', 'admin'),
('OUT', 'FPL_TEXT', 'ats/fpl/vietnam', 10, 1, 'Gửi TAC FPL', 'admin'),
('OUT', 'CHG_TEXT', 'ats/fpl/vietnam', 10, 1, 'Gửi TAC CHG (Change)', 'admin'),
('OUT', 'CNL_TEXT', 'ats/fpl/vietnam', 10, 1, 'Gửi TAC CNL (Cancel)', 'admin'),
('OUT', 'DLA_TEXT', 'ats/fpl/vietnam', 10, 1, 'Gửi TAC DLA (Delay)', 'admin'),
('OUT', 'DEP_TEXT', 'ats/fpl/vietnam', 10, 1, 'Gửi TAC DEP (Departure)', 'admin'),
('OUT', 'ARR_TEXT', 'ats/fpl/vietnam', 10, 1, 'Gửi TAC ARR (Arrival)', 'admin'),
-- AIREP Messages
('OUT', 'ARP_TEXT', 'ats/airep', 10, 1, 'Gửi TAC AIREP', 'admin'),
('OUT', 'ARS_TEXT', 'ats/airep', 10, 1, 'Gửi TAC AIREP SPECIAL', 'admin'),
-- NOTAM Messages
('OUT', 'NOTAM', 'ats/notam', 10, 1, 'Gửi AIXM NOTAM', 'admin'),
('OUT', 'NOTAM_TEXT', 'ats/notam', 10, 1, 'Gửi TAC NOTAM', 'admin'),
('OUT', 'ASHTAM_TEXT', 'ats/notam/ashtam', 10, 1, 'Gửi TAC ASHTAM', 'admin');

-- ============================================================
-- Bật lại kiểm tra khóa ngoại
SET FOREIGN_KEY_CHECKS = 1;
