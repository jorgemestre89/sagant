package com.sagant.notifications.services;

import com.sagant.notifications.domain.IdempotencyRecord;
import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.exceptions.IdempotencyConflictException;
import com.sagant.notifications.repositories.IdempotencyRecordRepository;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class NotificationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sagant_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    /*
     * Limpia los registros antes de cada prueba para evitar que los datos
     * de una ejecución afecten los resultados de las siguientes.
     * Se eliminan primero las claves de idempotencia para respetar la
     * relación con las notificaciones.
     */
    @BeforeEach
    void cleanDatabase() {
        idempotencyRecordRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    /*
     * Verifica que, al reutilizar una clave de idempotencia con una solicitud
     * idéntica a la original, el servicio devuelve la notificación previamente
     * creada sin insertar una nueva.
     *
     * También comprueba que existe un único registro de notificación, que existe
     * un único registro de idempotencia y que ambos están correctamente asociados.
     */
    @Test
    void shouldReturnOriginalNotificationWhenIdempotencyKeyIsReused() {
        String idempotencyKey = "integration-key-001";
        String recipient = "destinatario@test.com";
        NotificationChannel channel = NotificationChannel.LOG;
        String subject = "Confirmación de pago";
        String body = "Tu pago fue procesado correctamente";
        NotificationPriority priority = NotificationPriority.HIGH;
        Map<String, String> metadata = Map.of(
                "template", "payment-confirmation",
                "language", "es"
        );

        // Act: primera solicitud; debe crear y persistir la notificación.
        Notification original = notificationService.createNotification(
                idempotencyKey, recipient, channel, subject, body, priority, metadata, null
        );

        // Act: repetir exactamente la misma solicitud con la misma clave.
        Notification retry = notificationService.createNotification(
                idempotencyKey, recipient, channel, subject, body, priority, metadata, null
        );

        // Assert: el reintento devuelve la notificación original.
        assertThat(retry.getIdNotification())
                .isEqualTo(original.getIdNotification());

        // Assert: solo existe una notificación y un registro de idempotencia.
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);

        // Assert: el registro de idempotencia está asociado a la notificación original.
        IdempotencyRecord record = idempotencyRecordRepository
                .findById(idempotencyKey)
                .orElseThrow();

        assertThat(record.getNotification().getIdNotification())
                .isEqualTo(original.getIdNotification());
    }


    /*
     * Verifica que el servicio rechaza una solicitud que reutiliza una clave
     * de idempotencia existente con datos diferentes a los de la solicitud
     * original.
     *
     * Se espera una IdempotencyConflictException y se comprueba que el intento
     * rechazado no genera una nueva notificación ni un nuevo registro
     * de idempotencia.
     */
    @Test
    void shouldRejectDifferentRequestWhenIdempotencyKeyIsReused() {
        String idempotencyKey = "integration-key-002";
        Map<String, String> metadata = Map.of(
                "template", "payment-confirmation",
                "language", "es"
        );

        Notification original = notificationService.createNotification(
                idempotencyKey,
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                metadata,
                null
        );

        // Act / Assert: reutilizar la clave con un destinatario diferente.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                notificationService.createNotification(
                        idempotencyKey,
                        "otro-destinatario@test.com",
                        NotificationChannel.EMAIL,
                        "Confirmación de pago",
                        "Tu pago fue procesado correctamente",
                        NotificationPriority.HIGH,
                        metadata,
                        null
                )
        ).isInstanceOf(IdempotencyConflictException.class);

        // Assert: no se creó una segunda notificación.
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);

        assertThat(notificationRepository.findById(original.getIdNotification()))
                .isPresent();
    }

}
