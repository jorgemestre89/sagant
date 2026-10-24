package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.domain.enums.NotificationStatus;
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

    @InjectMocks
    private NotificationService notificationService;

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
                metadata
        );

        org.mockito.Mockito.doReturn(notificationToReturn)
                .when(notificationRepository)
                .save(any(Notification.class));

        // Comprobar que el servicio fue inicializado.
        assertThat(notificationService).isNotNull();


        //creo la notificación
        Notification result = notificationService.createNotification(
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                metadata
        );

        // Assert
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

        // Verificamos que se llamó una sola vez al repositorio
        verify(notificationRepository, org.mockito.Mockito.times(1))
                .save(any(Notification.class));
    }

}
