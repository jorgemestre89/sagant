package com.sagant.notifications.dtos.request;

import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.Objects;

@Schema(description = "Datos necesarios para registrar una notificación")
@Data
@NoArgsConstructor
public class NotificationRequestDTO {

    @Schema(
            description = "Email del destinatario",
            example = "usuario@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "El destinatario es obligatorio")
    @Email(message = "El destinatario debe ser un email válido")
    String recipient;

    @Schema(
            description = "Canal por el que se despachará la notificación",
            example = "LOG",
            defaultValue = "LOG",
            nullable = true
    )
    NotificationChannel channel = NotificationChannel.LOG;

    @Schema(
            description = "Asunto de la notificación",
            example = "Confirmación de operación",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "El asunto es obligatorio")
    String subject;

    @Schema(
            description = "Contenido de la notificación",
            example = "La operación se completó correctamente",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "El cuerpo es obligatorio")
    String body;

    @Schema(
            description = "Prioridad de procesamiento",
            example = "LOW",
            defaultValue = "LOW",
            nullable = true
    )
    private NotificationPriority priority = NotificationPriority.LOW;

    @Schema(
            description = "Metadatos adicionales de la notificación",
            example = "{\"origen\":\"sistema-operaciones\"}"
    )
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
