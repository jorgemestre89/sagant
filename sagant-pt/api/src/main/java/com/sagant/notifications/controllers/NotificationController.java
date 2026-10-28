package com.sagant.notifications.controllers;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.dtos.request.NotificationRequestDTO;
import com.sagant.notifications.dtos.response.NotificationResponseDTO;
import com.sagant.notifications.mapper.MapperUtils;
import com.sagant.notifications.services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.net.URI;
import java.util.Objects;

@Tag(
        name = "Notifications",
        description = "Operaciones para crear y gestionar notificaciones"
)
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(
            summary = "Crear una notificación",
            description = """
                    Registra una notificación para su procesamiento asíncrono.
                    Requiere un token JWT válido y el encabezado Idempotency-Key.
                    Si se repite la misma clave con la misma solicitud, se devuelve
                    la notificación original. Si se reutiliza con datos diferentes,
                    la API responde con un conflicto.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Notificación creada o notificación original devuelta por idempotencia"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Solicitud inválida, encabezado obligatorio ausente o datos que no superan las validaciones"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Token JWT ausente o inválido"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "La clave de idempotencia ya fue utilizada con una solicitud diferente"
            )
    })
    @PostMapping
    public ResponseEntity<NotificationResponseDTO> createNotification(
            @Parameter(
                    name = "Idempotency-Key",
                    description = "Clave única para evitar crear notificaciones duplicadas ante reintentos de la solicitud",
                    required = true,
                    in = ParameterIn.HEADER
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody NotificationRequestDTO request) {

        //mandamos a crear la notificación a partir de los datos recibidos del request
        Notification notification = notificationService.createNotification(
                idempotencyKey,
                request.getRecipient(),
                request.getChannel(),
                request.getSubject(),
                request.getBody(),
                request.getPriority(),
                Objects.isNull(request.getMetadata()) ? java.util.Map.of() : request.getMetadata(),
                request.getServiceUrl()
        );

        //mapeamos de Notificaction a NotificationResponseRecord
        NotificationResponseDTO response = MapperUtils.mapObject(notification, NotificationResponseDTO.class);

        //creamos la cabecera de la respuesta con la uri+idNotificacion creada
        URI location = URI.create(
                "/api/notifications/" + notification.getIdNotification()
        );

        //retornamos HTTP 201 Created, con la cabecera Location y el DTO de respuesta
        return ResponseEntity.created(location).body(response);
    }
}
