package com.sagant.notifications.dispatchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class LogNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationDispatcher.class);

    private final ObjectMapper objectMapper;

    public LogNotificationDispatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.LOG;
    }

    @Override
    public void dispatch(Notification notification) {
        /*
        * No registramos el body completo de la notificación,
        * para evitar exponer contenido potencialmente sensible.
        * */
        Map<String, Object> event = Map.of(
                "event", "notification.dispatched",
                "notificationId", notification.getIdNotification(),
                "channel", notification.getChannel().name(),
                "recipient", notification.getRecipient(),
                "priority", notification.getPriority().name(),
                "createdAt", notification.getCreatedAt().toString(),
                "dispatchedAt", Instant.now().toString()
        );

        try {
            /*
            * que el evento se escriba en el log significa que el despacho por ese canal se completó,
            * pero no implica que otro sistema haya recibido la notificación
            * */
            String json = objectMapper.writeValueAsString(event);
            log.info("{}", json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No fue posible serializar el evento de notificación", e
            );
        }
    }
}
