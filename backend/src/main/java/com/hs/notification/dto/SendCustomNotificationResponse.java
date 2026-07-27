package com.hs.notification.dto;

import java.util.List;

public record SendCustomNotificationResponse(
        NotificationJobResponse job,
        List<String> notices
) {}
