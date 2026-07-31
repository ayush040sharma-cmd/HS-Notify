package com.hs.notification.service;

import com.hs.notification.model.NotificationTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders {{variable}} style templates with two safety controls:
 *  1. allowed_variables whitelist - only variables explicitly permitted on the
 *     template may be interpolated. Anything else is dropped, not echoed, to
 *     prevent leaking arbitrary context data into outbound mail (DLP control).
 *  2. pii_mask_fields - listed variables are partially masked before
 *     interpolation (e.g. account numbers, phone numbers).
 */
@Service
public class TemplateRenderingService {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    public RenderedContent render(NotificationTemplate template, Map<String, Object> context) {
        List<String> allowed = template.getAllowedVariables() == null ? List.of() : template.getAllowedVariables();
        List<String> maskFields = template.getPiiMaskFields() == null ? List.of() : template.getPiiMaskFields();

        String subject = renderString(template.getSubjectTemplate(), context, allowed, maskFields);
        String body = renderString(template.getBodyTemplate(), context, allowed, maskFields);

        return new RenderedContent(subject, body);
    }

    private String renderString(String raw, Map<String, Object> context, List<String> allowed, List<String> maskFields) {
        if (raw == null) return "";
        Matcher matcher = VAR_PATTERN.matcher(raw);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(raw, lastEnd, matcher.start());
            String varName = matcher.group(1);

            if (!allowed.contains(varName)) {
                // Not whitelisted: render as empty rather than echoing unexpected context data.
                result.append("");
            } else {
                Object value = context.get(varName);
                String stringValue = value == null ? "" : value.toString();
                if (maskFields.contains(varName)) {
                    stringValue = mask(stringValue);
                }
                result.append(escapeHtml(stringValue));
            }
            lastEnd = matcher.end();
        }
        result.append(raw.substring(lastEnd));
        return result.toString();
    }

    /**
     * Attachment providers read the raw context map directly (case_id,
     * catalog_id, etc.), not through {{variable}} interpolation, so the
     * allowed_variables whitelist above doesn't apply to them the same way —
     * providers legitimately need structural lookup keys that were never
     * meant to appear in a rendered message. pii_mask_fields still should
     * apply, though: a field flagged as PII shouldn't reach attachment
     * generation unmasked just because it arrived via a different code path
     * than subject/body rendering. Returns a shallow copy; the original
     * context (and any key not in pii_mask_fields) is untouched, so
     * functional keys attachment providers rely on keep working.
     */
    public Map<String, Object> maskPiiForAttachments(NotificationTemplate template, Map<String, Object> context) {
        List<String> maskFields = template.getPiiMaskFields() == null ? List.of() : template.getPiiMaskFields();
        if (maskFields.isEmpty() || context == null || context.isEmpty()) {
            return context;
        }
        Map<String, Object> masked = new HashMap<>(context);
        for (String field : maskFields) {
            Object value = masked.get(field);
            if (value != null) {
                masked.put(field, mask(value.toString()));
            }
        }
        return masked;
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) return "****";
        int visible = 4;
        String tail = value.substring(value.length() - visible);
        return "*".repeat(Math.max(4, value.length() - visible)) + tail;
    }

    private String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record RenderedContent(String subject, String body) {}
}
