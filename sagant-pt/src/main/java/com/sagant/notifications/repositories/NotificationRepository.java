package com.sagant.notifications.repositories;

import com.sagant.notifications.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    /*
    * FOR UPDATE bloquea las filas seleccionadas durante la transacción.
    * SKIP LOCKED permite que otro worker omita las filas que ya están siendo reservadas.
    * */
    @Query(value = """
        SELECT * FROM notifications
        WHERE status = 'PENDING'
          AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
        ORDER BY
          CASE priority
            WHEN 'HIGH' THEN 1
            WHEN 'MEDIUM' THEN 2
            WHEN 'LOW' THEN 3
          END,
          created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Notification> lockPendingNotifications(@Param("batchSize") int batchSize);

    /*
    * Esta consulta selecciona únicamente las notificaciones cuyo procesamiento comenzó antes del límite temporal.
    * SKIP LOCKED permite que otros workers omitan las filas que ya están bloqueadas por otra transacción.
    * */
    @Query(value = """
        SELECT * FROM notifications
        WHERE status = 'PROCESSING'
          AND processing_started_at <= :cutoff
        ORDER BY processing_started_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Notification> lockAbandonedProcessing(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
