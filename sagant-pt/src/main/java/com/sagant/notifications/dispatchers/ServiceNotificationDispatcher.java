package com.sagant.notifications.dispatchers;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import com.sagant.notifications.dtos.request.ServiceNotificationRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ServiceNotificationDispatcher implements NotificationDispatcher {

    private final RestClient restClient;
    private final String serviceUrl;

    public ServiceNotificationDispatcher(RestClient.Builder restClientBuilder,
                                         @Value("${notifications.dispatch.service-url}") String serviceUrl) {

        this.restClient = restClientBuilder.build();
        this.serviceUrl = serviceUrl;
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

        /*
        * enviamos el dto con los datos de la notificación
        * y esperamos una respuesta exitosa sin necesidad de leer su cuerpo.
        * */
        restClient.post()
                .uri(serviceUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
