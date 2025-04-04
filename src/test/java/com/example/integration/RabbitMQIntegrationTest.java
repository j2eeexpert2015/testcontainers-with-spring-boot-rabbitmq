package com.example.integration;

import com.example.dto.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class RabbitMQIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RabbitMQIntegrationTest.class);

	@Container
	static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3-management");

	/**
	 * Dynamically inject RabbitMQ container host, port, and credentials into Spring
	 * Boot application context.
	 */
	@DynamicPropertySource
	static void configureRabbitProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
		registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
		registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
		registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
	}

	@Autowired
	private AmqpTemplate rabbitTemplate;

	/**
	 * Test that a valid order is consumed successfully and the queue is empty
	 * afterward.
	 */
	@Test
	void whenValidOrderPublished_thenConsumedSuccessfully() throws InterruptedException {
		Order validOrder = new Order("123", "Laptop", 1);
		logger.info("Publishing valid order: {}", validOrder);
		rabbitTemplate.convertAndSend("orders.exchange", "orders.routingKey", validOrder);

		// Wait briefly to allow listener to consume message
		Thread.sleep(2000);

		Object message = rabbitTemplate.receiveAndConvert("orders.queue", 1000);
		logger.info("Message received from order queue: {}", message);
		assertNull(message, "Queue should be empty after processing valid order");
	}

	/**
	 * Test that an invalid order is rejected and routed to the Dead Letter Queue
	 * (DLQ).
	 */
	@Test
	void whenInvalidOrderPublished_thenMovedToDLQ() throws InterruptedException {
		Order invalidOrder = new Order("456", "Phone", 0);
		logger.info("Publishing invalid order: {}", invalidOrder);
		rabbitTemplate.convertAndSend("orders.exchange", "orders.routingKey", invalidOrder);

		// Wait briefly to allow listener to reject and DLQ to receive
		Thread.sleep(2000);

		Object dlqMessage = rabbitTemplate.receiveAndConvert("orders.dlq", 1000);
		logger.info("Message received from DLQ: {}", dlqMessage);
		assertNotNull(dlqMessage, "Message should be in DLQ for invalid order");
		assertEquals("456", ((Order) dlqMessage).getId());
	}
}
