package com.example.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.dto.Order;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public OrderService(RabbitTemplate rabbitTemplate,
                      @Value("${rabbitmq.exchanges.order-exchange}") String exchange,
                      @Value("${rabbitmq.routing-keys.order-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publishOrder(Order order) {
        logger.info("Publishing order: {}", order);
        rabbitTemplate.convertAndSend(exchange, routingKey, order);
    }
}