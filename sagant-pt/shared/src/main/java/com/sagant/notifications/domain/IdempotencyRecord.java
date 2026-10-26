package com.sagant.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", unique = true)
    private Notification notification;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyRecord(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public void associateNotification(Notification notification) {
        this.notification = notification;
    }
}
