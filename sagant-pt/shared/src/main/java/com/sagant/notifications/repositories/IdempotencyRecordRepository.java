package com.sagant.notifications.repositories;

import com.sagant.notifications.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Para evitar problemas de concurrencia, reservaremos la clave mediante PostgreSQL y
     * dejamos que la base de datos resuelva qué petición consigue registrarla.
     *
     * @return 1 si reservó la clave y 0 si ya la calve estaba reservada
     * */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (idempotency_key)
            VALUES (:key)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int reserveKey(@Param("key") String key);
}
