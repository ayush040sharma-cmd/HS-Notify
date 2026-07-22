package com.hs.notification.dto;

import com.hs.notification.model.NotificationRule;

public record ActiveRuleResponse(
        String ruleCode,
        String triggerEvent,
        String templateCode,
        String channel
) {
    public static ActiveRuleResponse from(NotificationRule r) {
        return new ActiveRuleResponse(
                r.getRuleCode(), r.getTriggerEvent(), r.getTemplate().getTemplateCode(), r.getTemplate().getChannel());
    }
}
