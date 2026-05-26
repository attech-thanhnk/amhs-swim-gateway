# Tài Liệu Đặc Tả Kỹ Thuật — Giao Diện Kết Nối Đối Tác SWIM Client

Tài liệu này quy định các tiêu chuẩn kỹ thuật và phương thức kết nối dành cho các ứng dụng đối tác (SWIM Clients) khi thực hiện truyền tải bản tin qua hệ thống **AMHS-SWIM Gateway** thông qua hạ tầng hàng đợi tin nhắn Solace PubSub+ Broker (sử dụng giao thức AMQP 1.0 / JMS).

---

## 1. Nguyên Tắc Kết Nối Chung

Để đảm bảo tính toàn vẹn dữ liệu và an toàn thông tin hàng không, các bản tin được truyền gửi từ phía SWIM qua Broker vào Gateway phải tuân thủ cấu trúc bao gồm hai thành phần chính:
1.  **Nội dung bản tin (Payload)**: Bản tin khí tượng (METAR/SPECI/SIGMET) hoặc bản tin kế hoạch bay (FPL) được định dạng theo tiêu chuẩn hàng không ICAO (dạng văn bản cổ điển TAC hoặc dạng XML/JSON theo mô hình trao đổi thông tin hàng không hiện đại IWXXM/FIXM).
2.  **Các thuộc tính ứng dụng (Application Properties)**: Các cặp khóa - giá trị (Key-Value) được đính kèm trong phần Header của bản tin AMQP. Các thuộc tính này bắt buộc phải được khai báo để phục vụ công tác xác thực quyền truy cập (theo tiêu chuẩn C-20) và phân giải địa chỉ hàng không (Addressing Resolution).

---

## 2. Danh Mục Các Thuộc Tính Ứng Dụng (AMQP Properties)

Các ứng dụng kết nối của đối tác khi truyền bản tin qua Gateway bắt buộc phải cấu hình và đính kèm đầy đủ các thuộc tính ứng dụng sau:

| Tên Thuộc Tính (Key) | Kiểu Dữ Liệu | Yêu Cầu | Định Nghĩa & Quy Chuẩn | Ví dụ Thực Tế |
| :--- | :--- | :--- | :--- | :--- |
| `swim_enterprise` | String | **Bắt buộc** (Khi áp dụng chế độ `BY_ENTERPRISE`) | Mã định danh doanh nghiệp/tổ chức khởi tạo bản tin SWIM để phục vụ công tác kiểm soát quyền truy cập. | `"VATM"`, `"HVN"`, `"BBC"` |
| `user_id` | String | **Bắt buộc** (Khi áp dụng chế độ `BY_LIST`) | Mã tài khoản định danh duy nhất của phần mềm kết nối gửi tin. | `"app_met_01"`, `"fpl_dispatcher"` |
| `amhs_originator` | Khuyên dùng | Địa chỉ nơi khởi tạo bản tin AMHS (Originator Address - 8 ký tự). Nếu để trống, hệ thống sẽ sử dụng địa chỉ mặc định cấu hình trên Gateway. | `"VVTSZPZX"` |
| `amhs_recipients` | Khuyên dùng | Danh sách các địa chỉ nhận bản tin AMHS (Recipient Addresses). Trường hợp có nhiều địa chỉ nhận, các địa chỉ được phân tách bằng một dấu cách (space). | `"VVHHZQZX VVTSZQZX"` |
| `ats_priority` | Tùy chọn | Độ ưu tiên truyền tin của bản tin hàng không ATS theo quy chuẩn ICAO. | `"GG"`, `"FF"`, `"DD"`, `"SS"`, `"KK"` |
| `amhs_subject` | Tùy chọn | Loại bản tin hoặc tiêu đề tóm tắt của bản tin. Giá trị mặc định là `SWIM_INTERWORKING`. | `"METAR"`, `"FPL"` |
| `JMS_AMQP_CONTENT_TYPE` | Tùy chọn | Định dạng kiểu nội dung của phần dữ liệu bản tin (Payload). | `"application/json"`, `"application/xml"` |

---

## 3. Các Chế Độ Kiểm Soát Quyền Truy Cập (C-20) Trên Gateway

Quy trình xác thực quyền hạn đối với các bản tin từ phía SWIM đi vào mạng AMHS được cấu hình linh hoạt thông qua bảng `gateway_config`, tham số `AUTHORIZED_SWIM_USERS` với 03 chế độ hoạt động:

1.  **Chế độ cho phép toàn bộ (`ALL`)**:
    *   Hệ thống cho phép tất cả các bản tin từ SWIM Broker đi qua Gateway mà không thực hiện xác thực thông tin tài khoản gửi hoặc doanh nghiệp. Chế độ này thường được áp dụng trong giai đoạn chạy thử nghiệm và kiểm thử tích hợp hệ thống.
2.  **Chế độ kiểm soát theo doanh nghiệp (`BY_ENTERPRISE`)**:
    *   Bản tin truyền gửi bắt buộc phải đính kèm thuộc tính `swim_enterprise` hợp lệ. Nếu thuộc tính này bị khuyết hoặc giá trị truyền vào không nằm trong danh sách cấp phép `AUTHORIZED_SWIM_ENTERPRISES` tại database, bản tin sẽ bị Gateway **từ chối tiếp nhận và xử lý (REJECTED)**.
3.  **Chế độ kiểm soát theo danh sách tài khoản (`BY_LIST`)**:
    *   Bản tin truyền gửi bắt buộc phải chứa thuộc tính `user_id`. Nếu thông tin định danh tài khoản không hợp lệ hoặc không có tên trong danh sách whitelist `AUTHORIZED_SWIM_USERS`, bản tin sẽ lập tức bị chặn và ghi nhận nhật ký lỗi.

---

## 4. Ví Dụ Cấu Trúc Truyền Tin Bằng Mã Nguồn Java (JMS API)

Dưới đây là ví dụ cấu hình mã nguồn thiết lập thuộc tính bản tin trên ứng dụng gửi tin của đối tác:

```java
// 1. Khởi tạo đối tượng bản tin dạng văn bản
TextMessage message = session.createTextMessage(payloadContent);

// 2. Thiết lập các thuộc tính định danh và nghiệp vụ hàng không
message.setStringProperty("swim_enterprise", "VATM");
message.setStringProperty("user_id", "app_met_01");
message.setStringProperty("amhs_originator", "VVTSZPZX");
message.setStringProperty("amhs_recipients", "VVHHZQZX");
message.setStringProperty("ats_priority", "GG");
message.setStringProperty("amhs_subject", "METAR");

// 3. Thực hiện truyền gửi bản tin qua MessageProducer
producer.send(message);
```
