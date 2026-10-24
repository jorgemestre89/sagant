package com.sagant.notifications.controllers;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.dtos.request.NotificationRequestDTO;
import com.sagant.notifications.dtos.response.NotificationResponseDTO;
import com.sagant.notifications.mapper.MapperUtils;
import com.sagant.notifications.services.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponseDTO> createNotification(
            @Valid @RequestBody NotificationRequestDTO request) {

        //mandamos a crear la notificación a partir de los datos recibidos del request
        Notification notification = notificationService.createNotification(
                request.getRecipient(),
                request.getChannel(),
                request.getSubject(),
                request.getBody(),
                request.getPriority(),
                Objects.isNull(request.getMetadata()) ? java.util.Map.of() : request.getMetadata()
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
