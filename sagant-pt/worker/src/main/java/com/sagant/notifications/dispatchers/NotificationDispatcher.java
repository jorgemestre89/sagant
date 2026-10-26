package com.sagant.notifications.dispatchers;

import com.sagant.notifications.domain.Notification;
import com.sagant.notifications.domain.enums.NotificationChannel;

public interface NotificationDispatcher {
    /**
     * Identifica que canal implementa cada despachador
     * */
    NotificationChannel getChannel();
    /**
     * Realiza el envío de una notificación.
     *
     * @param notification notificación a envíar
     * */
    void dispatch(Notification notification);
}
