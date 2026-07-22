package com.hs.notification.dto;

import java.util.List;

public record EscalationConfigResponse(String chainCode, List<EscalationStep> chain) {
    public record EscalationStep(int order, String recipient, String channel, int delayMinutes) {}
}
