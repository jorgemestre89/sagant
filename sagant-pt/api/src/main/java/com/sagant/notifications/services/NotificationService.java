package com.sagant.notifications.services;

import com.sagant.notifications.domain.IdempotencyRecord;
import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.exceptions.IdempotencyConflictException;
import com.sagant.notifications.exceptions.MissingIdempotencyKeyException;
import com.sagant.notifications.repositories.IdempotencyRecordRepository;
import com.sagant.notifications.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               IdempotencyRecordRepository idempotencyRecordRepository) {
        this.notificationRepository = notificationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public Notification createNotification(String idempotencyKey, String recipient, NotificationChannel channel,
                                           String subject, String body, NotificationPriority priority,
                                           Map<String, String> metadata, String serviceUrl) {

        if (Objects.isNull(idempotencyKey) || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("No se puede crear la notificación. "+
                    "La clave de idempotencia es obligatoria."
            );
        }

        int reserved = idempotencyRecordRepository.reserveKey(idempotencyKey);

        if (reserved == 0) {
            IdempotencyRecord existing = idempotencyRecordRepository.findById(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("No se puede crear la notificación. "+
                            "No se encontró el registro de idempotencia"));

            Notification original = existing.getNotification();

            if (!sameRequest(original, recipient, channel, subject, body, priority, metadata, serviceUrl)) {

                log.warn(
                        "Conflicto de idempotencia: la clave ya fue utilizada con otra solicitud notificationId={}",
                        original.getIdNotification()
                );

                throw new IdempotencyConflictException("No se puede crear la notificación. "+
                        "La clave de idempotencia ya fue utilizada con una solicitud diferente."
                );
            }

            log.info(
                    "Solicitud idempotente repetida: se devuelve la notificación original notificationId={} estado={}",
                    original.getIdNotification(),
                    original.getStatus()
            );

            return original;
        }

        IdempotencyRecord record = idempotencyRecordRepository.findById(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("No se puede crear la notificación. "+
                        "No se encontró el registro de idempotencia reservado"));

        Notification notification = saveNotification(recipient, channel, subject, body, priority, metadata, serviceUrl);

        record.associateNotification(notification);

        log.info(
                "Notificación creada y asociada al registro de idempotencia notificationId={} canal={} prioridad={} estado={}",
                notification.getIdNotification(),
                notification.getChannel(),
                notification.getPriority(),
                notification.getStatus()
        );

        return notification;
    }

    private Notification saveNotification(String recipient, NotificationChannel channel, String subject, String body,
                                          NotificationPriority priority, Map<String, String> metadata, String serviceUrl) {

        Notification notification = new Notification(recipient, channel, subject, body, priority, metadata, serviceUrl);

        return notificationRepository.save(notification);
    }

    private boolean sameRequest(Notification original, String recipient, NotificationChannel channel, String subject,
                                String body, NotificationPriority priority, Map<String, String> metadata, String serviceUrl) {

        return Objects.equals(original.getRecipient(), recipient)
                && original.getChannel() == channel
                && Objects.equals(original.getSubject(), subject)
                && Objects.equals(original.getBody(), body)
                && original.getPriority() == priority
                && Objects.equals(original.getMetadata(), metadata)
                && Objects.equals(original.getServiceUrl(), serviceUrl);
    }

}
