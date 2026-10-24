package com.sagant.notifications.repositories;

import com.sagant.notifications.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
