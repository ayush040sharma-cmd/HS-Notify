package com.hs.notification.dto;

import java.util.List;

public record NotifyResponse(NotificationJobResponse job, List<String> notices) {}
