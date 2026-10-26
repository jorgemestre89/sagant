package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


/**
 * Pruebas unitarias del servicio de recuperación de notificaciones.
 *
 * <p>Verifica que las notificaciones abandonadas puedan recuperarse,
 * que se respete el tiempo máximo de procesamiento configurado y que
 * el servicio gestione correctamente los lotes vacíos y los tamaños
 * de lote inválidos.</p>
 *
 * <p>Se utiliza Mockito para simular el repositorio y comprobar las
 * interacciones del servicio sin acceder a una base de datos real.</p>
 */
@ExtendWith(MockitoExtension.class)
public class NotificationRecoveryServiceTest {
    @Mock
    private NotificationRepository notificationRepository;

    private NotificationRecoveryService notificationRecoveryService;

    @BeforeEach
    void setUp() {
        notificationRecoveryService = new NotificationRecoveryService(notificationRepository, Duration.ofMinutes(5));
    }

    /*
     * Verifica que las notificaciones abandonadas recibidas del repositorio
     * se recuperen, se persistan y se contabilicen correctamente.
     */
    @Test
    void shouldRecoverAbandonedNotifications() {
        Notification notification = mock(Notification.class);

        when(notificationRepository.lockAbandonedProcessing(any(Instant.class), eq(10)))
                .thenReturn(List.of(notification));

        int recovered = notificationRecoveryService.recoverAbandonedNotifications(10);

        assertEquals(1, recovered);

        verify(notificationRepository).lockAbandonedProcessing(any(Instant.class), eq(10));

        verify(notification).recoverFromProcessing();

        verify(notificationRepository).saveAll(List.of(notification));
    }

    /*
     * Verifica que el servicio calcule el instante límite utilizando
     * el tiempo máximo de procesamiento configurado.
     */
    @Test
    void shouldUseConfiguredProcessingTimeout() {
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        when(notificationRepository.lockAbandonedProcessing(any(Instant.class), eq(10)))
                .thenReturn(List.of());

        Instant beforeExecution = Instant.now();

        notificationRecoveryService.recoverAbandonedNotifications(10);

        Instant afterExecution = Instant.now();

        verify(notificationRepository).lockAbandonedProcessing(cutoffCaptor.capture(), eq(10));

        Instant cutoff = cutoffCaptor.getValue();

        assertEquals(
                beforeExecution.minus(Duration.ofMinutes(5)).getEpochSecond(),
                cutoff.getEpochSecond()
        );

        assertEquals(
                afterExecution.minus(Duration.ofMinutes(5)).getEpochSecond(),
                cutoff.getEpochSecond()
        );
    }

    /*
     * Verifica que el servicio devuelva cero y no intente guardar registros
     * cuando el repositorio no encuentra notificaciones abandonadas.
     */
    @Test
    void shouldReturnZeroWhenThereAreNoAbandonedNotifications() {
        when(notificationRepository.lockAbandonedProcessing(any(Instant.class), eq(10)))
                .thenReturn(List.of());

        int recovered = notificationRecoveryService.recoverAbandonedNotifications(10);

        assertEquals(0, recovered);

        verify(notificationRepository, never()).saveAll(anyList());
    }

    /*
     * Verifica que el servicio rechace los tamaños de lote iguales a cero
     * o negativos y que no interactúe con el repositorio en esos casos.
     */
    @Test
    void shouldRejectInvalidBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> notificationRecoveryService
                        .recoverAbandonedNotifications(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationRecoveryService
                        .recoverAbandonedNotifications(-1)
        );

        verifyNoInteractions(notificationRepository);
    }
}
