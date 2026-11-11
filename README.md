# Microservices Zero Bottleneck & High Reliability với RabbitMQ

[![Java](https://img.shields.io/badge/Java-21_LTS-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13_Management-FF6600.svg?style=flat-square&logo=rabbitmq)](https://www.rabbitmq.com/)
[![Playwright](https://img.shields.io/badge/Playwright-E2E_Automated-2EAD33.svg?style=flat-square&logo=playwright)](https://playwright.dev/)
[![Course](https://img.shields.io/badge/Course-MSS301_FPT_University-blue.svg?style=flat-square)](https://hcmuni.fpt.edu.vn/)

Dự án thực nghiệm giải pháp kiến trúc Microservices xử lý hàng chục ngàn giao dịch không nghẽn (**Zero Bottleneck**) và bảo toàn đơn hàng tuyệt đối khi dịch vụ con gặp sự cố (**High Reliability**) sử dụng **Spring Boot 3**, **RabbitMQ (AMQP 0-9-1)** và kiểm thử tự động trực quan với **Microsoft Playwright**.

Toàn bộ dự án được xây dựng và kiểm chứng theo phương pháp **Spec-Driven Development với AI Agent** dựa trên tài liệu đặc tả [`PRD.md`](./PRD.md).

---

## 1. Kiến Trúc Hệ Thống (Architecture Topology)

Hệ thống triển khai mô hình **Fanout Exchange (Broadcast 1-to-All)** kết hợp cơ chế xử lý tin nhắn độc hại (**Dead-Letter Exchange - DLX**) để cô lập sự cố:

```
                         [Client / Browser / Playwright]
                                       |
                         (HTTP POST /api/orders) ~15ms
                                       v
                              [OrderController]
                                       |
                       (RabbitTemplate.convertAndSend)
                                       v
                         [order.fanout.exchange] (Fanout)
                                /             \
                      (Broadcast)             (Broadcast)
                          /                         \
                         v                           v
              [order.email.queue]           [order.inventory.queue]
                       |                               |
                       v                               v
                [EmailConsumer]               [InventoryConsumer]
                       |                               |
                   (basicAck)                 (basicAck) / (basicNack)
                                                       |
                                               (Poison Pill / Lỗi)
                                                       v
                                                  [order.dlx] (Direct)
                                                       | (order.deadletter)
                                                       v
                                                  [order.dlq] (Bảo toàn)
```

### Công Nghệ Sử Dụng
* **Backend**: Java 21 LTS, Spring Boot 3.3.4, Spring AMQP (RabbitMQ), Lombok, Hibernate Validator.
* **Message Broker**: RabbitMQ 3.13 Management Alpine (chạy containerized qua Docker Compose).
* **Frontend Dashboard**: HTML5, Modern Tailwind CSS, Vanilla JS, Event Polling API (tích hợp sẵn tại cổng `8080`).
* **Kiểm Thử & Đo Lường**:
  * **Unit Test**: JUnit 5, Spring `MockMvc`, Mockito.
  * **E2E Automation Test**: Microsoft Playwright (`--headed` mode trực quan, hỗ trợ quay chậm `slowMo: 800ms`).

---

## 2. Cấu Trúc Thư Mục Dự Án (Project Structure)

```
rabbitmq-microservice-demo/
├── docker-compose.yml              # Cấu hình container RabbitMQ Management
├── pom.xml                         # Maven dependencies (Spring Boot 3, AMQP)
├── package.json                    # Cấu hình chạy kiểm thử Playwright E2E
├── playwright.config.ts            # Cấu hình Playwright (slowMo, BaseURL 8080)
├── PRD.md                          # Tài liệu đặc tả kỹ thuật (Spec-Driven Development)
├── README.md                       # Hướng dẫn dự án chuẩn GitHub
├── tests/
│   └── order-flow.spec.ts          # Bộ kiểm thử E2E Playwright (E2E-01 & E2E-02)
└── src/
    ├── main/
    │   ├── java/com/fpt/demo/
    │   │   ├── RabbitMqDemoApplication.java # Spring Boot Main Class
    │   │   ├── config/RabbitMQConfig.java   # Khởi tạo Exchange, Queues, DLX, DLQ
    │   │   ├── controller/OrderController.java # REST API tiếp nhận đơn & log
    │   │   ├── consumer/OrderConsumers.java # Consumer Email & Kho (ACK/NACK)
    │   │   ├── dto/                         # Request, Response, Message DTOs
    │   │   └── service/LogService.java      # Lưu vết realtime log cho Web UI
    │   └── resources/
    │       ├── application.yml              # Port 8080, cấu hình RabbitMQ & manual ACK
    │       └── static/index.html            # Web UI Developer Console
    └── test/java/com/fpt/demo/
        └── OrderControllerTest.java        # Unit test Controller & Validator (JUnit 5)
```

---

## 3. Yêu Cầu Môi Trường (Prerequisites)

* **Docker Desktop**: Đã cài đặt và đang chạy Docker Engine.
* **Java Development Kit (JDK)**: JDK 21 (hoặc JDK 17 trở lên).
* **Apache Maven**: Phiên bản 3.8+ (đã cấu hình biến môi trường `mvn`).
* **Node.js & NPM**: Node.js 18+ (để chạy automation test Playwright).

---

## 4. Hướng Dẫn Khởi Chạy Nhanh (Quick Start)

### Bước 1: Khởi chạy RabbitMQ Broker bằng Docker
Tại thư mục gốc của dự án, mở Terminal và chạy lệnh:
```bash
docker compose up -d
```
* **RabbitMQ AMQP Broker**: `localhost:5672`
* **RabbitMQ Management Dashboard**: [http://localhost:15672](http://localhost:15672)
  * Tài khoản: `guest`
  * Mật khẩu: `guest`

### Bước 2: Khởi chạy Ứng Dụng Spring Boot
Mở một cửa sổ Terminal mới tại thư mục dự án và thực thi:
```bash
mvn spring-boot:run
```
Sau khi thấy log `Started RabbitMqDemoApplication in ... seconds`:
👉 **Mở Trình Duyệt Truy Cập Web Console**: [http://localhost:8080](http://localhost:8080)

---

## 5. Hướng Dẫn Chạy Kiểm Thử (Testing)

### 5.1. Chạy Unit Test (JUnit 5 & MockMvc)
Kiểm tra tính toàn vẹn của tầng Controller, mã phản hồi HTTP 202 Accepted và tích hợp RabbitTemplate:
```bash
mvn test
```

### 5.2. Chạy E2E Automation Test với Microsoft Playwright
1. **Cài đặt dependencies và trình duyệt Chromium** (chỉ cần thực hiện lần đầu):
   ```bash
   npm install
   npx playwright install chromium
   ```

2. **Chạy kiểm thử tự động với giao diện trực quan (`--headed`)**:
   *(Đảm bảo ứng dụng Spring Boot và Docker RabbitMQ đang chạy)*
   ```bash
   npx playwright test --headed
   ```
   *Hoặc dùng lệnh tắt npm:*
   ```bash
   npm run test:headed
   ```

3. **Mô tả 2 kịch bản kiểm thử tự động:**
   * **`E2E-01`**: Điền đơn hàng chuẩn -> Bấm đặt hàng -> Xác thực phản hồi tức thì `HTTP 202 Accepted` (~15ms) -> Kiểm tra cả 2 Consumer (Email & Kho) đồng thời nhận tin và gửi tín hiệu **`Manual ACK`**.
   * **`E2E-02`**: Điền đơn lỗi kho (`quantity = -1`, `productName = MacBook Pro ERROR`) -> Consumer Kho phát hiện poison pill -> Gửi tín hiệu **`Manual NACK (requeue=false)`** -> RabbitMQ tự động đẩy tin vào **`order.dlq`** an toàn, không nghẽn queue chính.

4. **Xem báo cáo HTML chi tiết**:
   ```bash
   npx playwright show-report
   ```
   *Hoặc:*
   ```bash
   npm run report
   ```

---

## 6. Danh Mục REST API

| Phương thức | Endpoint | Chức năng | Phản hồi chuẩn | Ghi chú |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/orders` | Tiếp nhận đơn hàng mới và đẩy bất đồng bộ vào Fanout Exchange | `202 Accepted` | Zero Bottleneck (~15ms) |
| `GET` | `/api/orders/logs` | Truy vấn danh sách realtime logs của Producer & Consumer | `200 OK` | Phục vụ Web Console |
| `DELETE` | `/api/orders/logs` | Xóa sạch bộ đệm logs trên RAM | `204 No Content` | Dọn dẹp trạng thái |

---

## 7. Tài Liệu Kỹ Thuật
* **Đặc tả dự án theo chuẩn Spec-Driven Development**: Xem chi tiết tại [`PRD.md`](./PRD.md)
