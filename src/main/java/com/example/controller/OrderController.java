package com.example.controller;

import com.example.dto.Order;
import com.example.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders") // Base path for order-related endpoints
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    @Autowired // Constructor injection
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping // Handles POST requests to /orders
    public ResponseEntity<String> createOrder(@RequestBody Order order) {
        logger.info("Received order creation request via API: {}", order);
        try {
            orderService.publishOrder(order); // Use the existing service to publish
            logger.info("Order {} published successfully via API.", order.getId());
            return ResponseEntity.ok("Order received and published: ID " + order.getId());
        } catch (Exception e) {
            logger.error("Error publishing order {} via API: {}", order.getId(), e.getMessage(), e);
            // Depending on requirements, you might want a more specific error response
            return ResponseEntity.internalServerError().body("Failed to publish order.");
        }
    }
}