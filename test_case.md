## I. HƯỚNG DẪN 
1. **Môi trường kiểm thử**: Cơ sở dữ liệu MySQL `asg_db`, dịch vụ `gateway-swim` đang chạy.
2. **Nguyên tắc thực hiện**:
   - Đối với **Chiều đi (CTSW001 - CTSW020)**: Chạy câu lệnh SQL `INSERT` tương ứng vào bảng `gwout` -> Chờ 2 giây -> Chạy câu lệnh `SELECT` để kiểm tra trạng thái và chuỗi JSON đã dịch.
   - Đối với **Chiều về (CTSW101 - CTSW116)**: Thực hiện chèn bản tin SWIM JSON tương ứng vào Solace Broker (hoặc giả lập chèn vào bảng `gwin`) -> Chờ 2 giây -> Kiểm tra bản tin phong bì X.400 được sinh ra trong bảng `gwin`/`gwin_dispatch`.

---

## II. CHI TIẾT KỊCH BẢN KIỂM THỬ CHIỀU ĐI (AMHS -> SWIM)

### CTSW001: Convert Incoming IPM with Filing Time
* **Mục đích kiểm thử**: Kiểm tra Gateway trích xuất chính xác thời gian nộp điện (`filing_time = '070430'`, tức ngày 07 lúc 04:30 UTC) từ phong bì điện văn AMHS và ánh xạ vào trường `"ats_message_filing_time"` trong chuỗi SWIM JSON.
* **Điều kiện tiên quyết**: Bảng `routing` có quy tắc `OUT` hỗ trợ loại điện `METAR` (hoặc `METAR_TEXT`).
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, `TEXT`) 
VALUES (
    'TC-CTSW001', 
    'GG', 
    NOW(), 
    '070430', 
    'VVNBZTZX', 
    'VVHHZTZX', 
    'text', 
    'application/json', 
    0,
    'ZCZC ABC001

FF VVHHZTZX

070430 VVNBZTZX

METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG='
);
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW001';
```
* **Kết quả mong muốn**:
  - `status = 4` (`OUT_PUBLISHED` - Chuyển đổi và phát tin thành công).
  - Chuỗi JSON trong `payload_content` chứa thuộc tính `"ats_message_filing_time": "070430"`.

---

### CTSW002: Convert Incoming IPM with OHI
* **Mục đích kiểm thử**: Kiểm tra trích xuất trường Tiêu đề phụ tùy chọn (`optional_heading = 'OHI-TEST-DATA-123'`) từ điện AMHS và nhúng vào thuộc tính `"ats_message_optional_heading"` của SWIM JSON theo chuẩn ICAO EUR Doc 047.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text, optional_heading) 
VALUES ('TC-CTSW002', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC ABC002

FF VVHHZTZX

070430 VVNBZTZX

METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=', 'OHI-TEST-DATA-123');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW002';
```
* **Kết quả mong muốn**:
  - `status = 4` (`OUT_PUBLISHED`).
  - Trong JSON tại `payload_content` xuất hiện `"ats_message_optional_heading": "OHI-TEST-DATA-123"`.

---

### CTSW003: Generate Delivery Report (DR)
* **Mục đích kiểm thử**: Kiểm tra tính năng tự động sinh Báo cáo phát trả (Delivery Report - DR) quay ngược trở lại phía người gửi AMHS sau khi Gateway chuyển đổi và đẩy điện văn lên mạng SWIM thành công.
* **Câu lệnh nạp dữ liệu (Input)**: Chạy câu lệnh nạp dữ liệu của `CTSW001`.
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT * FROM gwin WHERE payload_content LIKE '%DeliveryReport%' OR text LIKE '%DR%' ORDER BY msgid DESC LIMIT 1;
```
* **Kết quả mong muốn**: Có bản ghi DR mới được sinh tự động trong bảng `gwin` với địa chỉ nhận đúng bằng người gửi gốc của điện văn.

---

### CTSW004: Generate Non-Delivery Report (NDR)
* **Mục đích kiểm thử**: Kiểm tra cơ chế xử lý ngoại lệ và sinh Báo cáo không phát trả (Non-Delivery Report - NDR) khi tiếp nhận bản tin AMHS bị lỗi cú pháp nghiêm trọng.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW004', 1, NOW(), '070430', 'VVTSZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'(FPL-LỖI-CÚ-PHÁP-HOÀN-TOÀN');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW004';
```
* **Kết quả mong muốn**:
  - `status = 5` (`OUT_FAILED`).
  - Trường `payload_content` chứa chuỗi log chi tiết: `Validation failed: Syntax error...`.

---

### CTSW005: Convert Incoming IPM with Subject
* **Mục đích kiểm thử**: Kiểm tra Gateway bảo toàn trường Tiêu đề (`subject = 'FLIGHT ADVISORY'`) từ thông điệp AMHS và ánh xạ vào trường `"subject"` trong JSON.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text, subject) 
VALUES ('TC-CTSW005', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=', 'FLIGHT ADVISORY');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW005';
```
* **Kết quả mong muốn**:
  - `status = 4` (`OUT_PUBLISHED`).
  - Chuỗi JSON trong `payload_content` chứa thuộc tính `"subject": "FLIGHT ADVISORY"`.

---

### CTSW006: Reject IPM Exceeding Max Size
* **Mục đích kiểm thử**: Kiểm tra tính năng bảo vệ Gateway khỏi tấn công từ chối dịch vụ (DoS) bằng cách từ chối các điện văn có dung lượng vượt quá cấu hình `MAX_PAYLOAD_SIZE`.
* **Điều kiện tiên quyết**: Đặt cấu hình `UPDATE gateway_config SET config_value = '100' WHERE config_key = 'MAX_PAYLOAD_SIZE';` (Giới hạn tối đa 100 Bytes).
* **Câu lệnh nạp dữ liệu (Input)**: Chèn bản tin dài 150 Bytes:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW006', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC ABC001 FF VVHHZTZX 070430 VVNBZTZX METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG= RẤT DÀI RẤT DÀI RẤT DÀI RẤT DÀI RẤT DÀI RẤT DÀI');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW006';
```
* **Kết quả mong muốn**:
  - `status = 5` (`OUT_FAILED`).
  - `payload_content` chứa thông báo: `Rejected: Message size exceeds maximum allowed payload size`.

---

### CTSW007: Reject IPM with Multiple Body Parts
* **Mô tả**: Gateway không hỗ trợ X.400 Multipart chứa nhiều body parts.
* **Kết quả mong muốn**: Khi luồng đồng bộ đẩy tin Multipart từ `mtcu_tmp` có số lượng body part > 1 -> Đánh dấu `status = 5` (FAILED).

---

### CTSW008: Reject IPM with Unsupported Content-Type
* **Mục đích kiểm thử**: Kiểm tra bộ lọc loại nội dung Content-Type. Gateway từ chối các điện văn có định dạng không nằm trong danh sách được phép `ALLOWED_CONTENT_TYPES`.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW008', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/unknown-mime-type', 0,
'METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW008';
```
* **Kết quả mong muốn**:
  - `status = 5` (`OUT_FAILED`).
  - `payload_content = 'Unsupported Content-Type'`.

---

### CTSW009: Distribute IPM to AMHS and AMQP
* **Mục tiêu**: Bản tin gửi đồng thời cho cả người dùng hàng không AMHS và Solace SWIM.
* **Kết quả mong muốn**: Hệ thống lưu bản copy trong `gwin` (để gửi đi mạng AMHS) và publish thành công lên Solace Broker.

---

### CTSW010: Reject IPM addressing More AMQP Consumers Than Max
* **Mục đích kiểm thử**: Kiểm tra giới hạn số lượng địa chỉ người nhận (`address`) trên một điện văn.
* **Điều kiện tiên quyết**: Đặt cấu hình `UPDATE gateway_config SET config_value = '2' WHERE config_key = 'MAX_RECIPIENTS';` (Cho phép tối đa 2 địa chỉ nhận).
* **Câu lệnh nạp dữ liệu (Input)**: Chèn bản tin gửi cho 3 địa chỉ (`VVHHZTZX, VVTSZTZX, VVDNZTZX`):
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW010', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX, VVTSZTZX, VVDNZTZX', 'text', 'application/json', 0,
'METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW010';
```
* **Kết quả mong muốn**:
  - `status = 5` (`OUT_FAILED`).
  - `payload_content` ghi nhận lỗi từ chối do vượt quá số người nhận cho phép.

---

### CTSW011: Probe Conveyance Test
* **Mục đích kiểm thử**: Kiểm tra tính năng tiếp nhận và xử lý điện Probe (điện kiểm thử đường truyền từ mạng AMHS).
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW011', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'probe', 'application/json', 0, 'PROBE REQUEST');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW011';
```
* **Kết quả mong muốn**:
  - `status = 4` (`OUT_PUBLISHED`).
  - `payload_content` chứa thông báo: `DR: Probe verified successfully. Delivery Report generated.`.

---

### CTSW012: Reject Probe for Unknown Recipients
* **Mục đích kiểm thử**: Kiểm tra từ chối bản tin Probe khi địa chỉ người nhận (`ZZZZZTZX`) không tồn tại trong cấu hình định tuyến của hệ thống.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW012', 2, NOW(), '070430', 'VVNBZTZX', 'ZZZZZTZX', 'probe', 'application/json', 0, 'PROBE REQUEST TO UNKNOWN');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW012';
```
* **Kết quả mong用途**:
  - `status = 5` (`OUT_FAILED`).
  - `payload_content` ghi nhận NDR: `NDR: Unknown recipient: ZZZZZTZX`.

---

### CTSW013: Reject Probe with Unknown Originator Address
* **Mục đích kiểm thử**: Kiểm tra từ chối điện Probe xuất phát từ một người gửi không xác định (`UNKNOWNZTZX`).
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW013', 2, NOW(), '070430', 'UNKNOWNZTZX', 'VVHHZTZX', 'probe', 'application/json', 0, 'PROBE REQUEST FROM UNKNOWN');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW013';
```
* **Kết quả mong muốn**:
  - `status = 5` (`OUT_FAILED`).
  - `payload_content` ghi nhận NDR: `NDR: Unknown originator 'UNKNOWNZTZX'`.

---

### CTSW014: Process RN for Priority != 'SS'

* **Mô tả**:
Kiểm tra Gateway xử lý bản tin Receipt Notification (RN) đối với bản tin gốc có mức ưu tiên thông thường (không phải `SS`). Khi nhận được RN hợp lệ, Gateway phải cập nhật trạng thái của bản tin gốc thành đã phát nhận thành công.

* **Điều kiện tiên quyết**:
- Trong bảng `gwout` đã tồn tại bản tin gốc có:
  - `amhsid = 'MSG-001'`
  - `status = 3`
  - `amhs_priority = 2` (FF/GG)

* **Câu lệnh nạp dữ liệu (Input)**:

```sql
INSERT INTO gwout
(amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES
('TC-CTSW014', 2, NOW(), '070430', 'VVHHZTZX', 'VVNBZTZX', 'ia5-text-body-part', 'application/json', 0, 'RN FOR MESSAGE MSG-001');
```

* **Thao tác kiểm tra**:

Kiểm tra bản tin RN:

```sql
SELECT status FROM gwout WHERE amhsid = 'TC-CTSW014';
```

Kiểm tra bản tin gốc:

```sql
SELECT status FROM gwout WHERE amhsid = 'MSG-001';
```

* **Kết quả mong muốn**:

- Bản tin RN được xử lý thành công (`status = 4`).
- Gateway xác định đúng bản tin gốc.
- Trạng thái bản tin gốc được cập nhật sang trạng thái "Delivered/Received" theo quy ước của hệ thống.
- Không phát sinh lỗi trong quá trình cập nhật.

---

### CTSW015: Process RN without Subject

* **Mô tả**:
Kiểm tra Gateway xử lý bản tin Receipt Notification (RN) khi tham chiếu đến một bản tin gốc không tồn tại trong hệ thống.

* **Điều kiện tiên quyết**:
- Không tồn tại bản tin có `amhsid = 'UNKNOWN-MSG'`.

* **Câu lệnh nạp dữ liệu (Input)**:

```sql
INSERT INTO gwout
(amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES ('TC-CTSW015', 2, NOW(), '070430', 'VVHHZTZX', 'VVNBZTZX', 'ia5-text-body-part', 'application/json', 0, 'RN FOR MESSAGE UNKNOWN-MSG' );
```

* **Thao tác kiểm tra**:

```sql
SELECT status FROM gwout WHERE amhsid = 'TC-CTSW015';
```

* **Kết quả mong muốn**:

- Gateway xử lý an toàn, không bị dừng hoặc phát sinh Exception.
- Bản tin RN được ghi nhận theo cơ chế của hệ thống (thành công hoặc lỗi tùy quy định).
- Không có bản ghi nào khác bị cập nhật trạng thái.
- Có log cảnh báo không tìm thấy bản tin gốc (`UNKNOWN-MSG`).
- Gateway tiếp tục xử lý các bản tin khác bình thường.

---

### CTSW016: Process EIT (Body Part Code 401)
* **Mục đích kiểm thử**: Kiểm tra tính năng tự động chuyển đổi chuẩn hóa mã số thô ISODE (`body_part_type = '401'`) thành chuỗi định dạng chuẩn ICAO `ia5-text-body-part`.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text) 
VALUES ('TC-CTSW016', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', '401', 'application/json', 0,
'METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, body_part_type, payload_content FROM gwout WHERE amhsid = 'TC-CTSW016';
```
* **Kết quả mong muốn**:
  - `status = 4` (`OUT_PUBLISHED`).
  - Giá trị trong cột `body_part_type` được chuyển thành `ia5-text-body-part`.

---

### CTSW017: Convert IPM with ia5-text
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text) 
VALUES ('TC-CTSW017', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'ia5-text-body-part', 'application/json', 0,
'METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=');
```
**Kết quả mong muốn:**
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW017';
```

- Bản ghi được xử lý thành công (`status = 4` - OUT_PUBLISHED).
- Trường `payload_content` được sinh ra ở định dạng JSON.
- JSON chứa:
  - `messageType = "METAR"`
  - `originalTac` đúng bằng nội dung METAR đầu vào.
  - `stationIcao = "VVNB"`
  - `observationTime = "070430Z"`
  - `nil = false`

---

### CTSW018: Convert IPM with General-Text (ISO 646)
* **Mục đích kiểm thử**: Kiểm tra khả năng tiếp nhận điện văn mã hóa bảng mã General Text (ISO 646) dạng `content_type = 'text/plain'` và chuyển đổi sang UTF-8 SWIM JSON chuẩn xác.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES ('TC-CTSW018', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'general-text-body-part', 'text/plain', 0, 
'THIS IS A GENERAL TEXT MESSAGE USING ISO 646 CHARACTER SET.');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW018';
```
* **Kết quả mong muốn**:
  - `status = 4` (`OUT_PUBLISHED`).
  - `payload_content` chứa chuỗi JSON hợp lệ với nội dung giải mã đúng từ ISO 646 sang UTF-8, không có ký tự lỗi.

---

### CTSW019: Convert IPM with General-Text (Non-ISO 646)
* **Mô tả**: Thử nghiệm bản tin mã hóa General Text phi tiêu chuẩn.
* **Câu lệnh nạp dữ liệu (Input)**:

```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES ('TC-CTSW019', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'general-text-body-part', 'text/plain; charset=UTF-8', 0,
'General text with UTF-8 characters: Café München 北京 Hà Nội ✈');
```

* **Thao tác kiểm tra**:

```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW019';
```

* **Kết quả mong muốn**: Gateway thực hiện giải mã ký tự an toàn và lưu vào JSON bình thường.

---

### CTSW020: Notify SS Message to Control Position
* **Mục đích kiểm thử**: Kiểm tra xử lý ưu tiên cho bản tin khẩn nguy (`amhs_priority = 'SS'`). Gateway ưu tiên chuyển đổi, đồng thời tự động phát sinh sự kiện cảnh báo đỏ hiển thị lên Dashboard Control Position.
* **Câu lệnh nạp dữ liệu (Input)**:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW020', 'SS', NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC ALR001

SS VVHHZTZX

070430 VVNBZTZX

ALR TYPE A');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW020';
```
* **Kết quả mong muốn**:
  - `status = 4` (`OUT_PUBLISHED`).
  - Bản tin được phát sinh sự kiện cảnh báo ưu tiên khẩn nguy `SS` gửi sang màn hình điều hành Control Position.

---

## III. CHI TIẾT KỊCH BẢN KIỂM THỬ CHIỀU VỀ (SWIM -> AMHS)

### CTSW101: Convert AMHS Unaware Message
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId" : "FPL_TEXT_1783310399799",
  "messageType" : "FPL",
  "timestamp" : 1783310399799,
  "aircraftId" : "HVN679",
  "flightRules" : "I",
  "flightType" : "S",
  "aircraftType" : "A321",
  "wakeTurbulence" : "M",
  "equipment" : "SDFGHIRWYZ/EB1",
  "departureIcao" : "VVTS",
  "eobt" : "0825",
  "cruisingSpeed" : "N0457",
  "cruisingLevel" : "F340",
  "route" : "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
  "destinationIcao" : "WMKK",
  "totalEet" : "0138",
  "altDestination1" : "WMKJ",
  "otherInfo" : "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
  "dof" : "260706",
  "registration" : "VNA326",
  "pbn" : "A1B1C1D1L1O1S2",
  "eet" : "WSJC0042 WMFC0053",
  "selcal" : "KSFL",
  "operator" : "HVN",
  "remarks" : "TCAS",
  "navCapabilities" : "RNP2"
}
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, text, priority, time FROM gwin WHERE message_id = 'TC-CTSW101' OR payload_content LIKE '%TC-CTSW101%';
```
* **Kết quả mong muốn**: Tạo thành công bản ghi trong bảng `gwin` chứa phong bì mặc định và nội dung văn bản hàng không (TAC) hoàn chỉnh.

---

### CTSW102: Reject AMQP Message Lacking Minimum Info
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId" : "FPL_TEXT_1783310399799",
  "messageType" : "FPL",
  "priority": 10,
  "timestamp" : 1783310399799,
  "aircraftId" : "HVN679",
  "flightRules" : "I",
  "flightType" : "S",
  "aircraftType" : "A321",
  "wakeTurbulence" : "M",
  "equipment" : "SDFGHIRWYZ/EB1",
  "departureIcao" : "VVTS",
  "eobt" : "0825",
  "cruisingSpeed" : "N0457",
  "cruisingLevel" : "F340",
  "route" : "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
  "destinationIcao" : "WMKK",
  "totalEet" : "0138",
  "altDestination1" : "WMKJ",
  "otherInfo" : "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
  "dof" : "260706",
  "registration" : "VNA326",
  "recipients": "VVVVNVNV",
  "pbn" : "A1B1C1D1L1O1S2",
  "eet" : "WSJC0042 WMFC0053",
  "selcal" : "KSFL",
  "operator" : "HVN",
  "remarks" : "TCAS",
  "navCapabilities" : "RNP2",
  "creationTime": null
}
```
* **Kết quả mong muốn**: Gateway từ chối xử lý, không ghi vào bảng `gwin` và phản hồi thông báo lỗi về Solace Broker.

---

# CTSW103: Conversion to IPM According to ATSMHS Service Level Parameter

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng chuyển đổi một thông điệp AMQP sang điện văn AMHS (IPM - Interpersonal Message) của IUT (Implementation Under Test) dựa trên tham số **`ATSMHS Service Level`** (`basic`, `extended`, `content-based`, `recipient-based`).

### Tiêu chí Đánh giá:
* **Chuyển đổi Basic Service Level**:
  * `amhs_ats_ohi` $\rightarrow$ Thể hiện trong thuộc tính `ATS-message-Optional-Heading-Info`.
  * `amhs_ats_ft` $\rightarrow$ Thể hiện trong thuộc tính `ATS-message-Filing-Time`.
  * `amhs_ats_pri` $\rightarrow$ Mapped thành `priority-indicator` trong `ATS-message-priority` (theo Table 9 - EUR Doc 047).
  * `amqp-value` $\rightarrow$ Mapped thành nội dung văn bản `ATS-message-text`.
* **Từ chối Binary trong Basic Level**:
  * Nếu message chứa nội dung binary khi chạy ở Basic Level $\rightarrow$ **Reject bản tin**, ghi log và cảnh báo lên màn hình giám sát (Control Position).
* **Chuyển đổi Extended Service Level**:
  * `amhs_ats_ohi` $\rightarrow$ Thể hiện trong `originators-reference` thuộc IPM Heading.
  * `amhs_ats_ft` $\rightarrow$ Thể hiện trong `Authorization-time` (định dạng `DDhhmm`).
  * `amhs_ats_pri` $\rightarrow$ Mapped thành `precedence` trong `recipient-specifier` (theo Table 9 - EUR Doc 047).
  * `precedence-policy-identifier` $\rightarrow$ Đạt giá trị cố định `1.3.27.8.0.0`.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi chuỗi 7 thông điệp AMQP tới IUT giải quyết cho remote AMHS user.

### A. Nhóm ATSMHS Service Level – Basic

*   **Case 1: Basic Service Level với Text Message (Thành công)**
    *   **Tham số**: `atsmhs_service_level = "basic"`, `content-type = "text/plain; charset=\"utf-8\""`
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW103-01",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "atsmhs_service_level": "basic",
        "amhs_ats_ohi": "VVTSYFYX",
        "amhs_ats_ft": "260825",
        "amhs_ats_pri": "FF"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

*   **Case 2: Basic Service Level với Binary Content (Bị Reject)**
    *   **Tham số**: `atsmhs_service_level = "basic"`, `content-type = "application/octet-stream"`
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW103-02",
        "creation-time": 1783310399799,
        "content-type": "application/octet-stream"
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "atsmhs_service_level": "basic",
        "amhs_ats_ohi": "VVTSYFYX",
        "amhs_ats_ft": "260825",
        "amhs_ats_pri": "FF"
      },
      "data": "A1B2C3D4E5F67890"
    }
    ```

---

### B. Nhóm ATSMHS Service Level – Extended

*   **Case 3: Extended Service Level (Thành công)**
    *   **Tham số**: `atsmhs_service_level = "extended"`, `content-type = "text/plain; charset=\"utf-8\""`
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW103-03",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "atsmhs_service_level": "extended",
        "amhs_ats_ohi": "VVTSYFYX",
        "amhs_ats_ft": "260825",
        "amhs_ats_pri": "FF"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

### C. Nhóm ATSMHS Service Level – Content Based

*   **Case 4: Content-based $\rightarrow$ Tự động chuyển thành Extended**
    *   **Tham số**: `atsmhs_service_level = "content-based"`, `content-type = "application/octet-stream"` (hoặc định dạng hỗ trợ Extended)
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW103-04",
        "creation-time": 1783310399799,
        "content-type": "application/octet-stream"
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "atsmhs_service_level": "content-based",
        "amhs_ats_ohi": "VVTSYFYX",
        "amhs_ats_ft": "260825",
        "amhs_ats_pri": "FF"
      },
      "data": "A1B2C3D4E5F67890"
    }
    ```

*   **Case 5: Content-based $\rightarrow$ Tự động chuyển thành Basic**
    *   **Tham số**: `atsmhs_service_level = "content-based"`, `content-type = "text/plain; charset=\"utf-8\""`
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW103-05",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "atsmhs_service_level": "content-based",
        "amhs_ats_ohi": "VVTSYFYX",
        "amhs_ats_ft": "260825",
        "amhs_ats_pri": "FF"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

### D. Nhóm ATSMHS Service Level – Recipient Based

*   **Case 6: Recipient-based $\rightarrow$ Extended (Tất cả người nhận đều hỗ trợ Extended Format)**
    *   **Tham số**: `atsmhs_service_level = "recipient-based"`, Recipient thuộc danh sách hỗ trợ Extended.
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW103-06",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX", "VVTNZEZX"],
        "atsmhs_service_level": "recipient-based",
        "amhs_ats_ohi": "VVTSYFYX",
        "amhs_ats_ft": "260825",
        "amhs_ats_pri": "FF"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

*   **Case 7: Recipient-based $\rightarrow$ Basic (Có ít nhất 1 người nhận KHÔNG hỗ trợ Extended Format)**
    *   **Tham số**: `atsmhs_service_level = "recipient-based"`, Có 1 recipient chỉ hỗ trợ Basic format.
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW103-07",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX", "OLD_BASIC_USER"],
        "atsmhs_service_level": "recipient-based",
        "amhs_ats_ohi": "VVTSYFYX",
        "amhs_ats_ft": "260825",
        "amhs_ats_pri": "FF"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

## 3. Kết quả Mong muốn (Expected Results)

### Database (`gwin`)
Lệnh kiểm tra bảng `gwin`:
```sql
SELECT status, text, priority, time, body_type FROM gwin WHERE payload_content LIKE '%TC-CTSW103%';
```

---

# CTSW104: Convert Incoming AMQP Messages with Different Priorities to AMHS

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng chuyển đổi độ ưu tiên (**Priority Mapping**) chính xác từ AMQP Message sang AMHS Message (IPM) dựa theo **Table 9 - EUR Doc 047**.

### Quy tắc Mapping & Thứ tự Ưu tiên (Precedence Rule):
1. **Bảng ánh ánh giá trị (Table 9 - EUR Doc 047)**:
   * AMQP Priority `0..9` / `amhs_ats_pri` được chuyển đổi tương ứng sang các mức ưu tiên của AMHS: **SS** (Emergency), **DD** (Distress/Urgent), **FF** (Flight Safety), **GG** (Meteorological/Routine), **KK** (Administrative).
2. **Quy tắc đè thuộc tính (Overriding Rule)**:
   * Nếu điện văn **CÓ** chứa thuộc tính `amhs_ats_pri` trong *Application Properties*, Gateway **bắt buộc phải dùng giá trị `amhs_ats_pri` này để quyết định độ ưu tiên AMHS**, ghi đè (takes precedence over) lên giá trị `priority` nằm trong AMQP Header.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

Dựa theo kịch bản thử nghiệm, SWIM Test Tool sẽ gửi tổng cộng **20 AMQP Messages** chia làm 4 nhóm thử nghiệm:

---

### Nhóm 1: Kiểm thử 10 AMQP Priority từ 0 đến 9 (10 Messages)
Mỗi message mang giá trị `priority` từ `0` đến `9` trong AMQP Header (không khai báo `amhs_ats_pri`).

*   **Payload mẫu (Ví dụ Case Priority = 0; các case từ 1-9 thay đổi tương tự)**:
    ```json
    {
      "header": { "priority": 0 },
      "properties": {
        "message-id": "TC-CTSW104-GRP1-P0",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"]
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

### Nhóm 2: AMQP Priority mặc định (4) kết hợp với các giá trị `amhs_ats_pri` (5 Messages)
Kiểm tra với Header `priority = 4` cố định và thử nghiệm 5 giá trị `amhs_ats_pri`: `SS`, `DD`, `FF`, `GG`, `KK`.

*   **Payload mẫu (Ví dụ case `amhs_ats_pri` = "SS")**:
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW104-GRP2-SS",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_pri": "SS"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

### Nhóm 3: AMQP Priority thấp (1) kết hợp với các giá trị `amhs_ats_pri` (4 Messages)
Kiểm tra với Header `priority = 1` và 4 giá trị `amhs_ats_pri`: `SS`, `DD`, `FF`, `GG`.

*   **Payload mẫu (Ví dụ case `amhs_ats_pri` = "FF")**:
    ```json
    {
      "header": { "priority": 1 },
      "properties": {
        "message-id": "TC-CTSW104-GRP3-FF",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_pri": "FF"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

### Nhóm 4: Kiểm thử Đè thuộc tính (Precedence Check - 1 Message)
Gửi AMQP Priority cao nhất (`priority = 9`) nhưng khai báo `amhs_ats_pri = "KK"` (mức ưu tiên thấp nhất). Kiểm tra IUT có ưu tiên lấy thuộc tính `amhs_ats_pri` hay không.

*   **Payload mẫu**:
    ```json
    {
      "header": { "priority": 9 },
      "properties": {
        "message-id": "TC-CTSW104-GRP4-PRECEDENCE",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_pri": "KK"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

## 3. Kết quả Mong muốn (Expected Results)

### Lệnh kiểm tra Database (`gwin`)
```sql
SELECT msgid, priority, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW104%' 
ORDER BY msgid ASC;
```

---


# CTSW105: Convert Incoming AMHS Aware AMQP Messages Containing Filing Time to AMHS

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng xử lý và chuyển đổi thuộc tính thời gian nộp điện văn (**Filing Time**) từ AMQP Message sang AMHS Message (IPM) của IUT (Implementation Under Test) theo quy định tại **EUR Doc 047 – Mục 4.5.2.10.1.b**.

### Quy tắc Chuyển đổi Filing Time (`amhs_ats_ft`):
1. **Trường hợp 1 (Rỗng `amhs_ats_ft`)**:
   * Nếu thuộc tính `amhs_ats_ft` bị thiếu hoặc rỗng, Gateway phải tự động lấy thời gian tạo điện văn (**`creation-time`** từ AMQP Properties), chuyển đổi sang định dạng **`DDhhmm`** (NgàyHHMM) để điền vào phần header AMHS thích hợp (`ATS-message-header` cho Basic Level hoặc `Authorization-time` cho Extended Level).
2. **Trường hợp 2 (Có `amhs_ats_ft`)**:
   * Nếu thuộc tính `amhs_ats_ft` có giá trị (ví dụ: `"250102"`), Gateway lấy chính xác giá trị chuỗi này để chuyển tiếp sang phần header AMHS tương ứng (`ATS-message-Filing-Time` trong `ATS-message-header` hoặc `Authorization-time` tùy theo ATSMHS Service Level).

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi chuỗi **02 AMQP Messages** tới IUT giải quyết cho remote AMHS user.

---

### Case 1: AMQP Message không chứa `amhs_ats_ft` (Lấy từ `creation-time`)
*   **Đặc điểm**: Bỏ rỗng thuộc tính `amhs_ats_ft`. Giá trị `creation-time` = `1783310399799` (tương ứng thời gian Epoch MS $\rightarrow$ ngày 06, 03:59 UTC).
*   **Kỳ vọng**: Filing Time đầu ra tự động tính toán từ `creation-time` và đạt giá trị dạng **`DDhhmm`** (`060359`).

```json
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW105-01",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_ats_ft": ""
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}

---

# CTSW106: Convert an AMHS Aware AMQP Message Containing Optional Heading Information

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng xử lý, chuyển đổi và cắt ngắn (**Trimming Logic**) thuộc tính thông tin tiêu đề bổ sung (**Optional Heading Information - `amhs_ats_ohi`**) từ AMQP Message sang AMHS Message (IPM) theo quy định tại **EUR Doc 047 – Mục 4.5.2.11**.

### Quy tắc Giới hạn Độ dài & Cắt chuỗi (`amhs_ats_ohi` Trimming Rule):
Độ dài tối đa cho phép của chuỗi `amhs_ats_ohi` phụ thuộc vào độ ưu tiên (**Priority**) của điện văn:
1. **Đối với Priority = 4**:
   * Giới hạn tối đa: **53 ký tự**.
   * Nếu độ dài chuỗi `amhs_ats_ohi` $> 53$ ký tự $\rightarrow$ Gateway phải tự động **cắt gọn (trim) về đúng 53 ký tự đầu tiên**.
2. **Đối với Priority = 6**:
   * Giới hạn tối đa: **48 ký tự**.
   * Nếu độ dài chuỗi `amhs_ats_ohi` $> 48$ ký tự $\rightarrow$ Gateway phải tự động **cắt gọn (trim) về đúng 48 ký tự đầu tiên**.
3. **Ánh xạ trường đầu ra**:
   * **Extended Service Level**: Chuyển thành trường `originators-reference` trong IPM Heading.
   * **Basic Service Level (Optional)**: Chuyển thành trường `ATS-Message-OptionalHeading-Info` trong `ATS-Message-Header`.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi chuỗi **06 AMQP Messages** tới IUT chia làm 2 nhóm theo độ ưu tiên:

---

### Nhóm 1: Priority = 4 (Giới hạn 53 ký tự)

*   **Case 1: Priority 4, `amhs_ats_ohi` < 53 ký tự (Ví dụ: 30 ký tự)**
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW106-01",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_ohi": "VVTSYFYX OPTIONAL HEADER LESS"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

*   **Case 2: Priority 4, `amhs_ats_ohi` chính xác 53 ký tự**
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW106-02",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_ohi": "VVTSYFYX OPTIONAL HEADER EXACTLY 53 CHARACTERS LONG!!"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

*   **Case 3: Priority 4, `amhs_ats_ohi` > 53 ký tự (Ví dụ: 65 ký tự $\rightarrow$ Cần trim về 53 ký tự)**
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW106-03",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_ohi": "VVTSYFYX OPTIONAL HEADER MORE THAN 53 CHARACTERS LONG EXTRA TEXT HERE"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

### Nhóm 2: Priority = 6 (Giới hạn 48 ký tự)

*   **Case 4: Priority 6, `amhs_ats_ohi` < 48 ký tự (Ví dụ: 30 ký tự)**
    ```json
    {
      "header": { "priority": 6 },
      "properties": {
        "message-id": "TC-CTSW106-04",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_ohi": "VVTSYFYX OPTIONAL HEADER LESS"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

*   **Case 5: Priority 6, `amhs_ats_ohi` chính xác 48 ký tự**
    ```json
    {
      "header": { "priority": 6 },
      "properties": {
        "message-id": "TC-CTSW106-05",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_ohi": "VVTSYFYX OPTIONAL HEADER EXACTLY 48 CHARS LONG!"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

*   **Case 6: Priority 6, `amhs_ats_ohi` > 48 ký tự (Ví dụ: 65 ký tự $\rightarrow$ Cần trim về 48 ký tự)**
    ```json
    {
      "header": { "priority": 6 },
      "properties": {
        "message-id": "TC-CTSW106-06",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_ohi": "VVTSYFYX OPTIONAL HEADER MORE THAN 48 CHARACTERS LONG EXTRA TEXT HERE"
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

## 3. Kết quả Mong muốn (Expected Results)

### Database (`gwin`)
Lệnh kiểm tra các bản ghi được sinh ra trong DB:
```sql
SELECT msgid, priority, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW106%' 
ORDER BY msgid ASC;
```

---

# CTSW107: Convert an AMQP Message Containing Subject

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng xử lý, chuyển đổi và thứ tự ưu tiên (**Precedence & Trimming Rule**) của thuộc tính **`subject`** từ AMQP Message sang trường `subject` trong IPM Heading của AMHS Message theo quy định tại **EUR Doc 047 – Mục 4.5.2.3**.

### Quy tắc Chuyển đổi & Giới hạn Độ dài (Subject Mapping Rules):
1. **Giới hạn Độ dài (Trimming Rule - Max 128 chars)**:
   * Trường `subject` trong IPM của AMHS có độ dài tối đa là **128 ký tự**.
   * Nếu giá trị subject truyền vào vượt quá 128 ký tự $\rightarrow$ Gateway phải tự động **cắt gọn (trim) về đúng 128 ký tự đầu tiên**.
2. **Quy tắc Ưu tiên (Overriding / Precedence Rule)**:
   * Nếu điện văn có khai báo thuộc tính **`amhs_subject`** trong *Application Properties*, Gateway **bắt buộc phải ưu tiên lấy giá trị của `amhs_subject`** để gán vào IPM Subject, ghi đè lên giá trị `subject` khai báo ở AMQP *Properties Section*.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi chuỗi **04 AMQP Messages** tới IUT thử nghiệm 4 trường hợp logic:

### Case 1: Subject trong Properties > 128 ký tự, `amhs_subject` bị rỗng
*   **Đặc điểm**: AMQP Properties `subject` chứa 140 ký tự; `amhs_subject` trong Application Properties để rỗng.
*   **Kỳ vọng**: Chuyển đổi thành công và **cắt gọn chuỗi `subject` về đúng 128 ký tự đầu tiên**.

```json
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW107-01",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\"",
    "subject": "THIS IS A VERY LONG AMQP SUBJECT THAT EXCEEDS THE MAXIMUM ALLOWED LENGTH OF 128 CHARACTERS FOR AMHS IPM SUBJECT FIELD AND MUST BE TRIMMED BY GATEWAY"
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_subject": ""
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}
Case 2: Subject hợp lệ trong Properties, amhs_subject bị rỗngĐặc điểm: AMQP Properties subject = "SWIM_INTERWORKING"; amhs_subject rỗng.Kỳ vọng: Giữ nguyên chuỗi subject từ AMQP Properties để map sang IPM Subject.JSON{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW107-02",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\"",
    "subject": "SWIM_INTERWORKING"
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_subject": ""
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}
Case 3: Subject trong Properties rỗng, khai báo amhs_subjectĐặc điểm: AMQP Properties subject = ""; amhs_subject = "Subject example".Kỳ vọng: Lấy giá trị từ amhs_subject ("Subject example") để map sang IPM Subject.JSON{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW107-03",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\"",
    "subject": ""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_subject": "Subject example"
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}
Case 4: Khai báo CẢ BÊN Subject Properties VÀ Application Property amhs_subjectĐặc điểm: AMQP Properties subject = "subject from properties"; amhs_subject = "subject from application property field".Kỳ vọng: Kiểm tra tính ưu tiên $\rightarrow$ Phải lấy giá trị amhs_subject ("subject from application property field") để map sang IPM Subject.JSON{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW107-04",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\"",
    "subject": "subject from properties"
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_subject": "subject from application property field"
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}
3. Kết quả Mong muốn (Expected Results)Database (gwin)Lệnh kiểm tra các bản ghi trong bảng gwin:SQLSELECT msgid, subject, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW107%' 
ORDER BY msgid ASC;

---

# CTSW108: Incoming AMQP Message with Known Originator Indicator

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng xử lý và chuyển đổi địa chỉ người gửi (**Originator Indicator**) từ AMQP Message sang AMHS Message (IPM) khi địa chỉ 8 ký tự ICAO này đã **được khai báo và nhận diện (known)** trong bảng định tuyến/chuyển đổi địa chỉ của IUT.

### Quy tắc Chuyển đổi Địa chỉ Originator (`amhs_originator` Mapping Rules):
1. **Chuyển đổi sang MF-Address (AMHS Address Format)**:
   * Giá trị địa chỉ ICAO/AFTN 8 ký tự (ví dụ: `VVTSYFYX`) từ thuộc tính `amhs_originator` được Gateway chuyển đổi sang địa chỉ AMHS dạng **MF-Address** (O/R Name) tương ứng theo bảng cấu hình ánh xạ địa chỉ AFTN/AMHS (Doc 047).
2. **Ánh xạ vào AMHS IPM Heading & Envelope**:
   * **Envelope Level**: Được gán vào trường `originator-name` của AMHS Envelope.
   * **IPM Heading Level**: Được gán vào trường `originator` trong `this-IPM` heading field (bao gồm `formal-name` và `user`).

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi **01 AMQP Message** tới IUT chứa thông tin người gửi chuẩn 8 ký tự `VVTSYFYX`:

```json
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW108-01",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_originator": "VVTSYFYX"
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}
3. Kết quả Mong muốn (Expected Results)
Database (gwin)
Lệnh kiểm tra bản ghi được tạo trong bảng gwin:

SQL
SELECT msgid, origin, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW108%' 
ORDER BY msgid ASC;
```
---

# CTSW109: Incoming AMQP Message with Unknown Originator Indicator

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng xử lý của IUT (Implementation Under Test) khi địa chỉ người gửi (**`amhs_originator`**) trong AMQP Message **không tồn tại (unknown)** trong cơ sở dữ liệu/bảng ánh xạ địa chỉ AFTN/AMHS của Gateway, dẫn đến quá trình dịch địa chỉ bị thất bại.

### Quy tắc Xử lý Địa chỉ Không xác định (Fallback & Reporting Rule):
1. **Sử dụng Default Originator**:
   * Khi không tìm thấy ánh xạ cho `amhs_originator`, Gateway không reject bản tin mà phải tự động chuyển sang sử dụng địa chỉ khởi tạo mặc định (**Default Originator**) được cấu hình trong hệ thống (Ví dụ: `VVTSSWIM` hoặc `/C=XX/ADMD=ICAO/PRMD=VIETNAM/O=VVTSSWIM/`).
2. **Ghi Log & Báo cáo Cảnh báo**:
   * Gateway bắt buộc phải ghi nhận sự kiện này vào **System Log** (mức WARN/ERROR).
   * Phát thông báo/cảnh báo sự cố chuyển đổi địa chỉ lên màn hình giám sát **Control Position** để tiếp viên/vận hành viên nắm thông tin.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi **01 AMQP Message** tới IUT chứa địa chỉ người gửi không hợp lệ/chưa khai báo `UNKNOWNX`:

```json
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW109-01",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_originator": "UNKNOWNX"
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}
3. Kết quả Mong muốn (Expected Results)
Database (gwin)
Lệnh kiểm tra bản ghi trong bảng gwin:

SQL
SELECT msgid, origin, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW109%' 
ORDER BY msgid ASC;
```
---

# CTSW110: Reject an AMQP Message with Unsupported Content-Type

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng kiểm tra tính hợp lệ (Validation) của thành phần Gateway thuộc IUT (Implementation Under Test) đối với thuộc tính **`content-type`** kết hợp với sự xuất hiện của phần tử dữ liệu **`amqp-value`** hoặc **`data`**.

### Quy tắc Chấp nhận / Từ chối (Assertion Rules - Doc 047 4.5.1.6):
1. **Trường hợp CHẤP NHẬN (Accept & Convert)**:
   * **Case 3**: `content-type` = `application/octet-stream`, chứa `data` (binary), KHÔNG chứa `amqp-value`.
   * **Case 4**: `content-type` = `text/plain; charset="utf-8"`, chứa `amqp-value` (text/json), KHÔNG chứa `data`.
2. **Trường hợp TỪ CHỐI (Reject)**:
   * **Case 1**: `content-type` = `text/plain`, nhưng cả `amqp-value` và `data` đều RỖNG (thiếu dữ liệu).
   * **Case 2**: `content-type` = `text/plain`, nhưng lại chứa `data` (binary) thay vì `amqp-value` (sai kiểu dữ liệu).
   * **Case 5 (Optional)**: `content-type` mang giá trị KHÔNG ĐƯỢC HỖ TRỢ (ví dụ: `image/png`, `application/xml` hoặc rỗng hoàn toàn khi không đúng cấu hình).
   * **Case 6 (Optional)**: Xuất hiện ĐỒNG THỜI cả `amqp-value` VÀ `data` trong cùng 1 điện văn.
3. **Cảnh báo & Báo cáo**:
   * Các bản tin bị Reject bắt buộc phải được ghi nhận vào **System Log** và báo cáo lên màn hình giám sát **Control Position**.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi chuỗi **06 AMQP Messages** tới IUT thử nghiệm các trường hợp hợp lệ và vi phạm:

---

### Group A: Các Bản Tin Bị Từ Chối (Rejected Messages: Case 1, 2, 5, 6)

*   **Case 1 (Reject - Rỗng cả Payload)**: `content-type = "text/plain; charset=\"utf-8\""`, cả `amqp-value` và `data` đều RỖNG.
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW110-01",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "amqp-value": null
    }
    ```

*   **Case 2 (Reject - Sai kiểu Content-Type với Payload)**: `content-type = "text/plain; charset=\"utf-8\""`, nhưng payload lại nằm trong `data` (Binary).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW110-02",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "data": "A1B2C3D4E5F67890"
    }
    ```

*   **Case 5 (Reject - Unsupported Content-Type)**: `content-type = "application/xml"` (không nằm trong danh sách hỗ trợ).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW10-05",
        "creation-time": 1783310399799,
        "content-type": "application/xml"
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "amqp-value": "<flight><id>HVN679</id></flight>"
    }
    ```

*   **Case 6 (Reject - Xung đột Payload)**: Chứa ĐỒNG THỜI cả `amqp-value` VÀ `data`.
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW110-06",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "amqp-value": { "messageId": "FPL_TEXT_1783310399799", "messageType": "FPL" },
      "data": "A1B2C3D4E5F67890"
    }
    ```

---

### Group B: Các Bản Tin Hợp Lệ Được Chấp Nhận (Accepted Messages: Case 3, 4)

*   **Case 3 (Accept - Binary Hợp lệ)**: `content-type = "application/octet-stream"`, payload nằm ở `data`.
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW110-03",
        "creation-time": 1783310399799,
        "content-type": "application/octet-stream"
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "data": "A1B2C3D4E5F67890"
    }
    ```

*   **Case 4 (Accept - Text Hợp lệ)**: `content-type = "text/plain; charset=\"utf-8\""`, payload nằm ở `amqp-value`.
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW110-04",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

## 3. Kết quả Mong muốn (Expected Results)

### Database (`gwin`)
Lệnh kiểm tra các bản ghi được lưu trữ:
```sql
SELECT msgid, priority, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW110%' 
ORDER BY msgid ASC;
```
---

Dưới đây là file .md hoàn chỉnh cho test case CTSW111, được thiết kế theo đúng yêu cầu kiểm thử kiểm tra kích thước dung lượng payload (Maximum Message Data Size) dựa theo EUR Doc 047 (Mục 4.5.1.7).

Markdown
# CTSW111: Check if the Size in Bytes of the Payload of an AMQP Message Exceeds the Maximum Configured

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng kiểm soát kích thước dung lượng bản tin đầu vào của IUT (Implementation Under Test) so với tham số cấu hình **`Maximum message data size`** (tính bằng Bytes) trên Gateway theo quy định tại **EUR Doc 047 – Mục 4.5.1.7**.

### Quy tắc Xử lý Giới hạn Kích thước (Payload Size Limit Rules):
1. **Các bản tin HỢP LỆ ($\le$ Maximum Configured Size)**:
   * **Case A (Text/JSON Payload - `amqp-value`)**: Kích thước phần tử `amqp-value` **nhỏ hơn hoặc bằng** giá trị cấu hình tối đa $\rightarrow$ Gateway **chấp nhận (Accept)** và chuyển đổi sang AMHS IPM.
   * **Case B (Binary Payload - `data`)**: Kích thước phần tử `data` (binary) **nhỏ hơn hoặc bằng** giá trị cấu hình tối đa $\rightarrow$ Gateway **chấp nhận (Accept)** và chuyển đổi sang AMHS IPM.
2. **Các bản tin VI PHẠM (> Maximum Configured Size)**:
   * **Case C (Text/JSON Payload - `amqp-value`)**: Kích thước phần tử `amqp-value` **vượt quá** giá trị cấu hình tối đa $\rightarrow$ Gateway **từ chối (Reject)**, **KHÔNG** chuyển đổi sang AMHS.
   * **Case D (Binary Payload - `data`)**: Kích thước phần tử `data` **vượt quá** giá trị cấu hình tối đa $\rightarrow$ Gateway **từ chối (Reject)**, **KHÔNG** chuyển đổi sang AMHS.
3. **Cảnh báo & Báo cáo**:
   * Hai bản tin vi phạm (Case C & D) phải bị Reject ngay lập tức, được ghi nhận vào **System Logs** và báo cáo sự cố vượt ngưỡng dữ liệu lên màn hình giám sát **Control Position**.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

Giả định tham số cấu hình **`Maximum message data size`** của Gateway đang đặt là **100 KB (102,400 Bytes)**. SWIM Test Tool sẽ gửi **04 AMQP Messages** tới IUT thử nghiệm:

---

### Group A: Các Bản Tin Hợp Lệ Được Chấp Nhận (Accepted Messages: Case A, Case B)

*   **Case A (Text/JSON Payload $\le$ Max Size)**: Dung lượng `amqp-value` khoảng 1 KB ($\le$ 100 KB).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW111-A",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

*   **Case B (Binary Payload $\le$ Max Size)**: Chuỗi Binary `data` dung lượng 50 KB ($\le$ 100 KB).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW111-B",
        "creation-time": 1783310399799,
        "content-type": "application/octet-stream"
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "data": "<50_KB_BINARY_HEX_DATA_STRING>"
    }
    ```

---

### Group B: Các Bản Tin Vi Phạm Bị Từ Chối (Rejected Messages: Case C, Case D)

*   **Case C (Text/JSON Payload > Max Size)**: Chuỗi Text/JSON `amqp-value` mở rộng dung lượng **150 KB** (> 100 KB).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW111-C",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799_EXCEEDED",
        "messageType": "FPL",
        "remarks": "<150_KB_EXCEEDED_TEXT_PAYLOAD_STRING_HERE...>"
      }
    }
    ```

*   **Case D (Binary Payload > Max Size)**: Chuỗi Binary `data` dung lượng **200 KB** (> 100 KB).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW111-D",
        "creation-time": 1783310399799,
        "content-type": "application/octet-stream"
      },
      "application-properties": { "amhs_recipients": ["VVNBZTZX"] },
      "data": "<200_KB_EXCEEDED_BINARY_HEX_DATA_STRING_HERE...>"
    }
    ```

---

## 3. Kết quả Mong muốn (Expected Results)

### Database (`gwin`)
Lệnh kiểm tra các bản ghi được lưu trữ:
```sql
SELECT msgid, priority, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW111%' 
ORDER BY msgid ASC;
```

---

# CTSW112: Check if an AMQP Message Addresses More AMHS Users than the Maximum Configured

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng kiểm soát số lượng địa chỉ người nhận (**`amhs_recipients`**) trong một điện văn AMQP của IUT (Implementation Under Test) so với tham số cấu hình **`Maximum message number of recipients`** trên Gateway theo quy định tại **EUR Doc 047 – Mục 4.5.1.8**.

### Quy tắc Xử lý Giới hạn Người nhận (Recipient Count Limit Rules):
1. **Các bản tin HỢP LỆ ($\le$ Maximum Configured Recipients)**:
   * **Case A**: Số lượng địa chỉ người nhận trong trường `amhs_recipients` **bằng hoặc nhỏ hơn** giá trị cấu hình tối đa ($\le 512$) $\rightarrow$ Gateway **chấp nhận (Accept)** và chuyển đổi sang AMHS IPM.
2. **Các bản tin VI PHẠM (> Maximum Configured Recipients)**:
   * **Case B**: Số lượng địa chỉ người nhận trong trường `amhs_recipients` **vượt quá** giá trị cấu hình tối đa ($> 512$, cụ thể là 513) $\rightarrow$ Gateway **từ chối (Reject)**, **KHÔNG** chuyển đổi sang AMHS.
3. **Cảnh báo & Báo cáo**:
   * Bản tin vi phạm (Case B) phải bị Reject ngay lập tức, được ghi nhận vào **System Logs** và báo cáo sự cố vượt quá giới hạn người nhận lên màn hình giám sát **Control Position**.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

Giả định tham số cấu hình **`Maximum message number of recipients`** của Gateway đang đặt mặc định là **512**. SWIM Test Tool sẽ gửi **02 AMQP Messages** tới IUT thử nghiệm:

---

### Group A: Bản Tin Hợp Lệ Được Chấp Nhận (Accepted Message: Case A)

*   **Case A (Số lượng Recipients $\le$ 512)**: Danh sách `amhs_recipients` chứa đúng **512 địa chỉ AMHS** (`VVNBZTZX_001` đến `VVNBZTZX_512`).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW112-A",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": [
          "VVNBZTZX_001",
          "VVNBZTZX_002",
          "...[510 recipients here]...",
          "VVNBZTZX_512"
        ]
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

### Group B: Bản Tin Vi Phạm Bị Từ Chối (Rejected Message: Case B)

*   **Case B (Số lượng Recipients > 512)**: Danh sách `amhs_recipients` chứa **513 địa chỉ AMHS** (`VVNBZTZX_001` đến `VVNBZTZX_513`).
    ```json
    {
      "header": { "priority": 4 },
      "properties": {
        "message-id": "TC-CTSW112-B",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": [
          "VVNBZTZX_001",
          "VVNBZTZX_002",
          "...[511 recipients here]...",
          "VVNBZTZX_513"
        ]
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799_EXCEEDED",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```

---

## 3. Kết quả Mong muốn (Expected Results)

### Database (`gwin`)
Lệnh kiểm tra các bản ghi được lưu trữ:
```sql
SELECT msgid, priority, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW112%' 
ORDER BY msgid ASC;
```

---

# CTSW113: Incoming Receipt and Non-Receipt Notifications (RN and NRN)

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng xử lý của IUT (Implementation Under Test) khi nhận các thông báo **IPN (Interpersonal Notification)** chứa **NRN (Non-Receipt Notification)** hoặc **RN (Receipt Notification)** từ phía AMHS trả về cho các điện văn AMQP đã phát đi trước đó, theo quy định tại **EUR Doc 047 – Mục 4.4.7.3 & 4.5.3.4**.

### Quy tắc Xử lý Biên nhận RN/NRN (Notification Rules):
1. **Yêu cầu Biên nhận (Notification Request Element)**:
   * Bản tin AMQP gửi đi phải được cấu hình để yêu cầu đồng thời cả hai loại biên nhận trong phần tử `notification-requests`: **"RN"** và **"NRN"**.
2. **Xử lý NRN (Non-Receipt Notification - Thông báo Chưa/Không nhận được)**:
   * Khi AMHS Test Tool từ chối/hủy bản tin (xoá Message 1) và phát lại NRN, Gateway phải:
     * **Lưu trữ bản tin/thông báo** vào CSDL/Hệ thống để phục vụ xử lý nghiệp vụ.
     * **Ghi log** và **phát cảnh báo sự cố (Alert/Report)** lên màn hình giám sát **Control Position**.
3. **Xử lý RN (Receipt Notification - Thông báo Đã nhận)**:
   * Khi AMHS Test Tool tiếp nhận thành công bản tin và phát lại RN, Gateway phải:
     * **Lưu trữ trạng thái/thông báo** vào CSDL/Hệ thống.
     * **Ghi log** và **Cập nhật báo cáo trạng thái** lên màn hình giám sát **Control Position**.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi **02 AMQP Messages** có độ ưu tiên `priority = 6` tới IUT. Cả 02 điện văn đều yêu cầu dịch sang AMHS và bật cờ yêu cầu biên nhận RN/NRN:

---

### Case 1: Gửi Điện văn AMQP #1 (Mô phỏng Nhận NRN từ AMHS)

*   **Payload AMQP gửi từ SWIM Test Tool**:
    ```json
    {
      "header": { "priority": 6 },
      "properties": {
        "message-id": "TC-CTSW113-01-NRN",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_pri": "GG",
        "notification_requests": ["RN", "NRN"]
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799_01",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```
*   **Kịch bản Mô phỏng AMHS**: AMHS Test Tool tiếp nhận, xóa bản tin (discard/discarded) và phát điện văn phản hồi **IPN / NRN** về Gateway.

---

### Case 2: Gửi Điện văn AMQP #2 (Mô phỏng Nhận RN từ AMHS)

*   **Payload AMQP gửi từ SWIM Test Tool**:
    ```json
    {
      "header": { "priority": 6 },
      "properties": {
        "message-id": "TC-CTSW113-02-RN",
        "creation-time": 1783310399799,
        "content-type": "text/plain; charset=\"utf-8\""
      },
      "application-properties": {
        "amhs_recipients": ["VVNBZTZX"],
        "amhs_ats_pri": "GG",
        "notification_requests": ["RN", "NRN"]
      },
      "amqp-value": {
        "messageId": "FPL_TEXT_1783310399799_02",
        "messageType": "FPL",
        "timestamp": 1783310399799,
        "aircraftId": "HVN679",
        "flightRules": "I",
        "flightType": "S",
        "aircraftType": "A321",
        "wakeTurbulence": "M",
        "equipment": "SDFGHIRWYZ/EB1",
        "departureIcao": "VVTS",
        "eobt": "0825",
        "cruisingSpeed": "N0457",
        "cruisingLevel": "F340",
        "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
        "destinationIcao": "WMKK",
        "totalEet": "0138",
        "altDestination1": "WMKJ",
        "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
        "dof": "260706",
        "registration": "VNA326",
        "pbn": "A1B1C1D1L1O1S2",
        "eet": "WSJC0042 WMFC0053",
        "selcal": "KSFL",
        "operator": "HVN",
        "remarks": "TCAS",
        "navCapabilities": "RNP2"
      }
    }
    ```
*   **Kịch bản Mô phỏng AMHS**: AMHS Test Tool tiếp nhận, đọc bản tin thành công và phát điện văn phản hồi **IPN / RN** về Gateway.

---

## 3. Kết quả Mong muốn (Expected Results)

### Database (`gwin` & Bảng lưu trữ Thông báo IPN)
Lệnh kiểm tra các bản ghi lưu trữ thông báo phản hồi IPN trong cơ sở dữ liệu:
```sql
SELECT msgid, priority, status, subject, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW113%' 
ORDER BY msgid ASC;
```
---

# CTSW114: Incoming Non-Delivery Reports (NDR)

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng tiếp nhận, xử lý, ghi log và báo cáo của IUT (Implementation Under Test) khi nhận được một **Báo cáo không giao được (NDR - Non-Delivery Report)** từ AMHS cho một bản tin AMQP đã gửi đi trước đó.

### Quy tắc Xử lý NDR:
1. **Tiếp nhận NDR**: Khi Gateway nhận được NDR từ AMHS phản hồi cho bản tin gốc, nó phải xác định được thông tin:
   * `non-delivery-reason-code`: Phải là **"unable-to-transfer"**.
   * `non-delivery-diagnostic-code`: Phải là **trường trống (empty)**.
2. **Hành động của Gateway**:
   * **Ghi log hệ thống**: Ghi lại sự kiện NDR để truy vết.
   * **Báo cáo cho Control Position**: Phát cảnh báo sự cố giao nhận bản tin (Delivery Failure) lên màn hình giám sát để nhân viên vận hành thực hiện các bước xử lý tiếp theo.

---

## 2. Các Trường hợp Thử nghiệm (Test Case Payload)

SWIM Test Tool gửi **01 AMQP Message** tới IUT, sau đó thực hiện xóa bản tin tại AMHS Test Tool để kích hoạt cơ chế sinh NDR.

### Payload AMQP gửi đi từ SWIM Test Tool:
```json
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW114-01",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"]
  },
  "amqp-value": {
    "messageId": "FPL_TEXT_1783310399799_114",
    "messageType": "FPL",
    "timestamp": 1783310399799,
    "aircraftId": "HVN679",
    "flightRules": "I",
    "flightType": "S",
    "aircraftType": "A321",
    "wakeTurbulence": "M",
    "equipment": "SDFGHIRWYZ/EB1",
    "departureIcao": "VVTS",
    "eobt": "0825",
    "cruisingSpeed": "N0457",
    "cruisingLevel": "F340",
    "route": "ANHOA5A ANHOA L637 BIBAN/N0459F360 L637 BITOD/M078F360 M765 IGARI R208 VKR/N0458F360 Y346 PULIP PULIP1G",
    "destinationIcao": "WMKK",
    "totalEet": "0138",
    "altDestination1": "WMKJ",
    "otherInfo": "PBN/A1B1C1D1L1O1S2 NAV/RNP2 DOF/260706 REG/VNA326 EET/WSJC0042 WMFC0053 SEL/KSFL CODE/88808D OPR/HVN PER/C RMK/TCAS",
    "dof": "260706",
    "registration": "VNA326",
    "pbn": "A1B1C1D1L1O1S2",
    "eet": "WSJC0042 WMFC0053",
    "selcal": "KSFL",
    "operator": "HVN",
    "remarks": "TCAS",
    "navCapabilities": "RNP2"
  }
}
Kịch bản mô phỏng AMHS: AMHS Test Tool tiếp nhận, đưa vào hàng đợi, sau đó thực hiện xóa bản tin (discard) $\rightarrow$ AMHS trả về NDR (Non-Delivery Report) về phía Gateway.3. Kết quả Mong muốn (Expected Results)Database (gwin & Bảng lưu trữ NDR)Lệnh kiểm tra thông báo NDR trong CSDL:SQLSELECT msgid, status, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW114%' 
ORDER BY msgid ASC;
```
---

Dưới đây là file .md hoàn chỉnh cho test case CTSW115, được thiết kế theo đúng yêu cầu kiểm thử việc xử lý và chuyển đổi kiểu phần thân (amhs_bodypart_type) và mã hóa nội dung (amhs_content_encoding) từ AMQP Message sang AMHS Message (IPM) dựa theo EUR Doc 047 (Mục 4.5.2.4, 4.5.2.5, 4.5.4.7 & Bảng 10).

Markdown
# CTSW115: Incoming AMQP Text Messages with amqp-value and Different amhs_bodypart_type and amhs_content_encoding Values

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng chuyển đổi chính xác phần thân tin nhắn (**Body-Part Type**) và định dạng mã hóa (**Content Encoding**) từ các thuộc tính `amhs_bodypart_type` và `amhs_content_encoding` của AMQP Message sang thông điệp AMHS (IPM) tương ứng theo quy định tại **EUR Doc 047 – Mục 4.5.2.4, 4.5.2.5, 4.5.4.7 & Bảng 10**.

### Quy tắc Chuyển đổi & Ánh xạ (Mapping Rules - Doc 047 Table 10):
1. **Nội dung văn bản (`ATS-message-text`)**:
   * Giá trị trong phần tử `amqp-value` của AMQP Message phải được giữ nguyên và gán vào phần `ATS-message-text` của AMHS Message.
2. **Kiểu phần thân & Mã hóa (Body-Part & Encoding Mapping)**:
   * **Case 1**: `amhs_bodypart_type = "ia5-text"`, `amhs_content_encoding = "IA5"` $\rightarrow$ Mapped sang AMHS Body Part dạng **IA5 Text**.
   * **Case 2**: `amhs_bodypart_type = "ia5_text_body_part"`, `amhs_content_encoding = "IA5"` $\rightarrow$ Mapped sang **IA5 Text Body Part**.
   * **Case 3**: `amhs_bodypart_type = "general-text-body-part"`, `amhs_content_encoding = "ISO-646"` $\rightarrow$ Mapped sang **General Text Body Part** với bộ mã **ISO-646**.
   * **Case 4**: `amhs_bodypart_type = "general-text-body-part"`, `amhs_content_encoding = "ISO-8859-1"` $\rightarrow$ Mapped sang **General Text Body Part** với bộ mã **ISO-8859-1**.
3. **Phần tử EIT (Original Encoded Information Types)**:
   * Cần kiểm tra phần tử `original-encoded-information-types` trong phong bì AMHS Envelope được khởi tạo chính xác theo đúng quy định tại Mục 4.5.4.7 của EUR Doc 047 tương ứng với từng kiểu Body Part.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi chuỗi **04 AMQP Messages** (IPMs) mang `content-type: text/plain; charset="utf-8"` tới IUT:

---

### Case 1: IA5 Text Body Part ("IA5")
*   **Tham số**: `amhs_bodypart_type = "ia5-text"`, `amhs_content_encoding = "IA5"`, `amqp-value = "Lorem ipsum"`

```json
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW115-01",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_bodypart_type": "ia5-text",
    "amhs_content_encoding": "IA5"
  },
  "amqp-value": "Lorem ipsum"
}
Case 2: IA5 Text Body Part Alternate ("IA5")
Tham số: amhs_bodypart_type = "ia5_text_body_part", amhs_content_encoding = "IA5", amqp-value = "Lorem ipsum i5bpt"

JSON
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW115-02",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_bodypart_type": "ia5_text_body_part",
    "amhs_content_encoding": "IA5"
  },
  "amqp-value": "Lorem ipsum i5bpt"
}
Case 3: General Text Body Part ("ISO-646")
Tham số: amhs_bodypart_type = "general-text-body-part", amhs_content_encoding = "ISO-646", amqp-value = "Lorem ipsum 646"

JSON
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW115-03",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_bodypart_type": "general-text-body-part",
    "amhs_content_encoding": "ISO-646"
  },
  "amqp-value": "Lorem ipsum 646"
}
Case 4: General Text Body Part ("ISO-8859-1")
Tham số: amhs_bodypart_type = "general-text-body-part", amhs_content_encoding = "ISO-8859-1", amqp-value = "Lorem ipsum 8859"

JSON
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW115-04",
    "creation-time": 1783310399799,
    "content-type": "text/plain; charset=\"utf-8\""
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_bodypart_type": "general-text-body-part",
    "amhs_content_encoding": "ISO-8859-1"
  },
  "amqp-value": "Lorem ipsum 8859"
}
3. Kết quả Mong muốn (Expected Results)
Database (gwin)
Lệnh kiểm tra các bản ghi được lưu trữ trong CSDL:

SQL
SELECT msgid, body_type, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW115%' 
ORDER BY msgid ASC;
```
---

# CTSW116: Incoming AMQP Binary Message with FTBP Attributes

## 1. Yêu cầu & Tiêu chí Kiểm thử (Requirements & Criteria)
Test case này kiểm tra khả năng chuyển đổi một bản tin AMQP dạng Binary chứa các thuộc tính **FTBP (File Transfer Body Part)** và xử lý giải nén dữ liệu (`swim_compression: gzip`) từ SWIM Test Tool sang điện văn AMHS IPM theo quy định tại **EUR Doc 047 – Mục 4.5.2.6, 4.5.2.7, 4.5.2.8, 4.5.2.13 & 4.5.2.14 b**.

### Quy tắc Chuyển đổi & Ánh xạ Thuộc tính FTBP (FTBP Mapping Rules):
1. **Ánh xạ Thuộc tính File (FTBP Parameters Mapping)**:
   * **`amhs_ftbp_file_name`** $\rightarrow$ Ánh xạ sang trường **`incomplete-pathname`** trong tham số File Transfer của AMHS Message.
   * **`amhs_ftbp_object_size`** $\rightarrow$ Ánh xạ sang trường **`actual-values`** trong tham số IPM File Transfer của AMHS Message.
   * **`amhs_ftbp_last_mod`** $\rightarrow$ Ánh xạ sang trường **`date-and-time-of-last-modification`** trong tham số IPM File Transfer (nếu có).
2. **Cấu trúc Thân Điện văn AMHS (Body Structure Rules)**:
   * Bản tin AMHS sinh ra phải chứa cấu trúc **`file-transfer-body-part`**.
   * Trường **`ATS-message-text` KHÔNG ĐƯỢC XUẤT HIỆN** trong bản tin AMHS sinh ra (theo Mục 4.5.2.14 b).
3. **Xử lý Giải nén Dữ liệu (Compression Handling)**:
   * Đối với bản tin chứa thuộc tính `swim_compression = "gzip"`, Gateway **bắt buộc phải tự động giải nén (uncompress)** phần payload binary trước khi chuyển đổi và đóng gói sang phần thân FTBP của AMHS.

---

## 2. Các Trường hợp Thử nghiệm (Test Cases Payload)

SWIM Test Tool gửi chuỗi **02 AMQP Messages** dạng Binary (`content-type: application/octet-stream`) tới IUT:

---

### Case 1: Binary Message không Nén (Uncompressed Binary FTBP)
*   **Đặc điểm**: `content-type = "application/octet-stream"`, `amqp-value = null`, `data` chứa chuỗi binary không nén. Có khai báo các thuộc tính `amhs_ftbp_file_name`, `amhs_ftbp_object_size`, `amhs_ftbp_last_mod`.

```json
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW116-01",
    "creation-time": 1783310399799,
    "content-type": "application/octet-stream"
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_ftbp_file_name": "flight_plan_data.bin",
    "amhs_ftbp_object_size": 1024,
    "amhs_ftbp_last_mod": "20260723142742Z"
  },
  "data": "A1B2C3D4E5F678904142434445464748"
}
Case 2: Binary Message Nén GZIP (Compressed Binary FTBP - GZIP)
Đặc điểm: Chứa đầy đủ các thuộc tính FTBP như Case 1, đồng thời khai báo thuộc tính swim_compression = "gzip". Phần dữ liệu trong data là chuỗi binary đã được nén chuẩn GZIP.

JSON
{
  "header": { "priority": 4 },
  "properties": {
    "message-id": "TC-CTSW116-02",
    "creation-time": 1783310399799,
    "content-type": "application/octet-stream"
  },
  "application-properties": {
    "amhs_recipients": ["VVNBZTZX"],
    "amhs_ftbp_file_name": "flight_plan_data_compressed.bin",
    "amhs_ftbp_object_size": 2048,
    "amhs_ftbp_last_mod": "20260723142742Z",
    "swim_compression": "gzip"
  },
  "data": "1F8B0800000000000203EDC101010000000280A003B2D50E000000"
}
3. Kết quả Mong muốn (Expected Results)
Database (gwin)
Lệnh kiểm tra các bản ghi được lưu trữ trong CSDL:

SQL
SELECT msgid, body_type, text, payload_content 
FROM gwin 
WHERE payload_content LIKE '%TC-CTSW116%' 
ORDER BY msgid ASC;
```
