package com.fpt.demo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Main Topology: Fanout Exchange và 2 Queue chính
    public static final String ORDER_FANOUT_EXCHANGE = "order.fanout.exchange";
    public static final String ORDER_EMAIL_QUEUE = "order.email.queue";
    public static final String ORDER_INVENTORY_QUEUE = "order.inventory.queue";

    // Dead-Letter Topology: DLX và DLQ cho các tin nhắn bị lỗi
    public static final String ORDER_DLX_EXCHANGE = "order.dlx";
    public static final String ORDER_DLQ_QUEUE = "order.dlq";
    public static final String ORDER_DEADLETTER_ROUTING_KEY = "order.deadletter";

    // FanoutExchange(tên exchange, durable, auto-delete)
    // durable: true => bền vững; nếu restart container Docker, Exchange này không
    // bị mất.
    // auto-delete: false => Khi không còn ai kết nối thì không tự xóa.
    @Bean
    public FanoutExchange orderFanoutExchange() {
        return new FanoutExchange(ORDER_FANOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderEmailQueue() {
        return new Queue(ORDER_EMAIL_QUEUE, true);
    }

    @Bean
    public Queue orderInventoryQueue() {
        return QueueBuilder.durable(ORDER_INVENTORY_QUEUE)
                .deadLetterExchange(ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DEADLETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(ORDER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDlq() {
        return new Queue(ORDER_DLQ_QUEUE, true);
    }

    @Bean
    public Binding bindingEmailQueue(Queue orderEmailQueue, FanoutExchange orderFanoutExchange) {
        return BindingBuilder.bind(orderEmailQueue).to(orderFanoutExchange);
    }

    @Bean
    public Binding bindingInventoryQueue(Queue orderInventoryQueue, FanoutExchange orderFanoutExchange) {
        return BindingBuilder.bind(orderInventoryQueue).to(orderFanoutExchange);
    }

    @Bean
    public Binding bindingDlq(Queue orderDlq, DirectExchange orderDlxExchange) {
        return BindingBuilder.bind(orderDlq).to(orderDlxExchange).with(ORDER_DEADLETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
