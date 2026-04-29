# Hướng dẫn Kiểm tra (Test) SWIM Component với Solace PubSub+

Tài liệu này hướng dẫn cách thiết lập và chạy thử nghiệm (End-to-End) luồng dữ liệu giữa SWIM Component và Solace Broker.

---

### 1. Chuẩn bị môi trường Solace
Nếu chưa có hệ thống Solace thật, bạn có thể:
*   **Solace Cloud**: Đăng ký tài khoản miễn phí tại [solace.com](https://console.solace.cloud/).
*   **Docker**: Chạy lệnh: `docker run -d -p 8080:8080 -p 55555:55555 -p 8008:8008 -p 1883:1883 -p 7671:7671 -p 5672:5672 -p 9000:9000 --shm-size=2g --name=solace solace/solace-pubsub-standard:latest`

**Quan trọng**: Trên Solace Console, hãy tạo một **Queue** tên là `vnhh/test` và một **Topic Subscription** tương ứng để nhận tin.

---

### 2. Cấu hình Database (Dựa trên Seed Data)
Hệ thống đã có sẵn dữ liệu mẫu trong file `seed.sql`. Bạn không cần chạy lại các lệnh `INSERT` nếu đã nạp seed data thành công.

#### Bước 2.1: Tài khoản Solace (Đã có trong Seed)
Kiểm tra tài khoản mặc định:
```sql
SELECT * FROM accounts WHERE account_name = 'solace-broker-primary';
```
*   **Host**: `127.0.0.1:5672`
*   **VPN**: `default`
*   **User/Pass**: `admin / admin`

#### Bước 2.2: Quy tắc định tuyến (Đã có trong Seed)
Seed data đã có sẵn quy tắc cho Topic `ats.met.metar`:
```sql
SELECT * FROM routing WHERE receive_topic = 'ats.met.metar' AND direction = 'IN';
```
*   **Recipients**: `VVHHZQZX VVHHZTZX VVHHZDZX` (Sẽ gửi cho Hanoi MET, ACC...)

> [!NOTE]
> Hệ thống phân biệt giữa tin dạng XML (ví dụ: `METAR`) và tin dạng văn bản cũ (ví dụ: `METAR_TEXT`). Hãy đảm bảo bảng `routing` có khai báo cho cả hai loại nếu bạn test cả hai định dạng.

---

### 3. Sử dụng Công cụ TestPublisher
Hệ thống đã có sẵn một công cụ giả lập phía SWIM gửi tin lên Solace:
*   **File**: `gateway-swim/src/test/java/vn/asg/swim/TestPublisher.java`
*   **Cách dùng**: 
    1. Mở file trong IntelliJ/Eclipse.
    2. Sửa các hằng số `BROKER_URL`, `USERNAME`, `PASSWORD` ở đầu file cho đúng với Solace của bạn.
    3. Chuột phải vào hàm `main` -> **Run 'TestPublisher.main()'**.

---

### 4. Chạy SWIM Component
1. Đảm bảo Database đã chạy và có dữ liệu cấu hình ở Bước 2.
2. Chạy module `gateway-swim` (Main Class: `SwimApplication.java`).
3. Kiểm tra Log startup, bạn sẽ thấy dòng:
   `[AMQP] Subscribed to queue: vnhh/test successfully.`

---

### 5. Kiểm tra kết quả
Sau khi `TestPublisher` gửi tin, hãy kiểm tra trong Database xem Gateway đã nhận và xử lý chưa:

#### 5.1 Kiểm tra bảng tin đến (gwin)
```sql
SELECT msgid, time, subject, addressing_source, address, status 
FROM gwin 
ORDER BY msgid DESC LIMIT 5;
```
*   `status = 0`: Mới nhận.
*   `status = 1`: Đang xử lý AMHS.
*   `status = 5`: Không hiểu nội dung (Unrouted).

---

### 7. Thử nghiệm với Solace "Try Me!" (Web Interface)
Nếu bạn không muốn chạy code Java (`TestPublisher`), bạn có thể dùng giao diện Web của chính Solace Cloud hoặc Solace Manager.

#### Bước 7.1: Truy cập Try Me!
1. Đăng nhập vào Solace Console.
2. Chọn Cluster của bạn -> Tab **Try Me!**.
3. Bên tay trái là **Publisher**, bên tay phải là **Subscriber**. Click **Connect** ở cả hai bên.

#### Bước 7.2: Gửi tin (Publish)
Tại bảng **Publisher**:
1. **Topic**: Nhập `ats.met.metar` (khớp với topic trong `seed.sql`).
2. **Message Content**: Dán nội dung XML METAR mẫu:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<iwxxm:METAR xmlns:iwxxm="http://icao.int/iwxxm/3.0"
             xmlns:aixm="http://www.aixm.aero/schema/5.1.1"
             xmlns:gml="http://www.opengis.net/gml/3.2">
    <iwxxm:aerodrome>
        <aixm:AirportHeliport gml:id="ah-vvhh">
            <aixm:timeSlice>
                <aixm:AirportHeliportTimeSlice gml:id="ts-vvhh">
                    <aixm:locationIndicatorICAO>VVHH</aixm:locationIndicatorICAO>
                </aixm:AirportHeliportTimeSlice>
            </aixm:timeSlice>
        </aixm:AirportHeliport>
    </iwxxm:aerodrome>
</iwxxm:METAR>
```
3. (Tùy chọn) Để test nạp địa chỉ AMHS thủ công: 
   * Click **User Properties**.
   * Thêm Key: `amhs_recipients` - Value: `VVTSZQZX VVHHZQZX`.
   * Thêm Key: `amhs_subject` - Value: `METAR`.
4. Nhấn **Publish**.

#### Bước 7.3: Kiểm tra Gateway
* Kiểm tra Dashboard hoặc truy vấn bảng `gwin` trong SQL để xem tin đã được Gateway "bốc" về chưa.
* Nếu Gateway đã `Connected` tới Solace, tin sẽ biến mất khỏi Queue ngay lập tức và xuất hiện trong Database.
