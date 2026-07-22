package com.hs.notification.dto;

import java.util.List;

public record MetricsResponse(
        List<HourlyPoint> hourly,
        List<DailyPoint> daily,
        List<Double> successRate,
        List<Integer> avgResponseMs
) {
    public record HourlyPoint(String hour, long sent, long failed) {}
    public record DailyPoint(String day, long sent, long failed) {}
}
