package com.sagant.notifications.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceNotificationRequestDTO {
    private String recipient;
    private String subject;
    private String body;
}
