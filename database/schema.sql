-- ============================================================
-- ASG (AMHS/SWIM Gateway) — Synchronized Database Schema
-- MySQL 5.7+ / 8.x Compatible
-- ============================================================

CREATE DATABASE IF NOT EXISTS `asg_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `asg_db`;

SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;

-- ============================================================
-- Bảng accounts: Quản lý kết nối AMQP / X400
-- ============================================================
CREATE TABLE IF NOT EXISTS `accounts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_name` varchar(50) DEFAULT NULL,
  `protocol` varchar(20) DEFAULT NULL,
  `host` varchar(255) DEFAULT NULL,
  `port` int(11) DEFAULT NULL,
  `config_json` text,
  `status` varchar(20) DEFAULT NULL,
  `bind_status` varchar(20) DEFAULT NULL,
  `certificate_path` varchar(500) DEFAULT NULL,
  `certificate_passphrase` text,
  `sasl_mechanism` varchar(20) DEFAULT NULL,
  `tls_enabled` tinyint(1) DEFAULT '0',
  `signed_messages_action` varchar(30) DEFAULT NULL,
  `unsigned_messages_action` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `account_name` (`account_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gateway_config: Cấu hình hệ thống động
-- ============================================================
CREATE TABLE IF NOT EXISTS `gateway_config` (
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(500) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gw_alert: Lưu cảnh báo hệ thống
-- ============================================================
CREATE TABLE IF NOT EXISTS `gw_alert` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `alert_type` varchar(30) NOT NULL,
  `severity` varchar(10) NOT NULL,
  `message` text NOT NULL,
  `ref_table` varchar(50) DEFAULT NULL,
  `ref_id` bigint(20) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'NEW',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `acknowledged_at` datetime DEFAULT NULL,
  `acknowledged_by` varchar(100) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin: Lưu điện văn INBOUND nhận từ SWIM AMQP
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwin` (
  `cpa` varchar(1) DEFAULT 'N',
  `msgid` bigint(20) NOT NULL AUTO_INCREMENT,
  `priority` tinyint(4) DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  `xml_payload` mediumtext,
  `TEXT` mediumtext,
  `source` varchar(200) DEFAULT NULL,
  `subject` varchar(128) DEFAULT NULL,
  `amqp_properties` text,
  `body_type` varchar(10) DEFAULT 'text',
  `origin` varchar(200) DEFAULT NULL,
  `message_id` varchar(255) DEFAULT NULL,
  `address` varchar(1000) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `amqp_message_id` varchar(256) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `originator` varchar(128) DEFAULT NULL,
  `addressing_source` varchar(200) DEFAULT NULL,
  `rejection_reason` varchar(64) DEFAULT NULL,
  `rejection_diagnostic` varchar(64) DEFAULT NULL,
  `retry_count` int(11) DEFAULT '0',
  `last_retry_at` datetime DEFAULT NULL,
  `payload_content` mediumtext,
  `error_type` int(11) DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  UNIQUE KEY `message_id` (`message_id`),
  KEY `idx_status` (`status`),
  KEY `idx_gwin_status_priority_time` (`status`, `priority`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin_dispatch: Lệnh gửi vào AMHS cho từng recipient của gwin
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwin_dispatch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `gwin_id` bigint(20) NOT NULL,
  `amhs_address` varchar(100) NOT NULL,
  `amhs_account` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int(11) NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error` text,
  `failed_step` varchar(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_gwin_dispatch_msg` (`gwin_id`),
  KEY `idx_gwin_dispatch_status_retry` (`status`, `next_retry_at`),
  CONSTRAINT `fk_gwin_dispatch_msg` FOREIGN KEY (`gwin_id`) REFERENCES `gwin` (`msgid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwout: Lưu điện văn OUTBOUND nhận từ AMHS
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwout` (
  `msgid` bigint(20) NOT NULL AUTO_INCREMENT,
  `priority` int(11) DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  `TEXT` mediumtext DEFAULT NULL,
  `origin` varchar(200) DEFAULT NULL,
  `address` varchar(1000) DEFAULT NULL,
  `optional_heading` varchar(60) DEFAULT NULL,
  `amhs_ttl` datetime DEFAULT NULL,
  `amhs_registered_id` varchar(200) DEFAULT NULL,
  `amhsid` varchar(200) DEFAULT NULL,
  `ipm_id` varchar(200) DEFAULT NULL,
  `filing_time` varchar(6) DEFAULT NULL,
  `priority2` int(11) DEFAULT NULL,
  `status` int(11) NOT NULL DEFAULT 0,
  `amqp_message_id` varchar(256) DEFAULT NULL,
  `body_type` varchar(10) DEFAULT 'text',
  `body_part_type` varchar(50) DEFAULT NULL,
  `body_part_charset` varchar(20) DEFAULT NULL,
  `ftbp_file_name` varchar(255) DEFAULT NULL,
  `ftbp_object_size` varchar(20) DEFAULT NULL,
  `ftbp_last_mod` varchar(20) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `message_signed` varchar(20) DEFAULT NULL,
  `rejection_reason` varchar(64) DEFAULT NULL,
  `rejection_diagnostic` varchar(64) DEFAULT NULL,
  `amhs_delivery_report` tinyint(1) DEFAULT '0',
  `retry_count` int(11) DEFAULT '0',
  `last_retry_at` datetime DEFAULT NULL,
  `payload_content` mediumtext,
  `error_type` int(11) DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  KEY `priority2` (`priority2`),
  KEY `idx_status` (`status`),
  KEY `idx_gwout_status_priority_time` (`status`, `priority`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwout_dispatch: Lệnh gửi lên AMQP broker cho từng recipient
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwout_dispatch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `gwout_id` bigint(20) NOT NULL,
  `recipient` varchar(100) NOT NULL,
  `message_type` varchar(50) DEFAULT NULL,
  `scope` varchar(10) DEFAULT NULL,
  `topic` varchar(100) DEFAULT NULL,
  `amqp_account` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int(11) NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error` text,
  `failed_step` varchar(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_gwout_dispatch_msg` (`gwout_id`),
  KEY `idx_gwout_dispatch_status_retry` (`status`, `next_retry_at`),
  CONSTRAINT `fk_gwout_dispatch_msg` FOREIGN KEY (`gwout_id`) REFERENCES `gwout` (`msgid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_conversion_log: Lưu trữ kết quả convert điện văn
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_conversion_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `reference_id` bigint(20) DEFAULT NULL,
  `date` varchar(8) DEFAULT NULL,
  `type` varchar(4) DEFAULT NULL,
  `category` varchar(12) DEFAULT NULL,
  `message_id` varchar(256) DEFAULT NULL,
  `ipm_id` varchar(256) DEFAULT NULL,
  `mts_id` varchar(256) DEFAULT NULL,
  `amqp_message_id` varchar(256) DEFAULT NULL,
  `priority` varchar(12) DEFAULT NULL,
  `ohi` varchar(64) DEFAULT NULL,
  `origin` varchar(128) DEFAULT NULL,
  `filing_time` varchar(20) DEFAULT NULL,
  `subject` varchar(512) DEFAULT NULL,
  `content` text,
  `converted_time` datetime DEFAULT NULL,
  `status` varchar(8) DEFAULT NULL,
  `action_taken` varchar(255) DEFAULT NULL,
  `non_delivery_reason` varchar(64) DEFAULT NULL,
  `non_delivery_diagnostic` varchar(64) DEFAULT NULL,
  `supplementary_info` varchar(512) DEFAULT NULL,
  `remark` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng performance_metrics: Thống kê hiệu năng định kỳ
-- ============================================================
CREATE TABLE IF NOT EXISTS `performance_metrics` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `timestamp` datetime DEFAULT NULL,
  `cpu_usage` float DEFAULT NULL,
  `heap_memory` float DEFAULT NULL,
  `msg_in_count` int(11) DEFAULT NULL,
  `msg_out_count` int(11) DEFAULT NULL,
  `active_threads` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng routing: Cấu hình ánh xạ / định tuyến AFTN ↔ SWIM
-- ============================================================
CREATE TABLE IF NOT EXISTS `routing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `direction` varchar(3) NOT NULL,
  `receive_topic` varchar(100) DEFAULT NULL,
  `message_filter` varchar(100) DEFAULT NULL,
  `recipients` varchar(500) DEFAULT NULL,
  `originator` varchar(8) DEFAULT NULL,
  `message_type` varchar(50) DEFAULT NULL,
  `detect_pattern` varchar(255) DEFAULT NULL,
  `send_topic` varchar(100) DEFAULT NULL,
  `priority` int(11) DEFAULT '100',
  `active` tinyint(1) DEFAULT '1',
  `convert_to_json` tinyint(1) DEFAULT '0',
  `note` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(50) DEFAULT NULL,
  `priority_amhs` varchar(255) DEFAULT NULL,
  `priority_swim` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng server_info: Thông tin phiên bản & IP của cụm máy chủ
-- ============================================================
CREATE TABLE IF NOT EXISTS `server_info` (
  `uuid` varchar(36) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `ip_address` varchar(255) DEFAULT NULL,
  `server_name` varchar(255) DEFAULT NULL,
  `version` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng system_history: Ghi nhận sự kiện hệ thống
-- ============================================================
CREATE TABLE IF NOT EXISTS `system_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_time` datetime NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `severity` varchar(20) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` text,
  `created_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng system_log: Log hệ thống ghi nhận từ SWIM Component
-- ============================================================
CREATE TABLE IF NOT EXISTS `system_log` (
  `uuid` varchar(36) NOT NULL,
  `timestamp` datetime DEFAULT NULL,
  `level` varchar(10) DEFAULT NULL,
  `module` varchar(30) DEFAULT NULL,
  `content` text,
  `status` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng user_system_history: Quản lý trạng thái đọc log của user
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_system_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `is_read` bit(1) DEFAULT NULL,
  `system_history_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng users: Danh sách người dùng hệ thống CP
-- ============================================================
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `avatar` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(100) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `last_login_ip` varchar(45) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('admin','viewer') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_r43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Tạo các bảng lịch sử (History) và các Index tối ưu hiệu năng
-- ============================================================

-- 1. Tạo bảng lịch sử cho gwout nếu chưa tồn tại
CREATE TABLE IF NOT EXISTS `gwout_history` LIKE `gwout`;

-- 2. Tạo bảng lịch sử cho gwout_dispatch nếu chưa tồn tại
CREATE TABLE IF NOT EXISTS `gwout_dispatch_history` LIKE `gwout_dispatch`;

-- 3. Tạo bảng lịch sử cho gwin nếu chưa tồn tại
CREATE TABLE IF NOT EXISTS `gwin_history` LIKE `gwin`;

