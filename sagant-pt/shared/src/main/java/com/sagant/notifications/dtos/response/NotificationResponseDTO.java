package com.sagant.notifications.dtos.response;

import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.domain.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponseDTO {
    private Long idNotification;
    private String recipient;
    NotificationChannel channel;
    String subject;
    private String body;
    private NotificationStatus status;
    NotificationPriority priority;
    Map<String, String> metadata;
    private Instant createdAt;
}
