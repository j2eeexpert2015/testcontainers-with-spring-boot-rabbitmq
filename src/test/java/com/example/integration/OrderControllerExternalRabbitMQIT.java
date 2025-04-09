package com.example.integration;

import com.example.dto.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class OrderControllerExternalRabbitMQIT {

    // SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(OrderControllerExternalRabbitMQIT.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Test
    void whenPostValidOrder_thenReturnsOkAndProcesses() throws Exception {
        Order validOrder = new Order("EXT-INT-001", "External RMQ Product", 1);
        String orderJson = objectMapper.writeValueAsString(validOrder);

        logger.info("EXT-IT: Sending valid order via MockMvc: {}", orderJson);
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Order received and published: ID EXT-INT-001"));

        await().pollDelay(Duration.ofSeconds(1)).untilAsserted(() -> {
            Object dlqMessage = amqpTemplate.receiveAndConvert("orders.dlq"); // Check against external DLQ
            assertThat(dlqMessage).isNull();
            logger.info("EXT-IT: Verified DLQ is empty after valid order.");
        });
    }

    @Test
    void whenPostInvalidOrder_thenReturnsOkAndGoesToDLQ() throws Exception {
        Order invalidOrder = new Order("EXT-INT-002", "External Invalid Product", 0);
        String orderJson = objectMapper.writeValueAsString(invalidOrder);

        logger.info("EXT-IT: Sending invalid order via MockMvc: {}", orderJson);
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Order received and published: ID EXT-INT-002"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Object dlqMessage = amqpTemplate.receiveAndConvert("orders.dlq"); // Check against external DLQ
            assertThat(dlqMessage).isNotNull();
            assertThat(dlqMessage).isInstanceOf(Order.class);
            assertThat(((Order) dlqMessage).getId()).isEqualTo("EXT-INT-002");
            logger.info("EXT-IT: Verified invalid order {} landed in DLQ.", ((Order) dlqMessage).getId());
        });
    }
}