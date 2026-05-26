-- ============================================================
-- ASG (AMHS/SWIM Gateway) — Database Schema (Synchronized)
-- ============================================================

CREATE DATABASE IF NOT EXISTS asg_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE asg_db;

SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Bảng gwout: AMHS Component ghi, SWIM Component đọc/cập nhật
-- ============================================================
DROP TABLE IF EXISTS `gwout`;
CREATE TABLE `gwout` (
  `msgid`                BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `priority`             TINYINT(4)    DEFAULT NULL,
  `time`                 DATETIME      DEFAULT NULL,
  `TEXT`                 VARCHAR(3200) DEFAULT NULL,
  `origin`               VARCHAR(8)    DEFAULT NULL,
  `address`              VARCHAR(250)  DEFAULT NULL,
  `optional_heading`     VARCHAR(60)   DEFAULT NULL,
  `amhs_ttl`             DATETIME      DEFAULT NULL,
  `amhs_registered_id`   VARCHAR(200)  DEFAULT NULL,
  `amhsid`               VARCHAR(200)  DEFAULT NULL,
  `ipm_id`               VARCHAR(200)  DEFAULT NULL,
  `filing_time`          VARCHAR(6)    DEFAULT NULL,
  `priority2`            INT(11)       DEFAULT NULL,
  `status`               INT(20)       DEFAULT NULL,
  `amqp_message_id`      VARCHAR(256)  DEFAULT NULL,
  `body_type`            VARCHAR(10)   DEFAULT 'text',
  `body_part_type`       VARCHAR(50)   DEFAULT NULL,
  `content_type`         VARCHAR(100)  DEFAULT NULL,
  `message_signed`       VARCHAR(20)   DEFAULT NULL,
  `rejection_reason`     VARCHAR(64)   DEFAULT NULL,
  `rejection_diagnostic` VARCHAR(64)   DEFAULT NULL,
  `amhs_delivery_report` TINYINT(1)    DEFAULT 0,
  `retry_count`          INT           DEFAULT 0,
  `last_retry_at`        DATETIME      DEFAULT NULL,
  `payload_content`      MEDIUMTEXT    DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  KEY `priority2` (`priority2`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwout_dispatch
-- ============================================================
DROP TABLE IF EXISTS `gwout_dispatch`;
CREATE TABLE `gwout_dispatch` (
  `id`                   BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `gwout_id`             BIGINT(20)    NOT NULL,
  `recipient`            VARCHAR(100)  NOT NULL,
  `message_type`         VARCHAR(50)   DEFAULT NULL,
  `scope`                VARCHAR(10)   DEFAULT NULL,
  `topic`                VARCHAR(100)  DEFAULT NULL,
  `amqp_account`         VARCHAR(50)   DEFAULT NULL,
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  `retry_count`          INT           NOT NULL DEFAULT 0,
  `next_retry_at`        DATETIME      DEFAULT NULL,
  `last_error`           TEXT          DEFAULT NULL,
  `failed_step`          VARCHAR(20)   DEFAULT NULL,
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_at`              DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_gwout_dispatch_msg` FOREIGN KEY (`gwout_id`) REFERENCES `gwout` (`msgid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin: SWIM Component ghi, AMHS Component đọc
-- ============================================================
DROP TABLE IF EXISTS `gwin`;
CREATE TABLE `gwin` (
  `cpa`                  VARCHAR(1)    DEFAULT 'N',
  `msgid`                BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `priority`             TINYINT(4)    DEFAULT NULL,
  `time`                 DATETIME      DEFAULT NULL,
  `xml_payload`          MEDIUMTEXT    DEFAULT NULL,
  `TEXT`                 MEDIUMTEXT    DEFAULT NULL,
  `source`               VARCHAR(200)  DEFAULT NULL,
  `subject`              VARCHAR(100)  DEFAULT NULL,
  `amqp_properties`      TEXT          DEFAULT NULL,
  `body_type`            VARCHAR(10)   DEFAULT 'text',
  `origin`               VARCHAR(200)  DEFAULT NULL,
  `message_id`           VARCHAR(255)  UNIQUE DEFAULT NULL,
  `address`              VARCHAR(250)  DEFAULT NULL,
  `status`               INT(20)       DEFAULT NULL,
  `amqp_message_id`      VARCHAR(256)  DEFAULT NULL,
  `content_type`         VARCHAR(100)  DEFAULT NULL,
  `originator`           VARCHAR(128)  DEFAULT NULL,
  `addressing_source`    VARCHAR(200)  DEFAULT NULL,
  `rejection_reason`     VARCHAR(64)   DEFAULT NULL,
  `rejection_diagnostic` VARCHAR(64)   DEFAULT NULL,
  `retry_count`          INT           DEFAULT 0,
  `last_retry_at`        DATETIME      DEFAULT NULL,
  `payload_content`      MEDIUMTEXT    DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin_dispatch
-- ============================================================
DROP TABLE IF EXISTS `gwin_dispatch`;
CREATE TABLE `gwin_dispatch` (
  `id`                   BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `gwin_id`              BIGINT(20)    NOT NULL,
  `amhs_address`         VARCHAR(100)  NOT NULL,
  `amhs_account`         VARCHAR(50)   DEFAULT NULL,
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  `retry_count`          INT           NOT NULL DEFAULT 0,
  `next_retry_at`        DATETIME      DEFAULT NULL,
  `last_error`           TEXT          DEFAULT NULL,
  `failed_step`          VARCHAR(20)   DEFAULT NULL,
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_at`              DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_gwin_dispatch_msg` FOREIGN KEY (`gwin_id`) REFERENCES `gwin` (`msgid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng routing (Đã thêm convert_to_json)
-- ============================================================
DROP TABLE IF EXISTS `routing`;
CREATE TABLE `routing` (
    `id`               INT           PRIMARY KEY AUTO_INCREMENT,
    `direction`        VARCHAR(3)    NOT NULL,
    `receive_topic`    VARCHAR(100)  DEFAULT NULL,
    `message_filter`   VARCHAR(100)  DEFAULT NULL,
    `recipients`       VARCHAR(500)  DEFAULT NULL,
    `originator`       VARCHAR(8)    DEFAULT NULL,
    `message_type`     VARCHAR(50)   DEFAULT NULL,
    `send_topic`       VARCHAR(100)  DEFAULT NULL,
    `priority`         INT           DEFAULT 100,
    `active`           BOOLEAN       DEFAULT TRUE,
    `convert_to_json`  BOOLEAN       DEFAULT FALSE,
    `note`             TEXT          DEFAULT NULL,
    `created_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`       VARCHAR(50)   DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_type_registry (Đã xóa difficulty/phase)
-- ============================================================
DROP TABLE IF EXISTS `message_type_registry`;
CREATE TABLE `message_type_registry` (
  `id`             BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `message_type`   VARCHAR(50)  NOT NULL UNIQUE,
  `detect_pattern` VARCHAR(255) NOT NULL,
  `active`         TINYINT(1)   NOT NULL DEFAULT 1,
  `note`           VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Các bảng hỗ trợ khác (Config, Account, Users...)
-- ============================================================
DROP TABLE IF EXISTS `gateway_config`;
CREATE TABLE `gateway_config` (
  `config_key`   VARCHAR(100) PRIMARY KEY,
  `config_value` VARCHAR(500),
  `description`  VARCHAR(500),
  `updated_at`   DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `accounts`;
CREATE TABLE `accounts` (
  `id`                       BIGINT(20)   PRIMARY KEY AUTO_INCREMENT,
  `account_name`             VARCHAR(50)  UNIQUE,
  `protocol`                 VARCHAR(20),
  `host`                     VARCHAR(255),
  `port`                     INTEGER,
  `config_json`              TEXT,
  `status`                   VARCHAR(20),
  `bind_status`              VARCHAR(20),
  `certificate_path`         VARCHAR(500),
  `certificate_passphrase`   TEXT,
  `sasl_mechanism`           VARCHAR(20),
  `tls_enabled`              TINYINT(1)   DEFAULT 0,
  `signed_messages_action`   VARCHAR(30),
  `unsigned_messages_action` VARCHAR(30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `cp_users`;
CREATE TABLE `cp_users` (
  `uuid`         VARCHAR(36)  PRIMARY KEY,
  `username`     VARCHAR(50)  NOT NULL UNIQUE,
  `password`     VARCHAR(255) NOT NULL,
  `role`         VARCHAR(20)  DEFAULT 'USER',
  `created_at`   DATETIME,
  `last_login`   DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `gw_alert`;
CREATE TABLE `gw_alert` (
  `id`               BIGINT(20) PRIMARY KEY AUTO_INCREMENT,
  `alert_type`       VARCHAR(30) NOT NULL,
  `severity`         VARCHAR(10) NOT NULL,
  `message`          TEXT NOT NULL,
  `ref_table`        VARCHAR(50),
  `ref_id`           BIGINT(20),
  `status`           VARCHAR(20) DEFAULT 'NEW',
  `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP,
  `acknowledged_at`  DATETIME,
  `acknowledged_by`  VARCHAR(100),
  `resolved_at`      DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng system_log (SWIM Component ghi log hệ thống)
-- ============================================================
DROP TABLE IF EXISTS `system_log`;
CREATE TABLE `system_log` (
  `uuid`      VARCHAR(36) PRIMARY KEY,
  `timestamp` DATETIME,
  `level`     VARCHAR(10),
  `module`    VARCHAR(30),
  `content`   TEXT,
  `status`    VARCHAR(10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng performance_metrics (Thống kê hiệu năng định kỳ)
-- ============================================================
DROP TABLE IF EXISTS `performance_metrics`;
CREATE TABLE `performance_metrics` (
  `id`             BIGINT(20) PRIMARY KEY AUTO_INCREMENT,
  `timestamp`      DATETIME,
  `cpu_usage`      FLOAT,
  `heap_memory`    FLOAT,
  `msg_in_count`   INT,
  `msg_out_count`  INT,
  `active_threads` INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_archive (Lưu trữ tin nhắn dài hạn - 30 ngày)
-- ============================================================
DROP TABLE IF EXISTS `message_archive`;
CREATE TABLE `message_archive` (
  `uuid`              VARCHAR(36) PRIMARY KEY,
  `msg_id`            VARCHAR(100),
  `mts_id`            VARCHAR(100),
  `ipm_id`            VARCHAR(100),
  `amqp_message_id`   VARCHAR(256),
  `recipients`        TEXT,
  `priority`          VARCHAR(2),
  `direction`         VARCHAR(20),
  `timestamp`         DATETIME,
  `raw_content`       TEXT,
  `processing_status` VARCHAR(20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_conversion_log (Nhật ký chuyển đổi tin nhắn)
-- ============================================================
DROP TABLE IF EXISTS `message_conversion_log`;
CREATE TABLE `message_conversion_log` (
  `id`                      BIGINT(20) PRIMARY KEY AUTO_INCREMENT,
  `reference_id`            BIGINT(20),
  `date`                    VARCHAR(8),
  `type`                    VARCHAR(4),
  `category`                VARCHAR(12),
  `message_id`              VARCHAR(256),
  `ipm_id`                  VARCHAR(256),
  `mts_id`                  VARCHAR(256),
  `amqp_message_id`         VARCHAR(256),
  `priority`                VARCHAR(12),
  `ohi`                     VARCHAR(64),
  `origin`                  VARCHAR(128),
  `filing_time`             VARCHAR(20),
  `subject`                 VARCHAR(512),
  `content`                 TEXT,
  `converted_time`          DATETIME,
  `status`                  VARCHAR(8),
  `action_taken`            VARCHAR(50),
  `non_delivery_reason`     VARCHAR(64),
  `non_delivery_diagnostic` VARCHAR(64),
  `supplementary_info`      VARCHAR(512),
  `remark`                  VARCHAR(1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
