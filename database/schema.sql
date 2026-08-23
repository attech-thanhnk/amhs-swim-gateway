-- ============================================================
-- ASG (AMHS/SWIM Gateway) — Verified Database Schema
-- Synchronized directly with active Java Entities & System Queries
-- ============================================================

CREATE DATABASE IF NOT EXISTS `asg_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `asg_db`;

SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Bảng `accounts`
-- ============================================================
DROP TABLE IF EXISTS `accounts`;
CREATE TABLE `accounts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `protocol` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `host` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `port` int(11) DEFAULT NULL,
  `config_json` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bind_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `certificate_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `certificate_passphrase` text COLLATE utf8mb4_unicode_ci,
  `sasl_mechanism` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tls_enabled` tinyint(1) DEFAULT '0',
  `signed_messages_action` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unsigned_messages_action` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `account_name` (`account_name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `gateway_config`
-- ============================================================
DROP TABLE IF EXISTS `gateway_config`;
CREATE TABLE `gateway_config` (
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `gw_alert`
-- ============================================================
DROP TABLE IF EXISTS `gw_alert`;
CREATE TABLE `gw_alert` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `alert_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `ref_table` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ref_id` bigint(20) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'NEW',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `acknowledged_at` datetime DEFAULT NULL,
  `acknowledged_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=477 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `gwin`
-- ============================================================
DROP TABLE IF EXISTS `gwin`;
CREATE TABLE `gwin` (
  `msgid` bigint(20) NOT NULL AUTO_INCREMENT,
  `address` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `addressing_source` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `atsmhs_service_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amhs_recipients` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amqp_properties` text COLLATE utf8mb4_unicode_ci,
  `body_type` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cpa` varchar(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_type` int(11) DEFAULT NULL,
  `message_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `origin` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_content` mediumtext COLLATE utf8mb4_unicode_ci,
  `priority` tinyint(4) DEFAULT NULL,
  `source` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `subject` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text` mediumtext COLLATE utf8mb4_unicode_ci,
  `time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  UNIQUE KEY `UK_4fnpvr4qibr3cidpyiei72g3h` (`message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=532 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `gwin_dispatch`
-- ============================================================
DROP TABLE IF EXISTS `gwin_dispatch`;
CREATE TABLE `gwin_dispatch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `amhs_address` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `failed_step` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gwin_id` bigint(20) NOT NULL,
  `last_error` text COLLATE utf8mb4_unicode_ci,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `retry_count` int(11) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `gwout`
-- ============================================================
DROP TABLE IF EXISTS `gwout`;
CREATE TABLE `gwout` (
  `msgid` bigint(20) NOT NULL AUTO_INCREMENT,
  `address` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amhs_delivery_report` bit(1) DEFAULT NULL,
  `amhs_priority` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amhs_registered_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amhs_ttl` datetime(6) DEFAULT NULL,
  `amhsid` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amqp_message_id` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_part_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_part_charset` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Repertoire general-text-body-part: ISO-646 hoß║Àc ISO-8859-1 (EUR Doc 047 ┬º4.4.3.4.9)',
  `ftbp_file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ftbp_object_size` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ftbp_last_mod` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_type` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `number_of_attachment` int(11) DEFAULT NULL,
  `origin_eit` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `x400_content_type` int(11) DEFAULT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_type` int(11) DEFAULT NULL,
  `filing_time` varchar(6) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ipm_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message_signed` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `optional_heading` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `origin` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_content` mediumtext COLLATE utf8mb4_unicode_ci,
  `rejection_diagnostic` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rejection_reason` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `swim_priority` int(11) DEFAULT NULL,
  `text` mediumtext COLLATE utf8mb4_unicode_ci,
  `time` datetime(6) DEFAULT NULL,
  `subject` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`msgid`)
) ENGINE=InnoDB AUTO_INCREMENT=58479 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `gwout_dispatch`
-- ============================================================
DROP TABLE IF EXISTS `gwout_dispatch`;
CREATE TABLE `gwout_dispatch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `amqp_account` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `failed_step` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gwout_id` bigint(20) NOT NULL,
  `last_error` text COLLATE utf8mb4_unicode_ci,
  `message_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `recipient` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retry_count` int(11) NOT NULL,
  `scope` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `topic` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=135 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `message_conversion_log`
-- ============================================================
DROP TABLE IF EXISTS `message_conversion_log`;
CREATE TABLE `message_conversion_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `reference_id` bigint(20) DEFAULT NULL,
  `date` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(4) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(12) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message_id` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ipm_id` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mts_id` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amqp_message_id` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` varchar(12) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ohi` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `origin` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `filing_time` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `subject` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `converted_time` datetime DEFAULT NULL,
  `status` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action_taken` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `non_delivery_reason` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `non_delivery_diagnostic` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `supplementary_info` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=110426 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `mtcu_tmp`
-- ============================================================
DROP TABLE IF EXISTS `mtcu_tmp`;
CREATE TABLE `mtcu_tmp` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `OriginError` varchar(255) DEFAULT NULL,
  `alternativeRecipienAllowed` bit(1) DEFAULT NULL,
  `atsExtention` bit(1) DEFAULT NULL,
  `atsFilingTime` varchar(255) DEFAULT NULL,
  `atsOhi` varchar(255) DEFAULT NULL,
  `atsPriority` varchar(255) DEFAULT NULL,
  `bodyPartCharacterSet` varchar(255) DEFAULT NULL,
  `bodyPartType` int(11) DEFAULT NULL,
  `content` longtext,
  `contentCorrelatorString` varchar(255) DEFAULT NULL,
  `contentId` varchar(255) DEFAULT NULL,
  `contentType` int(11) DEFAULT NULL,
  `conversionWithLossProhibited` bit(1) DEFAULT NULL,
  `dlExpansionProhibited` bit(1) DEFAULT NULL,
  `extended` bit(1) DEFAULT NULL,
  `implicitConversionProhibited` bit(1) DEFAULT NULL,
  `ipmId` varchar(255) DEFAULT NULL,
  `messageId` varchar(255) DEFAULT NULL,
  `numberOfAttachment` int(11) DEFAULT NULL,
  `numberOfDLAddress` int(11) DEFAULT NULL,
  `orAddress` varchar(255) DEFAULT NULL,
  `orderMessage` int(11) DEFAULT NULL,
  `originEncodeInformationType` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `sumissionTime` varchar(255) DEFAULT NULL,
  `ftbpFileName` varchar(255) DEFAULT NULL,
  `ftbpObjectSize` varchar(20) DEFAULT NULL,
  `ftbpLastMod` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `orderMessage` (`orderMessage`)
) ENGINE=InnoDB AUTO_INCREMENT=226005 DEFAULT CHARSET=latin1;

-- ============================================================
-- Bảng `mtcu_to`
-- ============================================================
DROP TABLE IF EXISTS `mtcu_to`;
CREATE TABLE `mtcu_to` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `mtaReportRequest` int(11) DEFAULT NULL,
  `phankenhduocaftn` bit(1) NOT NULL,
  `receiptNotification` int(11) DEFAULT NULL,
  `reportRequest` int(11) DEFAULT NULL,
  `receiveMessage_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_46hkt85ow6q0wki3tm0kawxrw` (`receiveMessage_id`),
  CONSTRAINT `FK_46hkt85ow6q0wki3tm0kawxrw` FOREIGN KEY (`receiveMessage_id`) REFERENCES `mtcu_tmp` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=118461 DEFAULT CHARSET=latin1;

-- ============================================================
-- Bảng `performance_metrics`
-- ============================================================
DROP TABLE IF EXISTS `performance_metrics`;
CREATE TABLE `performance_metrics` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `timestamp` datetime DEFAULT NULL,
  `cpu_usage` float DEFAULT NULL,
  `heap_memory` float DEFAULT NULL,
  `msg_in_count` int(11) DEFAULT NULL,
  `msg_out_count` int(11) DEFAULT NULL,
  `active_threads` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=51160 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `routing`
-- ============================================================
DROP TABLE IF EXISTS `routing`;
CREATE TABLE `routing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `direction` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `receive_topic` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message_filter` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipients` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `originator` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detect_pattern` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Mß║½u nhß║¡n diß╗çn loß║íi bß║ún tin tß╗½ nß╗Öi dung th├┤ (chß╗ë ├íp dß╗Ñng direction=OUT)',
  `send_topic` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` int(11) DEFAULT '100',
  `active` tinyint(1) DEFAULT '1',
  `convert_to_json` tinyint(1) DEFAULT '0',
  `note` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority_amhs` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority_swim` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `server_info`
-- ============================================================
DROP TABLE IF EXISTS `server_info`;
CREATE TABLE `server_info` (
  `uuid` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `server_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `system_history`
-- ============================================================
DROP TABLE IF EXISTS `system_history`;
CREATE TABLE `system_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_time` datetime NOT NULL,
  `event_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=193 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `system_log`
-- ============================================================
DROP TABLE IF EXISTS `system_log`;
CREATE TABLE `system_log` (
  `uuid` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `timestamp` datetime DEFAULT NULL,
  `level` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `user_system_history`
-- ============================================================
DROP TABLE IF EXISTS `user_system_history`;
CREATE TABLE `user_system_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `is_read` bit(1) DEFAULT NULL,
  `system_history_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=531 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Bảng `users`
-- ============================================================
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `last_login_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('admin','viewer') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_r43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
