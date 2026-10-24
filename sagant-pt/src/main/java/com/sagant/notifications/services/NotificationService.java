package com.sagant.notifications.services;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification createNotification(String recipient, NotificationChannel channel, String subject,  String body,
                                           NotificationPriority priority, Map<String, String> metadata) {

        Notification notification = new Notification(recipient, channel, subject, body, priority, metadata);

        return notificationRepository.save(notification);
    }
}
