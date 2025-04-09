package com.example.integration;

import com.example.dto.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers 
@AutoConfigureMockMvc
public class OrderControllerTestcontainersIT {

    private static final Logger logger = LoggerFactory.getLogger(OrderControllerTestcontainersIT.class);

    private static final DockerImageName RABBITMQ_IMAGE = DockerImageName.parse("rabbitmq:3-management");

    /*
    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(RABBITMQ_IMAGE)
            .withExposedPorts(5672, 15672);
     */

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(RABBITMQ_IMAGE)
            .withExposedPorts(5672, 15672)
            .waitingFor(
                    // Wait for a specific log message indicating RabbitMQ is ready
                    // The regex '.*Server startup complete.*\\n' waits for a line containing "Server startup complete"
                    // The '1' means wait for the first occurrence.
                    Wait.forLogMessage(".*Server startup complete.*\\n", 1)
                            .withStartupTimeout(Duration.ofSeconds(120)) // Increased timeout for slower systems
            );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    // Dynamically configure properties from the container
    @DynamicPropertySource
    static void configureRabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
       
        registry.add("spring.rabbitmq.listener.simple.acknowledge-mode", () -> "manual");
        registry.add("spring.rabbitmq.listener.simple.default-requeue-rejected", () -> "false");
        registry.add("rabbitmq.queues.order-queue", () -> "orders.queue");
        registry.add("rabbitmq.queues.order-dlq", () -> "orders.dlq");
        registry.add("rabbitmq.exchanges.order-exchange", () -> "orders.exchange");
        registry.add("rabbitmq.routing-keys.order-routing-key", () -> "orders.routingKey");
    }

    @Test
    void whenPostValidOrder_thenReturnsOkAndProcesses() throws Exception {
        Order validOrder = new Order("TC-INT-001", "Testcontainers Product", 1);
        String orderJson = objectMapper.writeValueAsString(validOrder);

        logger.info("TC-IT: Sending valid order via MockMvc: {}", orderJson);
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Order received and published: ID TC-INT-001"));

        await().pollDelay(Duration.ofSeconds(1)).untilAsserted(() -> {
            Object dlqMessage = amqpTemplate.receiveAndConvert("orders.dlq");
            assertThat(dlqMessage).isNull();
            logger.info("TC-IT: Verified DLQ is empty after valid order.");
        });
    }

    @Test
    void whenPostInvalidOrder_thenReturnsOkAndGoesToDLQ() throws Exception {
        Order invalidOrder = new Order("TC-INT-002", "TC Invalid Product", 0);
        String orderJson = objectMapper.writeValueAsString(invalidOrder);

        logger.info("TC-IT: Sending invalid order via MockMvc: {}", orderJson);
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Order received and published: ID TC-INT-002"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Object dlqMessage = amqpTemplate.receiveAndConvert("orders.dlq");
            assertThat(dlqMessage).isNotNull();
            assertThat(dlqMessage).isInstanceOf(Order.class);
            assertThat(((Order) dlqMessage).getId()).isEqualTo("TC-INT-002");
            logger.info("TC-IT: Verified invalid order {} landed in DLQ.", ((Order) dlqMessage).getId());
        });
    }
}