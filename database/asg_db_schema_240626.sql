-- MySQL dump 10.13  Distrib 8.4.10, for Linux (x86_64)
--
-- Host: localhost    Database: asg_db
-- ------------------------------------------------------
-- Server version	8.4.10-0ubuntu0.26.04.1
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO,ANSI' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table "accounts"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "accounts" (
  "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID đại diện tài khoản kết nối',
  "account_name" varchar(50) DEFAULT NULL COMMENT 'Tên gợi nhớ duy nhất (Ví dụ: SOLACE_NODE_01)',
  "protocol" varchar(20) DEFAULT NULL COMMENT 'Giao thức kết nối: AMQP hoặc X400',
  "host" varchar(255) DEFAULT NULL COMMENT 'Địa chỉ IP hoặc tên miền của Broker/MTA',
  "port" int DEFAULT NULL COMMENT 'Cổng dịch vụ (AMQP: 5672, X.400: 102)',
  "config_json" text COMMENT 'Tham số chi tiết (username, password, vpn, client-id, v.v.) dạng JSON',
  "status" varchar(20) DEFAULT NULL COMMENT 'Logic: ACTIVE (Đang dùng), INACTIVE (Đã khóa)',
  "bind_status" varchar(20) DEFAULT NULL COMMENT 'Vật lý: CONNECTED (Thông), DISCONNECTED (Mất), CONNECTING (Đang thử)',
  "certificate_path" varchar(500) DEFAULT NULL COMMENT 'Đường dẫn vật lý tới tệp chứng chỉ số SSL/TLS',
  "certificate_passphrase" text COMMENT 'Mật khẩu tệp chứng chỉ (Đã mã hóa AES-256)',
  "sasl_mechanism" varchar(20) DEFAULT NULL COMMENT 'Cơ chế xác thực: PLAIN, EXTERNAL, ANONYMOUS',
  "tls_enabled" tinyint(1) DEFAULT '0' COMMENT 'Sử dụng mã hóa đường truyền: 0=No, 1=Yes',
  "signed_messages_action" varchar(30) DEFAULT NULL COMMENT 'Xử lý tin có chữ ký: KEEP (Giữ), STRIP (Xóa)',
  "unsigned_messages_action" varchar(30) DEFAULT NULL COMMENT 'Xử lý tin không chữ ký: ACCEPT (Chấp nhận), REJECT (Từ chối)',
  PRIMARY KEY ("id"),
  UNIQUE KEY "uq_account_name" ("account_name")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "cp_users"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "cp_users" (
  "uuid" varchar(36) NOT NULL COMMENT 'UUID định danh quản trị viên',
  "username" varchar(50) NOT NULL COMMENT 'Tên đăng nhập duy nhất vào dashboard',
  "password" varchar(255) NOT NULL COMMENT 'Mật khẩu đã được băm bằng thuật toán BCrypt',
  "role" varchar(20) DEFAULT 'USER' COMMENT 'Phân quyền: ADMIN hoặc OPERATOR',
  "created_at" datetime DEFAULT NULL COMMENT 'Ngày tạo tài khoản người dùng',
  "last_login" datetime DEFAULT NULL COMMENT 'Thời điểm cuối cùng đăng nhập thành công',
  PRIMARY KEY ("uuid"),
  UNIQUE KEY "username" ("username")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "gateway_config"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "gateway_config" (
  "config_key" varchar(100) NOT NULL COMMENT 'Tên hằng số cấu hình (Ví dụ: POLL_INTERVAL_MS)',
  "config_value" varchar(500) DEFAULT NULL COMMENT 'Giá trị cấu hình hiện tại đang áp dụng cho Gateway',
  "description" varchar(500) DEFAULT NULL COMMENT 'Mô tả chi tiết chức năng và ảnh hưởng của tham số tới hệ thống',
  "updated_at" datetime DEFAULT NULL COMMENT 'Thời điểm cập nhật giá trị tham số cuối cùng',
  PRIMARY KEY ("config_key")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "gw_alert"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "gw_alert" (
  "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'Định danh cảnh báo hệ thống',
  "alert_type" varchar(30) NOT NULL COMMENT 'Loại: CONN (Kết nối), DISK (Lưu trữ), APP (Logic), DB (Cơ sở dữ liệu)',
  "severity" varchar(10) NOT NULL COMMENT 'Mức độ khẩn cấp: INFO, WARN, ERROR, CRITICAL',
  "message" text NOT NULL COMMENT 'Nội dung thông báo lỗi chi tiết giúp Operator xử lý',
  "ref_table" varchar(50) DEFAULT NULL COMMENT 'Tên bảng dữ liệu liên quan phát sinh lỗi (nếu có)',
  "ref_id" bigint DEFAULT NULL COMMENT 'ID của bản ghi liên quan trong bảng ref_table',
  "status" varchar(20) NOT NULL DEFAULT 'NEW' COMMENT 'Quy trình: NEW (Mới), ACK (Đã tiếp nhận), RESOLVED (Đã giải quyết)',
  "created_at" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm phát sinh sự cố',
  "acknowledged_at" datetime DEFAULT NULL COMMENT 'Thời điểm Operator bấm nút xác nhận xem',
  "acknowledged_by" varchar(100) DEFAULT NULL COMMENT 'Tên hoặc UUID của Operator thực hiện xác nhận',
  "resolved_at" datetime DEFAULT NULL COMMENT 'Thời điểm Operator đánh dấu đã khắc phục xong',
  PRIMARY KEY ("id"),
  KEY "idx_status" ("status"),
  KEY "idx_type_severity" ("alert_type","severity")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "gwin"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "gwin" (
  "cpa" varchar(1) NOT NULL DEFAULT 'N' COMMENT 'Trạng thái CPA (Confirmed Promptness) (Y/N)',
  "msgid" bigint NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính, định danh duy nhất tin đến SWIM',
  "priority" tinyint DEFAULT NULL COMMENT 'Độ ưu tiên AMQP (0-9) nhận từ Topic',
  "time" datetime DEFAULT NULL COMMENT 'Thời điểm SWIM Component nhận được tin từ Broker',
  "TEXT" varchar(3200) DEFAULT NULL COMMENT 'Nội dung Payload điện văn nguyên bản (String/XML)',
  "source" varchar(200) DEFAULT NULL COMMENT 'Tên Topic/Queue vật lý nguồn',
  "subject" varchar(100) DEFAULT NULL COMMENT 'Phân loại tin do Gateway nhận diện (METAR, TAF, FPL...)',
  "amqp_properties" text COMMENT 'Toàn bộ Header Header/Properties của AMQP dạng JSON',
  "body_type" varchar(10) DEFAULT 'text' COMMENT 'Định dạng dữ liệu: text hoặc binary',
  "origin" varchar(200) DEFAULT NULL COMMENT 'Định danh máy chủ SWIM gửi tin',
  "message_id" varchar(255) DEFAULT NULL COMMENT 'Global UUID của bản tin SWIM toàn mạng',
  "address" varchar(250) DEFAULT NULL COMMENT 'Danh sách địa chỉ AFTN người nhận resolved',
  "status" int DEFAULT NULL COMMENT '0=NEW, 1=PROCESS, 2=SENT, 3=ERROR, 5=UNROUTED',
  "amqp_message_id" varchar(256) DEFAULT NULL COMMENT 'ID của bản tin trong thuộc tính AMQP',
  "content_type" varchar(100) DEFAULT NULL COMMENT 'MIME type của bản tin SWIM',
  "originator" varchar(128) DEFAULT NULL COMMENT 'Địa chỉ người gửi AMHS (X.400) sau khi chuyển đổi',
  "addressing_source" varchar(200) DEFAULT NULL COMMENT 'Phương thức giải địa chỉ: RULE, XML, AMQP',
  "rejection_reason" varchar(64) DEFAULT NULL COMMENT 'Lý do bị hệ thống MTA AMHS từ chối',
  "rejection_diagnostic" varchar(64) DEFAULT NULL COMMENT 'Chẩn đoán chi tiết lỗi gửi AMHS',
  "retry_count" int DEFAULT '0' COMMENT 'Số lần thử gửi tin sang AMHS',
  "last_retry_at" datetime DEFAULT NULL COMMENT 'Lần cuối cùng thử gửi sang AMHS',
  "error_type" int DEFAULT NULL,
  "payload_content" mediumtext,
  PRIMARY KEY ("msgid"),
  UNIQUE KEY "message_id" ("message_id"),
  KEY "idx_status" ("status")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "gwin_dispatch"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "gwin_dispatch" (
  "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'Định danh lệnh đẩy tin AMHS',
  "gwin_id" bigint NOT NULL COMMENT 'FK trỏ tới tin đến từ SWIM trong bảng `gwin` (N-1)',
  "amhs_address" varchar(100) NOT NULL COMMENT 'Địa chỉ X.400 của một người nhận cụ thể',
  "amhs_account" varchar(50) DEFAULT NULL COMMENT 'Tên tài khoản X.400 được dùng để gửi tin',
  "status" varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Trạng thái: PENDING, SENT, FAILED',
  "retry_count" int NOT NULL DEFAULT '0' COMMENT 'Số lần thử đẩy tin vào MTA AMHS',
  "next_retry_at" datetime DEFAULT NULL COMMENT 'Hẹn giờ thử lại tiếp theo',
  "last_error" text COMMENT 'Lỗi từ Isode M-Switch API hoặc TCP Connection',
  "failed_step" varchar(20) DEFAULT NULL COMMENT 'Bước lỗi: MTA_CONNECT, ADDRESS_VALIDATION, SUBMIT',
  "created_at" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Ngày giờ tạo bản ghi lệnh',
  "updated_at" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Lần cuối cập nhật',
  "sent_at" datetime DEFAULT NULL COMMENT 'Thời điểm MTA báo nhận bản tin thành công',
  PRIMARY KEY ("id"),
  KEY "idx_gwin_id" ("gwin_id"),
  KEY "idx_status" ("status"),
  KEY "fk_gwin_dispatch_acc" ("amhs_account"),
  CONSTRAINT "fk_gwin_dispatch_acc" FOREIGN KEY ("amhs_account") REFERENCES "accounts" ("account_name") ON DELETE SET NULL,
  CONSTRAINT "fk_gwin_dispatch_msg" FOREIGN KEY ("gwin_id") REFERENCES "gwin" ("msgid") ON DELETE CASCADE
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "gwout"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "gwout" (
  "msgid" bigint NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính tự tăng, định danh duy nhất bản tin AMHS đi',
  "priority" int DEFAULT NULL,
  "time" datetime DEFAULT NULL COMMENT 'Thời điểm hệ thống nhận tin từ AMHS MTA',
  "TEXT" varchar(3200) DEFAULT NULL COMMENT 'Nội dung điện văn ATS thô (IA5 String)',
  "origin" varchar(8) DEFAULT NULL COMMENT 'Địa chỉ AFTN người gửi (8 ký tự)',
  "address" varchar(250) DEFAULT NULL COMMENT 'Danh sách địa chỉ AFTN nhận, cách nhau dấu cách',
  "optional_heading" varchar(60) DEFAULT NULL COMMENT 'Thông tin OHI trích xuất từ P1/P3 Header',
  "amhs_ttl" datetime DEFAULT NULL COMMENT 'Thời gian sống tối đa của bản tin trên mạng AMHS',
  "amhs_registered_id" varchar(200) DEFAULT NULL COMMENT 'Mã đăng ký bản tin của hệ thống Isode M-Switch',
  "amhsid" varchar(200) DEFAULT NULL COMMENT 'MTS-Identifier: Mã định danh truyền dẫn AMHS phục vụ báo nhận',
  "ipm_id" varchar(200) DEFAULT NULL COMMENT 'IPM-Identifier: Mã định danh nội dung phục vụ báo nhận RN/NRN',
  "filing_time" varchar(6) DEFAULT NULL COMMENT 'Filing Time (DDHHMM) trích xuất từ điện văn',
  "priority2" int DEFAULT NULL COMMENT 'Độ ưu tiên AMQP (0-9) ánh xạ từ ATS priority',
  "status" int DEFAULT NULL COMMENT 'Trạng thái: 0:NEW, 1:PROCESS, 2:SENT, 3:ERROR, 4:REJECT',
  "amqp_message_id" varchar(256) DEFAULT NULL COMMENT 'ID bản tin do SWIM Broker gán sau khi đẩy thành công',
  "body_type" varchar(10) DEFAULT 'text' COMMENT 'Định dạng dữ liệu: text hoặc binary',
  "body_part_type" varchar(50) DEFAULT NULL COMMENT 'Loại body part: ia5-text, general-text, file-transfer',
  "content_type" varchar(100) DEFAULT NULL COMMENT 'MIME type: text/plain, application/xml...',
  "message_signed" varchar(20) DEFAULT NULL COMMENT 'Trạng thái ký: signed, unsigned, invalid-signature',
  "rejection_reason" varchar(64) DEFAULT NULL COMMENT 'Lý do từ chối từ Broker SWIM',
  "rejection_diagnostic" varchar(64) DEFAULT NULL COMMENT 'Chẩn đoán lỗi từ chối từ Broker SWIM',
  "amhs_delivery_report" tinyint(1) DEFAULT '0' COMMENT 'Yêu cầu báo nhận AMHS: 0=No, 1=Yes',
  "retry_count" int DEFAULT '0' COMMENT 'Số lần đã thử đẩy tin lên SWIM',
  "last_retry_at" datetime DEFAULT NULL COMMENT 'Thời điểm cuối cùng thử đẩy tin',
  "error_type" int DEFAULT NULL,
  "payload_content" mediumtext,
  PRIMARY KEY ("msgid"),
  KEY "priority2" ("priority2"),
  KEY "idx_status" ("status")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "gwout_dispatch"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "gwout_dispatch" (
  "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'Định danh duy nhất bản ghi lệnh đẩy tin SWIM',
  "gwout_id" bigint NOT NULL COMMENT 'FK trỏ tới bản tin gốc trong bảng `gwout` (N-1)',
  "recipient" varchar(100) NOT NULL COMMENT 'Tên Topic/Queue vật lý đích trên Broker SWIM',
  "message_type" varchar(50) DEFAULT NULL COMMENT 'Loại tin nghiệp vụ (METAR, TAF, FPL, v.v.)',
  "scope" varchar(10) DEFAULT NULL COMMENT 'Phạm vi bản tin: local, regional, global',
  "topic" varchar(100) DEFAULT NULL COMMENT 'Tên Topic ảo được sử dụng trong logic định tuyến',
  "amqp_account" varchar(50) DEFAULT NULL COMMENT 'Tên tài khoản kết nối AMQP được dùng để publish',
  "status" varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Trạng thái: PENDING, SENT, FAILED',
  "retry_count" int NOT NULL DEFAULT '0' COMMENT 'Số lần đã thử thực hiện lệnh publish này',
  "next_retry_at" datetime DEFAULT NULL COMMENT 'Thời điểm hệ thống sẽ thử lại nếu lần trước lỗi',
  "last_error" text COMMENT 'Lỗi kỹ thuật chi tiết từ Broker hoặc thư viện AMQP',
  "failed_step" varchar(20) DEFAULT NULL COMMENT 'Bước gặp lỗi: CONNECT, AUTH, PUBLISH, COMMIT',
  "created_at" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Ngày giờ tạo lệnh đẩy tin',
  "updated_at" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Ngày giờ cập nhật trạng thái mới nhất',
  "sent_at" datetime DEFAULT NULL COMMENT 'Thời điểm chính xác đẩy tin thành công lên SWIM',
  PRIMARY KEY ("id"),
  KEY "idx_gwout_id" ("gwout_id"),
  KEY "idx_status" ("status"),
  KEY "fk_gwout_dispatch_acc" ("amqp_account"),
  CONSTRAINT "fk_gwout_dispatch_acc" FOREIGN KEY ("amqp_account") REFERENCES "accounts" ("account_name") ON DELETE SET NULL,
  CONSTRAINT "fk_gwout_dispatch_msg" FOREIGN KEY ("gwout_id") REFERENCES "gwout" ("msgid") ON DELETE CASCADE
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "message_conversion_log"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "message_conversion_log" (
  "id" bigint NOT NULL AUTO_INCREMENT,
  "action_taken" varchar(50) DEFAULT NULL,
  "amhs_priority" varchar(12) DEFAULT NULL,
  "amqp_message_id" varchar(256) DEFAULT NULL,
  "content" text,
  "converted_time" datetime(6) DEFAULT NULL,
  "created_time" datetime(6) DEFAULT NULL,
  "date" varchar(8) DEFAULT NULL,
  "direction" varchar(12) DEFAULT NULL,
  "filing_time" varchar(20) DEFAULT NULL,
  "ipm_id" varchar(256) DEFAULT NULL,
  "mts_id" varchar(256) DEFAULT NULL,
  "non_delivery_diagnostic" varchar(64) DEFAULT NULL,
  "non_delivery_reason" varchar(64) DEFAULT NULL,
  "ohi" varchar(64) DEFAULT NULL,
  "origin" varchar(128) DEFAULT NULL,
  "raw_content" text,
  "recipients" varchar(128) DEFAULT NULL,
  "reference_id" bigint DEFAULT NULL,
  "remark" varchar(256) DEFAULT NULL,
  "routing_id" varchar(128) DEFAULT NULL,
  "status" varchar(8) DEFAULT NULL,
  "subject" varchar(512) DEFAULT NULL,
  "supplementary_info" varchar(512) DEFAULT NULL,
  "swim_priority" varchar(12) DEFAULT NULL,
  "type" varchar(4) DEFAULT NULL,
  PRIMARY KEY ("id")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "message_type_registry"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "message_type_registry" (
  "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID mẫu nhận dạng loại tin',
  "message_type" varchar(50) NOT NULL COMMENT 'Mã loại tin nghiệp vụ (Ví dụ: METAR, TAF, FPL, NOTAM)',
  "detect_pattern" varchar(255) NOT NULL COMMENT 'Chuỗi Regex hoặc Keyword dùng để quét nội dung Payload thô',
  "difficulty" varchar(10) NOT NULL COMMENT 'Mức độ phức tạp khi xử lý: easy, medium, hard',
  "phase" tinyint NOT NULL DEFAULT '1' COMMENT 'Giai đoạn triển khai áp dụng loại tin này',
  "active" tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Trạng thái áp dụng: 0 (Tắt), 1 (Đang kích hoạt)',
  "note" varchar(500) DEFAULT NULL COMMENT 'Ghi chú kỹ thuật về quy tắc nhận dạng',
  PRIMARY KEY ("id"),
  UNIQUE KEY "uq_message_type" ("message_type")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "performance_metrics"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "performance_metrics" (
  "id" bigint NOT NULL AUTO_INCREMENT COMMENT 'ID mẫu giám sát hiệu năng',
  "timestamp" timestamp NULL DEFAULT NULL COMMENT 'Thời điểm lấy mẫu hệ thống',
  "cpu_usage" float DEFAULT NULL COMMENT 'Phần trăm CPU sử dụng trung bình (0.0 - 100.0)',
  "heap_memory" float DEFAULT NULL COMMENT 'Dung lượng RAM Java Heapđang dùng (Đơn vị: MB)',
  "msg_in_count" int DEFAULT NULL COMMENT 'Tổng số bản tin SWIM nhận được trong chu kỳ theo dõi',
  "msg_out_count" int DEFAULT NULL COMMENT 'Tổng số bản tin AMHS gửi đi thành công trong chu kỳ',
  "active_threads" int DEFAULT NULL COMMENT 'Số lượng luồng xử lý (Threads) đang chạy song song',
  PRIMARY KEY ("id"),
  KEY "idx_ts" ("timestamp")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "routing"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "routing" (
  "id" int NOT NULL AUTO_INCREMENT COMMENT 'Định danh quy tắc định tuyến',
  "direction" varchar(3) NOT NULL COMMENT 'Hướng xử lý: ''IN'' (SWIM → AMHS) hoặc ''OUT'' (AMHS → SWIM)',
  "receive_topic" varchar(100) DEFAULT NULL COMMENT 'Tên Topic nguồn trên SWIM Broker (Ví dụ: ats.met.metar)',
  "message_filter" varchar(100) DEFAULT NULL COMMENT 'Chuỗi lọc nội dung (Ví dụ: METAR, TAF, VVHH... hoặc NULL=TẤT CẢ)',
  "recipients" varchar(500) DEFAULT NULL COMMENT 'Danh sách địa chỉ AFTN người nhận (8 ký tự), cách nhau dấu cách',
  "originator" varchar(8) DEFAULT NULL COMMENT 'Địa chỉ AFTN người gửi giả định khi đẩy vào mạng AMHS',
  "message_type" varchar(50) DEFAULT NULL COMMENT 'Loại điện văn AMHS trích xuất (Ví dụ: METAR, FPL, TAF)',
  "send_topic" varchar(100) DEFAULT NULL COMMENT 'Tên Topic đích trên SWIM Broker bản tin sẽ được đẩy lên',
  "priority" int DEFAULT '100' COMMENT 'Độ ưu tiên áp dụng (Số nhỏ được ưu tiên xử lý trước)',
  "active" tinyint(1) DEFAULT '1' COMMENT 'Trạng thái kích hoạt: 0 (Vô hiệu), 1 (Kích hoạt)',
  "note" text COMMENT 'Ghi chú vận hành của Operator về mục đích quy tắc',
  "created_at" datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Ngày giờ khởi tạo quy tắc',
  "updated_at" datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Ngày giờ cập nhật cuối cùng',
  "created_by" varchar(50) DEFAULT NULL COMMENT 'Tên hoặc định danh người thiết lập quy tắc',
  "priority_amhs" varchar(255) DEFAULT NULL,
  "priority_swim" int DEFAULT NULL,
  PRIMARY KEY ("id"),
  KEY "idx_direction" ("direction","active"),
  KEY "idx_receive" ("receive_topic","message_filter"),
  KEY "idx_type" ("message_type"),
  KEY "fk_routing_user" ("created_by"),
  CONSTRAINT "fk_routing_user" FOREIGN KEY ("created_by") REFERENCES "cp_users" ("username") ON DELETE SET NULL
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "server_info"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "server_info" (
  "uuid" varchar(36) NOT NULL,
  "description" varchar(255) DEFAULT NULL,
  "ip_address" varchar(255) DEFAULT NULL,
  "server_name" varchar(255) DEFAULT NULL,
  "version" varchar(255) DEFAULT NULL,
  PRIMARY KEY ("uuid")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "system_history"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "system_history" (
  "id" bigint NOT NULL AUTO_INCREMENT,
  "created_by" varchar(100) DEFAULT NULL,
  "description" text,
  "event_time" datetime(6) NOT NULL,
  "event_type" varchar(50) NOT NULL,
  "severity" varchar(20) DEFAULT NULL,
  "title" varchar(255) DEFAULT NULL,
  PRIMARY KEY ("id")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "system_log"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "system_log" (
  "uuid" varchar(36) NOT NULL,
  "timestamp" datetime DEFAULT NULL COMMENT 'Ngày giờ phát sinh log sự kiện',
  "level" varchar(10) DEFAULT NULL COMMENT 'Mức độ log: INFO, WARN, ERROR, DEBUG',
  "module" varchar(30) DEFAULT NULL COMMENT 'Tên thành phần phát sinh: SWIM_COMPONENT, AMHS_COMPONENT, ITCU, CP',
  "content" text COMMENT 'Nội dung thông báo lỗi hoặc hành động hệ thống',
  "status" varchar(10) DEFAULT NULL COMMENT 'Trạng thái xử lý log trên dashboard: READ, UNREAD',
  PRIMARY KEY ("uuid"),
  KEY "idx_ts" ("timestamp"),
  KEY "idx_level" ("level"),
  KEY "idx_module" ("module")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "system_statistics"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "system_statistics" (
  "id" bigint NOT NULL AUTO_INCREMENT,
  "stat_time" datetime NOT NULL,
  "cpu_usage" decimal(5,2) DEFAULT NULL,
  "memory_usage" decimal(5,2) DEFAULT NULL,
  "disk_usage" decimal(5,2) DEFAULT NULL,
  "active_threads" int DEFAULT NULL,
  "heap_used_mb" int DEFAULT NULL,
  "heap_max_mb" int DEFAULT NULL,
  "uptime_seconds" bigint DEFAULT NULL,
  "created_at" datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY ("id"),
  UNIQUE KEY "stat_time" ("stat_time")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "user_system_history"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "user_system_history" (
  "id" bigint NOT NULL AUTO_INCREMENT,
  "is_read" bit(1) DEFAULT NULL,
  "system_history_id" bigint DEFAULT NULL,
  "user_id" bigint DEFAULT NULL,
  PRIMARY KEY ("id")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table "users"
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE "users" (
  "id" bigint NOT NULL AUTO_INCREMENT,
  "avatar" varchar(255) DEFAULT NULL,
  "created_at" datetime(6) DEFAULT NULL,
  "email" varchar(100) NOT NULL,
  "full_name" varchar(100) NOT NULL,
  "is_active" bit(1) DEFAULT NULL,
  "last_login_at" datetime(6) DEFAULT NULL,
  "last_login_ip" varchar(45) DEFAULT NULL,
  "password" varchar(255) NOT NULL,
  "role" enum('admin','viewer') NOT NULL,
  "updated_at" datetime(6) DEFAULT NULL,
  "username" varchar(50) NOT NULL,
  PRIMARY KEY ("id"),
  UNIQUE KEY "UK_6dotkott2kjsp8vw4d0m25fb7" ("email"),
  UNIQUE KEY "UK_r43af9ap4edm43mmtq01oddj6" ("username")
);
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'asg_db'
--

--
-- Dumping routines for database 'asg_db'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 14:46:22
