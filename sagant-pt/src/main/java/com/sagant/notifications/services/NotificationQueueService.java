package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationQueueService {
    private final NotificationRepository notificationRepository;

    public NotificationQueueService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Reserva un lote de notificaciones pendientes para su procesamiento asíncrono.
     * <p>
     * Recupera las notificaciones cuyo estado es {@code PENDING} y cuyo próximo
     * intento de despacho ya está habilitado, respetando el tamaño máximo del lote
     * y el orden de prioridad definido en el repositorio.
     * Las notificaciones seleccionadas se marcan como {@code PROCESSING} y se
     * guardan dentro de la misma transacción para evitar que otros workers las
     * reclamen simultáneamente.
     *
     * @param batchSize cantidad máxima de notificaciones que se desean reservar;
     *                  debe ser mayor que cero
     *
     * @return lista de notificaciones reservadas para su procesamiento; puede estar
     *         vacía si no hay notificaciones disponibles
     *
     * @throws IllegalArgumentException si {@code batchSize} es menor o igual a cero
     */
    @Transactional
    public List<Notification> claimPendingNotifications(int batchSize) {

        if (batchSize <= 0) {
            throw new IllegalArgumentException("El tamaño del lote debe ser mayor que cero.");
        }

        List<Notification> notifications = notificationRepository.lockPendingNotifications(batchSize);

        notifications.forEach(Notification::markAsProcessing);

        return notificationRepository.saveAll(notifications);
    }
}
