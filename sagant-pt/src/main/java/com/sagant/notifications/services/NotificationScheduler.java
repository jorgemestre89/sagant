package com.sagant.notifications.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Ejecuta periódicamente el procesamiento y la recuperación
 * de notificaciones.
 */
@Component
//hace que el scheduler se registre cuando la propiedad vale true y también cuando no está definida.
@ConditionalOnProperty(
        name = "notifications.processing.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationScheduler {

    private static final int BATCH_SIZE = 50;

    private final NotificationProcessingService notificationProcessingService;
    private final NotificationRecoveryService notificationRecoveryService;

    public NotificationScheduler(NotificationProcessingService notificationProcessingService,
                                 NotificationRecoveryService notificationRecoveryService) {
        this.notificationProcessingService = notificationProcessingService;
        this.notificationRecoveryService = notificationRecoveryService;
    }

    /**
     * Procesa periódicamente las notificaciones pendientes.
     */
    @Scheduled(fixedDelayString = "${notifications.processing.interval:PT2S}")
    public void processPendingNotifications() {
        notificationProcessingService.processPendingNotifications(BATCH_SIZE);
    }

    /**
     * Recupera periódicamente las notificaciones cuyo procesamiento
     * quedó abandonado.
     */
    @Scheduled(fixedDelayString = "${notifications.processing.recovery-interval:PT1M}")
    public void recoverAbandonedNotifications() {
        notificationRecoveryService.recoverAbandonedNotifications(BATCH_SIZE);
    }
}
