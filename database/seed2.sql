-- ============================================================
-- ASG — Seed Data (Full 1:1 Symmetry TAC & JSON)
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
-- 2. message_type_registry - Đồng bộ 1:1 (JSON vs TAC)
-- ============================================================
INSERT IGNORE INTO `message_type_registry` (`message_type`, `detect_pattern`, `active`, `note`) VALUES
-- Modern JSON Standards (SWIM Side)
('METAR', '"messageType":"METAR"', 1, 'JSON METAR'),
('SPECI', '"messageType":"SPECI"', 1, 'JSON SPECI'),
('TAF', '"messageType":"TAF"', 1, 'JSON TAF'),
('SIGMET', '"messageType":"SIGMET"', 1, 'JSON SIGMET'),
('AIRMET', '"messageType":"AIRMET"', 1, 'JSON AIRMET'),
('GAMET', '"messageType":"GAMET"', 1, 'JSON GAMET'),
('FPL', '"messageType":"FPL"', 1, 'JSON Flight Plan'),
('CHG', '"messageType":"CHG"', 1, 'JSON Change'),
('CNL', '"messageType":"CNL"', 1, 'JSON Cancel'),
('DLA', '"messageType":"DLA"', 1, 'JSON Delay'),
('DEP', '"messageType":"DEP"', 1, 'JSON Departure'),
('ARR', '"messageType":"ARR"', 1, 'JSON Arrival'),
('NOTAM', '"messageType":"NOTAM"', 1, 'JSON NOTAM'),
('ASHTAM', '"messageType":"ASHTAM"', 1, 'JSON ASHTAM'),
('VAA', '"messageType":"VAA"', 1, 'JSON Volcanic Ash Advisory'),
('TCA', '"messageType":"TCA"', 1, 'JSON Tropical Cyclone Advisory'),
('ARP', '"messageType":"ARP"', 1, 'JSON AIREP'),
('ARS', '"messageType":"ARS"', 1, 'JSON AIREP Special'),
('ALR', '"messageType":"ALR"', 1, 'JSON Alerting'),
('EST', '"messageType":"EST"', 1, 'JSON Estimate'),
('CDN', '"messageType":"CDN"', 1, 'JSON Coordination'),
('ACP', '"messageType":"ACP"', 1, 'JSON Acceptance'),
('SPL', '"messageType":"SPL"', 1, 'JSON Supplementary FPL'),
('RQP', '"messageType":"RQP"', 1, 'JSON Request FPL'),
('RQS', '"messageType":"RQS"', 1, 'JSON Request Supplementary FPL'),
('CPL', '"messageType":"CPL"', 1, 'JSON Current FPL'),
('SNOWTAM', '"messageType":"SNOWTAM"', 1, 'JSON SNOWTAM'),
('SYNOP', '"messageType":"SYNOP"', 1, 'JSON SYNOP'),
('DFPL', '"messageType":"DFPL"', 1, 'JSON Daily FPL');

-- Legacy TAC Standards (AMHS Side)
INSERT IGNORE INTO `message_type_registry` (`message_type`, `detect_pattern`, `active`, `note`) VALUES
('METAR_TEXT', 'METAR ', 1, 'TAC METAR'),
('SPECI_TEXT', 'SPECI ', 1, 'TAC SPECI'),
('TAF_TEXT', 'TAF ', 1, 'TAC TAF'),
('SIGMET_TEXT', 'SIGMET ', 1, 'TAC SIGMET'),
('AIRMET_TEXT', 'AIRMET ', 1, 'TAC AIRMET'),
('GAMET_TEXT', 'GAMET ', 1, 'TAC GAMET'),
('FPL_TEXT', '(FPL-', 1, 'TAC FPL'),
('CHG_TEXT', '(CHG-', 1, 'TAC CHG'),
('CNL_TEXT', '(CNL-', 1, 'TAC CNL'),
('DLA_TEXT', '(DLA-', 1, 'TAC DLA'),
('DEP_TEXT', '(DEP-', 1, 'TAC DEP'),
('ARR_TEXT', '(ARR-', 1, 'TAC ARR'),
('NOTAM_TEXT', '(', 1, 'TAC NOTAM'),
('ASHTAM_TEXT', 'ASHTAM ', 1, 'TAC ASHTAM'),
('VAA_TEXT', 'VAA ', 1, 'TAC VAA'),
('TCA_TEXT', 'TCA ', 1, 'TAC TCA'),
('ARP_TEXT', '(ARP-', 1, 'TAC AIREP'),
('ARS_TEXT', '(ARS-', 1, 'TAC AIREP Special'),
('ALR_TEXT', '(ALR-', 1, 'Alerting Message (TAC)'),
('EST_TEXT', '(EST-', 1, 'Estimate Message (TAC)'),
('CDN_TEXT', '(CDN-', 1, 'Coordination Message (TAC)'),
('ACP_TEXT', '(ACP-', 1, 'Acceptance Message (TAC)'),
('SPL_TEXT', '(SPL-', 1, 'Supplementary Flight Plan (TAC)'),
('RQP_TEXT', '(RQP-', 1, 'Request Flight Plan (TAC)'),
('RQS_TEXT', '(RQS-', 1, 'Request Supplementary Flight Plan (TAC)'),
('CPL_TEXT', '(CPL-', 1, 'Current Flight Plan (TAC)'),
('SNOWTAM_TEXT', '(SNOWTAM', 1, 'Snowtam Message (TAC)'),
('SYNOP_TEXT', 'AAXX', 1, 'Synoptic Report (TAC)'),
('DFPL_TEXT', 'DFPL', 1, 'Daily Flight Plan Report'),
('UNKNOWN', 'UNKNOWN', 1, 'Loại không xác định');

-- ============================================================
-- 3. users
-- ============================================================
INSERT IGNORE INTO `users` (`id`, `username`, `password`, `email`, `full_name`, `role`, `is_active`, `created_at`, `updated_at`) VALUES
(1, 'admin', '$2a$10$9rwGdXi0PX2nRAEVfQ3zKe0Y/8t2Dx6uxE4HOCjiuvA7.IofHGJzC', 'admin@example.com', 'System Administrator', 'admin', 1, NOW(), NOW()),
(2, 'viewer', '$2a$10$9rwGdXi0PX2nRAEVfQ3zKe0Y/8t2Dx6uxE4HOCjiuvA7.IofHGJzC', 'viewer@example.com', 'Viewer User', 'viewer', 1, NOW(), NOW());

-- ============================================================
-- 4. accounts
-- ============================================================
INSERT IGNORE INTO `accounts` (`account_name`, `protocol`, `host`, `port`, `config_json`, `status`, `bind_status`) VALUES
('solace-broker-primary', 'AMQP', 'host.docker.internal', 5672,
 '{"username":"admin","password":"admin","vpn":"default"}', 'ACTIVE', 'DISCONNECTED');

-- ============================================================
-- 5. routing (Updated with new schema)
-- ============================================================

-- AMHS -> SWIM (OUT) - Meteorological Group
INSERT IGNORE INTO `routing` (`direction`, `message_type`, `send_topic`, `priority_swim`, `active`, `note`, `created_by`, `created_at`, `updated_at`) VALUES
('OUT', 'METAR_TEXT',  'ats/met/metar',  10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'SPECI_TEXT',  'ats/met/speci',  10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'TAF_TEXT',    'ats/met/taf',    10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'SIGMET_TEXT', 'ats/met/sigmet', 5,  1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'AIRMET_TEXT', 'ats/met/airmet', 8,  1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'GAMET_TEXT',  'ats/met/gamet',  12, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'SNOWTAM_TEXT','ats/met/snowtam', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'ASHTAM_TEXT', 'ats/met/ashtam', 5,  1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'VAA_TEXT',    'ats/met/vaa',    5,  1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'TCA_TEXT',    'ats/met/tca',    5,  1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'SYNOP_TEXT',  'ats/met/synop',  20, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),

-- AMHS -> SWIM (OUT) - Flight Planning Group
('OUT', 'FPL_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'CHG_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'CNL_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'DLA_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'DEP_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'ARR_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'SPL_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'RQP_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'RQS_TEXT',    'ats/fpl/flightplan', 10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'DFPL_TEXT',   'ats/fpl/daily',      30, 1, 'Keep TAC',    'admin', NOW(), NOW()),

-- AMHS -> SWIM (OUT) - Coordination & Alerting
('OUT', 'ALR_TEXT',    'ats/alerting',       5,  1, 'Khẩn nguy',   'admin', NOW(), NOW()),
('OUT', 'EST_TEXT',    'ats/coordination',   15, 1, 'Phối hợp',    'admin', NOW(), NOW()),
('OUT', 'CDN_TEXT',    'ats/coordination',   15, 1, 'Phối hợp',    'admin', NOW(), NOW()),
('OUT', 'ACP_TEXT',    'ats/coordination',   15, 1, 'Phối hợp',    'admin', NOW(), NOW()),
('OUT', 'CPL_TEXT',    'ats/coordination',   15, 1, 'Phối hợp',    'admin', NOW(), NOW()),

-- AMHS -> SWIM (OUT) - Others
('OUT', 'NOTAM_TEXT',  'ats/notam',          10, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'ARP_TEXT',    'ats/airep',          15, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'ARS_TEXT',    'ats/airep',          15, 1, 'TAC -> JSON', 'admin', NOW(), NOW()),
('OUT', 'UNKNOWN',     'ats/generic/unknown', 255, 1, 'Catch-all', 'admin', NOW(), NOW());

-- SWIM -> AMHS (IN)
INSERT IGNORE INTO `routing` (`direction`, `receive_topic`, `message_type`, `recipients`, `priority_amhs`, `active`, `note`, `created_by`, `created_at`, `updated_at`) VALUES
('IN', 'ats/met/metar',  'METAR',  'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/speci',  'SPECI',  'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/taf',    'TAF',    'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/sigmet', 'SIGMET', 'VVNBZTZX', 'HIGH',   1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/airmet', 'AIRMET', 'VVNBZTZX', 'HIGH',   1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/gamet',  'GAMET',  'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/ashtam', 'ASHTAM', 'VVNBZTZX', 'HIGH',   1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/vaa',    'VAA',    'VVNBZTZX', 'HIGH',   1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/tca',    'TCA',    'VVNBZTZX', 'HIGH',   1, 'JSON -> AMHS', 'admin', NOW(), NOW()),

('IN', 'ats/fpl/flightplan', 'FPL', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/fpl/flightplan', 'CHG', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/fpl/flightplan', 'CNL', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/fpl/flightplan', 'DLA', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/fpl/flightplan', 'DEP', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/fpl/flightplan', 'ARR', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),

('IN', 'ats/notam',  'NOTAM',  'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/airep',  'ARP',    'VVNBZTZX', 'LOW',    1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/airep',  'ARS',    'VVNBZTZX', 'LOW',    1, 'JSON -> AMHS', 'admin', NOW(), NOW()),

-- Coordination & Alerting (IN)
('IN', 'ats/alerting',     'ALR', 'VVNBZTZX', 'HIGH',   1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/coordination', 'EST', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/coordination', 'CDN', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/coordination', 'ACP', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/coordination', 'CPL', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),

-- Supplementary FPL (IN)
('IN', 'ats/fpl/flightplan', 'SPL', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/fpl/flightplan', 'RQP', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/fpl/flightplan', 'RQS', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),

-- Special MET (IN)
('IN', 'ats/met/snowtam', 'SNOWTAM', 'VVNBZTZX', 'NORMAL', 1, 'JSON -> AMHS', 'admin', NOW(), NOW()),
('IN', 'ats/met/synop',   'SYNOP',   'VVNBZTZX', 'LOW',    1, 'JSON -> AMHS', 'admin', NOW(), NOW());

SET FOREIGN_KEY_CHECKS = 1;
