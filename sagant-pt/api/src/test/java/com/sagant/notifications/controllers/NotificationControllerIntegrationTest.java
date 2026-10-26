package com.sagant.notifications.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagant.notifications.repositories.IdempotencyRecordRepository;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class NotificationControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("sagant_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        idempotencyRecordRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    /*
     * Verifica que el endpoint REST permita crear una notificación
     * cuando recibe una solicitud válida y una clave de idempotencia.
     *
     * Comprueba el estado HTTP, los datos principales de la respuesta,
     * la cabecera Location y la persistencia de la notificación.
     */
    @Test
    void shouldCreateNotificationThroughRestEndpoint() throws Exception {
        String idempotencyKey = "controller-integration-key-001";

        String requestBody = """
                {
                    "recipient": "destinatario@test.com",
                    "channel": "EMAIL",
                    "subject": "Confirmación de pago",
                    "body": "Tu pago fue procesado correctamente",
                    "priority": "HIGH",
                    "metadata": {
                        "template": "payment-confirmation",
                        "language": "es"
                    }
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNotification").isNumber())
                .andExpect(jsonPath("$.recipient")
                        .value("destinatario@test.com"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.subject")
                        .value("Confirmación de pago"))
                .andExpect(jsonPath("$.body")
                        .value("Tu pago fue procesado correctamente"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.metadata.language").value("es"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Location"));

        // Verificar que la petición también produjo los registros esperados en PostgreSQL.
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }


    /*
     * Verifica que, al enviar dos veces la misma solicitud HTTP con la misma
     * clave de idempotencia, el endpoint devuelve la misma notificación
     * y no genera registros duplicados en PostgreSQL.
     */
    @Test
    void shouldReturnOriginalNotificationWhenSameRequestIsRepeatedThroughRestEndpoint()
            throws Exception {

        String idempotencyKey = "controller-integration-key-002";

        String requestBody = """
            {
                "recipient": "destinatario@test.com",
                "channel": "EMAIL",
                "subject": "Confirmación de pago",
                "body": "Tu pago fue procesado correctamente",
                "priority": "HIGH",
                "metadata": {
                    "template": "payment-confirmation",
                    "language": "es"
                }
            }
            """;

        // Primera petición: crear la notificación.
        String firstResponse = mockMvc.perform(post("/api/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Obtener el identificador devuelto por la primera petición.
        long originalId = objectMapper.readTree(firstResponse)
                .get("idNotification")
                .asLong();

        // Segundo intento: repetir exactamente la misma petición.
        mockMvc.perform(post("/api/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNotification").value(originalId));

        // Comprobar que el reintento no duplicó los registros.
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }


    /*
     * Verifica que el endpoint rechaza una petición que reutiliza una clave
     * de idempotencia existente con datos diferentes a los de la solicitud
     * original, devolviendo HTTP 409 Conflict.
     *
     * También comprueba que el intento rechazado no crea otra notificación
     * ni otro registro de idempotencia.
     */
    @Test
    void shouldReturnConflictWhenDifferentRequestUsesSameKeyThroughRestEndpoint()
            throws Exception {

        String idempotencyKey = "controller-integration-key-003";

        String originalRequest = """
            {
                "recipient": "destinatario@test.com",
                "channel": "EMAIL",
                "subject": "Confirmación de pago",
                "body": "Tu pago fue procesado correctamente",
                "priority": "HIGH",
                "metadata": {
                    "template": "payment-confirmation",
                    "language": "es"
                }
            }
            """;

        String differentRequest = """
            {
                "recipient": "otro-destinatario@test.com",
                "channel": "EMAIL",
                "subject": "Confirmación de pago",
                "body": "Tu pago fue procesado correctamente",
                "priority": "HIGH",
                "metadata": {
                    "template": "payment-confirmation",
                    "language": "es"
                }
            }
            """;

        // Primera petición: registrar la clave con la solicitud original.
        mockMvc.perform(post("/api/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(originalRequest))
                .andExpect(status().isCreated());

        // Segundo intento: misma clave, pero con un destinatario diferente.
        mockMvc.perform(post("/api/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentRequest))
                .andExpect(status().isConflict());

        // Comprobar que solo permanecen los registros de la solicitud original.
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }

}
