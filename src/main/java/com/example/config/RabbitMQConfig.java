package com.example.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queues.order-queue}")
    private String orderQueue;

    @Value("${rabbitmq.queues.order-dlq}")
    private String orderDlq;

    @Value("${rabbitmq.exchanges.order-exchange}")
    private String orderExchange;

    @Value("${rabbitmq.routing-keys.order-routing-key}")
    private String orderRoutingKey;

    @Bean
    Queue orderQueue() {
        return QueueBuilder.durable(orderQueue)
                .deadLetterExchange("") // Default exchange
                .deadLetterRoutingKey(orderDlq)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(orderDlq).build();
    }

    @Bean
    DirectExchange orderExchange() {
        return new DirectExchange(orderExchange);
    }

    @Bean
    Binding binding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue)
                .to(orderExchange)
                .with(orderRoutingKey);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}