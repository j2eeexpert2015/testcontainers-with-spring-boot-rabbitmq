package com.example.listener;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.dto.Order;
import com.rabbitmq.client.Channel;

@Component
public class OrderListener {
    private static final Logger logger = LoggerFactory.getLogger(OrderListener.class);

    @RabbitListener(queues = "${rabbitmq.queues.order-queue}")
    public void handleOrder(Order order, Message message, Channel channel) throws IOException {
        try {
            logger.info("Processing order: {}", order);
            
            if (order.getQuantity() <= 0) {
                throw new IllegalArgumentException("Invalid quantity: " + order.getQuantity());
            }
            
            // Business logic here
            logger.info("Successfully processed order: {}", order.getId());
            
            // Manual acknowledgment
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            logger.error("Failed to process order {}: {}", order.getId(), e.getMessage());
            
            // Reject and don't requeue (will go to DLQ)
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
        }
    }
}