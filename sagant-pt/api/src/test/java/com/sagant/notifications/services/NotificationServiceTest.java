package com.sagant.notifications.services;

import com.sagant.notifications.domain.IdempotencyRecord;
import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.domain.enums.NotificationStatus;
import com.sagant.notifications.exceptions.IdempotencyConflictException;
import com.sagant.notifications.exceptions.MissingIdempotencyKeyException;
import com.sagant.notifications.repositories.IdempotencyRecordRepository;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @InjectMocks
    private NotificationService notificationService;

    /*
     * Verifica que, al recibir una solicitud con una clave de idempotencia nueva,
     * el servicio crea la notificación, la guarda y la asocia al registro
     * de idempotencia correspondiente.
     */
    @Test
    void shouldCreateAndSaveNotification() {

        Map<String, String> metadata = Map.of(
                "template", "payment-confirmation",
                "language", "es"
        );


        Notification notificationToReturn = new Notification(
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                metadata,
                null
        );

        IdempotencyRecord idempotencyRecord = new IdempotencyRecord("abc-123");

        org.mockito.Mockito.when(notificationRepository.save(any(Notification.class)))
                .thenReturn(notificationToReturn);

        org.mockito.Mockito.when(idempotencyRecordRepository.reserveKey("abc-123"))
                .thenReturn(1);

        org.mockito.Mockito.when(idempotencyRecordRepository.findById("abc-123"))
                .thenReturn(java.util.Optional.of(idempotencyRecord));

        // Comprobar que el servicio fue inicializado.
        assertThat(notificationService).isNotNull();


        // Ejecutamos el caso de uso con una clave de idempotencia nueva.
        Notification result = notificationService.createNotification(
                "abc-123",
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                metadata,
                null
        );

        // Verificamos los datos de la notificación.
        assertThat(result.getRecipient())
                .isEqualTo("destinatario@test.com");
        assertThat(result.getChannel())
                .isEqualTo(NotificationChannel.EMAIL);
        assertThat(result.getSubject())
                .isEqualTo("Confirmación de pago");
        assertThat(result.getBody())
                .isEqualTo("Tu pago fue procesado correctamente");
        assertThat(result.getPriority())
                .isEqualTo(NotificationPriority.HIGH);
        assertThat(result.getMetadata())
                .isEqualTo(metadata);
        assertThat(result.getStatus())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(result.getCreatedAt()).isNotNull();

        // Verificamos que se llamó una sola vez al repositorio para guardar la notificación
        verify(notificationRepository, org.mockito.Mockito.times(1))
                .save(any(Notification.class));

        // Verificamos que la notificación guardada queda asociada al registro correspondiente
        // a la clave de idempotencia.
        assertThat(idempotencyRecord.getNotification())
                .isSameAs(notificationToReturn);
    }


    /*
     * Verifica que, al reutilizar una clave de idempotencia con una solicitud
     * idéntica, el servicio devuelve la notificación original y no crea otra.
     *
     * La reserva de la clave indica que ya existe y el registro recuperado
     * contiene la notificación previamente asociada.
     */
    @Test
    void shouldReturnOriginalNotificationWhenSameIdempotencyKeyIsReused() {
        String key = "abc-123";
        Map<String, String> metadata = Map.of(
                "template", "payment-confirmation",
                "language", "es"
        );

        Notification original = new Notification(
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                metadata,
                null
        );

        IdempotencyRecord record = new IdempotencyRecord(key);
        record.associateNotification(original);

        org.mockito.Mockito.doReturn(0)
                .when(idempotencyRecordRepository)
                .reserveKey(key);

        org.mockito.Mockito.when(idempotencyRecordRepository.findById(key))
                .thenReturn(java.util.Optional.of(record));

        Notification result = notificationService.createNotification(
                key,
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                metadata,
                null
        );

        org.assertj.core.api.Assertions.assertThat(result).isSameAs(original);

        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    /*
     * Verifica que se rechaza una solicitud que reutiliza una clave de
     * idempotencia existente con datos diferentes a los de la solicitud original.
     *
     * El servicio lanza IdempotencyConflictException y no guarda otra
     * notificación.
     */
    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentRequest() {
        String key = "abc-123";

        Notification original = new Notification(
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                Map.of("language", "es"),
                null
        );

        IdempotencyRecord record = new IdempotencyRecord(key);
        record.associateNotification(original);

        org.mockito.Mockito.when(idempotencyRecordRepository.reserveKey(key))
                .thenReturn(0);

        org.mockito.Mockito.when(idempotencyRecordRepository.findById(key))
                .thenReturn(java.util.Optional.of(record));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                notificationService.createNotification(
                        key,
                        "otro-destinatario@test.com",
                        NotificationChannel.EMAIL,
                        "Confirmación de pago",
                        "Tu pago fue procesado correctamente",
                        NotificationPriority.HIGH,
                        Map.of("language", "es"),
                        null
                )
        ).isInstanceOf(IdempotencyConflictException.class);

        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    /*
     * Verifica que el servicio rechaza una clave de idempotencia compuesta
     * únicamente por espacios, lanzando MissingIdempotencyKeyException.
     *
     * La validación ocurre antes de reservar o consultar la clave y antes
     * de guardar la notificación.
     */
    @Test
    void shouldRejectBlankIdempotencyKey() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        notificationService.createNotification(
                                "   ",
                                "destinatario@test.com",
                                NotificationChannel.EMAIL,
                                "Confirmación de pago",
                                "Tu pago fue procesado correctamente",
                                NotificationPriority.HIGH,
                                Map.of("language", "es"),
                                null
                        )
                ).isInstanceOf(MissingIdempotencyKeyException.class)
                .hasMessageContaining("La clave de idempotencia es obligatoria");

        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(Notification.class));

        org.mockito.Mockito.verifyNoInteractions(idempotencyRecordRepository);
    }

}
