package com.hs.notification.dto;

import java.util.List;

public record UpdateEscalationConfigRequest(List<StepUpdate> chain) {
    public record StepUpdate(int order, String recipient, int delayMinutes) {}
}
