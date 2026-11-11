package com.fpt.demo.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpt.demo.config.RabbitMQConfig;
import com.fpt.demo.dto.OrderMessage;
import com.fpt.demo.dto.OrderRequest;
import com.fpt.demo.dto.OrderResponse;
import com.fpt.demo.service.LogService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final RabbitTemplate rabbitTemplate;
    private final LogService logService;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Tạo mã đơn hàng duy nhất dạng ORD-XXXXXX
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String nowIso = Instant.now().toString();

        // 2. Đóng gói message payload để đẩy vào message broker
        OrderMessage orderMessage = OrderMessage.builder()
                .orderId(orderId)
                .customerName(request.getCustomerName())
                .email(request.getEmail())
                .productName(request.getProductName())
                .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                .amount(request.getAmount() != null ? request.getAmount() : 0.0)
                .createdAt(nowIso)
                .build();

        // 3. Đẩy bất đồng bộ vào Fanout Exchange (Zero Bottleneck)
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_FANOUT_EXCHANGE, "", orderMessage);

        // 4. Ghi log hành động của Producer
        logService.addLog("PRODUCER", "Đã bắn sự kiện " + orderId + " vào " + RabbitMQConfig.ORDER_FANOUT_EXCHANGE);

        // 5. Đo độ trễ phản hồi (thường chỉ 5 - 20ms)
        long latencyMs = System.currentTimeMillis() - startTime;

        OrderResponse response = OrderResponse.builder()
                .orderId(orderId)
                .status("ACCEPTED")
                .message("Đơn hàng đã được tiếp nhận và đẩy vào Fanout Exchange thành công!")
                .latencyMs(latencyMs)
                .timestamp(nowIso)
                .build();

        // 6. Trả về ngay HTTP 202 Accepted mà không cần đợi gửi email hay trừ kho
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<String>> getLogs() {
        return ResponseEntity.ok(logService.getLogs());
    }

    @DeleteMapping("/logs")
    public ResponseEntity<Void> clearLogs() {
        logService.clearLogs();
        return ResponseEntity.noContent().build();
    }
}
