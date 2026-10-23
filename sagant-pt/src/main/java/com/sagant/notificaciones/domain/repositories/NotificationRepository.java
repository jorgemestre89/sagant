package com.sagant.notificaciones.domain.repositories;

import com.sagant.notificaciones.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
