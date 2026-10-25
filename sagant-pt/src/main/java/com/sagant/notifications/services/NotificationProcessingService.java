package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.repositories.NotificationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;

import java.util.List;
import java.util.Objects;

/**
 *  Coordina el procesamiento de las notificaciones pendientes,
 *  desde su reserva hasta la actualización de su estado.
 *  Delega la obtención y reserva de notificaciones a
 *  {@link NotificationQueueService} y el envío a
 *  {@link NotificationDispatchService}. Si un envío falla,
 *  gestiona los reintentos y persiste el resultado de cada intento.
 *  */
@Service
public class NotificationProcessingService {
    private final NotificationQueueService notificationQueueService;
    private final NotificationDispatchService notificationDispatchService;
    private final NotificationRepository notificationRepository;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Executor notificationExecutor;

    public NotificationProcessingService(NotificationQueueService notificationQueueService,
                                         NotificationDispatchService notificationDispatchService,
                                         NotificationRepository notificationRepository,
                                         @Value("${notifications.processing.max-attempts:3}") int maxAttempts,
                                         @Value("${notifications.processing.retry-delay:PT10S}") Duration retryDelay,
                                         @Qualifier("notificationExecutor") Executor notificationExecutor) {

        this.notificationQueueService = notificationQueueService;
        this.notificationDispatchService = notificationDispatchService;
        this.notificationRepository = notificationRepository;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
        this.notificationExecutor = notificationExecutor;
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

        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (Notification notification : notifications) {
            try {
                CompletableFuture<Void> task = CompletableFuture.runAsync(
                        () -> processNotification(notification),
                        notificationExecutor
                );

                tasks.add(task);
            } catch (RejectedExecutionException exception) {
                // El executor no aceptó la tarea: liberar la reserva.
                notification.recoverFromProcessing();
                notificationRepository.save(notification);
            }
        }

        //esperamos a que terminen las tareas aceptadas antes de retornar
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture<?>[0])).join();
    }

    /**
     *  Envía una notificación y persiste el resultado del procesamiento.
     *  <p>
     *  Si el envío finaliza correctamente, la notificación se marca como
     *  {@code SENT}. Si el envío falla, se registra el error y se programa
     *  un reintento mientras no se haya alcanzado el máximo de intentos
     *  configurado.
     *  El tiempo de espera entre reintentos aumenta
     *  exponencialmente a partir del retraso base configurado.
     *  Cuando se alcanza el máximo de intentos, la notificación se marca
     *  como {@code FAILED}.
     *  En todos los casos, se persiste el estado  actualizado de la notificación.
     *  </p>
     *
     *  @param notification notificación previamente reservada para su procesamiento
     */
    private void processNotification(Notification notification) {
        try {
            notificationDispatchService.dispatch(notification);

            notification.markAsSent();
            notificationRepository.save(notification);

        } catch (RuntimeException exception) {
            String error = exception.getMessage();

            if (Objects.isNull(error) || error.isBlank()) {
                error = exception.getClass().getSimpleName();
            }

            if (notification.getAttemptCount() < maxAttempts) {
                long delaySeconds = retryDelay.toSeconds()
                        * (1L << (notification.getAttemptCount() - 1));

                Instant retryAt = Instant.now().plusSeconds(delaySeconds);

                notification.scheduleRetry(retryAt, error);
            } else {
                notification.markAsFailed(error);
            }

            notificationRepository.save(notification);
        }
    }
}
