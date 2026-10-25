package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.repositories.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Coordina el procesamiento de las notificaciones pendientes,
 * desde su reserva hasta la actualización de su estado final.
 * <p>
 * Delega la obtención y reserva de notificaciones a
 * {@link NotificationQueueService} y el envío al
 * {@link NotificationDispatchService}.
 * </p>
 */
@Service
public class NotificationProcessingService {
    private final NotificationQueueService notificationQueueService;
    private final NotificationDispatchService notificationDispatchService;
    private final NotificationRepository notificationRepository;

    public NotificationProcessingService(NotificationQueueService notificationQueueService,
                                         NotificationDispatchService notificationDispatchService,
                                         NotificationRepository notificationRepository) {

        this.notificationQueueService = notificationQueueService;
        this.notificationDispatchService = notificationDispatchService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Obtiene y procesa un lote de notificaciones pendientes.
     * <p>
     * Las notificaciones disponibles se reservan mediante
     * {@link NotificationQueueService#claimPendingNotifications(int)}
     * y se procesan individualmente. Un fallo durante el envío de una
     * notificación no impide continuar con las restantes del lote.
     * </p>
     *
     * @param batchSize cantidad máxima de notificaciones que se reservarán;
     *                  debe ser mayor que cero
     * @throws IllegalArgumentException si {@code batchSize} es menor o igual a cero
     */
    public void processPendingNotifications(int batchSize) {

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "El tamaño del lote debe ser mayor que cero."
            );
        }

        List<Notification> notifications = notificationQueueService.claimPendingNotifications(batchSize);

        for (Notification notification : notifications) {
            processNotification(notification);
        }
    }

    /**
     * Envía una notificación y persiste el resultado del procesamiento.
     * <p>
     * Si el envío finaliza correctamente, la notificación se marca como
     * {@code SENT}. Si el dispatcher lanza una {@link RuntimeException},
     * se marca como {@code FAILED} y se registra el mensaje del error.
     * </p>
     *
     * @param notification notificación previamente reservada para su procesamiento
     */
    private void processNotification(Notification notification) {

        try {
            notificationDispatchService.dispatch(notification);
        } catch (RuntimeException exception) {
            String error = exception.getMessage();

            if (Objects.isNull(error) || error.isBlank()) {
                error = exception.getClass().getSimpleName();
            }

            notification.markAsFailed(error);
            notificationRepository.save(notification);
            return;
        }

        notification.markAsSent();
        notificationRepository.save(notification);
    }
}
