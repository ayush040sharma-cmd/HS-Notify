package com.hs.notification.dto;

public record ServiceHealthItem(String name, String status, Integer responseTimeMs, String details) {}
