package com.sagant.notifications.repositories;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.domain.enums.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/*
* prueba de integración para comprobar que Spring Data JPA puede guardar
* y recuperar la notificación usando PostgreSQL.
*
* */

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
public class NotificationRepositoryTest {

    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationRepositoryTest(NotificationRepository notificationRepository){
        this.notificationRepository = notificationRepository;
    }

    @Test
    void shouldPersistAndRetrieveNotification() {
        Map<String, String> metadata = Map.of(
                "template", "payment-confirmation",
                "language", "es"
        );

        Notification notification = new Notification(
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Confirmación de pago",
                "Tu pago fue procesado correctamente",
                NotificationPriority.HIGH,
                metadata
        );

        // Act: persistir en PostgreSQL
        Notification saved = notificationRepository.saveAndFlush(notification);

        // Assert: verificar que se generó el identificador
        assertThat(saved.getIdNotification()).isNotNull();

        // Act: recuperar la notificación
        Optional<Notification> result = notificationRepository.findById(saved.getIdNotification());

        // Assert: verificar los datos recuperados
        assertThat(result).isPresent();

        Notification recovered = result.orElseThrow();

        assertThat(recovered.getIdNotification())
                .isEqualTo(saved.getIdNotification());

        assertThat(recovered.getChannel())
                .isEqualTo(NotificationChannel.EMAIL);
        assertThat(recovered.getSubject())
                .isEqualTo("Confirmación de pago");
        assertThat(recovered.getBody())
                .isEqualTo("Tu pago fue procesado correctamente");
        assertThat(recovered.getPriority())
                .isEqualTo(NotificationPriority.HIGH);
        assertThat(recovered.getMetadata())
                .containsEntry("template", "payment-confirmation")
                .containsEntry("language", "es");
        assertThat(recovered.getStatus())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(recovered.getCreatedAt()).isNotNull();
    }
}
