package com.example.unit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.dto.Order;
import com.example.service.OrderService;

@ExtendWith(MockitoExtension.class)
public class OrderServiceUnitTest {
    @Mock
    private RabbitTemplate rabbitTemplate;
    
    @InjectMocks
    private OrderService orderService;
    
    private final String testExchange = "orders.exchange";
    private final String testRoutingKey = "orders.routingKey";
    
    @BeforeEach
    void setUp() {
        // Initialize the service with test values
        orderService = new OrderService(rabbitTemplate, testExchange, testRoutingKey);
    }
    
    @Test
    void publishOrder_shouldSendToRabbitMQ() {
        // Given
        Order order = new Order("789", "Monitor", 2);
        
        // When
        orderService.publishOrder(order);
        
        // Then
        verify(rabbitTemplate).convertAndSend(
            eq(testExchange),
            eq(testRoutingKey),
            eq(order)
        );
    }
}