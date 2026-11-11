# PRODUCT REQUIREMENTS DOCUMENT (PRD)

## 1. Intent (Mục đích cốt lõi)
Giải quyết bài toán kỹ thuật duy nhất trong kiến trúc Microservices:
> **"Làm thế nào để hệ thống Microservices xử lý hàng chục ngàn giao dịch mà không bị nghẽn (Zero Bottleneck) và không bao giờ mất đơn hàng khi dịch vụ con gặp sự cố (High Reliability) nhờ RabbitMQ?"**

* **Thách thức kiến trúc cũ**: Gọi đồng bộ HTTP/REST trực tiếp giữa các service dẫn đến hiện tượng cạn kiệt luồng Tomcat (*Thread Pool Starvation*) và sụp đổ dây chuyền (*Cascading Failure*) khi một service con (như kho hoặc email) bị chậm hoặc nghẽn.
* **Giải pháp cốt lõi**:
  * Sử dụng RabbitMQ Message Broker làm lớp đệm bất đồng bộ (Decoupling & Asynchronous).
  * Định tuyến phát thanh bằng **Fanout Exchange** (`order.fanout.exchange`) nhân bản tức thời tới các Consumer.
  * Phản hồi khách hàng ngay lập tức bằng mã **HTTP 202 Accepted** trong khoảng **~15ms** (**Zero Bottleneck**).
  * Bảo toàn đơn hàng bằng cơ chế **Manual ACK** (`channel.basicAck`) và cô lập dữ liệu độc hại qua **Dead-Letter Exchange (DLX)** sang **Dead-Letter Queue (DLQ)** (**High Reliability**).
* **Quy trình thực hiện**: Áp dụng chuẩn quy trình **Spec-Driven Development với AI Agent** theo yêu cầu của giảng viên.

---

## 2. User Value Proposition (App mang lại điều gì cho User)

### 2.1. Đối với Khách hàng (End-User)
* **Phản hồi tức thì (Zero Bottleneck)**: Khi bấm "Đặt hàng", giao diện phản hồi thành công ngay lập tức với độ trễ cực thấp (~15ms, HTTP 202 Accepted). Người dùng không bao giờ phải chịu cảnh xoay vòng chờ đợi (loading spinner) các tác vụ nền như gửi email hay tính toán tồn kho.
* **Trải nghiệm mượt mà không timeout**: Ngay cả trong khung giờ Flash Sale hay đỉnh tải hàng chục ngàn người truy cập cùng lúc, hệ thống vẫn duy trì tốc độ phản hồi ổn định.

### 2.2. Đối với Đội ngũ Vận hành & Doanh nghiệp (System & Business)
* **Độ tin cậy tuyệt đối (High Reliability)**: Không bao giờ mất đơn hàng nhờ *Tam giác bền vững* (`Durable Exchange` + `Durable Queue` + `Persistent Message`) kết hợp xác nhận thủ công (`Manual ACK`). Nếu Consumer gặp sự cố sập nguồn, tin nhắn vẫn tồn tại an toàn trên RabbitMQ.
* **Cô lập sự cố thông minh (Fault Isolation)**: Các đơn hàng lỗi hoặc dị thường (Poison Messages) tự động được chuyển hướng sang hàng đợi rác (`order.dlq`) qua cơ chế DLX, ngăn chặn hoàn toàn vòng lặp sập hệ thống (Crash Loop) và không làm tắc nghẽn các đơn hàng hợp lệ khác.

---

## 3. Thiết kế chi tiết (RabbitMQ Topology & System Design)

### 3.1. Thông số Kỹ thuật
* **Framework**: Spring Boot 3.3.x, Java 21 LTS, Spring AMQP.
* **Message Broker**: RabbitMQ 3.13 Management (AMQP 0-9-1) chạy trên Docker container (Port 5672 cho AMQP, Port 15672 cho Management UI).
* **Giao diện**: Web UI (`index.html`) tích hợp trực tiếp tại `http://localhost:8080`.

### 3.2. Cấu hình RabbitMQ Topology
1. **Exchange chính**: `order.fanout.exchange` (Type: `fanout`, `durable: true`)
   * Nhân bản (Broadcast) tin nhắn đơn hàng đồng thời tới tất cả các queue gắn kết.
2. **Queue chính**:
   * `order.email.queue` (`durable: true`): Lắng nghe bởi `EmailConsumer` để gửi email xác nhận.
   * `order.inventory.queue` (`durable: true`, đính kèm tham số `x-dead-letter-exchange: order.dlx`, `x-dead-letter-routing-key: order.deadletter`): Lắng nghe bởi `InventoryConsumer` để trừ kho.
3. **Dead-Letter Topology (Xử lý sự cố)**:
   * **DLX**: `order.dlx` (Type: `direct`, `durable: true`).
   * **DLQ**: `order.dlq` (`durable: true`), bind với routing key `order.deadletter` để lưu trữ các message bị từ chối (`basicNack` với `requeue = false`).

---

## 4. API Contract & Message Payload

### 4.1. REST API Đặt hàng (Order Producer)
* **Endpoint**: `POST /api/orders`
* **Content-Type**: `application/json`
* **Request Body**:
```json
{
  "customerName": "Võ Quang Trung",
  "email": "trungvq@fpt.edu.vn",
  "productName": "MacBook Pro M3 Max",
  "quantity": 1,
  "amount": 3500.0
}
```
* **Response Status**: `202 Accepted`
* **Response Body**:
```json
{
  "orderId": "ORD-882910",
  "status": "ACCEPTED",
  "message": "Đơn hàng đã được tiếp nhận và đẩy vào Fanout Exchange thành công!",
  "latencyMs": 15,
  "timestamp": "2026-11-11T08:30:00Z"
}
```

### 4.2. REST API Truy vấn Log Consumer (Phục vụ Web UI Realtime)
* **Endpoint**: `GET /api/orders/logs`
* **Response Status**: `200 OK`
* **Response Body**:
```json
[
  "[08:30:00.015] [PRODUCER] Đã bắn sự kiện ORD-882910 vào order.fanout.exchange",
  "[08:30:00.028] [EMAIL-CONSUMER] Đã gửi email xác nhận cho trungvq@fpt.edu.vn (Manual ACK)",
  "[08:30:00.031] [INVENTORY-CONSUMER] Đã trừ kho 1x MacBook Pro M3 Max (Manual ACK)"
]
```

### 4.3. Cấu trúc AMQP Message Payload
* **Delivery Mode**: `2` (Persistent)
* **Content Type**: `application/json`
* **Payload**:
```json
{
  "orderId": "ORD-882910",
  "customerName": "Võ Quang Trung",
  "email": "trungvq@fpt.edu.vn",
  "productName": "MacBook Pro M3 Max",
  "quantity": 1,
  "amount": 3500.0,
  "createdAt": "2026-11-11T08:30:00Z"
}
```
* **Quy tắc kích hoạt DLQ**: Nếu `quantity <= 0` hoặc `productName` chứa từ khóa `"ERROR"`, `InventoryConsumer` chủ động gọi `channel.basicNack(deliveryTag, false, false)` để RabbitMQ đẩy message sang `order.dlq`.

---

## 5. Đặc tả Kịch bản Kiểm thử (Test Cases Specification)

### 5.1. Unit Test (Spring Boot MockMvc & Mockito)
* **UT-01 (Happy Path API)**: Gọi `POST /api/orders` hợp lệ $\rightarrow$ Trả về HTTP `202 Accepted`, JSON có `status = ACCEPTED`, và verify `RabbitTemplate.convertAndSend()` được gọi đúng 1 lần vào `order.fanout.exchange`.
* **UT-02 (Validation Error)**: Gọi `POST /api/orders` thiếu trường `productName` $\rightarrow$ Trả về HTTP `400 Bad Request`.

### 5.2. Automated E2E Test (Microsoft Playwright)
* **E2E-01 (Luồng đặt hàng chuẩn - Zero Bottleneck)**:
  1. Mở trình duyệt tại `http://localhost:8080`.
  2. Điền form thông tin đơn hàng và bấm "Đặt hàng ngay".
  3. Kiểm tra badge trạng thái hiển thị HTTP `202 Accepted` kèm độ trễ phản hồi (< 50ms).
  4. Kiểm tra bảng Realtime Logs xuất hiện đủ 2 dòng xác nhận từ `EMAIL-CONSUMER` và `INVENTORY-CONSUMER`.
* **E2E-02 (Luồng cô lập lỗi - High Reliability & DLQ)**:
  1. Bấm nút "Giả lập lỗi Kho (DLQ)".
  2. Kiểm tra badge trạng thái vẫn trả về HTTP `202 Accepted`.
  3. Kiểm tra bảng Log hiển thị thông báo `INVENTORY-CONSUMER` từ chối tin nhắn (`NACK - Requeue: false`) và chuyển hướng sang `order.dlq`.

