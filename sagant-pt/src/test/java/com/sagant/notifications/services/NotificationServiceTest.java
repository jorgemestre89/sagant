package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationStatus;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        Notification notificationToReturn = new Notification(
                "destinatario@test.com",
                "Notificación de prueba"
        );

        org.mockito.Mockito.doReturn(notificationToReturn)
                .when(notificationRepository)
                .save(any(Notification.class));

        // Comprobar que el servicio fue inicializado.
        assertThat(notificationService).isNotNull();


        //creo la notificación
        Notification result = notificationService.createNotification(
                "destinatario@test.com",
                "Notificación de prueba"
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRecipient())
                .isEqualTo(notificationToReturn.getRecipient());
        assertThat(result.getMessage())
                .isEqualTo(notificationToReturn.getMessage());
        assertThat(result.getStatus())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(result.getCreatedAt()).isNotNull();

        // Verificamos que se llamó una sola vez al repositorio
        verify(notificationRepository, org.mockito.Mockito.times(1))
                .save(any(Notification.class));
    }

}
