-- ============================================================
-- ASG (AMHS/SWIM Gateway) — Verified Seed Data
-- Synchronized directly with active Java Entities & System Queries
-- ============================================================
USE `asg_db`;

SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Seed data for table `gateway_config`
-- ============================================================
INSERT IGNORE INTO `gateway_config` (`config_key`, `config_value`, `description`, `updated_at`) VALUES
('ALLOW_NON_ISO646_REPERTOIRE', 'true', 'CTSW019: cho phép general-text-body-part có repertoire khác ISO 646 (true=chuyển đổi, false=từ chối)', '2026-08-26 00:00:00'),
('ALLOWED_ORIGINS', 'http://192.168.22.159:5173,http://localhost:5173,http://localhost:3000,http://192.168.22.188:3000,http://192.168.22.163:3000', 'CORS Allowed Origins', '2026-06-02 15:27:02'),
('ATSMHS_EXTENDED_CAPABLE_ADDRESSES', '', 'Danh sách địa chỉ hỗ trợ Extended ATSMHS', '2026-06-02 15:27:02'),
('ATSMHS_SERVICE_LEVEL', 'CONTENT_BASED', 'EXTENDED / BASIC / CONTENT_BASED / RECIPIENTS_BASED', '2026-06-02 15:27:02'),
('AUTHORIZED_AMHS_ADDRESSES', '', 'Whitelist địa chỉ AMHS (nếu dùng BY_LIST)', '2026-06-02 15:27:02'),
('AUTHORIZED_AMHS_PRMDS', '', 'Whitelist PRMD (nếu dùng BY_PRMD)', '2026-06-02 15:27:02'),
('AUTHORIZED_AMHS_USERS', 'ALL', 'ALL / BY_LIST / BY_PRMD', '2026-06-02 15:27:02'),
('AUTHORIZED_SWIM_ENTERPRISES', '', 'Whitelist Enterprise SWIM (nếu dùng BY_ENTERPRISE)', '2026-06-02 15:27:02'),
('AUTHORIZED_SWIM_USERS', 'ALL', 'ALL / BY_LIST / BY_ENTERPRISE', '2026-06-02 15:27:02'),
('CONVERSION_DIRECTION', 'BOTH', 'BOTH / AMHS_TO_SWIM / SWIM_TO_AMHS', '2026-06-02 15:27:02'),
('DEFAULT_ORIGINATOR_AFTN', 'VVTSSWIM', 'AFTN originator ITCU dùng khi dựng bản tin ở chiều SWIM -> AMHS', '2026-06-02 15:27:02'),
('GATEWAY_AMHS_ADDRESS', 'VVTSSWIM', 'Địa chỉ AFTN gateway dùng để NHẬN bản tin từ AMHS; bị loại khỏi amhs_recipients (§4.4.3.4.4)', '2026-08-29 00:00:00'),
('GATEWAY_ID', 'ASG-GW-01', 'Định danh Gateway', '2026-06-02 15:27:02'),
('GATEWAY_IP', '0.0.0.0', 'IP lắng nghe', '2026-06-02 15:27:02'),
('INBOUND_BATCH_SIZE', '10', 'Số lượng tin AMHS xử lý mỗi lô', '2026-06-02 15:27:02'),
('JWT_EXPIRATION_MS', '3600000', 'Thời hạn Token (ms) - Mặc định 1 giờ', '2026-06-02 15:27:02'),
('JWT_SECRET', 'asgGatewaySecretKey2026ChangeInProduction!', 'Khóa bí mật tạo JWT', '2026-06-02 15:27:02'),
('LOG_RETENTION_DAYS', '30', 'Số ngày giữ log hệ thống', '2026-06-02 15:27:02'),
('MAX_MESSAGE_RECIPIENTS', '512', 'Max recipients per message (0 = unlimited). Appendix A CTSW010/CTSW011 giả định 512', '2026-08-29 00:00:00'),
('MAX_MSG_DATA_SIZE', '2097152', 'Kích thước tin tối đa (bytes) - Mặc định 2MB', '2026-06-02 15:27:02'),
('OUTBOUND_BATCH_SIZE', '1000', 'Số lượng tin SWIM xử lý mỗi lô', '2026-06-02 15:27:02'),
('POLL_INTERVAL_MS', '500', 'Tần suất poll tin tức (ms)', '2026-06-02 15:27:02'),
('RETRY_DELAY_1ST_SECONDS', '30', 'Thời gian chờ retry lần 1 (s)', '2026-06-02 15:27:02'),
('RETRY_DELAY_2ND_SECONDS', '120', 'Thời gian chờ retry lần 2 (s)', '2026-06-02 15:27:02'),
('RETRY_DELAY_3RD_SECONDS', '300', 'Thời gian chờ retry lần 3 (s)', '2026-06-02 15:27:02'),
('RETRY_MAX_COUNT', '3', 'Số lần retry tối đa khi lỗi gửi tin', '2026-06-02 15:27:02'),
('SERVER_PORT_CP', '8180', 'Cổng Dashboard', '2026-06-02 15:27:02'),
('SERVER_PORT_SWIM', '8181', 'Cổng SWIM Component', '2026-06-02 15:27:02'),
('STRICT_COMPLIANCE_MODE', 'false', 'Bật chế độ kiểm tra EUR Doc 047 (S-06)', '2026-06-02 15:27:02');

-- ============================================================
-- Seed data for table `accounts`
-- ============================================================
INSERT IGNORE INTO `accounts` (`id`, `account_name`, `protocol`, `host`, `port`, `config_json`, `status`, `bind_status`, `certificate_path`, `certificate_passphrase`, `sasl_mechanism`, `tls_enabled`, `signed_messages_action`, `unsigned_messages_action`) VALUES
(6, 'solace-broker-primary', 'AMQP', '192.168.22.163', 5672, '{"username":"admin","password":"admin","vpn":"default"}', 'ACTIVE', 'CONNECTED', NULL, NULL, NULL, 0, NULL, NULL);

-- ============================================================
-- Seed data for table `routing`
-- ============================================================
INSERT IGNORE INTO `routing` (`id`, `direction`, `receive_topic`, `message_filter`, `recipients`, `originator`, `message_type`, `detect_pattern`, `send_topic`, `priority`, `active`, `convert_to_json`, `note`, `created_at`, `updated_at`, `created_by`, `priority_amhs`, `priority_swim`) VALUES
(1, 'OUT', NULL, NULL, NULL, NULL, 'METAR_TEXT', 'METAR ', 'ats/met/metar', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-20 08:17:54', 'admin', NULL, NULL),
(2, 'OUT', NULL, NULL, NULL, NULL, 'SPECI_TEXT', 'SPECI ', 'ats/met/speci', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(3, 'OUT', NULL, NULL, NULL, NULL, 'TAF_TEXT', 'TAF ', 'ats/met/taf', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-20 08:15:17', 'admin', NULL, NULL),
(4, 'OUT', NULL, NULL, NULL, NULL, 'SIGMET_TEXT', 'SIGMET ', 'ats/met/sigmet', 5, 1, 1, 'TAC--> IWXXM', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', '', NULL),
(5, 'OUT', NULL, NULL, NULL, NULL, 'AIRMET_TEXT', 'AIRMET ', 'ats/met/airmet', 8, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(6, 'OUT', NULL, NULL, NULL, NULL, 'GAMET_TEXT', 'GAMET ', 'ats/met/gamet', 12, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(7, 'OUT', NULL, NULL, NULL, NULL, 'SNOWTAM_TEXT', '(SNOWTAM', 'ats/met/snowtam', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(8, 'OUT', NULL, NULL, NULL, NULL, 'ASHTAM_TEXT', 'ASHTAM ', 'ats/met/ashtam', 5, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(9, 'OUT', NULL, NULL, NULL, NULL, 'VAA_TEXT', 'VAA ', 'ats/met/vaa', 5, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(10, 'OUT', NULL, NULL, NULL, NULL, 'TCA_TEXT', 'TCA ', 'ats/met/tca', 5, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(11, 'OUT', NULL, NULL, NULL, NULL, 'SYNOP_TEXT', 'AAXX', 'ats/met/synop', 20, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(12, 'OUT', NULL, NULL, NULL, NULL, 'FPL_TEXT', '(FPL-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(13, 'OUT', NULL, NULL, NULL, NULL, 'CHG_TEXT', '(CHG-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(14, 'OUT', NULL, NULL, NULL, NULL, 'CNL_TEXT', '(CNL-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(15, 'OUT', NULL, NULL, NULL, NULL, 'DLA_TEXT', '(DLA-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(16, 'OUT', NULL, NULL, NULL, NULL, 'DEP_TEXT', '(DEP-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(17, 'OUT', NULL, NULL, NULL, NULL, 'ARR_TEXT', '(ARR-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(18, 'OUT', NULL, NULL, NULL, NULL, 'SPL_TEXT', '(SPL-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(19, 'OUT', NULL, NULL, NULL, NULL, 'RQP_TEXT', '(RQP-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', '', NULL),
(20, 'OUT', NULL, NULL, NULL, NULL, 'RQS_TEXT', '(RQS-', 'ats/fpl/flightplan', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(21, 'OUT', NULL, NULL, NULL, NULL, 'DFPL_TEXT', 'DFPL', 'ats/fpl/daily', 30, 1, 0, 'Keep TAC', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(22, 'OUT', NULL, NULL, NULL, NULL, 'ALR_TEXT', '(ALR-', 'ats/alerting', 5, 1, 1, 'Khẩn nguy', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(23, 'OUT', NULL, NULL, NULL, NULL, 'EST_TEXT', '(EST-', 'ats/coordination', 15, 1, 1, 'Phối hợp', '2026-06-02 15:27:02', '2026-08-19 11:45:31', 'admin', NULL, NULL),
(24, 'OUT', NULL, NULL, NULL, NULL, 'CDN_TEXT', '(CDN-', 'ats/coordination', 15, 1, 1, 'Phối hợp', '2026-06-02 15:27:02', '2026-08-19 11:45:32', 'admin', NULL, NULL),
(25, 'OUT', NULL, NULL, NULL, NULL, 'ACP_TEXT', '(ACP-', 'ats/coordination', 15, 1, 1, 'Phối hợp', '2026-06-02 15:27:02', '2026-08-19 11:45:32', 'admin', NULL, NULL),
(26, 'OUT', NULL, NULL, NULL, NULL, 'CPL_TEXT', '(CPL-', 'ats/coordination', 15, 1, 1, 'Phối hợp', '2026-06-02 15:27:02', '2026-08-19 11:45:32', 'admin', NULL, NULL),
(27, 'OUT', NULL, NULL, NULL, NULL, 'NOTAM_TEXT', '(', 'ats/notam', 10, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:32', 'admin', NULL, NULL),
(28, 'OUT', NULL, NULL, NULL, NULL, 'ARP_TEXT', '(ARP-', 'ats/airep', 15, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:32', 'admin', NULL, NULL),
(29, 'OUT', NULL, NULL, NULL, NULL, 'ARS_TEXT', '(ARS-', 'ats/airep', 15, 1, 1, 'TAC -> JSON', '2026-06-02 15:27:02', '2026-08-19 11:45:32', 'admin', NULL, NULL),
(30, 'OUT', NULL, NULL, NULL, NULL, 'UNKNOWN', NULL, 'ats/generic/unknown', 255, 1, 0, 'Catch-all', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(32, 'IN', 'ats/met/speci', NULL, 'VVNBZTZX', '', 'SPECI', NULL, '', 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-09 17:00:11', 'admin', '', NULL),
(33, 'IN', 'ats/met/taf', NULL, 'VVNBZTZX', NULL, 'TAF', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(34, 'IN', 'ats/met/sigmet', NULL, 'VVNBZTZX', NULL, 'SIGMET', NULL, NULL, 5, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(35, 'IN', 'ats/met/airmet', NULL, 'VVNBZTZX', NULL, 'AIRMET', NULL, NULL, 8, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(36, 'IN', 'ats/met/gamet', NULL, 'VVNBZTZX', NULL, 'GAMET', NULL, NULL, 12, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(37, 'IN', 'ats/met/ashtam', NULL, 'VVNBZTZX', NULL, 'ASHTAM', NULL, NULL, 5, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(38, 'IN', 'ats/met/vaa', NULL, 'VVNBZTZX', NULL, 'VAA', NULL, NULL, 5, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(39, 'IN', 'ats/met/tca', NULL, 'VVNBZTZX', NULL, 'TCA', NULL, NULL, 5, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(40, 'IN', 'ats/fpl/flightplan', NULL, 'VVTSSWIM', '', 'FPL', NULL, '', 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-08-12 08:42:18', 'admin', '', NULL),
(41, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'CHG', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(42, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'CNL', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(43, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'DLA', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(44, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'DEP', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(45, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'ARR', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(46, 'IN', 'ats/notam', NULL, 'VVNBZTZX', NULL, 'NOTAM', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(47, 'IN', 'ats/airep', NULL, 'VVNBZTZX', NULL, 'ARP', NULL, NULL, 15, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(48, 'IN', 'ats/airep', NULL, 'VVNBZTZX', NULL, 'ARS', NULL, NULL, 15, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(49, 'IN', 'ats/alerting', NULL, 'VVNBZTZX', NULL, 'ALR', NULL, NULL, 5, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(50, 'IN', 'ats/coordination', NULL, 'VVNBZTZX', NULL, 'EST', NULL, NULL, 15, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(51, 'IN', 'ats/coordination', NULL, 'VVNBZTZX', NULL, 'CDN', NULL, NULL, 15, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(52, 'IN', 'ats/coordination', NULL, 'VVNBZTZX', NULL, 'ACP', NULL, NULL, 15, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(53, 'IN', 'ats/coordination', NULL, 'VVNBZTZX', NULL, 'CPL', NULL, NULL, 15, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(54, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'SPL', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(55, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'RQP', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(56, 'IN', 'ats/fpl/flightplan', NULL, 'VVNBZTZX', NULL, 'RQS', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(57, 'IN', 'ats/met/snowtam', NULL, 'VVNBZTZX', NULL, 'SNOWTAM', NULL, NULL, 10, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL),
(58, 'IN', 'ats/met/synop', NULL, 'VVNBZTZX', NULL, 'SYNOP', NULL, NULL, 20, 1, 0, 'JSON -> AMHS', '2026-06-02 15:27:02', '2026-06-02 15:27:02', 'admin', NULL, NULL);

-- ============================================================
-- Seed data for table `users`
-- ============================================================
INSERT IGNORE INTO `users` (`id`, `avatar`, `created_at`, `email`, `full_name`, `is_active`, `last_login_at`, `last_login_ip`, `password`, `role`, `updated_at`, `username`) VALUES
(1, NULL, '2026-06-12 10:12:21', 'admin@example.com', 'System Administrator', 'b''\\x01''', '2026-08-20 08:34:32.581075', NULL, '$2a$10$bq9bkDdzN7Ylb5fYOdKkrOX9J9ERlln77JmTqC/Lg9f27af6hX0H2', 'admin', '2026-06-12 10:12:21', 'admin'),
(2, NULL, '2026-06-12 10:12:21', 'viewer@example.com', 'Viewer User', 'b''\\x01''', NULL, NULL, '1', 'viewer', '2026-06-12 10:12:21', 'viewer');

-- ============================================================
-- Seed data for table `server_info`
-- ============================================================
INSERT IGNORE INTO `server_info` (`uuid`, `description`, `ip_address`, `server_name`, `version`) VALUES
('04a8ec63-6b19-4a6e-99d4-7e492b8e1a3b', 'ASG Gateway Parent Project Version 1', '192.168.22.160', 'Admin-PC', '@project.parent.version@'),
('088265ad-ac69-4842-bd85-ab0dafc804d8', 'ASG Gateway Parent Project Version 1', '192.168.0.100', 'DESKTOP-GTO8DR8', '1.0.0'),
('38e494b7-c7d0-4aaf-9600-d2f167a8f96c', 'ASG Gateway Parent Project Version 1', '172.19.0.4', 'a971936bce62', '1.0.0'),
('5fe1dea9-bf61-44fa-a19e-c4d89b7199f7', 'ASG Gateway Parent Project Version 1', '172.21.0.3', 'add031879b2c', '1.0.0'),
('8c4d2b33-41a4-4199-9c5d-799610fe8a34', 'ASG Gateway Parent Project Version 1', '192.168.0.103', 'Admin-PC', '1.0.0'),
('9671746c-9bd9-4c07-9798-9ddf559e4a60', 'ASG Gateway Parent Project Version 1', '192.168.0.103', 'Admin-PC', '@project.parent.version@'),
('99c73c94-442c-4e3b-a662-ec89e5b57c32', 'ASG Gateway Parent Project Version 1', '192.168.0.100', 'DESKTOP-GTO8DR8', '@project.parent.version@'),
('a39acb48-18b2-48b4-b677-b648cdb7af75', 'ASG Gateway Parent Project Version 1', '192.168.22.160', 'DESKTOP-GTO8DR8', '1.0.0'),
('a5e05449-74a5-4c4f-8642-223f0febad29', 'ASG Gateway Parent Project Version 1', '172.19.0.2', '694b85e6d98b', '1.0.0'),
('e8247835-0739-47b6-9597-fa365447ec88', 'ASG Gateway Parent Project Version 1', '172.19.0.3', '525e6f4d68d1', '1.0.0'),
('f153f2d9-1885-4798-918a-3fe7e6322b70', 'ASG Gateway Parent Project Version 1', '172.21.0.4', 'ad62a12519d3', '1.0.0'),
('f3f09f69-6b76-4d7e-8f9e-d4cb01a7a417', 'ASG Gateway Parent Project Version 1', '127.0.1.1', 'DESKTOP-Q23EH77', '1.0.0');
