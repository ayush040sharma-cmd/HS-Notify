package com.hs.notification.dto;

import java.util.List;

/** Reference data to populate the rule create/edit form's dropdowns in one call. */
public record RuleFormOptionsResponse(
        List<TemplateOption> templates,
        List<RecipientGroupOption> recipientGroups,
        List<EscalationChainOption> escalationChains
) {
    public record TemplateOption(String templateCode, String channel, String status) {}
    public record RecipientGroupOption(String groupCode, String description) {}
    public record EscalationChainOption(String chainCode, String description) {}
}
