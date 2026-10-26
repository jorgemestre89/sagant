package com.sagant.notifications.domain;

import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.domain.enums.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idNotification;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, length = 5000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    //attr para reintentos
    @Column(nullable = false)
    private int attemptCount;

    @Column
    private Instant nextAttemptAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    //permite saber cuándo una notificación pasó a PROCESSING y detectar reservas abandonadas si la aplicación se cae
    @Column
    private Instant processingStartedAt;

    public Notification(String recipient, NotificationChannel channel, String subject, String body,
                        NotificationPriority priority, Map<String, String> metadata) {
        this.recipient = recipient;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.priority = priority;
        this.status = NotificationStatus.PENDING;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    //tranciciones del estado de una notificación
    /**
     * Marca la notificación como en proceso e incrementa el contador
     * de intentos de envío.
     */
    public void markAsProcessing() {
        this.status = NotificationStatus.PROCESSING;
        this.processingStartedAt = Instant.now();
        this.attemptCount++;
    }

    /**
     * Marca la notificación como enviada y borra las fechas de
     * inicio de procesamiento, el proximo intento y el mensaje
     * de último error.
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.processingStartedAt = null;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * Marca la notificación como fallida y borra la fecha de
     * inicio de procesamiento y guarda el mensaje de error
     */
    public void markAsFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.processingStartedAt = null;
        this.lastError = error;
    }

    public void scheduleRetry(Instant retryAt, String error) {
        this.status = NotificationStatus.PENDING;
        this.processingStartedAt = null;
        this.nextAttemptAt = retryAt;
        this.lastError = error;
    }

    /**
     * Marca la notificación como pendiente para que
     * pueda ser procesada y borrra la fecha de
     * inicio de procesamiento.
     */
    public void recoverFromProcessing() {
        this.status = NotificationStatus.PENDING;
        this.processingStartedAt = null;
    }

}
