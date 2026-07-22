package com.hs.notification.service.channel;

import com.hs.notification.model.NotificationJob;

public interface ChannelSender {
    String channel();
    void send(NotificationJob job) throws Exception;
}
