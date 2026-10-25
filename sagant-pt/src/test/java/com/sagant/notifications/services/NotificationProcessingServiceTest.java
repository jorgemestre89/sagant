package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
                Duration.ofSeconds(10)
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

}