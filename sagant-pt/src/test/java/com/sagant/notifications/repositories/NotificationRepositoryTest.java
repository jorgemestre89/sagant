package com.sagant.notifications.repositories;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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
        // Arrange: preparar la notificación
        Notification notification = new Notification(
                "destinatario@test.com",
                "Notificación de prueba"
        );

        // Act: persistir en PostgreSQL
        Notification saved = notificationRepository.saveAndFlush(
                notification
        );

        // Assert: verificar que se generó el identificador
        assertThat(saved.getIdNotification()).isNotNull();

        // Act: recuperar la notificación
        Optional<Notification> result = notificationRepository.findById(saved.getIdNotification());

        // Assert: verificar los datos recuperados
        assertThat(result).isPresent();

        Notification recovered = result.orElseThrow();

        assertThat(recovered.getIdNotification())
                .isEqualTo(saved.getIdNotification());

        assertThat(recovered.getRecipient())
                .isEqualTo("destinatario@test.com");

        assertThat(recovered.getMessage())
                .isEqualTo("Notificación de prueba");

        assertThat(recovered.getStatus())
                .isEqualTo(NotificationStatus.PENDING);

        assertThat(recovered.getCreatedAt())
                .isNotNull();
    }
}
