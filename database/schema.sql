-- ============================================================
-- ASG (AMHS/SWIM Gateway) — Database Schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS asg_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE asg_db;

-- Fix Vietnamese character encoding
SET NAMES 'utf8mb4';
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Bảng gwout: AMHS Component ghi, SWIM Component đọc/cập nhật
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwout` (
  `msgid`                BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính tự tăng, định danh duy nhất bản tin AMHS đi',
  `priority`             TINYINT(4)    DEFAULT NULL COMMENT 'Độ ưu tiên ATS (SS=1, DD=2, FF=3, GG=4, KK=5) theo ICAO',
  `time`                 DATETIME      DEFAULT NULL COMMENT 'Thời điểm hệ thống nhận tin từ AMHS MTA',
  `TEXT`                 VARCHAR(3200) DEFAULT NULL COMMENT 'Nội dung điện văn ATS thô (IA5 String)',
  `origin`               VARCHAR(8)    DEFAULT NULL COMMENT 'Địa chỉ AFTN người gửi (8 ký tự)',
  `address`              VARCHAR(250)  DEFAULT NULL COMMENT 'Danh sách địa chỉ AFTN nhận, cách nhau dấu cách',
  `optional_heading`     VARCHAR(60)   DEFAULT NULL COMMENT 'Thông tin OHI trích xuất từ P1/P3 Header',
  `amhs_ttl`             DATETIME      DEFAULT NULL COMMENT 'Thời gian sống tối đa của bản tin trên mạng AMHS',
  `amhs_registered_id`   VARCHAR(200)  DEFAULT NULL COMMENT 'Mã đăng ký bản tin của hệ thống Isode M-Switch',
  `amhsid`               VARCHAR(200)  DEFAULT NULL COMMENT 'MTS-Identifier: Mã định danh truyền dẫn AMHS phục vụ báo nhận',
  `ipm_id`               VARCHAR(200)  DEFAULT NULL COMMENT 'IPM-Identifier: Mã định danh nội dung phục vụ báo nhận RN/NRN',
  `filing_time`          VARCHAR(6)    DEFAULT NULL COMMENT 'Filing Time (DDHHMM) trích xuất từ điện văn',
  `priority2`            INT(11)       DEFAULT NULL COMMENT 'Độ ưu tiên AMQP (0-9) ánh xạ từ ATS priority',
  `status`               INT(20)       DEFAULT NULL COMMENT 'Trạng thái: 0:NEW, 1:PROCESS, 2:SENT, 3:ERROR, 4:REJECT',
  `amqp_message_id`      VARCHAR(256)  DEFAULT NULL COMMENT 'ID bản tin do SWIM Broker gán sau khi đẩy thành công',
  `body_type`            VARCHAR(10)   DEFAULT 'text' COMMENT 'Định dạng dữ liệu: text hoặc binary',
  `body_part_type`       VARCHAR(50)   DEFAULT NULL COMMENT 'Loại body part: ia5-text, general-text, file-transfer',
  `content_type`         VARCHAR(100)  DEFAULT NULL COMMENT 'MIME type: text/plain, application/xml...',
  `message_signed`       VARCHAR(20)   DEFAULT NULL COMMENT 'Trạng thái ký: signed, unsigned, invalid-signature',
  `rejection_reason`     VARCHAR(64)   DEFAULT NULL COMMENT 'Lý do từ chối từ Broker SWIM',
  `rejection_diagnostic` VARCHAR(64)   DEFAULT NULL COMMENT 'Chẩn đoán lỗi từ chối từ Broker SWIM',
  `amhs_delivery_report` TINYINT(1)    DEFAULT 0 COMMENT 'Yêu cầu báo nhận AMHS: 0=No, 1=Yes',
  `retry_count`          INT           DEFAULT 0 COMMENT 'Số lần đã thử đẩy tin lên SWIM',
  `last_retry_at`        DATETIME      DEFAULT NULL COMMENT 'Thời điểm cuối cùng thử đẩy tin',
  PRIMARY KEY (`msgid`),
  KEY `priority2` (`priority2`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwout_dispatch: Từng lệnh publish lên AMQP
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwout_dispatch` (
  `id`                   BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT 'Định danh duy nhất bản ghi lệnh đẩy tin SWIM',
  `gwout_id`             BIGINT(20)    NOT NULL COMMENT 'FK trỏ tới bản tin gốc trong bảng `gwout` (N-1)',
  `recipient`            VARCHAR(100)  NOT NULL COMMENT 'Tên Topic/Queue vật lý đích trên Broker SWIM',
  `message_type`         VARCHAR(50)   DEFAULT NULL COMMENT 'Loại tin nghiệp vụ (METAR, TAF, FPL, v.v.)',
  `scope`                VARCHAR(10)   DEFAULT NULL COMMENT 'Phạm vi bản tin: local, regional, global',
  `topic`                VARCHAR(100)  DEFAULT NULL COMMENT 'Tên Topic ảo được sử dụng trong logic định tuyến',
  `amqp_account`         VARCHAR(50)   DEFAULT NULL COMMENT 'Tên tài khoản kết nối AMQP được dùng để publish',
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'Trạng thái: PENDING, SENT, FAILED',
  `retry_count`          INT           NOT NULL DEFAULT 0 COMMENT 'Số lần đã thử thực hiện lệnh publish này',
  `next_retry_at`        DATETIME      DEFAULT NULL COMMENT 'Thời điểm hệ thống sẽ thử lại nếu lần trước lỗi',
  `last_error`           TEXT          DEFAULT NULL COMMENT 'Lỗi kỹ thuật chi tiết từ Broker hoặc thư viện AMQP',
  `failed_step`          VARCHAR(20)   DEFAULT NULL COMMENT 'Bước gặp lỗi: CONNECT, AUTH, PUBLISH, COMMIT',
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Ngày giờ tạo lệnh đẩy tin',
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Ngày giờ cập nhật trạng thái mới nhất',
  `sent_at`              DATETIME      DEFAULT NULL COMMENT 'Thời điểm chính xác đẩy tin thành công lên SWIM',
  PRIMARY KEY (`id`),
  KEY `idx_gwout_id` (`gwout_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_gwout_dispatch_msg` FOREIGN KEY (`gwout_id`) REFERENCES `gwout` (`msgid`) ON DELETE CASCADE,
  CONSTRAINT `fk_gwout_dispatch_acc` FOREIGN KEY (`amqp_account`) REFERENCES `accounts` (`account_name`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin: SWIM Component ghi, AMHS Component đọc
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwin` (
  `cpa`                  VARCHAR(1)    NOT NULL DEFAULT 'N' COMMENT 'Trạng thái CPA (Confirmed Promptness) (Y/N)',
  `msgid`                BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính, định danh duy nhất tin đến SWIM',
  `priority`             TINYINT(4)    DEFAULT NULL COMMENT 'Độ ưu tiên AMQP (0-9) nhận từ Topic',
  `time`                 DATETIME      DEFAULT NULL COMMENT 'Thời điểm SWIM Component nhận được tin từ Broker',
  `TEXT`                 VARCHAR(3200) DEFAULT NULL COMMENT 'Nội dung Payload điện văn nguyên bản (String/XML)',
  `source`               VARCHAR(200)  DEFAULT NULL COMMENT 'Tên Topic/Queue vật lý nguồn',
  `subject`              VARCHAR(100)  DEFAULT NULL COMMENT 'Phân loại tin do Gateway nhận diện (METAR, TAF, FPL...)',
  `amqp_properties`      TEXT          DEFAULT NULL COMMENT 'Toàn bộ Header Header/Properties của AMQP dạng JSON',
  `body_type`            VARCHAR(10)   DEFAULT 'text' COMMENT 'Định dạng dữ liệu: text hoặc binary',
  `origin`               VARCHAR(200)  DEFAULT NULL COMMENT 'Định danh máy chủ SWIM gửi tin',
  `message_id`           VARCHAR(255)  UNIQUE DEFAULT NULL COMMENT 'Global UUID của bản tin SWIM toàn mạng',
  `address`              VARCHAR(250)  DEFAULT NULL COMMENT 'Danh sách địa chỉ AFTN người nhận resolved',
  `status`               INT(20)       DEFAULT NULL COMMENT '0=NEW, 1=PROCESS, 2=SENT, 3=ERROR, 5=UNROUTED',
  `amqp_message_id`      VARCHAR(256)  DEFAULT NULL COMMENT 'ID của bản tin trong thuộc tính AMQP',
  `content_type`         VARCHAR(100)  DEFAULT NULL COMMENT 'MIME type của bản tin SWIM',
  `originator`           VARCHAR(128)  DEFAULT NULL COMMENT 'Địa chỉ người gửi AMHS (X.400) sau khi chuyển đổi',
  `addressing_source`    VARCHAR(200)  DEFAULT NULL COMMENT 'Phương thức giải địa chỉ: RULE, XML, AMQP',
  `rejection_reason`     VARCHAR(64)   DEFAULT NULL COMMENT 'Lý do bị hệ thống MTA AMHS từ chối',
  `rejection_diagnostic` VARCHAR(64)   DEFAULT NULL COMMENT 'Chẩn đoán chi tiết lỗi gửi AMHS',
  `retry_count`          INT           DEFAULT 0 COMMENT 'Số lần thử gửi tin sang AMHS',
  `last_retry_at`        DATETIME      DEFAULT NULL COMMENT 'Lần cuối cùng thử gửi sang AMHS',
  PRIMARY KEY (`msgid`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gwin_dispatch: Từng lệnh gửi vào AMHS cho mỗi recipient
-- ============================================================
CREATE TABLE IF NOT EXISTS `gwin_dispatch` (
  `id`                   BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT 'Định danh lệnh đẩy tin AMHS',
  `gwin_id`              BIGINT(20)    NOT NULL COMMENT 'FK trỏ tới tin đến từ SWIM trong bảng `gwin` (N-1)',
  `amhs_address`         VARCHAR(100)  NOT NULL COMMENT 'Địa chỉ X.400 của một người nhận cụ thể',
  `amhs_account`         VARCHAR(50)   DEFAULT NULL COMMENT 'Tên tài khoản X.400 được dùng để gửi tin',
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'Trạng thái: PENDING, SENT, FAILED',
  `retry_count`          INT           NOT NULL DEFAULT 0 COMMENT 'Số lần thử đẩy tin vào MTA AMHS',
  `next_retry_at`        DATETIME      DEFAULT NULL COMMENT 'Hẹn giờ thử lại tiếp theo',
  `last_error`           TEXT          DEFAULT NULL COMMENT 'Lỗi từ Isode M-Switch API hoặc TCP Connection',
  `failed_step`          VARCHAR(20)   DEFAULT NULL COMMENT 'Bước lỗi: MTA_CONNECT, ADDRESS_VALIDATION, SUBMIT',
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Ngày giờ tạo bản ghi lệnh',
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Lần cuối cập nhật',
  `sent_at`              DATETIME      DEFAULT NULL COMMENT 'Thời điểm MTA báo nhận bản tin thành công',
  PRIMARY KEY (`id`),
  KEY `idx_gwin_id` (`gwin_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_gwin_dispatch_msg` FOREIGN KEY (`gwin_id`) REFERENCES `gwin` (`msgid`) ON DELETE CASCADE,
  CONSTRAINT `fk_gwin_dispatch_acc` FOREIGN KEY (`amhs_account`) REFERENCES `accounts` (`account_name`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gw_alert: Cảnh báo gửi lên Control Position để operator xử lý
-- ============================================================
CREATE TABLE IF NOT EXISTS `gw_alert` (
  `id`                   BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT 'Định danh cảnh báo hệ thống',
  `alert_type`           VARCHAR(30)   NOT NULL COMMENT 'Loại: CONN (Kết nối), DISK (Lưu trữ), APP (Logic), DB (Cơ sở dữ liệu)',
  `severity`             VARCHAR(10)   NOT NULL COMMENT 'Mức độ khẩn cấp: INFO, WARN, ERROR, CRITICAL',
  `message`              TEXT          NOT NULL COMMENT 'Nội dung thông báo lỗi chi tiết giúp Operator xử lý',
  `ref_table`            VARCHAR(50)   DEFAULT NULL COMMENT 'Tên bảng dữ liệu liên quan phát sinh lỗi (nếu có)',
  `ref_id`               BIGINT(20)    DEFAULT NULL COMMENT 'ID của bản ghi liên quan trong bảng ref_table',
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'NEW' COMMENT 'Quy trình: NEW (Mới), ACK (Đã tiếp nhận), RESOLVED (Đã giải quyết)',
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm phát sinh sự cố',
  `acknowledged_at`      DATETIME      DEFAULT NULL COMMENT 'Thời điểm Operator bấm nút xác nhận xem',
  `acknowledged_by`      VARCHAR(100)  DEFAULT NULL COMMENT 'Tên hoặc UUID của Operator thực hiện xác nhận',
  `resolved_at`          DATETIME      DEFAULT NULL COMMENT 'Thời điểm Operator đánh dấu đã khắc phục xong',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_type_severity` (`alert_type`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_conversion_log: log chuyển đổi (30 ngày)
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_conversion_log` (
  `id`                       BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT 'ID nhật ký chuyển đổi',
  `reference_id`             BIGINT(20)    DEFAULT NULL COMMENT 'Tham chiếu tới msgid của bảng gwin/gwout tương ứng',
  `date`                     VARCHAR(8)    DEFAULT NULL COMMENT 'Ngày ghi nhật ký (YYYYMMDD) phục vụ phân tách dữ liệu',
  `type`                     VARCHAR(4)    DEFAULT NULL COMMENT 'Phân loại Component: AMHS hoặc SWIM',
  `category`                 VARCHAR(12)   DEFAULT NULL COMMENT 'Chiều dữ liệu: IN (đến Gateway) hoặc OUT (rời Gateway)',
  `message_id`               VARCHAR(256)  DEFAULT NULL COMMENT 'MTS-Identifier (Message Transfer System) của AMHS',
  `ipm_id`                   VARCHAR(256)  DEFAULT NULL COMMENT 'IPM-Identifier (Inter-Personal Message) của AMHS (G-14)',
  `mts_id`                   VARCHAR(256)  DEFAULT NULL COMMENT 'MTS-Identifier để đối soát báo nhận AMHS (G-13)',
  `amqp_message_id`          VARCHAR(256)  DEFAULT NULL COMMENT 'Mã định danh bản tin SWIM Broker',
  `priority`                 VARCHAR(12)   DEFAULT NULL COMMENT 'Độ ưu tiên truyền dẫn (String)',
  `ohi`                      VARCHAR(64)   DEFAULT NULL COMMENT 'Optional Heading trích xuất từ Header bản tin',
  `origin`                   VARCHAR(128)  DEFAULT NULL COMMENT 'Địa chỉ người gửi bản tin',
  `filing_time`              VARCHAR(20)   DEFAULT NULL COMMENT 'Thời điểm nộp tin DDHHMM',
  `subject`                  VARCHAR(512)  DEFAULT NULL COMMENT 'Tiêu đề trích xuất từ điện văn',
  `content`                  TEXT          COMMENT 'Tóm tắt nội dung văn bản sau chuyển đổi',
  `converted_time`           DATETIME      DEFAULT NULL COMMENT 'Thời điểm hoàn thành quy trình chuyển đổi',
  `status`                   VARCHAR(8)    DEFAULT NULL COMMENT 'Kết quả: OK (Thành công), ERROR (Lỗi), REJECT (Từ chối)',
  `action_taken`             VARCHAR(50)   DEFAULT NULL COMMENT 'Logic áp dụng: convert-to-fixm, convert-to-tac, etc.',
  `non_delivery_reason`      VARCHAR(64)   DEFAULT NULL COMMENT 'Lý do NDR (Non-Delivery Report) nếu lỗi gửi AMHS',
  `non_delivery_diagnostic`  VARCHAR(64)   DEFAULT NULL COMMENT 'Chẩn đoán kỹ thuật NDR',
  `supplementary_info`       VARCHAR(512)  DEFAULT NULL COMMENT 'Thông tin bổ trợ phục vụ hậu kiểm',
  `remark`                   VARCHAR(256)  DEFAULT NULL COMMENT 'Ghi chú thêm của hệ thống',
  PRIMARY KEY (`id`),
  KEY `DATE`       (`date`, `id`),
  KEY `AMQP_ID`    (`amqp_message_id`),
  KEY `MESSAGE_ID` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_archive: lưu trữ dài hạn (30 ngày)
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_archive` (
  `uuid`               CHAR(36)     NOT NULL COMMENT 'UUID định danh bản ghi lưu trữ vĩnh viễn',
  `msg_id`             VARCHAR(100) DEFAULT NULL COMMENT 'ID nội bộ ánh xạ sang bảng gwin hoặc gwout',
  `mts_id`             VARCHAR(100) DEFAULT NULL COMMENT 'MTS-Identifier phục vụ tra cứu trên hạ tầng AMHS',
  `ipm_id`             VARCHAR(100) DEFAULT NULL COMMENT 'IPM-Identifier phục vụ tra cứu nội dung người dùng',
  `amqp_message_id`    VARCHAR(256) DEFAULT NULL COMMENT 'Global Message ID của hạ tầng SWIM',
  `recipients`         TEXT         COMMENT 'Toàn bộ danh sách người nhận ở dạng chuỗi dài',
  `priority`           VARCHAR(2)   DEFAULT NULL COMMENT 'Ký hiệu độ ưu tiên truyền dẫn',
  `direction`          VARCHAR(20)  DEFAULT NULL COMMENT 'Hướng tin: AMHS_TO_SWIM hoặc SWIM_TO_AMHS',
  `timestamp`          TIMESTAMP    NULL DEFAULT NULL COMMENT 'Thời điểm bản tin được đưa vào kho lưu trữ',
  `raw_content`        TEXT         COMMENT 'Dữ liệu nguyên bản (Raw Payload) 100% không chỉnh sửa',
  `processing_status`  VARCHAR(20)  DEFAULT NULL COMMENT 'Trạng thái xử lý tại thời điểm archive (ARCHIVED)',
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
  `id`                       BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT 'ID đại diện tài khoản kết nối',
  `account_name`             VARCHAR(50)  DEFAULT NULL COMMENT 'Tên gợi nhớ duy nhất (Ví dụ: SOLACE_NODE_01)',
  `protocol`                 VARCHAR(20)  DEFAULT NULL COMMENT 'Giao thức kết nối: AMQP hoặc X400',
  `host`                     VARCHAR(255) DEFAULT NULL COMMENT 'Địa chỉ IP hoặc tên miền của Broker/MTA',
  `port`                     INTEGER      DEFAULT NULL COMMENT 'Cổng dịch vụ (AMQP: 5672, X.400: 102)',
  `config_json`              TEXT         DEFAULT NULL COMMENT 'Tham số chi tiết (username, password, vpn, client-id, v.v.) dạng JSON',
  `status`                   VARCHAR(20)  DEFAULT NULL COMMENT 'Logic: ACTIVE (Đang dùng), INACTIVE (Đã khóa)',
  `bind_status`              VARCHAR(20)  DEFAULT NULL COMMENT 'Vật lý: CONNECTED (Thông), DISCONNECTED (Mất), CONNECTING (Đang thử)',
  `certificate_path`         VARCHAR(500) DEFAULT NULL COMMENT 'Đường dẫn vật lý tới tệp chứng chỉ số SSL/TLS',
  `certificate_passphrase`   TEXT         DEFAULT NULL COMMENT 'Mật khẩu tệp chứng chỉ (Đã mã hóa AES-256)',
  `sasl_mechanism`           VARCHAR(20)  DEFAULT NULL COMMENT 'Cơ chế xác thực: PLAIN, EXTERNAL, ANONYMOUS',
  `tls_enabled`              TINYINT(1)   DEFAULT 0 COMMENT 'Sử dụng mã hóa đường truyền: 0=No, 1=Yes',
  `signed_messages_action`   VARCHAR(30)  DEFAULT NULL COMMENT 'Xử lý tin có chữ ký: KEEP (Giữ), STRIP (Xóa)',
  `unsigned_messages_action` VARCHAR(30)  DEFAULT NULL COMMENT 'Xử lý tin không chữ ký: ACCEPT (Chấp nhận), REJECT (Từ chối)',
  UNIQUE KEY `uq_account_name` (`account_name`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng routing: mapping AFTN <-> SWIM topic (Simple Routing)
-- ============================================================
CREATE TABLE IF NOT EXISTS `routing` (
    `id`             INT           PRIMARY KEY AUTO_INCREMENT COMMENT 'Định danh quy tắc định tuyến',
    `direction`      VARCHAR(3)    NOT NULL COMMENT 'Hướng xử lý: ''IN'' (SWIM → AMHS) hoặc ''OUT'' (AMHS → SWIM)',
    
    -- ========== CHIỀU IN (SWIM → AMHS) ==========
    `receive_topic`  VARCHAR(100)  COMMENT 'Tên Topic nguồn trên SWIM Broker (Ví dụ: ats.met.metar)',
    `message_filter` VARCHAR(100)  COMMENT 'Chuỗi lọc nội dung (Ví dụ: METAR, TAF, VVHH... hoặc NULL=TẤT CẢ)',
    `recipients`     VARCHAR(500)  COMMENT 'Danh sách địa chỉ AFTN người nhận (8 ký tự), cách nhau dấu cách',
    `originator`     VARCHAR(8)    COMMENT 'Địa chỉ AFTN người gửi giả định khi đẩy vào mạng AMHS',
    
    -- ========== CHIỀU OUT (AMHS → SWIM) ==========
    `message_type`   VARCHAR(50)   COMMENT 'Loại điện văn AMHS trích xuất (Ví dụ: METAR, FPL, TAF)',
    `send_topic`     VARCHAR(100)  COMMENT 'Tên Topic đích trên SWIM Broker bản tin sẽ được đẩy lên',
    
    -- ========== THAM SỐ CHUNG ==========
    `priority`       INT           DEFAULT 100 COMMENT 'Độ ưu tiên áp dụng (Số nhỏ được ưu tiên xử lý trước)',
    `active`         BOOLEAN       DEFAULT TRUE COMMENT 'Trạng thái kích hoạt: 0 (Vô hiệu), 1 (Kích hoạt)',
    `note`           TEXT          COMMENT 'Ghi chú vận hành của Operator về mục đích quy tắc',
    
    `created_at`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT 'Ngày giờ khởi tạo quy tắc',
    `updated_at`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Ngày giờ cập nhật cuối cùng',
    `created_by`     VARCHAR(50)   COMMENT 'Tên hoặc định danh người thiết lập quy tắc',
    
    INDEX idx_direction (`direction`, `active`),
    INDEX idx_receive (`receive_topic`, `message_filter`),
    INDEX idx_type (`message_type`),
    CONSTRAINT `fk_routing_user` FOREIGN KEY (`created_by`) REFERENCES `cp_users` (`username`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng performance_metrics: SWIM Component ghi định kỳ
-- ============================================================
CREATE TABLE IF NOT EXISTS `performance_metrics` (
  `id`             BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID mẫu giám sát hiệu năng',
  `timestamp`      TIMESTAMP  NULL DEFAULT NULL COMMENT 'Thời điểm lấy mẫu hệ thống',
  `cpu_usage`      FLOAT      DEFAULT NULL COMMENT 'Phần trăm CPU sử dụng trung bình (0.0 - 100.0)',
  `heap_memory`    FLOAT      DEFAULT NULL COMMENT 'Dung lượng RAM Java Heapđang dùng (Đơn vị: MB)',
  `msg_in_count`   INTEGER    DEFAULT NULL COMMENT 'Tổng số bản tin SWIM nhận được trong chu kỳ theo dõi',
  `msg_out_count`  INTEGER    DEFAULT NULL COMMENT 'Tổng số bản tin AMHS gửi đi thành công trong chu kỳ',
  `active_threads` INTEGER    DEFAULT NULL COMMENT 'Số lượng luồng xử lý (Threads) đang chạy song song',
  PRIMARY KEY (`id`),
  KEY `idx_ts` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng gateway_config: tham số cấu hình hệ thống
-- ============================================================
CREATE TABLE IF NOT EXISTS `gateway_config` (
  `config_key`   VARCHAR(100) NOT NULL COMMENT 'Tên hằng số cấu hình (Ví dụ: POLL_INTERVAL_MS)',
  `config_value` VARCHAR(500) DEFAULT NULL COMMENT 'Giá trị cấu hình hiện tại đang áp dụng cho Gateway',
  `description`  VARCHAR(500) DEFAULT NULL COMMENT 'Mô tả chi tiết chức năng và ảnh hưởng của tham số tới hệ thống',
  `updated_at`   DATETIME     DEFAULT NULL COMMENT 'Thời điểm cập nhật giá trị tham số cuối cùng',
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng system_log: log hệ thống — SWIM ghi, CP đọc
-- ============================================================
CREATE TABLE IF NOT EXISTS `system_log` (
  `uuid`      CHAR(36)     NOT NULL COMMENT 'UUID định danh bản ghi log hệ thống',
  `timestamp` DATETIME     DEFAULT NULL COMMENT 'Ngày giờ phát sinh log sự kiện',
  `level`     VARCHAR(10)  DEFAULT NULL COMMENT 'Mức độ log: INFO, WARN, ERROR, DEBUG',
  `module`    VARCHAR(30)  DEFAULT NULL COMMENT 'Tên thành phần phát sinh: SWIM_COMPONENT, AMHS_COMPONENT, ITCU, CP',
  `content`   TEXT         DEFAULT NULL COMMENT 'Nội dung thông báo lỗi hoặc hành động hệ thống',
  `status`    VARCHAR(10)  DEFAULT NULL COMMENT 'Trạng thái xử lý log trên dashboard: READ, UNREAD',
  PRIMARY KEY (`uuid`),
  KEY `idx_ts`     (`timestamp`),
  KEY `idx_level`  (`level`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng message_type_registry: quy tắc nhận dạng loại điện văn
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_type_registry` (
  `id`             BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT 'ID mẫu nhận dạng loại tin',
  `message_type`   VARCHAR(50)  NOT NULL COMMENT 'Mã loại tin nghiệp vụ (Ví dụ: METAR, TAF, FPL, NOTAM)',
  `detect_pattern` VARCHAR(255) NOT NULL COMMENT 'Chuỗi Regex hoặc Keyword dùng để quét nội dung Payload thô',
  `difficulty`     VARCHAR(10)  NOT NULL COMMENT 'Mức độ phức tạp khi xử lý: easy, medium, hard',
  `phase`          TINYINT(4)   NOT NULL DEFAULT 1 COMMENT 'Giai đoạn triển khai áp dụng loại tin này',
  `active`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT 'Trạng thái áp dụng: 0 (Tắt), 1 (Đang kích hoạt)',
  `note`           VARCHAR(500) DEFAULT NULL COMMENT 'Ghi chú kỹ thuật về quy tắc nhận dạng',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_message_type` (`message_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Bảng cp_users: tài khoản đăng nhập Control Position
-- ============================================================
CREATE TABLE IF NOT EXISTS `cp_users` (
  `uuid`         VARCHAR(36)  NOT NULL COMMENT 'UUID định danh quản trị viên',
  `username`     VARCHAR(50)  NOT NULL UNIQUE COMMENT 'Tên đăng nhập duy nhất vào dashboard',
  `password`     VARCHAR(255) NOT NULL COMMENT 'Mật khẩu đã được băm bằng thuật toán BCrypt',
  `role`         VARCHAR(20)  DEFAULT 'USER' COMMENT 'Phân quyền: ADMIN hoặc OPERATOR',
  `created_at`   DATETIME     DEFAULT NULL COMMENT 'Ngày tạo tài khoản người dùng',
  `last_login`   DATETIME     DEFAULT NULL COMMENT 'Thời điểm cuối cùng đăng nhập thành công',
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;



