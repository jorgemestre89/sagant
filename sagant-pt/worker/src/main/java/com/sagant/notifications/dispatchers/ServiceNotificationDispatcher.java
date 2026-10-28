package com.sagant.notifications.dispatchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.dtos.request.ServiceNotificationRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Component
public class ServiceNotificationDispatcher implements NotificationDispatcher {

    private final RestClient restClient;
    //private final String serviceUrl;
    private final ObjectMapper objectMapper;

    public ServiceNotificationDispatcher(RestClient.Builder restClientBuilder,/*
                                         @Value("${notifications.dispatch.service-url}") String serviceUrl,*/
                                         ObjectMapper objectMapper) {

        this.restClient = restClientBuilder.build();
        //this.serviceUrl = serviceUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SERVICE;
    }

    @Override
    public void dispatch(Notification notification) {
        //transformamos la entidad en el DTO de salida
        ServiceNotificationRequestDTO request = new ServiceNotificationRequestDTO(
                        notification.getRecipient(),
                        notification.getSubject(),
                        notification.getBody()
        );

        //leemos a url a enviar la notificación
        String serviceUrlByNotification = notification.getServiceUrl();

        if (Objects.isNull(serviceUrlByNotification) || serviceUrlByNotification.isBlank()) {
            throw new IllegalStateException(
                    "La notificación SERVICE no tiene una URL de destino"
            );
        }

        /*
        * enviamos el dto con los datos de la notificación
        * y esperamos una respuesta exitosa sin necesidad de leer su cuerpo.
        * */
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(request);

            restClient.post()
                    .uri(serviceUrlByNotification)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.length)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo serializar la notificación para el servicio externo", e);
        }
    }

}
