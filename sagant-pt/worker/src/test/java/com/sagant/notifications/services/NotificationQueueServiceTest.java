package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.domain.enums.NotificationStatus;
import com.sagant.notifications.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationQueueServiceTest {
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationQueueService notificationQueueService;

    /*
     * Verifica que el servicio rechace tamaños de lote inválidos.
     *
     * Un lote debe tener al menos una notificación. Si el tamaño es cero
     * o negativo, se espera una IllegalArgumentException y se comprueba
     * que el repositorio no haya sido invocado.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectInvalidBatchSize(int batchSize) {
        assertThrows(
                IllegalArgumentException.class,
                () -> notificationQueueService.claimPendingNotifications(batchSize)
        );

        verifyNoInteractions(notificationRepository);
    }

    /*
     * Verifica el comportamiento cuando no existen notificaciones
     * pendientes disponibles para reservar.
     *
     * El servicio debe devolver una lista vacía sin producir errores.
     * También se verifica que consulte el repositorio y que invoque
     * saveAll con la lista vacía, de acuerdo con la implementación actual.
     */
    @Test
    void shouldReturnEmptyListWhenNoPendingNotificationsExist() {
        when(notificationRepository.lockPendingNotifications(10))
                .thenReturn(List.of());

        when(notificationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> result = notificationQueueService.claimPendingNotifications(10);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(notificationRepository).lockPendingNotifications(10);
        verify(notificationRepository).saveAll(anyList());
    }

    /*
     * Verifica que las notificaciones reservadas cambien al estado
     * PROCESSING y registren la fecha de inicio del procesamiento.
     *
     * También comprueba que el servicio devuelva la notificación
     * reservada y que persista los cambios mediante saveAll.
     */
    @Test
    void shouldMarkNotificationsAsProcessingAndSaveThem() {
        Notification notification = new Notification(
                "destinatario@test.com",
                NotificationChannel.EMAIL,
                "Asunto",
                "Contenido",
                NotificationPriority.HIGH,
                Map.of(),
                null
        );

        when(notificationRepository.lockPendingNotifications(10))
                .thenReturn(List.of(notification));

        when(notificationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> result = notificationQueueService.claimPendingNotifications(10);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.PROCESSING, result.get(0).getStatus());
        assertNotNull(result.get(0).getProcessingStartedAt());

        verify(notificationRepository).lockPendingNotifications(10);
        verify(notificationRepository).saveAll(anyList());
    }
}
