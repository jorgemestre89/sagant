package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.repositories.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Servicio responsable de recuperar las notificaciones que permanecen en
 * estado {@code PROCESSING} durante más tiempo que el límite configurado.
 *
 * <p>Las notificaciones abandonadas vuelven al estado {@code PENDING} para
 * que puedan procesarse nuevamente. La recuperación se realiza dentro de
 * una transacción y utiliza bloqueos de filas con {@code SKIP LOCKED} para
 * evitar interferir con registros bloqueados por otros procesos.</p>
 *
 * <p>El tiempo máximo de procesamiento se configura mediante la propiedad
 * {@code notifications.processing.timeout}, cuyo valor predeterminado es
 * de cinco minutos.</p>
 *
 * <p>La recuperación puede provocar un envío duplicado si el proceso
 * anterior llegó a contactar al servicio externo, pero falló antes de
 * guardar el estado final de la notificación. Si no se admiten duplicados,
 * debe considerarse la idempotencia en el servicio de destino.</p>
 */
@Service
public class NotificationRecoveryService {
    private final NotificationRepository notificationRepository;
    private final Duration processingTimeout;

    public NotificationRecoveryService(NotificationRepository notificationRepository,
                                       @Value("${notifications.processing.timeout:PT5M}") Duration processingTimeout) {
        this.notificationRepository = notificationRepository;
        this.processingTimeout = processingTimeout;
    }

    /**
     * Recupera un lote de notificaciones cuyo tiempo máximo de procesamiento
     * ya venció.
     *
     * <p>Cada notificación seleccionada vuelve al estado {@code PENDING} y
     * los cambios se guardan dentro de la transacción actual.</p>
     *
     * @param batchSize cantidad máxima de notificaciones a recuperar;
     *                  debe ser mayor que cero
     *
     * @return cantidad de notificaciones recuperadas
     *
     * @throws IllegalArgumentException si {@code batchSize} es menor o
     *                                  igual a cero
     */
    @Transactional
    public int recoverAbandonedNotifications(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be greater than zero");
        }

        Instant cutoff = Instant.now().minus(processingTimeout);

        List<Notification> abandoned = notificationRepository.lockAbandonedProcessing(cutoff, batchSize);

        if (abandoned.isEmpty()) {
            return 0;
        }

        abandoned.forEach(Notification::recoverFromProcessing);

        notificationRepository.saveAll(abandoned);

        return abandoned.size();
    }
}
