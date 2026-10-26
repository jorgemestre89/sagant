package com.sagant.notifications.dtos.request;

import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Objects;

@Data
@NoArgsConstructor
public class NotificationRequestDTO {
    @NotBlank(message = "El destinatario es obligatorio")
    @Email(message = "El destinatario debe ser un email válido")
    String recipient;

    NotificationChannel channel = NotificationChannel.LOG;

    @NotBlank(message = "El asunto es obligatorio")
    String subject;

    @NotBlank(message = "El cuerpo es obligatorio")
    String body;

    private NotificationPriority priority = NotificationPriority.LOW;

    private Map<String, String> metadata = Map.of();

    public NotificationRequestDTO(String recipient, NotificationChannel channel, String subject, String body,
                                  NotificationPriority priority, Map<String, String> metadata) {

        this.recipient = recipient;
        this.channel = Objects.nonNull(channel) ? channel : NotificationChannel.LOG;
        this.subject = subject;
        this.body = body;
        this.priority = Objects.nonNull(priority) ? priority : NotificationPriority.LOW;
        this.metadata = Objects.nonNull(metadata) ? Map.copyOf(metadata) : Map.of();
    }
}
