-- ============================================================
-- ASG — Seed Data
-- ============================================================
USE asg_db;

SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;

-- ============================================================
-- gateway_config defaults
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
('DEFAULT_ORIGINATOR_AFTN', 'VVHHZPZX', 'AFTN originator mặc định. Ví dụ: VVHHZPZX', NOW()),
('DEFAULT_RECIPIENTS_INBOUND', 'VVHHZTZX', 'Recipients mặc định khi không resolve được', NOW()),
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
-- message_type_registry — nhận diện điện văn
-- ============================================================
INSERT INTO `message_type_registry` (`message_type`, `detect_pattern`, `scope_source`, `difficulty`, `phase`, `active`, `note`) VALUES
('METAR', '<iwxxm:METAR', 'body_xml', 'easy', 1, 1, 'IWXXM METAR'),
('FPL', '<fx:FlightPlan', 'body_xml', 'easy', 1, 1, 'FIXM Flight Plan'),
('NOTAM', '<aixm:Event', 'body_xml', 'medium', 1, 1, 'AIXM NOTAM'),
('TAF', '<iwxxm:TAF', 'body_xml', 'easy', 1, 1, 'IWXXM TAF'),
('SIGMET', '<iwxxm:SIGMET', 'body_xml', 'medium', 1, 1, 'IWXXM SIGMET'),
('SPECI', '<iwxxm:SPECI', 'body_xml', 'easy', 1, 1, 'IWXXM SPECI'),
('METAR_TEXT', 'METAR ', 'fixed', 'easy', 1, 1, 'Legacy TAC METAR'),
('SPECI_TEXT', 'SPECI ', 'fixed', 'easy', 1, 1, 'Legacy TAC SPECI'),
('FPL_TEXT', '(FPL-', 'fixed', 'easy', 1, 1, 'Legacy TAC FPL'),
('DEP_TEXT', '(DEP-', 'fixed', 'easy', 1, 1, 'Legacy TAC DEP'),
('ARR_TEXT', '(ARR-', 'fixed', 'easy', 1, 1, 'Legacy TAC ARR'),
('CHG_TEXT', '(CHG-', 'fixed', 'easy', 1, 1, 'Legacy TAC CHG'),
('CNL_TEXT', '(CNL-', 'fixed', 'easy', 1, 1, 'Legacy TAC CNL'),
('DLA_TEXT', '(DLA-', 'fixed', 'easy', 1, 1, 'Legacy TAC DLA'),
('NOTAM_TEXT', 'NOTAM ', 'fixed', 'easy', 1, 1, 'Legacy TAC NOTAM'),
('TAF_TEXT', 'TAF ', 'fixed', 'easy', 1, 1, 'Legacy TAC TAF'),
('SIGMET_TEXT', 'SIGMET ', 'fixed', 'easy', 1, 1, 'Legacy TAC SIGMET');

-- ============================================================
-- routing — dữ liệu mẫu
-- ============================================================
INSERT INTO `routing` (`direction`, `message_type`, `amhs_address`, `send_queue`, `priority`, `active`, `created_at`, `updated_at`) VALUES
('OUT', 'METAR', 'VVHHYNYX', 'ats/met/vn', 10, 1, NOW(), NOW()),
('OUT', 'METAR', 'VVTSYNYX', 'ats/met/vn', 10, 1, NOW(), NOW()),
('OUT', 'FPL',   'VVHHZPZX', 'ats/atfm/vn', 10, 1, NOW(), NOW()),
('OUT', 'FPL',   'VVTSZPZX', 'ats/atfm/vn', 10, 1, NOW(), NOW()),
('OUT', 'NOTAM', 'VVNBZQZX', 'ats/atfm/vn', 10, 1, NOW(), NOW()),
('OUT', 'METAR_TEXT', 'VVHHZQZX', 'ats/met/vn', 10, 1, NOW(), NOW()),
('OUT', 'FPL_TEXT',   'VVHHZPZX', 'ats/atfm/vn', 10, 1, NOW(), NOW()),
('OUT', 'SPECI',      NULL,      'ats/met/vn', 100, 1, NOW(), NOW()),
('OUT', 'TAF',        NULL,      'ats/met/vn', 100, 1, NOW(), NOW()),
('OUT', 'SIGMET',     NULL,      'ats/met/vn', 100, 1, NOW(), NOW()),
('OUT', 'SPECI_TEXT', NULL,      'ats/met/vn', 100, 1, NOW(), NOW()),
('OUT', 'TAF_TEXT',   NULL,      'ats/met/vn', 100, 1, NOW(), NOW()),
('OUT', 'SIGMET_TEXT', NULL,     'ats/met/vn', 100, 1, NOW(), NOW()),
('OUT', 'DEP_TEXT',   NULL,      'ats/atfm/vn', 100, 1, NOW(), NOW()),
('OUT', 'ARR_TEXT',   NULL,      'ats/atfm/vn', 100, 1, NOW(), NOW()),
('OUT', 'CHG_TEXT',   NULL,      'ats/atfm/vn', 100, 1, NOW(), NOW()),
('OUT', 'CNL_TEXT',   NULL,      'ats/atfm/vn', 100, 1, NOW(), NOW()),
('OUT', 'DLA_TEXT',   NULL,      'ats/atfm/vn', 100, 1, NOW(), NOW()),
('OUT', 'NOTAM_TEXT', NULL,      'ats/atfm/vn', 100, 1, NOW(), NOW());

-- ============================================================
-- cp_users — admin mặc định (password: Admin@123)
-- BCrypt hash của "Admin@123"
-- ============================================================
INSERT INTO `cp_users` (`uuid`, `username`, `password`, `role`, `created_at`) VALUES
('11111111-1111-1111-1111-111111111111', 'admin',
 '$2a$10$9rwGdXi0PX2nRAEVfQ3zKe0Y/8t2Dx6uxE4HOCjiuvA7.IofHGJzC',
 'ADMIN', NOW());

-- ============================================================
-- accounts — mẫu 1 account Solace
-- ============================================================
INSERT INTO `accounts` (`account_name`, `protocol`, `host`, `port`, `config_json`, `status`, `bind_status`, `sasl_mechanism`, `tls_enabled`) VALUES
('solace-broker-1', 'AMQP', '192.168.22.159', 5672,
 '{"username":"admin","password":"admin","vpn":"default"}',
 'ACTIVE', 'DISCONNECTED', 'PLAIN', 0);

-- ============================================================
-- gwout — vài bản ghi mẫu để test
-- ============================================================
INSERT INTO `gwout` (`priority`, `time`, `TEXT`, `origin`, `address`, `amhsid`, `ipm_id`, `filing_time`, `priority2`, `status`, `body_part_type`, `content_type`) VALUES
(4, NOW(), '<?xml version="1.0" encoding="UTF-8"?><iwxxm:METAR xmlns:iwxxm="http://icao.int/iwxxm/3.0" xmlns:aixm="http://www.aixm.aero/schema/5.1.1" xmlns:gml="http://www.opengis.net/gml/3.2" status="NORMAL"><iwxxm:aerodrome><aixm:AirportHeliport gml:id="ah-vvhh"><aixm:timeSlice><aixm:AirportHeliportTimeSlice gml:id="ahts-vvhh"><aixm:locationIndicatorICAO>VVHH</aixm:locationIndicatorICAO></aixm:AirportHeliportTimeSlice></aixm:timeSlice></aixm:AirportHeliport></iwxxm:aerodrome></iwxxm:METAR>',
 'VVHHZQZX', 'VVHHYNYX VVTSYNYX', 'VN/HAN/20260407/000001', 'VVHH.20260407.001',
 '070000', 4, 0, 'ia5-text', 'text/plain'),

(5, NOW(), '<?xml version="1.0" encoding="UTF-8"?><fx:FlightPlan xmlns:fx="http://www.fixm.aero/flight/4.3" xmlns:fb="http://www.fixm.aero/base/4.3"><fx:departure><fx:departureAerodrome><fb:locationIndicator>VVCS</fb:locationIndicator></fx:departureAerodrome></fx:departure><fx:arrival><fx:destinationAerodrome><fb:locationIndicator>VVPQ</fb:locationIndicator></fx:destinationAerodrome></fx:arrival><fx:routeTrajectory><fx:route><fx:routeText>VVCS DCT VVDN DCT VVPQ</fx:routeText></fx:route></fx:routeTrajectory></fx:FlightPlan>',
 'VVTSZQZX', 'VVTSYNYX', 'VN/SGN/20260407/000002', 'VVTS.20260407.001',
 '070030', 5, 0, 'ia5-text', 'text/plain');

-- ============================================================
-- icao_fir_mapping
-- ============================================================
INSERT INTO `icao_fir_mapping` (`icao_airport`, `icao_fir`, `airport_name`, `country`) VALUES
-- QUỐC TẾ KHU VỰC --
('RJTT', 'RJJJ', 'Tokyo Haneda',              'Japan'),
('RJAA', 'RJJJ', 'Tokyo Narita',              'Japan'),
('RKSI', 'RKRR', 'Incheon',                   'South Korea'),
('VDPP', 'VDPP', 'Phnom Penh',                'Cambodia'),
('VLVT', 'VLVT', 'Wattay',                    'Laos'),
('VTBD', 'VTBB', 'Don Mueang',                'Thailand'),
('VTBS', 'VTBB', 'Suvarnabhumi',              'Thailand'),
('WSSS', 'WSJC', 'Singapore Changi',          'Singapore'),
('ZGGG', 'ZGZU', 'Guangzhou',                 'China'),

-- VIỆT NAM: HANOI FIR (Lý thuyết từ vĩ tuyến 15 trở ra Bắc) --
('VVNB', 'VVHN', 'Noi Bai (Hanoi)',           'Vietnam'),
('VVHH', 'VVHN', 'Hanoi ATC (Originator)',    'Vietnam'),
('VVDN', 'VVHN', 'Da Nang',                   'Vietnam'),
('VVCI', 'VVHN', 'Cat Bi (Hai Phong)',        'Vietnam'),
('VVVD', 'VVHN', 'Van Don (Quang Ninh)',      'Vietnam'),
('VVPB', 'VVHN', 'Phu Bai (Hue)',             'Vietnam'),
('VVVH', 'VVHN', 'Vinh (Nghe An)',            'Vietnam'),
('VVDB', 'VVHN', 'Dien Bien Phu',             'Vietnam'),
('VVTX', 'VVHN', 'Tho Xuan (Thanh Hoa)',      'Vietnam'),
('VVDH', 'VVHN', 'Dong Hoi',                  'Vietnam'),
('VVCA', 'VVHN', 'Chu Lai',                   'Vietnam'),

-- VIỆT NAM: HO CHI MINH FIR (Lý thuyết từ vĩ tuyến 15 trở vào Nam) --
('VVTS', 'VVHM', 'Tan Son Nhat (HCMC)',       'Vietnam'),
('VVCR', 'VVHM', 'Cam Ranh (Nha Trang)',      'Vietnam'),
('VVPQ', 'VVHM', 'Phu Quoc',                  'Vietnam'),
('VVCT', 'VVHM', 'Can Tho',                   'Vietnam'),
('VVBM', 'VVHM', 'Buon Ma Thuot',             'Vietnam'),
('VVDL', 'VVHM', 'Lien Khuong (Da Lat)',      'Vietnam'),
('VVPK', 'VVHM', 'Pleiku',                    'Vietnam'),
('VVPC', 'VVHM', 'Phu Cat (Quy Nhon)',        'Vietnam'),
('VVTH', 'VVHM', 'Tuy Hoa',                   'Vietnam'),
('VVCS', 'VVHM', 'Con Dao',                   'Vietnam'),
('VVRG', 'VVHM', 'Rach Gia',                  'Vietnam'),
('VVCM', 'VVHM', 'Ca Mau',                    'Vietnam');

-- ============================================================
-- fir_units
-- AFTN addresses theo ICAO Doc 7474 / AIP Vietnam
-- ============================================================
INSERT INTO `fir_units` (`fir_code`, `unit_type`, `aftn_address`, `role`) VALUES
('VVHN', 'ACC', 'VVHHZTZX', 'primary'),
('VVHN', 'MET', 'VVHHZQZX', 'primary'),
('VVHN', 'ARO', 'VVHHZPZX', 'primary'),
('VVHN', 'COM', 'VVHHZCZX', 'primary'),
('VVHM', 'ACC', 'VVTSZDYX', 'primary'),
('VVHM', 'MET', 'VVTSZQZX', 'primary'),
('VVHM', 'ARO', 'VVTSZPZX', 'primary'),
('VVHM', 'COM', 'VVTSZEZX', 'primary');

-- ============================================================
-- message_unit_mapping
-- ============================================================
INSERT INTO `message_unit_mapping` (`message_type`, `requires_unit_types`) VALUES
('ARR',     'ACC,ARO'),
('CHG',     'ACC,ARO'),
('CNL',     'ACC,ARO'),
('DEP',     'ACC,ARO'),
('FPL',     'ACC,ARO'),
('METAR',   'MET,ACC'),
('NOTAM',   'ACC,ARO,COM'),
('SIGMET',  'MET,ACC'),
('SNOWTAM', 'ACC,ARO,COM'),
('SPECI',   'MET,ACC'),
('TAF',     'MET,ACC');

-- ============================================================
-- distribution_list
-- ============================================================
INSERT INTO `distribution_list` (`list_name`, `recipients`, `message_types`, `scope`, `description`) VALUES
('VVHH_METAR',  'VVHHZQZX VVHHZTZX VVTSZDYX', 'METAR,SPECI', 'VVHH', 'METAR/SPECI Nội Bài'),
('VVTS_METAR',  'VVTSZQZX VVTSZDYX',           'METAR,SPECI', 'VVTS', 'METAR/SPECI Tân Sơn Nhất'),
('VVDN_METAR',  'VVDNZQZX VVDNZDYX',           'METAR,SPECI', 'VVDN', 'METAR/SPECI Đà Nẵng'),
('VVHH_TAF',    'VVHHZQZX VVHHZTZX',           'TAF',         'VVHH', 'TAF Nội Bài'),
('VVTS_TAF',    'VVTSZQZX VVTSZDYX',           'TAF',         'VVTS', 'TAF Tân Sơn Nhất'),
('VVHN_SIGMET', 'VVHHZQZX VVTSZDYX',           'SIGMET',      'VVHN', 'SIGMET FIR Hà Nội (VVHN)'),
('VVHM_SIGMET', 'VVTSZQZX VVHHZTZX',           'SIGMET',      'VVHM', 'SIGMET FIR HCM (VVHM)'),
('VVHN_FPL',    'VVHHZTZX VVHHZPZX',           'FPL,CHG,CNL,DEP,ARR', 'VVHN', 'FPL FIR Hà Nội (VVHN)'),
('VVHM_FPL',    'VVTSZDYX VVTSZPZX',           'FPL,CHG,CNL,DEP,ARR', 'VVHM', 'FPL FIR HCM (VVHM)'),
('VVHN_NOTAM',  'VVHHZTZX VVHHZPZX VVHHZQZX', 'NOTAM',       'VVHN', 'NOTAM FIR Hà Nội (VVHN)'),
('VVHM_NOTAM',  'VVTSZDYX VVTSZPZX VVTSZQZX', 'NOTAM',       'VVHM', 'NOTAM FIR HCM (VVHM)'),
('VN_ALL_ACC',  'VVHHZTZX VVTSZDYX',           NULL,          'GLOBAL', 'Tất cả ACC Việt Nam'),
('VN_ALL_MET',  'VVHHZQZX VVTSZQZX',           'METAR,TAF,SIGMET', 'GLOBAL', 'Tất cả MET offices Việt Nam');

-- ============================================================
-- fir_prefix_mapping
-- ============================================================
INSERT INTO `fir_prefix_mapping` (`icao_prefix`, `icao_fir`, `description`) VALUES
('VV',   'VVHN', 'Hanoi FIR (Mặc định cho toàn bộ VV)'),
('VVT',  'VVHM', 'Ho Chi Minh FIR (Tân Sơn Nhất / Nam Trung Bộ)'),
('VVC',  'VVHM', 'Ho Chi Minh FIR (Cam Ranh / Côn Đảo)'),
('VVPQ', 'VVHM', 'Ho Chi Minh FIR (Phú Quốc)'),
('VVCT', 'VVHM', 'Ho Chi Minh FIR (Cần Thơ)'),
('VT',   'VTBB', 'Bangkok FIR (Thailand)'),
('VD',   'VDPP', 'Phnom Penh FIR (Cambodia)'),
('VY',   'VYYF', 'Yangon FIR (Myanmar)'),
('VL',   'VLVT', 'Vientiane FIR (Laos)'),
('VH',   'VHHK', 'Hong Kong FIR'),
('VM',   'VHHK', 'Macau (thuộc Hong Kong FIR)'),
('WS',   'WSJC', 'Singapore FIR'),
('WM',   'WMFC', 'Kuala Lumpur FIR (Peninsular Malaysia)'),
('WB',   'WBFC', 'Kota Kinabalu FIR (Borneo/Sabah/Brunei)'),
('WI',   'WIIF', 'Jakarta FIR (Indonesia tây/bắc)'),
('WA',   'WAAC', 'Ujung Pandang FIR (Indonesia đông)'),
('RP',   'RPHI', 'Manila FIR (Philippines)'),
('RC',   'RCAA', 'Taipei FIR (Taiwan)'),
('RJ',   'RJJJ', 'Fukuoka FIR (Japan)'),
('RK',   'RKRR', 'Incheon FIR (South Korea)'),
('ZB',   'ZBPE', 'Beijing FIR'),
('ZS',   'ZSHA', 'Shanghai FIR'),
('ZG',   'ZGZU', 'Guangzhou FIR'),
('ZJ',   'ZJSA', 'Sanya FIR (Hải Nam - Biển Đông)'),
('ZP',   'ZPPP', 'Kunming FIR (Vân Nam)'),
('ZH',   'ZHWH', 'Wuhan FIR'),
('ZY',   'ZYYY', 'Shenyang FIR'),
('ZL',   'ZLHW', 'Lanzhou FIR'),
('ZW',   'ZWWW', 'Urumqi FIR'),
('VN',   'VNKT', 'Kathmandu FIR (Nepal)'),
('VI',   'VIDF', 'Delhi FIR (India bắc)'),
('VE',   'VECF', 'Kolkata FIR (India đông)'),
('VO',   'VOMF', 'Mumbai/Chennai FIR (India tây/nam)');
