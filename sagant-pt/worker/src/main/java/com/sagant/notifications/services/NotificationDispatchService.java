package com.sagant.notifications.services;

import com.sagant.notifications.dispatchers.NotificationDispatcher;
import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private final Map<NotificationChannel, NotificationDispatcher> dispatchers;

    public NotificationDispatchService(List<NotificationDispatcher> dispatcherList) {

        //creamos un mapa con las claves de los canales
        this.dispatchers = new EnumMap<>(NotificationChannel.class);

        /*
        * luego definimos las estrategias para cada canal,
        * es decir el dispatcher implementado para cada caso
        * */
        for (NotificationDispatcher dispatcher : dispatcherList) {
            NotificationDispatcher previous = this.dispatchers.put(
                    dispatcher.getChannel(), dispatcher
            );

            if (Objects.nonNull(previous)) {
                throw new IllegalStateException(
                        "Existe más de un dispatcher para el canal "
                                + dispatcher.getChannel()
                );
            }
        }
    }

    /**
     * responsable de despachar la notificación de acuerdo
     * al canal que tenga definido
     * */
    public void dispatch(Notification notification) {
        Long notificationId = notification.getIdNotification();
        NotificationChannel channel = notification.getChannel();

        log.info(
                "Inicio del despacho de notificación notificationId={} canal={}",
                notificationId,
                channel
        );

        NotificationDispatcher dispatcher = dispatchers.get(channel);

        if (Objects.isNull(dispatcher)) {

            log.error(
                    "No existe un dispatcher configurado para la notificación notificationId={} canal={}",
                    notificationId,
                    channel
            );

            throw new IllegalArgumentException(
                    "No existe un dispatcher configurado para el canal "
                            + channel
            );
        }

        dispatcher.dispatch(notification);

        log.info(
                "Despacho de notificación finalizado correctamente notificationId={} canal={}",
                notificationId,
                channel
        );

    }
}
