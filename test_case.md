## I. HƯỚNG DẪN 
1. **Môi trường kiểm thử**: Cơ sở dữ liệu MySQL `asg_db`, dịch vụ `gateway-swim` đang chạy.
2. **Nguyên tắc thực hiện**:
   - Đối với **Chiều đi (CTSW001 - CTSW020)**: Chạy câu lệnh SQL `INSERT` tương ứng vào bảng `gwout` -> Chờ 2 giây -> Chạy câu lệnh `SELECT` để kiểm tra trạng thái và chuỗi JSON đã dịch.
   - Đối với **Chiều về (CTSW101 - CTSW116)**: Thực hiện chèn bản tin SWIM JSON tương ứng vào Solace Broker (hoặc giả lập chèn vào bảng `gwin`) -> Chờ 2 giây -> Kiểm tra bản tin phong bì X.400 được sinh ra trong bảng `gwin`/`gwin_dispatch`.

---

## II. CHI TIẾT KỊCH BẢN KIỂM THỬ CHIỀU ĐI (AMHS -> SWIM)

### CTSW001: Convert Incoming IPM with Filing Time
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
    'ZCZC ABC001\r\nFF VVHHZTZX\r\n070430 VVNBZTZX\r\nMETAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG='
);
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW001';
```
* **Kết quả mong muốn**: `status = 3`. Trong chuỗi JSON tại `payload_content` có thuộc tính `"ats_message_filing_time": "070430"`.

---

### CTSW002: Convert Incoming IPM with OHI
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
* **Kết quả mong muốn**: `status = 3`. Trong JSON tại `payload_content` có thuộc tính `"ats_message_optional_heading": "OHI-TEST-DATA-123"`.

---

### CTSW003: Generate Delivery Report (DR)
* **Câu lệnh nạp dữ liệu (Input)**: Chạy lại câu lệnh của `CTSW001`.
* **Quy trình**: Hệ thống tự động xử lý.
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT * FROM gwin WHERE payload_content LIKE '%DeliveryReport%' OR text LIKE '%DR%' ORDER BY id DESC LIMIT 1;
```
* **Kết quả mong muốn**: Có bản ghi DR được sinh tự động gửi trả lại AMHS.

---

### CTSW004: Generate Non-Delivery Report (NDR)
* **Câu lệnh nạp dữ liệu (Input)**: Chèn một bản tin FPL lỗi cú pháp nghiêm trọng:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW004', 1, NOW(), '070430', 'VVTSZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'(FPL-LỖI-CÚ-PHÁP-HOÀN-TOÀN');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW004';
```
* **Kết quả mong muốn**: `status = 5` (FAILED). Payload ghi nhận log lỗi phân tích cú pháp.

---

### CTSW005: Convert Incoming IPM with Subject
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
* **Kết quả mong muốn**: `status = 3`. Trong JSON tại `payload_content` có thuộc tính `"subject": "FLIGHT ADVISORY"`.

---

### CTSW006: Reject IPM Exceeding Max Size
* **Quy trình**: Cấu hình cấu phần `gateway.max-payload-size = 100` (100 Bytes).
* **Câu lệnh nạp dữ liệu (Input)**: Chèn bản tin dài hơn 100 ký tự:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW006', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC ABC001 FF VVHHZTZX 070430 VVNBZTZX METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG= RẤT DÀI RẤT DÀI RẤT DÀI RẤT DÀI RẤT DÀI RẤT DÀI');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW006';
```
* **Kết quả mong muốn**: `status = 5` (FAILED). Payload chứa thông báo từ chối do vượt quá kích thước cho phép.

---

### CTSW007: Reject IPM with Multiple Body Parts
* **Mô tả**: Gateway không hỗ trợ X.400 Multipart chứa nhiều body parts.
* **Kết quả mong muốn**: Khi luồng đồng bộ đẩy tin Multipart từ `mtcu_tmp` có số lượng body part > 1 -> Đánh dấu `status = 5` (FAILED).

---

### CTSW008: Reject IPM with Unsupported Content-Type
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
* **Kết quả mong muốn**: `status = 5` (FAILED). Log báo Content-Type không hỗ trợ.

---

### CTSW009: Distribute IPM to AMHS and AMQP
* **Mục tiêu**: Bản tin gửi đồng thời cho cả người dùng hàng không AMHS và Solace SWIM.
* **Kết quả mong muốn**: Hệ thống lưu bản copy trong `gwin` (để gửi đi mạng AMHS) và publish thành công lên Solace Broker.

---

### CTSW010: Reject IPM addressing More AMQP Consumers Than Max
* **Quy trình**: Đặt cấu hình số địa chỉ nhận tối đa `gateway.max-recipients = 2`.
* **Câu lệnh nạp dữ liệu (Input)**: Chèn bản tin gửi tới 3 địa chỉ nhận:
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW010', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX VVTSZPZX VVDNZPZX', 'text', 'application/json', 0,
'METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW010';
```
* **Kết quả mong muốn**: `status = 5` (FAILED). Log báo số người nhận vượt quá giới hạn cấu hình.

---

### CTSW011: Probe Conveyance Test

* **Mô tả**:
Kiểm tra Gateway xử lý bản tin Probe hợp lệ. Gateway phải tiếp nhận bản tin Probe, xử lý thành công và tạo Probe Report phản hồi theo đúng chuẩn AMHS.

* **Câu lệnh nạp dữ liệu (Input)**:

```sql
INSERT INTO gwout
(amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES
('TC-CTSW011', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'ia5-text-body-part', 'application/json', 0, 'PROBE REQUEST');
```

* **Thao tác kiểm tra**:

```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW011';
```

* **Kết quả mong muốn**:

- `status = 3`.
- Gateway xử lý thành công bản tin Probe.
- Sinh Probe Report phản hồi.
- Không phát sinh Exception hoặc lỗi xử lý.

### CTSW012: Reject Probe for Unknown Recipients

* **Mô tả**:
Kiểm tra Gateway từ chối bản tin Probe khi địa chỉ nhận không tồn tại trong cấu hình định tuyến.

* **Điều kiện tiên quyết**:
- Gateway đang hoạt động bình thường.
- Địa chỉ `ZZZZZTZX` không tồn tại trong cấu hình Gateway.

* **Câu lệnh nạp dữ liệu (Input)**:

```sql
INSERT INTO gwout
(amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES
('TC-CTSW012', 2, NOW(), '070430', 'VVNBZTZX', 'ZZZZZTZX', 'ia5-text-body-part', 'application/json', 0, 'PROBE REQUEST');
```

* **Thao tác kiểm tra**:

```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW012';
```

* **Kết quả mong muốn**:

- Gateway từ chối xử lý bản tin.
- `status` chuyển sang trạng thái lỗi (theo quy ước của hệ thống).
- Có log ghi nhận địa chỉ nhận không hợp lệ.
- Không sinh Probe Report.

### CTSW013: Reject Probe with Unknown Originator Address

* **Mô tả**:
Kiểm tra Gateway từ chối bản tin Probe khi địa chỉ Originator không hợp lệ hoặc không được phép gửi.

* **Điều kiện tiên quyết**:
- Gateway đang hoạt động bình thường.
- Địa chỉ `UNKNOWNZTZX` không được cấu hình là Originator hợp lệ.

* **Câu lệnh nạp dữ liệu (Input)**:

```sql
INSERT INTO gwout
(amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES
('TC-CTSW013', 2, NOW(), '070430', 'UNKNOWNZTZX', 'VVHHZTZX', 'ia5-text-body-part', 'application/json', 0, 'PROBE REQUEST');
```

* **Thao tác kiểm tra**:

```sql
SELECT status FROM gwout WHERE amhsid = 'TC-CTSW013';
```

* **Kết quả mong muốn**:

- Gateway từ chối bản tin Probe.
- `status` chuyển sang trạng thái lỗi.
- Không sinh Probe Report.
- Có log ghi nhận Originator không hợp lệ hoặc không được phép gửi Probe.

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

- Bản tin RN được xử lý thành công (`status = 3`).
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

### CTSW016: Process EIT
* **Câu lệnh nạp dữ liệu (Input)**: Chèn bản tin có `body_part_type = '401'` (mã thô của ISODE).
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text) 
VALUES ('TC-CTSW016', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', '401', 'application/json', 0,
'METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW016';
```
* **Kết quả mong muốn**: `status = 3` (Hoặc xử lý thành công). Kiểu mã hóa được tự động chuẩn hóa sang `ia5-text-body-part` để bảo đảm tính hợp lệ của SWIM.

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

- Bản ghi được xử lý thành công (`status = 3`).
- Trường `payload_content` được sinh ra ở định dạng JSON.
- JSON chứa:
  - `messageType = "METAR"`
  - `originalTac` đúng bằng nội dung METAR đầu vào.
  - `stationIcao = "VVNB"`
  - `observationTime = "070430Z"`
  - `nil = false`

---

### CTSW018: Convert IPM with General-Text (ISO 646)
* **Mô tả**: Thử nghiệm với bản tin mã hóa bảng mã General Text (ISO 646).
* **Câu lệnh nạp dữ liệu (Input)**:

```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_part_type, content_type, status, text)
VALUES ('TC-CTSW018', 2, NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'general-text-body-part', 'text/plain', 0, 'THIS IS A GENERAL TEXT MESSAGE USING ISO 646 CHARACTER SET.');
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW018';
```
* **Kết quả mong muốn**: Dịch và hiển thị nội dung UTF-8 chuẩn xác trong SWIM JSON.
- `status = 3` (xử lý thành công).
- Trường `payload_content` được sinh ra.
- Nội dung trong `payload_content` là JSON hợp lệ.
- Nội dung text được giải mã đúng từ bảng mã ISO 646 sang UTF-8.
- Không xuất hiện ký tự lỗi (`�`, `?`) hoặc lỗi mã hóa.
- Chuỗi trong JSON phải trùng khớp với nội dung đầu vào:

```text
THIS IS A GENERAL TEXT MESSAGE USING ISO 646 CHARACTER SET.
```

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
* **Câu lệnh nạp dữ liệu (Input)**: Chèn bản tin khẩn nguy có độ ưu tiên chữ là `SS` (hoặc số ưu tiên `0` tương ứng):
```sql
INSERT INTO gwout (amhsid, amhs_priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TC-CTSW020', 'SS', NOW(), '070430', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC ALR001
SS VVHHZTZX
070430 VVNBZTZX
ALR TYPE A');
```

* **Thao tác kiểm tra**:
```sql
SELECT status, payload_content FROM gwout WHERE amhsid = 'TC-CTSW020';
```

* **Kết quả mong muốn**: Trạng thái xử lý thành công. Đồng thời hệ thống ghi nhận vào bảng cảnh báo và hiển thị cảnh báo khẩn nguy trực quan trên Dashboard Control Panel.
- `status = 3`.
- `payload_content` được tạo thành công.
- JSON chứa mức ưu tiên `SS` hoặc giá trị ưu tiên tương ứng.
- Gateway phát sinh sự kiện cảnh báo đến Control Position.
- Nếu hệ thống có bảng lưu cảnh báo thì sinh thêm một bản ghi tương ứng.
- Dashboard Control Panel hiển thị cảnh báo khẩn nguy với mức ưu tiên `SS`.
- Không phát sinh lỗi trong log của Gateway.

---

## III. CHI TIẾT KỊCH BẢN KIỂM THỬ CHIỀU VỀ (SWIM -> AMHS)

### CTSW101: Convert AMHS Unaware Message
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW101",
  "messageType": "METAR",
  "text": "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG="
}
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT status, text, priority, time FROM gwin WHERE amhsid = 'TC-CTSW101';
```
* **Kết quả mong muốn**: Tạo thành công bản ghi trong bảng `gwin` chứa phong bì mặc định và nội dung văn bản hàng không (TAC) hoàn chỉnh.

---

### CTSW102: Reject AMQP Message Lacking Minimum Info
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW102"
}
```
* **Kết quả mong muốn**: Gateway từ chối xử lý, không ghi vào bảng `gwin` và phản hồi thông báo lỗi về Solace Broker.

---

### CTSW103: Convert according to Service Level
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW103",
  "messageType": "METAR",
  "serviceLevel": "Extended",
  "text": "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG="
}
```
* **Kết quả mong muốn**: Sinh bản tin AMHS cấu trúc phong bì X.400 chuẩn Extended Service tương ứng.

---

### CTSW104: Convert according to Priority
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW104",
  "messageType": "METAR",
  "priority": 2,
  "text": "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG="
}
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT priority, text FROM gwin WHERE amhsid = 'TC-CTSW104';
```
* **Kết quả mong muốn**: Bản ghi sinh ra có cột `priority` được điền tương ứng là `FF` (độ ưu tiên chữ hàng không cho giá trị số 2).

---

### CTSW105: Convert according to Filing Time
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW105",
  "messageType": "METAR",
  "timestamp": "2026-07-07T05:00:00Z",
  "text": "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG="
}
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT filing_time FROM gwin WHERE amhsid = 'TC-CTSW105';
```
* **Kết quả mong muốn**: Trường `filing_time` được định dạng lại thành chuỗi 6 chữ số chuẩn hàng không: `070500`.

---

### CTSW106: Convert according to Originator
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW106",
  "messageType": "METAR",
  "originator": "VVTSZPZX",
  "text": "METAR VVTS 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG="
}
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT origin FROM gwin WHERE amhsid = 'TC-CTSW106';
```
* **Kết quả mong muốn**: Địa chỉ ngắn `VVTSZPZX` được tra cứu ánh xạ sang địa chỉ dài X.400 đầy đủ của AMHS.

---

### CTSW107: Convert according to Addressees
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW107",
  "messageType": "METAR",
  "addressees": ["VVNBZTZX", "VVTSZPZX"],
  "text": "METAR VVTS 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG="
}
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT address FROM gwin WHERE amhsid = 'TC-CTSW107';
```
* **Kết quả mong muốn**: Danh sách địa chỉ nhận được chuyển dịch sang dạng chuỗi địa chỉ dài X.400 phân tách nhau hợp lệ để mạng AMHS chuyển phát.

---

### CTSW108: Convert according to Message Text
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW108",
  "messageType": "METAR",
  "text": "METAR VVTS 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG="
}
```
* **Lệnh kiểm tra (Verify)**:
```sql
SELECT text FROM gwin WHERE amhsid = 'TC-CTSW108';
```
* **Kết quả mong muốn**: Phần thân bản tin trong CSDL được định dạng hoàn hảo với các ký tự xuống dòng chuẩn hàng không (CR/LF).

---

### CTSW109: Convert according to Receiver
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW109",
  "receiver": "VVTSSWIM",
  "text": "METAR VVTS 070430Z..."
}
```
* **Kết quả mong muốn**: Bản tin định tuyến đến đúng hòm thư nhận thực tế của tổng đài để phát đi.

---

### CTSW110: Convert according to Priority Indicator
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW110",
  "priorityIndicator": "GG",
  "text": "METAR VVTS 070430Z..."
}
```
* **Kết quả mong muốn**: Sinh bản tin AMHS với chỉ số báo độ ưu tiên tương ứng được gán trên phong bì thư X.400.

---

### CTSW111: Check Payload Size Exceeding Max
* **Quy trình**: Cấu hình giới hạn kích thước nhận SWIM `gateway.inbound.max-size = 100` (100 Bytes).
* **Thông điệp SWIM gửi tới (Input JSON)**: Gửi thông điệp SWIM JSON có chiều dài text vượt quá 100 bytes.
* **Kết quả mong muốn**: Bản tin bị từ chối chuyển đổi sang AMHS, không ghi vào bảng `gwin` và Solace nhận thông báo lỗi từ chối.

---

### CTSW112: Check Recipients Count Exceeding Max
* **Quy trình**: Đặt cấu hình giới hạn số người nhận tối đa `gateway.inbound.max-recipients = 2`.
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW112",
  "addressees": ["VVNBZTZX", "VVTSZPZX", "VVDNZPZX"],
  "text": "METAR VVTS 070430Z..."
}
```
* **Kết quả mong muốn**: Bản tin bị từ chối chuyển dịch sang AMHS do vượt giới hạn người nhận tối đa.

---

### CTSW113: Process Incoming RN and NRN
* **Mô tả**: Nhận một bản tin RN hoặc NRN (báo phát tin thành công/lỗi) từ AMHS.
* **Kết quả mong muốn**: Tìm và cập nhật đúng mã trạng thái phát tin của thông điệp SWIM gốc tương ứng trong CSDL.

---

### CTSW114: Process Incoming NDR
* **Mô tả**: Nhận một báo lỗi phát tin không thành công (NDR) gửi trả từ AMHS.
* **Kết quả mong muốn**: Cập nhật trạng thái thông điệp SWIM gốc thành FAILED kèm lý do lỗi chi tiết thu thập từ NDR.

---

### CTSW115: Process with amqp-value and different EIT/Encoding
* **Thông điệp SWIM gửi tới (Input JSON)**:
```json
{
  "messageId": "TC-CTSW115",
  "amhsBodypartType": "general-text-body-part",
  "amhsContentEncoding": "iso-646",
  "text": "METAR VVTS 070430Z..."
}
```
* **Kết quả mong muốn**: Biên dịch chính xác phần thân bản tin AMHS tương ứng theo kiểu và định dạng mã hóa yêu cầu.

---

### CTSW116: Process Binary Message with FTBP Attributes
* **Thông điệp SWIM gửi tới (Input JSON)**: Gửi thông điệp nhị phân kèm thuộc tính tệp đính kèm.
* **Kết quả mong muốn**: Giải mã dữ liệu và đóng gói vào cấu trúc File Transfer Body Part (FTBP) theo tiêu chuẩn của dịch vụ mở rộng ATSMHS Extended Service gửi đi AMHS.
