-- ============================================================
-- ASG — Seed Data
-- ============================================================
USE asg_db;

SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. gateway_config
-- ============================================================
INSERT IGNORE INTO `gateway_config` (`config_key`, `config_value`, `description`, `updated_at`) VALUES
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
('CLEANUP_ARCHIVE_AFTER_HOURS', '24', 'Số giờ trước khi archive bản ghi đã xử lý xong sang bảng history', NOW()),
('CLEANUP_RETENTION_DAYS',  '30',    'Số ngày giữ bản ghi trong bảng history trước khi xóa hẳn (EUR Doc 047 §2.5.2.2.3/§2.5.3.2.3: tối thiểu 30 ngày)', NOW()),
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
('ALLOWED_ORIGINS', 'http://192.168.22.159:5173,http://localhost:5173,http://localhost:3000', 'CORS Allowed Origins', NOW()),
('GATEWAY_IP', '0.0.0.0', 'IP lắng nghe', NOW()),
('SERVER_PORT_CP', '8180', 'Cổng Dashboard', NOW()),
('SERVER_PORT_SWIM', '8181', 'Cổng SWIM Component', NOW()),
('GATEWAY_ID', 'ASG-GW-01', 'Định danh Gateway', NOW());

-- ============================================================
-- 2. users
-- ============================================================
INSERT IGNORE INTO `users` (`id`, `username`, `password`, `email`, `full_name`, `role`, `is_active`, `created_at`, `updated_at`) VALUES
(1, 'admin', '$2a$10$9rwGdXi0PX2nRAEVfQ3zKe0Y/8t2Dx6uxE4HOCjiuvA7.IofHGJzC', 'admin@example.com', 'System Administrator', 'admin', 1, NOW(), NOW()),
(2, 'viewer', '$2a$10$9rwGdXi0PX2nRAEVfQ3zKe0Y/8t2Dx6uxE4HOCjiuvA7.IofHGJzC', 'viewer@example.com', 'Viewer User', 'viewer', 1, NOW(), NOW());

-- ============================================================
-- 3. accounts
-- ============================================================
INSERT IGNORE INTO `accounts` (`account_name`, `protocol`, `host`, `port`, `config_json`, `status`, `bind_status`) VALUES
('solace-broker-primary', 'AMQP', 'host.docker.internal', 5672,
 '{"username":"admin","password":"admin","vpn":"default"}', 'ACTIVE', 'DISCONNECTED');

-- ============================================================
-- 4. routing (Full Coverage)
-- Nội dung bản tin luôn được forward nguyên văn (EUR Doc 047: không convert
-- TAC<->JSON) — cột convert_to_json không còn được code sử dụng nên bỏ khỏi seed.
-- detect_pattern (chiều OUT) thay thế bảng message_type_registry cũ — chỉ giữ mẫu
-- TAC vì nội dung AMHS thật luôn là TAC, không bao giờ là JSON.
-- ============================================================
INSERT IGNORE INTO `routing` (`direction`, `message_type`, `detect_pattern`, `send_topic`, `priority`, `active`, `note`, `created_by`) VALUES
-- AMHS -> SWIM (OUT) - Meteorological Group
('OUT', 'METAR_TEXT',  'METAR ',    'ats/met/metar',  10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'SPECI_TEXT',  'SPECI ',    'ats/met/speci',  10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'TAF_TEXT',    'TAF ',      'ats/met/taf',    10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'SIGMET_TEXT', 'SIGMET ',   'ats/met/sigmet', 5,  1, 'AMHS -> SWIM', 'admin'),
('OUT', 'AIRMET_TEXT', 'AIRMET ',   'ats/met/airmet', 8,  1, 'AMHS -> SWIM', 'admin'),
('OUT', 'GAMET_TEXT',  'GAMET ',    'ats/met/gamet',  12, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'SNOWTAM_TEXT','(SNOWTAM',  'ats/met/snowtam', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'ASHTAM_TEXT', 'ASHTAM ',   'ats/met/ashtam', 5,  1, 'AMHS -> SWIM', 'admin'),
('OUT', 'VAA_TEXT',    'VAA ',      'ats/met/vaa',    5,  1, 'AMHS -> SWIM', 'admin'),
('OUT', 'TCA_TEXT',    'TCA ',      'ats/met/tca',    5,  1, 'AMHS -> SWIM', 'admin'),
('OUT', 'SYNOP_TEXT',  'AAXX',      'ats/met/synop',  20, 1, 'AMHS -> SWIM', 'admin'),

-- AMHS -> SWIM (OUT) - Flight Planning Group
('OUT', 'FPL_TEXT',    '(FPL-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'CHG_TEXT',    '(CHG-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'CNL_TEXT',    '(CNL-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'DLA_TEXT',    '(DLA-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'DEP_TEXT',    '(DEP-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'ARR_TEXT',    '(ARR-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'SPL_TEXT',    '(SPL-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'RQP_TEXT',    '(RQP-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'RQS_TEXT',    '(RQS-', 'ats/fpl/flightplan', 10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'DFPL_TEXT',   'DFPL',  'ats/fpl/daily',      30, 1, 'AMHS -> SWIM', 'admin'),

-- AMHS -> SWIM (OUT) - Coordination & Alerting
('OUT', 'ALR_TEXT',    '(ALR-', 'ats/alerting',       5,  1, 'Khẩn nguy',   'admin'),
('OUT', 'EST_TEXT',    '(EST-', 'ats/coordination',   15, 1, 'Phối hợp',    'admin'),
('OUT', 'CDN_TEXT',    '(CDN-', 'ats/coordination',   15, 1, 'Phối hợp',    'admin'),
('OUT', 'ACP_TEXT',    '(ACP-', 'ats/coordination',   15, 1, 'Phối hợp',    'admin'),
('OUT', 'CPL_TEXT',    '(CPL-', 'ats/coordination',   15, 1, 'Phối hợp',    'admin'),

-- AMHS -> SWIM (OUT) - Others
('OUT', 'NOTAM_TEXT',  '(',     'ats/notam',          10, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'ARP_TEXT',    '(ARP-', 'ats/airep',          15, 1, 'AMHS -> SWIM', 'admin'),
('OUT', 'ARS_TEXT',    '(ARS-', 'ats/airep',          15, 1, 'AMHS -> SWIM', 'admin'),
-- UNKNOWN: catch-all, không set detect_pattern (dùng đúng giá trị fallback có sẵn của MessageDetectService khi không khớp mẫu nào)
('OUT', 'UNKNOWN',     NULL,    'ats/generic/unknown', 255, 1, 'Catch-all', 'admin');

-- SWIM -> AMHS (IN)
INSERT IGNORE INTO `routing` (`direction`, `receive_topic`, `message_type`, `priority`, `active`, `recipients`, `note`, `created_by`) VALUES
('IN', 'ats/met/metar',  'METAR',  10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/speci',  'SPECI',  10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/taf',    'TAF',    10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/sigmet', 'SIGMET', 5,  1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/airmet', 'AIRMET', 8,  1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/gamet',  'GAMET',  12, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/ashtam', 'ASHTAM', 5,  1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/vaa',    'VAA',    5,  1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/tca',    'TCA',    5,  1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),

('IN', 'ats/fpl/flightplan', 'FPL', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/fpl/flightplan', 'CHG', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/fpl/flightplan', 'CNL', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/fpl/flightplan', 'DLA', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/fpl/flightplan', 'DEP', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/fpl/flightplan', 'ARR', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),

('IN', 'ats/notam',  'NOTAM',      10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/airep',  'ARP',        15, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/airep',  'ARS',        15, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),

-- Coordination & Alerting (IN)
('IN', 'ats/alerting',     'ALR', 5,  1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/coordination', 'EST', 15, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/coordination', 'CDN', 15, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/coordination', 'ACP', 15, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/coordination', 'CPL', 15, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),

-- Supplementary FPL (IN)
('IN', 'ats/fpl/flightplan', 'SPL', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/fpl/flightplan', 'RQP', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/fpl/flightplan', 'RQS', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),

-- Special MET (IN)
('IN', 'ats/met/snowtam', 'SNOWTAM', 10, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin'),
('IN', 'ats/met/synop',   'SYNOP',   20, 1, 'VVNBZTZX', 'SWIM -> AMHS', 'admin');

SET FOREIGN_KEY_CHECKS = 1;
