package com.fpt.demo.consumer;

import java.io.IOException;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fpt.demo.config.RabbitMQConfig;
import com.fpt.demo.dto.OrderMessage;
import com.fpt.demo.service.LogService;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumers {

    private final LogService logService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_EMAIL_QUEUE)
    public void handleEmailNotification(OrderMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            log.info("[EMAIL-CONSUMER] Nhận được đơn hàng: {}", message.getOrderId());

            // Giả lập tác vụ gửi email ngầm (khoảng 10ms)
            Thread.sleep(10);

            logService.addLog("EMAIL-CONSUMER",
                    "Đã gửi email xác nhận cho " + message.getEmail() + " (Manual ACK)");

            // Gửi tín hiệu xác nhận hoàn thành (Manual ACK)
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[EMAIL-CONSUMER] Lỗi xử lý: ", e);
            // Nếu có lỗi bất ngờ, nack và không requeue để tránh kẹt hàng đợi
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_INVENTORY_QUEUE)
    public void handleInventoryDeduction(OrderMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            log.info("[INVENTORY-CONSUMER] Nhận được đơn hàng: {}", message.getOrderId());

            // Quy tắc kích hoạt DLQ: Nếu quantity <= 0 hoặc productName chứa từ khóa
            // "ERROR"
            boolean isPoisonMessage = message.getQuantity() == null || message.getQuantity() <= 0
                    || (message.getProductName() != null && message.getProductName().toUpperCase().contains("ERROR"));

            if (isPoisonMessage) {
                // ĐƠN LỖI: Chủ động từ chối tin nhắn và KHÔNG requeue -> RabbitMQ tự đẩy vào
                // order.dlq
                logService.addLog("INVENTORY-CONSUMER",
                        "Lỗi kho: Đơn " + message.getOrderId() + " không hợp lệ (SL: " + message.getQuantity()
                                + ", SP: " + message.getProductName()
                                + ") -> NACK (Requeue: false) -> Đẩy sang order.dlq");

                channel.basicNack(tag, false, false);
                return;
            }

            // ĐƠN HỢP LỆ: Giả lập trừ kho và gửi Manual ACK
            Thread.sleep(15);

            logService.addLog("INVENTORY-CONSUMER",
                    "Đã trừ kho " + message.getQuantity() + "x " + message.getProductName() + " (Manual ACK)");

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[INVENTORY-CONSUMER] Lỗi ngoại lệ: ", e);
            channel.basicNack(tag, false, false);
        }
    }
}
