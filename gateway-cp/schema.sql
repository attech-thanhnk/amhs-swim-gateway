-- ============================================================
-- ASG (AMHS/SWIM Gateway) — Database Schema
-- MySQL 8.x
-- ============================================================

CREATE DATABASE IF NOT EXISTS asg_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE asg_db;

-- Fix Vietnamese character encoding when running on Windows/custom clients
SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;

-- ============================================================
-- Bảng gwout: AMHS Component ghi, SWIM Component đọc/cập nhật
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwout` (
  `msgid`                BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `priority`             TINYINT(4)    DEFAULT NULL COMMENT 'ATS priority số (sau ánh xạ)',
  `time`                 DATETIME      DEFAULT NULL COMMENT 'Thời điểm nhận từ AMHS',
  `TEXT`                 VARCHAR(3200) DEFAULT NULL COMMENT 'Nội dung điện văn (ATS message text)',
  `origin`               VARCHAR(8)    DEFAULT NULL COMMENT 'Địa chỉ AFTN người gửi (8 ký tự)',
  `address`              VARCHAR(250)  DEFAULT NULL COMMENT 'Địa chỉ AFTN người nhận',
  `optional_heading`     VARCHAR(60)   DEFAULT NULL COMMENT 'OHI - Optional Heading Information',
  `amhs_ttl`             DATETIME      DEFAULT NULL,
  `amhs_registered_id`   VARCHAR(200)  DEFAULT NULL,
  `amhsid`               VARCHAR(200)  DEFAULT NULL COMMENT 'MTS-Identifier của điện văn AMHS',
  `ipm_id`               VARCHAR(200)  DEFAULT NULL COMMENT 'IPM-Identifier',
  `filing_time`          VARCHAR(6)    DEFAULT NULL COMMENT 'ATS Filing Time (DDhhmm)',
  `priority2`            INT(11)       DEFAULT NULL COMMENT 'AMQP numeric priority (0-9) sau ánh xạ',
  `status`               INT(20)       DEFAULT NULL COMMENT '0=NEW 1=PROCESSING 2=SENT 3=ERROR 4=REJECTED',
  `amqp_message_id`      VARCHAR(256)  DEFAULT NULL COMMENT 'message-id trả về từ broker',
  `body_type`            VARCHAR(10)   DEFAULT 'text',
  `body_part_type`       VARCHAR(50)   DEFAULT NULL COMMENT 'ia5-text / file-transfer-body-part / ...',
  `content_type`         VARCHAR(100)  DEFAULT NULL COMMENT 'text/plain hoặc application/octet-stream',
  `message_signed`       VARCHAR(20)   DEFAULT NULL COMMENT 'signed / unsigned / invalid-signature',
  `rejection_reason`     VARCHAR(64)   DEFAULT NULL,
  `rejection_diagnostic` VARCHAR(64)   DEFAULT NULL,
  `amhs_delivery_report` TINYINT(1)    DEFAULT 0 COMMENT '0=no, 1=yes',
  `retry_count`          INT           DEFAULT 0,
  `last_retry_at`        DATETIME      DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  KEY `priority2` (`priority2`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwout_dispatch: Từng lệnh publish lên AMQP
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwout_dispatch` (
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
  KEY `idx_gwout_id` (`gwout_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin: SWIM Component ghi, AMHS Component đọc
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwin` (
  `cpa`                  VARCHAR(1)    NOT NULL DEFAULT 'N',
  `msgid`                BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `priority`             TINYINT(4)    DEFAULT NULL COMMENT 'AMQP priority (0-9)',
  `time`                 DATETIME      DEFAULT NULL COMMENT 'Thời điểm nhận từ broker',
  `TEXT`                 VARCHAR(3200) DEFAULT NULL COMMENT 'Nội dung điện văn (amqp-value)',
  `source`               VARCHAR(200)  DEFAULT NULL COMMENT 'Tên queue/topic nhận được',
  `subject`              VARCHAR(100)  DEFAULT NULL,
  `amqp_properties`      TEXT          DEFAULT NULL,
  `body_type`            VARCHAR(10)   DEFAULT 'text',
  `origin`               VARCHAR(200)  DEFAULT NULL,
  `message_id`           VARCHAR(255)  UNIQUE DEFAULT NULL,
  `address`              VARCHAR(250)  DEFAULT NULL COMMENT 'Địa chỉ AFTN người nhận',
  `status`               INT(20)       DEFAULT NULL COMMENT '0=NEW 1=PROCESSING 2=SENT 3=ERROR 4=REJECTED',
  `amqp_message_id`      VARCHAR(256)  DEFAULT NULL COMMENT 'message-id từ AMQP properties',
  `content_type`         VARCHAR(100)  DEFAULT NULL COMMENT 'content-type từ AMQP properties',
  `originator`           VARCHAR(128)  DEFAULT NULL COMMENT 'amhs_originator từ application properties',
  `addressing_source`    VARCHAR(200)  DEFAULT NULL COMMENT 'Nguồn resolve địa chỉ AMHS',
  `rejection_reason`     VARCHAR(64)   DEFAULT NULL,
  `rejection_diagnostic` VARCHAR(64)   DEFAULT NULL,
  `retry_count`          INT           DEFAULT 0,
  `last_retry_at`        DATETIME      DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin_dispatch: Từng lệnh gửi vào AMHS cho mỗi recipient
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwin_dispatch` (
  `id`                   BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `gwin_id`              BIGINT(20)    NOT NULL,
  `amhs_address`         VARCHAR(100)  NOT NULL,
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  `retry_count`          INT           NOT NULL DEFAULT 0,
  `next_retry_at`        DATETIME      DEFAULT NULL,
  `last_error`           TEXT          DEFAULT NULL,
  `failed_step`          VARCHAR(20)   DEFAULT NULL,
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_at`              DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gwin_id` (`gwin_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gw_alert: Cảnh báo gửi lên Control Position để operator xử lý
-- ============================================================
CREATE TABLE IF NOT EXISTS `gw_alert` (
  `id`                   BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `alert_type`           VARCHAR(30)   NOT NULL,
  `severity`             VARCHAR(10)   NOT NULL,
  `message`              TEXT          NOT NULL,
  `ref_table`            VARCHAR(50)   DEFAULT NULL,
  `ref_id`               BIGINT(20)    DEFAULT NULL,
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'NEW',
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `acknowledged_at`      DATETIME      DEFAULT NULL,
  `acknowledged_by`      VARCHAR(100)  DEFAULT NULL,
  `resolved_at`          DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_type_severity` (`alert_type`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_conversion_log: log chuyển đổi (30 ngày)
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_conversion_log` (
  `id`                       BIGINT(20)    NOT NULL AUTO_INCREMENT,
  `reference_id`             BIGINT(20)    DEFAULT NULL,
  `date`                     VARCHAR(8)    DEFAULT NULL COMMENT 'YYYYMMDD',
  `type`                     VARCHAR(4)    DEFAULT NULL COMMENT 'AMHS / SWIM',
  `category`                 VARCHAR(12)   DEFAULT NULL COMMENT 'IN / OUT',
  `message_id`               VARCHAR(256)  DEFAULT NULL COMMENT 'AMHS MTS-ID',
  `ipm_id`                   VARCHAR(256)  DEFAULT NULL COMMENT 'EUR Doc 047 §4.3.4f - IPM Identifier (G-14)',
  `mts_id`                   VARCHAR(256)  DEFAULT NULL COMMENT 'EUR Doc 047 §4.3.4e - MTS Identifier (G-13)',
  `amqp_message_id`          VARCHAR(256)  DEFAULT NULL COMMENT 'AMQP message-id',
  `priority`                 VARCHAR(12)   DEFAULT NULL,
  `ohi`                      VARCHAR(64)   DEFAULT NULL,
  `origin`                   VARCHAR(128)  DEFAULT NULL,
  `filing_time`              VARCHAR(20)   DEFAULT NULL,
  `subject`                  VARCHAR(512)  DEFAULT NULL,
  `content`                  TEXT,
  `converted_time`           DATETIME      DEFAULT NULL,
  `status`                   VARCHAR(8)    DEFAULT NULL COMMENT 'OK / ERROR / REJECT',
  `action_taken`             VARCHAR(50)   DEFAULT NULL COMMENT 'convert-as-amqp / convert-as-ipm / reject',
  `non_delivery_reason`      VARCHAR(64)   DEFAULT NULL,
  `non_delivery_diagnostic`  VARCHAR(64)   DEFAULT NULL,
  `supplementary_info`       VARCHAR(512)  DEFAULT NULL,
  `remark`                   VARCHAR(256)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `DATE`       (`date`, `id`),
  KEY `AMQP_ID`    (`amqp_message_id`),
  KEY `MESSAGE_ID` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_archive: lưu trữ dài hạn (30 ngày)
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_archive` (
  `uuid`               CHAR(36)     NOT NULL,
  `msg_id`             VARCHAR(100) DEFAULT NULL,
  `mts_id`             VARCHAR(100) DEFAULT NULL,
  `ipm_id`             VARCHAR(100) DEFAULT NULL,
  `amqp_message_id`    VARCHAR(256) DEFAULT NULL,
  `recipients`         TEXT,
  `priority`           VARCHAR(2)   DEFAULT NULL,
  `direction`          VARCHAR(20)  DEFAULT NULL COMMENT 'AMHS_TO_SWIM / SWIM_TO_AMHS',
  `timestamp`          TIMESTAMP    NULL DEFAULT NULL,
  `raw_content`        TEXT,
  `processing_status`  VARCHAR(20)  DEFAULT NULL,
  PRIMARY KEY (`uuid`),
  KEY `idx_amqp_id` (`amqp_message_id`),
  KEY `idx_mts_id`  (`mts_id`),
  KEY `idx_ipm_id`  (`ipm_id`),
  KEY `idx_ts`      (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng accounts: CP quản lý kết nối AMQP / X400
-- ============================================================
CREATE TABLE IF NOT EXISTS `accounts` (
  `id`                       BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `account_name`             VARCHAR(50)  DEFAULT NULL,
  `protocol`                 VARCHAR(20)  DEFAULT NULL COMMENT 'AMQP / X400',
  `host`                     VARCHAR(255) DEFAULT NULL,
  `port`                     INTEGER      DEFAULT NULL,
  `config_json`              TEXT         DEFAULT NULL COMMENT 'Cấu hình chi tiết JSON',
  `status`                   VARCHAR(20)  DEFAULT NULL COMMENT 'ACTIVE / INACTIVE',
  `bind_status`              VARCHAR(20)  DEFAULT NULL COMMENT 'CONNECTED / DISCONNECTED / CONNECTING',
  `certificate_path`         VARCHAR(500) DEFAULT NULL,
  `certificate_passphrase`   TEXT         DEFAULT NULL COMMENT 'AES-256 encrypted',
  `sasl_mechanism`           VARCHAR(20)  DEFAULT NULL COMMENT 'PLAIN / EXTERNAL',
  `tls_enabled`              TINYINT(1)   DEFAULT 0,
  `signed_messages_action`   VARCHAR(30)  DEFAULT NULL,
  `unsigned_messages_action` VARCHAR(30)  DEFAULT NULL,
  UNIQUE KEY `uq_account_name` (`account_name`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng routing: mapping AFTN ↔ SWIM topic
-- ============================================================
CREATE TABLE IF NOT EXISTS `routing` (
  `id`                   BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `direction`            VARCHAR(10)  NOT NULL,
  `message_type`         VARCHAR(50)  DEFAULT NULL,
  `amhs_address`         VARCHAR(100) DEFAULT NULL,
  `send_queue`           VARCHAR(100) DEFAULT NULL,
  `receive_queue`        VARCHAR(100) DEFAULT NULL,
  `subject`              VARCHAR(100) DEFAULT NULL,
  `prop_key`             VARCHAR(255) DEFAULT NULL,
  `prop_value`           VARCHAR(255) DEFAULT NULL,
  `sender_aftn_address`  VARCHAR(100) DEFAULT NULL,
  `aftn_recipients`      TEXT         DEFAULT NULL,
  `default_priority`     TINYINT(4)   DEFAULT NULL,
  `amqp_account`         VARCHAR(50)  DEFAULT NULL,
  `priority`             SMALLINT(6)  NOT NULL DEFAULT 100,
  `active`               TINYINT(1)   NOT NULL DEFAULT 1,
  `note`                 VARCHAR(500) DEFAULT NULL,
  `created_at`           DATETIME(6)  NOT NULL,
  `updated_at`           DATETIME(6)  NOT NULL,
  `created_by`           VARCHAR(100) DEFAULT NULL,
  `updated_by`           VARCHAR(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng performance_metrics: SWIM Component ghi định kỳ
-- ============================================================
CREATE TABLE IF NOT EXISTS `performance_metrics` (
  `id`             BIGINT(20) NOT NULL AUTO_INCREMENT,
  `timestamp`      TIMESTAMP  NULL DEFAULT NULL,
  `cpu_usage`      FLOAT      DEFAULT NULL COMMENT 'Phần trăm CPU',
  `heap_memory`    FLOAT      DEFAULT NULL COMMENT 'MB heap đang dùng',
  `msg_in_count`   INTEGER    DEFAULT NULL COMMENT 'Tổng message nhận được',
  `msg_out_count`  INTEGER    DEFAULT NULL COMMENT 'Tổng message gửi đi',
  `active_threads` INTEGER    DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ts` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gateway_config: tham số cấu hình hệ thống
-- ============================================================
CREATE TABLE IF NOT EXISTS `gateway_config` (
  `config_key`   VARCHAR(100) NOT NULL,
  `config_value` VARCHAR(500) DEFAULT NULL,
  `description`  VARCHAR(500) DEFAULT NULL,
  `updated_at`   DATETIME     DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng system_log: log hệ thống — SWIM ghi, CP đọc
-- ============================================================
CREATE TABLE IF NOT EXISTS `system_log` (
  `uuid`      CHAR(36)     NOT NULL,
  `timestamp` DATETIME     DEFAULT NULL,
  `level`     VARCHAR(10)  DEFAULT NULL COMMENT 'INFO / WARN / ERROR',
  `module`    VARCHAR(30)  DEFAULT NULL COMMENT 'AMHS_COMPONENT / SWIM_COMPONENT / ITCU / CP',
  `content`   TEXT         DEFAULT NULL,
  `status`    VARCHAR(10)  DEFAULT NULL COMMENT 'READ / UNREAD',
  PRIMARY KEY (`uuid`),
  KEY `idx_ts`     (`timestamp`),
  KEY `idx_level`  (`level`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng address_map: tra địa chỉ AFTN ↔ AMHS
-- ============================================================
CREATE TABLE IF NOT EXISTS `address_map` (
  `id`           INT          NOT NULL AUTO_INCREMENT,
  `aftn_address` VARCHAR(8)   NOT NULL COMMENT 'AFTN 8 ký tự',
  `x400_address` VARCHAR(256) NOT NULL COMMENT 'X.400 address',
  `display_name` VARCHAR(255) DEFAULT NULL,
  `active`       TINYINT(1)   DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_aftn` (`aftn_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_type_registry: quy tắc nhận dạng loại điện văn
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_type_registry` (
  `id`             BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `message_type`   VARCHAR(50)  NOT NULL,
  `detect_pattern` VARCHAR(255) NOT NULL,
  `scope_source`   VARCHAR(30)  NOT NULL,
  `difficulty`     VARCHAR(10)  NOT NULL,
  `phase`          TINYINT(4)   NOT NULL DEFAULT 1,
  `active`         TINYINT(1)   NOT NULL DEFAULT 1,
  `note`           VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_message_type` (`message_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng amhs_report_log: report từ AMHS Component (CP đọc)
-- ============================================================
CREATE TABLE IF NOT EXISTS `amhs_report_log` (
  `id`           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `timestamp`    DATETIME     DEFAULT NULL,
  `report_type`  VARCHAR(50)  DEFAULT NULL,
  `message_id`   VARCHAR(256) DEFAULT NULL,
  `originator`   VARCHAR(128) DEFAULT NULL,
  `recipients`   VARCHAR(500) DEFAULT NULL,
  `status`       VARCHAR(20)  DEFAULT NULL,
  `reason`       VARCHAR(256) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ts` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng amhs_probe_log: probe log từ AMHS Component (CP đọc)
-- ============================================================
CREATE TABLE IF NOT EXISTS `amhs_probe_log` (
  `id`           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  `timestamp`    DATETIME     DEFAULT NULL,
  `probe_type`   VARCHAR(50)  DEFAULT NULL,
  `target`       VARCHAR(255) DEFAULT NULL,
  `result`       VARCHAR(20)  DEFAULT NULL,
  `latency_ms`   INTEGER      DEFAULT NULL,
  `detail`       TEXT         DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ts` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng cp_users: tài khoản đăng nhập Control Position
-- ============================================================
CREATE TABLE IF NOT EXISTS `cp_users` (
  `uuid`         VARCHAR(36)  NOT NULL,
  `username`     VARCHAR(50)  NOT NULL UNIQUE,
  `password`     VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed',
  `role`         VARCHAR(20)  DEFAULT 'USER' COMMENT 'ADMIN / USER',
  `created_at`   DATETIME     DEFAULT NULL,
  `last_login`   DATETIME     DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- THE FOLLOWING TABLES ARE FOR AMHS-UNAWARE ADDRESSING RESOLUTION
-- ============================================================

-- ============================================================
-- icao_fir_mapping
-- ============================================================
CREATE TABLE IF NOT EXISTS `icao_fir_mapping` (
    `icao_airport`  VARCHAR(4)   NOT NULL COMMENT 'ICAO aerodrome code (PK)',
    `icao_fir`      VARCHAR(4)   NOT NULL COMMENT 'FIR sở hữu aerodrome (VVHN / VVHM / ...)',
    `airport_name`  VARCHAR(255)     NULL,
    `country`       VARCHAR(50)      NULL,
    `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`icao_airport`),
    KEY `idx_fir` (`icao_fir`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Map ICAO aerodrome → FIR';

-- ============================================================
-- distribution_list
-- ============================================================
CREATE TABLE IF NOT EXISTS `distribution_list` (
    `id`            INT          NOT NULL AUTO_INCREMENT,
    `list_name`     VARCHAR(100) NOT NULL,
    `recipients`    VARCHAR(500) NOT NULL COMMENT 'Space-separated AFTN addresses',
    `message_types` VARCHAR(200)     NULL COMMENT 'CSV loại điện văn',
    `scope`         VARCHAR(50)      NULL COMMENT 'ICAO/FIR scope',
    `description`   TEXT             NULL,
    `active`        TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_list_name` (`list_name`),
    KEY `idx_scope`  (`scope`),
    KEY `idx_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- fir_units
-- ============================================================
CREATE TABLE IF NOT EXISTS `fir_units` (
    `id`            INT          NOT NULL AUTO_INCREMENT,
    `fir_code`      VARCHAR(4)   NOT NULL,
    `unit_type`     VARCHAR(10)  NOT NULL COMMENT 'ACC | APP | TWR | MET | ARO | COM',
    `aftn_address`  VARCHAR(8)   NOT NULL,
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'primary',
    `active`        TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_fir_type_role` (`fir_code`, `unit_type`, `role`),
    KEY `idx_fir_type` (`fir_code`, `unit_type`),
    KEY `idx_active`   (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Đơn vị ATC trong mỗi FIR → AFTN addresses';

-- ============================================================
-- message_unit_mapping
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_unit_mapping` (
    `message_type`        VARCHAR(20)  NOT NULL,
    `requires_unit_types` VARCHAR(100) NOT NULL COMMENT 'CSV: MET,ACC,...',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`message_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- fir_prefix_mapping
-- ============================================================
CREATE TABLE IF NOT EXISTS `fir_prefix_mapping` (
    `icao_prefix`  VARCHAR(4)  NOT NULL COMMENT 'Tiền tố waypoint (1-4 ký tự)',
    `icao_fir`     VARCHAR(4)  NOT NULL COMMENT 'FIR sở hữu. Ví dụ: VVHN, VVHM',
    `description`  VARCHAR(255)    NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`icao_prefix`),
    KEY `idx_fir` (`icao_fir`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ánh xạ tiền tố (prefix) của waypoint sang FIR code';

