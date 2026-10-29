package com.sagant.notifications.dtos.response;

import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.domain.enums.NotificationPriority;
import com.sagant.notifications.domain.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Representación de una notificación registrada")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponseDTO {

    @Schema(description = "Identificador de la notificación", example = "123")
    private Long idNotification;

    @Schema(description = "Email del destinatario", example = "usuario@example.com")
    private String recipient;

    @Schema(description = "Canal de despacho", example = "LOG")
    NotificationChannel channel;

    @Schema(description = "Asunto", example = "Confirmación de operación")
    String subject;

    @Schema(description = "Contenido", example = "La operación se completó correctamente")
    private String body;

    @Schema(description = "Estado de procesamiento", example = "PENDING")
    private NotificationStatus status;

    @Schema(
            description = "Prioridad asignada a la notificación",
            example = "LOW",
            allowableValues = {"LOW", "MEDIUM", "HIGH"}
    )
    NotificationPriority priority;

    @Schema(description = "Metadatos adicionales")
    Map<String, String> metadata;

    @Schema(description = "Fecha de creación en formato ISO-8601",
            example = "2026-10-27T15:30:00Z")
    private Instant createdAt;

    @Schema(
            description = "URL HTTP de destino, obligatoria para el canal SERVICE",
            example = "http://localhost:8090/notifications"
    )
    private String serviceUrl;
}
