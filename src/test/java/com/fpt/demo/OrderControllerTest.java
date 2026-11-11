package com.fpt.demo;

import com.fpt.demo.controller.OrderController;
import com.fpt.demo.service.LogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private LogService logService;

    @Test
    @DisplayName("UT-01: Đặt hàng hợp lệ -> Trả về HTTP 202 Accepted và đẩy vào Fanout Exchange")
    void shouldAcceptOrderImmediatelyAndPublishToFanoutExchange() throws Exception {
        String payload = """
            {
                "customerName": "Võ Quang Trung",
                "email": "trungvq@fpt.edu.vn",
                "productName": "MacBook Pro M3 Max",
                "quantity": 1,
                "amount": 3500.0
            }
            """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted()) // HTTP 202 Accepted
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.latencyMs").exists());

        // Xác thực RabbitTemplate.convertAndSend() được gọi đúng 1 lần vào Fanout Exchange
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq("order.fanout.exchange"), eq(""), any(Object.class));
    }

    @Test
    @DisplayName("UT-02: Đặt hàng thiếu tên sản phẩm -> Trả về HTTP 400 Bad Request")
    void shouldReturnBadRequestWhenProductNameIsMissing() throws Exception {
        String invalidPayload = """
            {
                "customerName": "Võ Quang Trung",
                "email": "trungvq@fpt.edu.vn",
                "quantity": 1,
                "amount": 3500.0
            }
            """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest()); // HTTP 400 Bad Request
    }
}

