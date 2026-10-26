package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link NotificationProcessingService}.
 * <p>
 * Verifica el procesamiento de las notificaciones pendientes,
 * incluyendo el despacho, la actualización de estados y el
 * comportamiento ante errores durante el envío.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationProcessingServiceTest {

    @Mock
    private NotificationQueueService notificationQueueService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationProcessingService notificationProcessingService;

    @BeforeEach
    void setUp() {
        notificationProcessingService = new NotificationProcessingService(
                notificationQueueService,
                notificationDispatchService,
                notificationRepository,
                3,
                Duration.ofSeconds(10),
                Runnable::run
        );
    }

    /*
     * Verifica que no se intente despachar ni persistir ninguna notificación
     * cuando la cola no contiene notificaciones pendientes.
     */
    @Test
    void shouldDoNothingWhenThereAreNoPendingNotifications() {
        // Arrange
        when(notificationQueueService.claimPendingNotifications(10))
                .thenReturn(List.of());

        // Act
        notificationProcessingService.processPendingNotifications(10);

        // Assert
        verify(notificationQueueService).claimPendingNotifications(10);
        verifyNoInteractions(notificationDispatchService, notificationRepository);
    }

    /*
     * Verifica que una notificación cuyo despacho finaliza correctamente
     * se marque como enviada y se persista en el repositorio.
     */
    @Test
    void shouldDispatchAndMarkNotificationAsSent() {
        // Arrange
        Notification notification = mock(Notification.class);

        when(notificationQueueService.claimPendingNotifications(10))
                .thenReturn(List.of(notification));

        // Act
        notificationProcessingService.processPendingNotifications(10);

        // Assert
        verify(notificationDispatchService).dispatch(notification);
        verify(notification).markAsSent();
        verify(notificationRepository).save(notification);
        verify(notification, never()).markAsFailed(anyString());
    }

    /**
     * Verifica que un error de envío programe un reintento
     * cuando todavía quedan intentos disponibles.
     */
    @Test
    void shouldScheduleRetryWhenDispatchThrowsException() {
        // Arrange
        Notification notification = mock(Notification.class);

        when(notificationQueueService.claimPendingNotifications(10))
                .thenReturn(List.of(notification));
        when(notification.getAttemptCount()).thenReturn(1);

        doThrow(new RuntimeException("Error de envío"))
                .when(notificationDispatchService)
                .dispatch(notification);

        // Act
        notificationProcessingService.processPendingNotifications(10);

        // Assert
        verify(notificationDispatchService).dispatch(notification);
        verify(notification).scheduleRetry(
                any(Instant.class),
                eq("Error de envío")
        );
        verify(notificationRepository).save(notification);
        verify(notification, never()).markAsFailed(anyString());
        verify(notification, never()).markAsSent();
    }


    /**
     * Verifica que un error al despachar una notificación no interrumpa
     * el procesamiento de las siguientes notificaciones del mismo lote.
     */
    @Test
    void shouldContinueProcessingWhenOneNotificationFails() {
        // Arrange
        Notification failedNotification = mock(Notification.class);
        Notification successfulNotification = mock(Notification.class);

        when(notificationQueueService.claimPendingNotifications(10))
                .thenReturn(List.of(
                        failedNotification,
                        successfulNotification
                ));
        when(failedNotification.getAttemptCount()).thenReturn(1);

        doThrow(new RuntimeException("Error de envío"))
                .when(notificationDispatchService)
                .dispatch(failedNotification);

        // Act
        notificationProcessingService.processPendingNotifications(10);

        // Assert
        verify(notificationDispatchService).dispatch(failedNotification);
        verify(notificationDispatchService).dispatch(successfulNotification);

        verify(failedNotification).scheduleRetry(
                any(Instant.class),
                eq("Error de envío")
        );
        verify(failedNotification, never()).markAsFailed(anyString());
        verify(failedNotification, never()).markAsSent();

        verify(successfulNotification).markAsSent();
        verify(successfulNotification, never()).markAsFailed(anyString());

        verify(notificationRepository).save(failedNotification);
        verify(notificationRepository).save(successfulNotification);
    }


    /*
     * Verifica que un tamaño de lote igual a cero provoque
     * {@link IllegalArgumentException} y que no se intente
     * despachar ni persistir ninguna notificación.
     */
    @Test
    void shouldRejectInvalidBatchSize() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> notificationProcessingService.processPendingNotifications(0)
        );

        verifyNoInteractions(
                notificationDispatchService,
                notificationRepository
        );
    }


    /**
     * Verifica que la notificación quede definitivamente fallida
     * cuando se agota el máximo de intentos de envío.
     */
    @Test
    void shouldMarkNotificationAsFailedWhenMaxAttemptsReached() {
        // Arrange
        Notification notification = mock(Notification.class);

        when(notificationQueueService.claimPendingNotifications(10))
                .thenReturn(List.of(notification));
        when(notification.getAttemptCount()).thenReturn(3);

        doThrow(new RuntimeException("Error de envío"))
                .when(notificationDispatchService)
                .dispatch(notification);

        // Act
        notificationProcessingService.processPendingNotifications(10);

        // Assert
        verify(notificationDispatchService).dispatch(notification);
        verify(notification).markAsFailed("Error de envío");
        verify(notification, never()).scheduleRetry(
                any(Instant.class),
                anyString()
        );
        verify(notification, never()).markAsSent();
        verify(notificationRepository).save(notification);
    }

    /*
     * Verifica que dos notificaciones puedan despacharse concurrentemente
     * utilizando un executor con varios hilos.
     *
     * La prueba bloquea ambos despachos hasta que los dos hayan comenzado,
     * demostrando que el procesamiento ocurre en paralelo y no de forma
     * secuencial. Luego permite que finalicen y verifica que ambas
     * notificaciones se marquen como enviadas y se persistan.
     *
     *
     * @throws Exception si ocurre un error o un timeout durante la ejecución
     *                   concurrente de la prueba
     */
    @Test
    void shouldProcessNotificationsConcurrently() throws Exception {
        // Arrange
        Notification first = mock(Notification.class);
        Notification second = mock(Notification.class);

        when(notificationQueueService.claimPendingNotifications(2))
                .thenReturn(List.of(first, second));

        CountDownLatch dispatchStarted = new CountDownLatch(2);
        CountDownLatch allowDispatchToFinish = new CountDownLatch(1);

        doAnswer(invocation -> {
            dispatchStarted.countDown();

            boolean released = allowDispatchToFinish.await(
                    5, TimeUnit.SECONDS
            );

            if (!released) {
                throw new RuntimeException(
                        "Timeout esperando la liberación del despacho"
                );
            }

            return null;
        }).when(notificationDispatchService).dispatch(any(Notification.class));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        ExecutorService coordinator = Executors.newSingleThreadExecutor();

        NotificationProcessingService concurrentService =
                new NotificationProcessingService(
                        notificationQueueService,
                        notificationDispatchService,
                        notificationRepository,
                        3,
                        Duration.ofSeconds(10),
                        workers
                );

        try {
            // Act: ejecutar el lote en un hilo distinto al de los workers.
            Future<?> processing = coordinator.submit(
                    () -> concurrentService.processPendingNotifications(2)
            );

            // Assert: ambos despachos deben comenzar antes de liberar cualquiera.
            assertTrue(
                    dispatchStarted.await(5, TimeUnit.SECONDS),
                    "Las dos notificaciones deberían procesarse en paralelo"
            );

            // Permitir que los dos despachos finalicen.
            allowDispatchToFinish.countDown();

            // Verificar que el procesamiento del lote termina.
            processing.get(5, TimeUnit.SECONDS);

            verify(notificationDispatchService).dispatch(first);
            verify(notificationDispatchService).dispatch(second);

            verify(first).markAsSent();
            verify(second).markAsSent();

            verify(notificationRepository).save(first);
            verify(notificationRepository).save(second);
        } finally {
            // Evitar que los hilos queden bloqueados si falla una aserción.
            allowDispatchToFinish.countDown();
            workers.shutdownNow();
            coordinator.shutdownNow();
        }
    }

    /**
     * Verifica que una notificación reservada vuelva a estado pendiente
     * si el executor rechaza la tarea, y que el cambio se persista
     * para permitir un nuevo intento de procesamiento.
     */
    @Test
    void shouldRecoverNotificationWhenExecutorRejectsTask() {
        // Arrange
        Notification notification = mock(Notification.class);

        when(notificationQueueService.claimPendingNotifications(1))
                .thenReturn(List.of(notification));

        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("Executor no disponible");
        };

        NotificationProcessingService service = new NotificationProcessingService(
                notificationQueueService,
                notificationDispatchService,
                notificationRepository,
                3,
                Duration.ofSeconds(10),
                rejectingExecutor
        );

        // Act
        service.processPendingNotifications(1);

        // Assert
        verify(notification).recoverFromProcessing();
        verify(notificationRepository).save(notification);
        verifyNoInteractions(notificationDispatchService);
    }
}