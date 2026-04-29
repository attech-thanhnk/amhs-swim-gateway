# Đặc tả Cơ sở dữ liệu AMHS-SWIM Gateway

---

### 1. Bảng `gwout` (Tin điện đi từ AMHS)
Lưu trữ các bản tin nhận được từ hòm thư AMHS (MTA) để chuẩn bị chuyển đổi sang SWIM.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `msgid` | BIGINT(20) | **PK**, AI | Khóa chính tự tăng, định danh duy nhất bản tin AMHS đi trong hệ thống. |
| `priority` | TINYINT(4) | | Độ ưu tiên ATS (SS=1, DD=2, FF=3, GG=4, KK=5) theo tiêu chuẩn ICAO Annex 10. |
| `time` | DATETIME | | Thời điểm hệ thống Gateway nhận bản tin này từ phía AMHS MTA. |
| `TEXT` | VARCHAR(3200)| | Nội dung văn bản điện văn ATS thô (IA5 String) bao gồm Heading và Text. |
| `origin` | VARCHAR(8) | | Địa chỉ AFTN người gửi (8 ký tự) trích xuất từ phần Origin của điện văn hoặc P1 Header. |
| `address` | VARCHAR(250) | | Danh sách các địa chỉ AFTN người nhận, cách nhau bởi một dấu cách. |
| `optional_heading`| VARCHAR(60) | | Thông tin OHI (Optional Heading Information) trích xuất từ P1/P3 Header của bản tin AMHS. |
| `amhs_ttl` | DATETIME | | Time-to-live: Thời điểm bản tin sẽ hết hạn và không được phép truyền dẫn trên mạng AMHS. |
| `amhs_registered_id`| VARCHAR(200)| | Mã đăng ký bản tin duy nhất do hệ thống trung gian (Isode M-Switch) cung cấp. |
| `amhsid` | VARCHAR(200) | | **MTS-Identifier**: Mã định danh duy nhất của lớp Message Transfer System phục vụ báo nhận (Delivery Report). |
| `ipm_id` | VARCHAR(200) | | **IPM-Identifier**: Mã định danh nội dung người dùng phục vụ báo nhận nội dung (RN/NRN). |
| `filing_time` | VARCHAR(6) | | Filing Time (DDHHMM) trích xuất từ bản tin, thể hiện ngày/giờ/phút nộp tin. |
| `priority2` | INT(11) | Index | Độ ưu tiên AMQP (0-9) đã qua ánh xạ từ độ ưu tiên ATS để đẩy lên SWIM Broker. |
| `status` | INT(20) | Index | Trạng thái xử lý: 0:NEW, 1:PROCESSING, 2:SENT, 3:ERROR, 4:REJECTED. |
| `amqp_message_id`| VARCHAR(256)| | Mã định danh bản tin (message-id) mà SWIM Broker trả về sau khi publish thành công. |
| `body_type` | VARCHAR(10) | | Định dạng body chính: 'text' (văn bản) hoặc 'binary' (dữ liệu nhị phân). |
| `body_part_type` | VARCHAR(50) | | Phân loại Body Part AMHS: ia5-text, general-text, file-transfer-body-part. |
| `content_type` | VARCHAR(100) | | MIME type của bản tin: text/plain, application/xml, application/octet-stream. |
| `message_signed` | VARCHAR(20) | | Phân loại chữ ký: 'signed' (Đã ký), 'unsigned' (Không ký), 'invalid-signature' (Lỗi ký). |
| `rejection_reason`| VARCHAR(64) | | Lý do bị từ chối nếu Broker SWIM không chấp nhận bản tin. |
| `rejection_diagnostic`| VARCHAR(64)| | Chẩn đoán kỹ thuật chi tiết về lỗi từ chối của Broker. |
| `amhs_delivery_report`| TINYINT(1)| | Cờ yêu cầu báo nhận AMHS: 0 (Không yêu cầu), 1 (Yêu cầu báo nhận). |
| `retry_count` | INT | | Tổng số lần hệ thống đã thực hiện thử lại (Retry) khi có lỗi đẩy SWIM. |
| `last_retry_at` | DATETIME | | Thời điểm gần nhất hệ thống thực hiện lệnh thử lại. |

### 2. Bảng `gwout_dispatch` (Log đẩy tin SWIM)
Ghi lại từng lần thử publish bản tin lên các Topic khác nhau trên SWIM Broker.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT(20) | **PK**, AI | Định danh duy nhất của mỗi lệnh đẩy tin SWIM. |
| `gwout_id` | BIGINT(20) | **FK (gwout)** | Tham chiếu vật lý tới bản tin cha trong bảng `gwout`. |
| `recipient` | VARCHAR(100) | NotNull | Tên Topic vật lý đích trên Broker (Ví dụ: `ats.met.metar`). |
| `message_type` | VARCHAR(50) | | Loại tin nghiệp vụ được ghi nhận tại thời điểm đẩy (METAR, TAF, FPL, v.v.). |
| `scope` | VARCHAR(10) | | Phạm vi định tuyến áp dụng: local, regional, global. |
| `topic` | VARCHAR(100) | | Tên Topic logic dùng trong quá trình xử lý định tuyến. |
| `amqp_account` | VARCHAR(50) | **FK (accounts)** | Tên tài khoản kết nối AMQP vật lý được dùng để publish. |
| `status` | VARCHAR(20) | Index | Trạng thái lệnh: PENDING (Chờ), SENT (Thành công), FAILED (Thất bại). |
| `retry_count` | INT | | Số lần đã thử thực hiện lệnh cụ thể này. |
| `next_retry_at` | DATETIME | | Hẹn giờ thời điểm hệ thống sẽ tự động thử lại sau khi gặp lỗi. |
| `last_error` | TEXT | | Thông tin lỗi chi tiết trả về từ Broker hoặc thư viện kết nối AMQP. |
| `failed_step` | VARCHAR(20) | | Bước nghiệp vụ bị lỗi (Ví dụ: CONNECT, AUTH, PUBLISH). |
| `created_at` | DATETIME | | Thời điểm tạo lệnh phân phối (First Trigger). |
| `updated_at` | DATETIME | | Thời điểm cập nhật trạng thái mới nhất cho lệnh phân phối này. |
| `sent_at` | DATETIME | | Thời điểm chính xác hệ thống nhận được ACK thành công từ SWIM Broker. |

### 3. Bảng `gwin` (Tin điện đến từ SWIM)
Lưu trữ các bản tin nhận được từ hạ tầng SWIM Broker để chuyển đổi sang AMHS.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `cpa` | VARCHAR(1) | | Confirmed Promptness: Trạng thái xác nhận xử lý nhanh (Y=Yes, N=No). |
| `msgid` | BIGINT(20) | **PK**, AI | Định danh duy nhất nội bộ cho bản tin từ SWIM. |
| `priority` | TINYINT(4) | | Độ ưu tiên AMQP trích xuất từ Message Header (0-9). |
| `time` | DATETIME | | Thời điểm SWIM Component nhận tin từ Message Broker. |
| `TEXT` | VARCHAR(3200)| | Nội dung Payload thô của bản tin (XML IWXXM/FIXM hoặc chuỗi ký tự). |
| `source` | VARCHAR(200) | | Tên Topic hoặc Queue vật lý mà tin này được lấy ra. |
| `subject` | VARCHAR(100) | | Loại tin do Gateway tự động nhận diện (METAR, NOTAM, SPECI...). |
| `amqp_properties`| TEXT | | Toàn bộ thuộc tính Header, Application Properties của AMQP dạng JSON. |
| `body_type` | VARCHAR(10) | | Kiểu dữ liệu nhận được: 'text' hoặc 'binary'. |
| `origin` | VARCHAR(200) | | Tên hoặc địa chỉ máy chủ SWIM nguồn phát bản tin này. |
| `message_id` | VARCHAR(255) | **Unique** | Global UUID định danh duy nhất bản tin trên toàn hạ tầng SWIM. |
| `address` | VARCHAR(250) | | Danh sách địa chỉ nhận AFTN đã được phân giải (Resolved Recipients). |
| `status` | INT(20) | Index | Trạng thái: 0:NEW, 1:PROCESS, 2:SENT, 3:ERROR, 5:UNROUTED. |
| `amqp_message_id`| VARCHAR(256)| | ID bản tin trích xuất từ thuộc tính `message-id` của AMQP. |
| `content_type` | VARCHAR(100) | | MIME type của tin SWIM nhận được (Ví dụ: `application/xml`). |
| `originator` | VARCHAR(128) | | Địa chỉ người gửi AMHS (X.400) được hệ thống chuyển đổi từ dữ liệu SWIM. |
| `addressing_source`| VARCHAR(200)| | Nguồn giải địa chỉ: RULE (Quy tắc), XML (Nội dung XML), AMQP (Metadata). |
| `rejection_reason`| VARCHAR(64) | | Lý do bị từ chối nếu MTA AMHS không chấp nhận tin điện. |
| `rejection_diagnostic`| VARCHAR(64)| | Chẩn đoán lỗi chi tiết từ hệ thống MTA/MSwitch. |
| `retry_count` | INT | | Số lần hệ thống đã thử đẩy tin vào mạng AMHS. |
| `last_retry_at` | DATETIME | | Thời điểm cuối cùng thực hiện lệnh thử gửi AMHS. |

### 4. Bảng `gwin_dispatch` (Log đẩy tin AMHS)
Chi tiết việc phân phối tin SWIM tới từng hộp thư (Mailbox) AMHS cụ thể.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT(20) | **PK**, AI | Khóa chính cho lệnh đẩy tin AMHS. |
| `gwin_id` | BIGINT(20) | **FK (gwin)** | Tham chiếu vật lý sang bảng `gwin` chứa nội dung tin gốc. |
| `amhs_address` | VARCHAR(100) | NotNull | Địa chỉ X.400 của một người nhận cụ thể trên mạng AMHS. |
| `amhs_account` | VARCHAR(50) | **FK (accounts)** | Tên tài khoản X.400 vật lý được sử dụng để gửi bản tin này. |
| `status` | VARCHAR(20) | Index | Trạng thái: PENDING (Đang chờ), SENT (Xong), FAILED (Lỗi). |
| `retry_count` | INT | | Số lần thử đẩy tin riêng cho người nhận này. |
| `next_retry_at` | DATETIME | | Hẹn giờ thời điểm sẽ thử lại lệnh cho người nhận này. |
| `last_error` | TEXT | | Lỗi chi tiết trả về từ Isode M-Switch API (MTA Submission). |
| `failed_step` | VARCHAR(20) | | Bước lỗi: CONNECT, VALIDATE_RECIPIENT, SUBMIT_MESSAGE. |
| `created_at` | DATETIME | | Thời điểm khởi tạo lệnh phân phối. |
| `updated_at` | DATETIME | | Lần cuối cập nhật trạng thái lệnh. |
| `sent_at` | DATETIME | | Thời điểm chính xác MTA chốt báo nhận Submission thành công. |

### 5. Bảng `gw_alert` (Cảnh báo vận hành)
Các thông báo lỗi hoặc cảnh báo cần sự can thiệp của con người trên Dashboard.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT(20) | **PK**, AI | Khóa chính cảnh báo. |
| `alert_type` | VARCHAR(30) | NotNull | Phân loại: CONN (Kết nối), DISK (Lưu trữ), APP (Chương trình), DB (Cơ sở dữ liệu). |
| `severity` | VARCHAR(10) | NotNull | Mức độ: INFO (Xanh), WARN (Vàng), ERROR (Cam), CRITICAL (Đỏ). |
| `message` | TEXT | | Mô tả chi tiết lỗi phát sinh giúp Operator biết cần làm gì. |
| `ref_table` | VARCHAR(50) | | Tên bảng dữ liệu có liên quan trực tiếp đến sự cố. |
| `ref_id` | BIGINT(20) | | ID của bản ghi cụ thể gây ra cảnh báo trên bảng ref_table. |
| `status` | VARCHAR(20) | Index | Quy trình xử lý: NEW (Mới), ACK (Đã xem), RESOLVED (Đã khắc phục). |
| `created_at` | DATETIME | | Thời điểm chính xác xảy ra sự cố được ghi nhận. |
| `acknowledged_at`| DATETIME | | Thời điểm Operator bấm nút xác nhận (Acknowledge). |
| `acknowledged_by`| VARCHAR(100)| | Tên hoặc tài khoản của Operator đã xử lý xác nhận. |
| `resolved_at` | DATETIME | | Thời điểm Operator đánh dấu đã xử lý triệt để xong xuôi. |

### 6. Bảng `message_conversion_log` (Nhật ký Chuyển đổi)
Lưu trữ tóm tắt các hành động xử lý log logic (lưu trữ 30 ngày).

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT(20) | **PK**, AI | Định danh duy nhất bản ghi log chuyển đổi nghiệp vụ. |
| `reference_id` | BIGINT(20) | | msgid tham chiếu tới bảng gwin hoặc gwout tương ứng. |
| `date` | VARCHAR(8) | Index | Ngày phát sinh (YYYYMMDD) dùng để tìm kiếm nhanh theo ngày. |
| `type` | VARCHAR(4) | | Thành phần xử lý: AMHS hoặc SWIM. |
| `category` | VARCHAR(12) | | Chiều xử lý: IN (Tin đến) hoặc OUT (Tin đi). |
| `message_id` | VARCHAR(256) | | MTS-Identifier của bản tin AMHS liên quan. |
| `ipm_id` | VARCHAR(256) | | IPM-Identifier của bản tin AMHS liên quan (EUR Doc 047). |
| `mts_id` | VARCHAR(256) | | MTS-Identifier phục vụ tra cứu báo nhận (EUR Doc 047). |
| `amqp_message_id`| VARCHAR(256)| | ID của bản tin tương ứng trên hạ tầng SWIM. |
| `priority` | VARCHAR(12) | | Độ ưu tiên ghi nhận được tại thời điểm xử lý. |
| `ohi` | VARCHAR(64) | | Thông tin Optional Heading trích xuất từ điện văn. |
| `origin` | VARCHAR(128) | | Địa chỉ người gửi bản tin gốc. |
| `filing_time` | VARCHAR(20) | | Filing Time (DDHHMM) trích xuất được. |
| `subject` | VARCHAR(512) | | Tiêu đề tóm tắt bản tin. |
| `content` | TEXT | | Tóm tắt sơ bộ nội dung Payload sau khi bóc tách. |
| `converted_time` | DATETIME | | Thời điểm quy trình chuyển đổi logic thực tế kết thúc. |
| `status` | VARCHAR(8) | | Kết quả Logic: OK (Thành công), ERROR (Lỗi), REJECT (Từ chối). |
| `action_taken` | VARCHAR(50) | | Tên Logic áp dụng (Ví dụ: `CONVERT_TAC_TO_IWXXM`). |
| `non_delivery_reason`| VARCHAR(64)| | Mã lý do NDR nếu hệ thống AMHS từ chối phát tin. |
| `non_delivery_diagnostic`| VARCHAR(64)| | Mã chẩn đoán chi tiết cho lỗi NDR. |
| `supplementary_info`| VARCHAR(512)| | Thông tin bổ trợ phục vụ hậu kiểm chi tiết. |
| `remark` | VARCHAR(256) | | Ghi chú thêm do hệ thống tự động ghi nhận. |

### 7. Bảng `message_archive` (Kho lưu trữ RAW)
Sao lưu nguyên trạng toàn bộ nội dung bản tin (lưu trữ 30 ngày).

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `uuid` | CHAR(36) | **PK** | Khóa chính chuẩn UUID cấp độ lưu trữ. |
| `msg_id` | VARCHAR(100) | | ID nội bộ để đối soát ngược lại gwin/gwout. |
| `mts_id` | VARCHAR(100) | | MTS Identifier của hạ tầng AMHS. |
| `ipm_id` | VARCHAR(100) | | IPM Identifier của hạ tầng AMHS. |
| `amqp_message_id`| VARCHAR(256)| | Message ID của hạ tầng SWIM. |
| `recipients` | TEXT | | Toàn bộ danh sách người nhận nguyên trạng. |
| `priority` | VARCHAR(2) | | Độ ưu tiên thực tế tại thời điểm lưu. |
| `direction` | VARCHAR(20) | | Phân loại hướng: AMHS_TO_SWIM hoặc SWIM_TO_AMHS. |
| `timestamp` | TIMESTAMP | Index | Thời điểm chính xác hệ thống Archive ghi nhận Payload. |
| `raw_content` | LONGTEXT | | Nội dung thô (Raw Payload) nguyên bản 100% không qua xử lý. |
| `processing_status`| VARCHAR(20)| | Trạng thái cuối cùng: ARCHIVED. |

### 8. Bảng `accounts` (Quản lý kết nối)
Quản lý các tài khoản để kết nối tới SWIM Broker hoặc AMHS MTA.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT(20) | **PK**, AI | Định danh duy nhất của tài khoản kết nối. |
| `account_name` | VARCHAR(50) | **Unique** | Tên gợi nhớ duy nhất (Ví dụ: SOLACE_PRIMARY). |
| `protocol` | VARCHAR(20) | NotNull | Giao thức truyền dẫn: AMQP hoặc X400. |
| `host` | VARCHAR(255) | | IP hoặc Hostname của máy chủ thành phần đích. |
| `port` | INTEGER | | Cổng kết nối (AMQP: 5672, X.400: 102). |
| `config_json` | TEXT | | Chứa Username, Password, VPN, ClientID, v.v. dạng JSON mã hóa. |
| `status` | VARCHAR(20) | | Logic Operator: ACTIVE (Kích hoạt), INACTIVE (Khóa tài khoản). |
| `bind_status` | VARCHAR(20) | | Vật lý thực tế: CONNECTED, DISCONNECTED, CONNECTING. |
| `certificate_path` | VARCHAR(500) | | Đường dẫn đến file chứng chỉ SSL/TLS nếu có. |
| `certificate_passphrase`| TEXT | | Mật khẩu giải mã chứng chỉ (Mã hóa AES-256). |
| `sasl_mechanism` | VARCHAR(20) | | Cơ chế xác thực: PLAIN, EXTERNAL, ANONYMOUS. |
| `tls_enabled` | TINYINT(1) | Default 0 | Trạng thái mã hóa: 0 (Không SSL), 1 (Có sử dụng SSL). |
| `signed_messages_action`| VARCHAR(30)| | Cách xử lý tin có chữ ký: KEEP (Giữ nguyên), STRIP (Xóa chữ ký). |
| `unsigned_messages_action`| VARCHAR(30)| | Cách xử lý tin không chữ ký: ACCEPT (Chấp nhận), REJECT (Từ chối). |

### 9. Bảng `routing` (Quy tắc định tuyến)
Trái tim của hệ thống Gateway, quyết định luồng đi của mọi bản tin.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | INT | **PK**, AI | Khóa chính tự tăng của quy tắc định tuyến. |
| `direction` | VARCHAR(3) | NotNull | Hướng áp dụng quy tắc: **'IN'** (SWIM -> AMHS) hoặc **'OUT'** (AMHS -> SWIM). |
| `receive_topic` | VARCHAR(100) | | Topic nguồn từ SWIM mà quy tắc này sẽ quét (chiều IN). |
| `message_filter`| VARCHAR(100) | | Chuỗi lọc nghiệp vụ (Ví dụ: METAR, VVHH, FPL). |
| `recipients` | VARCHAR(500) | | Địa chỉ AFTN nhận (cách nhau dấu cách) cho chiều IN. |
| `originator` | VARCHAR(8) | | Địa chỉ gửi giả định khi đẩy tin vào MTA cho chiều IN. |
| `message_type` | VARCHAR(50) | | Loại tin AMHS để Gateway nhận diện và bốc lên SWIM (chiều OUT). |
| `send_topic` | VARCHAR(100) | | Topic đích sẽ đẩy tin lên trên SWIM Broker (chiều OUT). |
| `priority` | INT | Default 100 | Độ ưu tiên thực thi quy tắc (Rule Priority). |
| `active` | BOOLEAN | Default 1 | Trạng thái bật/tắt của quy tắc định tuyến này. |
| `note` | TEXT | | Diễn giải hoặc ghi chú vận hành của Operator. |
| `created_at` | DATETIME | | Thời điểm hệ thống khởi tạo bản ghi quy tắc. |
| `updated_at` | DATETIME | | Thời điểm gần nhất có sự thay đổi cấu hình quy tắc. |
| `created_by` | VARCHAR(50) | **FK (cp_users)** | Tài khoản Operator thực hiện thiết lập quy tắc này. |

### 10. Bảng `performance_metrics` (Giám sát Hiệu năng)
Dữ liệu nhịp tim của hệ thống phục vụ Dashboard.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT(20) | **PK**, AI | Định danh của chu mẫu đo. |
| `timestamp` | TIMESTAMP | Index | Thời điểm lấy mẫu tài nguyên hệ thống. |
| `cpu_usage` | FLOAT | | Phần trăm CPU trung bình toàn Server tại thời điểm đo. |
| `heap_memory` | FLOAT | | Lượng RAM Java Heap đang bị chiếm dụng (Đơn vị: MB). |
| `msg_in_count` | INTEGER | | Tổng bản tin nhận được từ SWIM trong khoảng thời gian lấy mẫu. |
| `msg_out_count` | INTEGER | | Tổng bản tin đẩy lên SWIM thành công trong khoảng thời gian lấy mẫu. |
| `active_threads`| INTEGER | | Số lượng luồng (Threads) đồng thời đang xử lý trong Gateway. |

### 11. Bảng `gateway_config` (Cấu hình hệ thống)
Các tham số vận hành mềm có thể thay đổi trực tuyến (Runtime).

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `config_key` | VARCHAR(100) | **PK** | Mã tham số định danh duy nhất (Ví dụ: POLL_INTERVAL_MS). |
| `config_value` | VARCHAR(500) | | Giá trị thực tế của cấu hình đang áp dụng. |
| `description` | VARCHAR(500) | | Diễn giải chức năng tham số để tránh nhầm lẫn khi Operator cấu hình. |
| `updated_at` | DATETIME | | Lần cuối cùng tham số này được hiệu chỉnh. |

### 12. Bảng `system_log` (Nhật ký Hệ thống)
Nhật ký sự kiện kỹ thuật chi tiết của tất cả các thành phần phần mềm.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `uuid` | CHAR(36) | **PK** | UUID định danh bản ghi log. |
| `timestamp` | DATETIME | Index | Thời điểm chính xác xảy ra sự kiện. |
| `level` | VARCHAR(10) | Index | Mức độ kỹ thuật: INFO, WARN, ERROR, DEBUG. |
| `module` | VARCHAR(30) | Index | Tên Module phát sinh: SWIM_COMPONENT, AMHS_COMPONENT, CP, ITCU. |
| `content` | TEXT | | Nội dung thông điệp kỹ thuật từ mã nguồn chương trình. |
| `status` | VARCHAR(10) | | Trạng thái hiển thị trên giao diện quản trị: READ hoặc UNREAD. |

### 13. Bảng `message_type_registry` (Mẫu Loại tin)
Quản lý các từ khóa để Gateway tự động nhận diện điện văn.

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT(20) | **PK**, AI | Khóa chính mẫu tin. |
| `message_type` | VARCHAR(50) | **Unique** | Tên loại tin chuẩn hàng không (Ví dụ: METAR, TAF, SPECI). |
| `detect_pattern`| VARCHAR(255)| | Biểu thức Regex hoặc từ khóa mồi để nhận dạng (Ví dụ: `<iwxxm:METAR`). |
| `difficulty` | VARCHAR(10) | | Chỉ số bộ nhớ để đánh giá độ phức tạp xử lý. |
| `phase` | TINYINT(4) | | Giai đoạn triển khai tính năng nhận diện loại tin này. |
| `active` | TINYINT(1) | Default 1 | Bật hoặc tắt tính năng tự động nhận diện loại tin cụ thể này. |
| `note` | VARCHAR(500) | | Ghi chú kỹ thuật chi tiết về cấu trúc mẫu tin. |

### 14. Bảng `cp_users` (Quản trị viên)
Quản lý quyền truy cập vào bảng điều khiển Dashboad (Control Position).

| Cột | Kiểu SQL | Ràng buộc | Mô tả Nghiệp vụ Chi tiết |
| :--- | :--- | :--- | :--- |
| `uuid` | VARCHAR(36) | **PK** | UUID định danh người dùng. |
| `username` | VARCHAR(50) | **Unique** | Tên đăng nhập duy nhất không phân biệt hoa thường. |
| `password` | VARCHAR(255) | | Mật khẩu bảo mật đã được băm bằng thuật toán BCrypt cường độ cao. |
| `role` | VARCHAR(20) | | Nhóm quyền: **ADMIN** (Điều trị hệ thống), **OPERATOR** (Vận hành tin điện). |
| `created_at` | DATETIME | | Ngày khởi tạo người dùng trong hệ thống Gateway. |
| `last_login` | DATETIME | | Thời điểm cuối cùng tài khoản đăng nhập thành công vào Dashboard. |