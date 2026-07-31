package com.hs.notification.service;

import com.hs.notification.model.NotificationTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRenderingServiceTest {

    private TemplateRenderingService service;

    @BeforeEach
    void setUp() {
        service = new TemplateRenderingService();
    }

    // --- whitelist enforcement ---

    @Test
    void allowedVariableIsInterpolated() {
        NotificationTemplate template = templateWith(
                "Hello {{name}}",
                List.of("name"),
                List.of()
        );
        String result = service.render(template, Map.of("name", "Alice")).subject();
        assertThat(result).isEqualTo("Hello Alice");
    }

    @Test
    void variableNotOnWhitelistRendersEmpty() {
        NotificationTemplate template = templateWith(
                "Hello {{name}} secret={{internal_key}}",
                List.of("name"),   // internal_key is NOT listed
                List.of()
        );
        String result = service.render(template, Map.of("name", "Bob", "internal_key", "S3CRET")).subject();
        assertThat(result).isEqualTo("Hello Bob secret=");
    }

    @Test
    void missingContextValueRendersEmpty() {
        NotificationTemplate template = templateWith("ID: {{case_id}}", List.of("case_id"), List.of());
        String result = service.render(template, Map.of()).subject();
        assertThat(result).isEqualTo("ID: ");
    }

    @Test
    void emptyAllowedListDropsAllVariables() {
        NotificationTemplate template = templateWith("{{a}} {{b}}", List.of(), List.of());
        String result = service.render(template, Map.of("a", "X", "b", "Y")).subject();
        assertThat(result).isEqualTo(" ");
    }

    // --- PII masking ---

    @Test
    void piiFieldIsMaskedBeforeInterpolation() {
        NotificationTemplate template = templateWith(
                "Account: {{account_number}}",
                List.of("account_number"),
                List.of("account_number")
        );
        String result = service.render(template, Map.of("account_number", "1234567890")).subject();
        // last 4 visible, rest masked: ******7890
        assertThat(result).isEqualTo("Account: ******7890");
    }

    @Test
    void shortPiiValueMaskedFully() {
        NotificationTemplate template = templateWith(
                "Pin: {{pin}}",
                List.of("pin"),
                List.of("pin")
        );
        String result = service.render(template, Map.of("pin", "12")).subject();
        assertThat(result).isEqualTo("Pin: ****");
    }

    @Test
    void nonPiiFieldIsNotMasked() {
        NotificationTemplate template = templateWith(
                "{{name}}",
                List.of("name"),
                List.of()  // name NOT in pii list
        );
        String result = service.render(template, Map.of("name", "Jane")).subject();
        assertThat(result).isEqualTo("Jane");
    }

    // --- HTML escaping ---

    @Test
    void htmlCharsInValueAreEscaped() {
        NotificationTemplate template = templateWith(
                "<b>{{msg}}</b>",
                List.of("msg"),
                List.of()
        );
        String result = service.render(template, Map.of("msg", "<script>alert('xss')</script>")).subject();
        // single-quote is not escaped by this implementation (not a required HTML escape)
        assertThat(result).isEqualTo("<b>&lt;script&gt;alert('xss')&lt;/script&gt;</b>")
                .doesNotContain("<script>");
    }

    @Test
    void ampersandIsEscaped() {
        NotificationTemplate template = templateWith("{{val}}", List.of("val"), List.of());
        String result = service.render(template, Map.of("val", "A&B")).subject();
        assertThat(result).isEqualTo("A&amp;B");
    }

    @Test
    void doubleQuoteIsEscaped() {
        NotificationTemplate template = templateWith("{{val}}", List.of("val"), List.of());
        String result = service.render(template, Map.of("val", "say \"hi\"")).subject();
        assertThat(result).isEqualTo("say &quot;hi&quot;");
    }

    // --- body rendering ---

    @Test
    void bodyIsRenderedIndependentlyOfSubject() {
        NotificationTemplate template = new NotificationTemplate();
        template.setSubjectTemplate("Subject: {{id}}");
        template.setBodyTemplate("<p>Body: {{id}}</p>");
        template.setAllowedVariables(List.of("id"));
        template.setPiiMaskFields(List.of());

        TemplateRenderingService.RenderedContent result =
                service.render(template, Map.of("id", "42"));

        assertThat(result.subject()).isEqualTo("Subject: 42");
        assertThat(result.body()).isEqualTo("<p>Body: 42</p>");
    }

    // --- attachment-context PII masking ---

    @Test
    void maskPiiForAttachmentsMasksListedFields() {
        NotificationTemplate template = templateWith("x", List.of(), List.of("account_number"));
        Map<String, Object> masked = service.maskPiiForAttachments(template,
                Map.of("account_number", "1234567890", "case_id", "42"));
        assertThat(masked.get("account_number")).isEqualTo("******7890");
        assertThat(masked.get("case_id")).isEqualTo("42"); // non-PII structural key untouched
    }

    @Test
    void maskPiiForAttachmentsLeavesNonPiiKeysAndOriginalMapUntouched() {
        NotificationTemplate template = templateWith("x", List.of(), List.of());
        Map<String, Object> original = Map.of("case_id", "42", "catalog_id", "7");
        Map<String, Object> masked = service.maskPiiForAttachments(template, original);
        assertThat(masked).isEqualTo(original);
    }

    @Test
    void maskPiiForAttachmentsHandlesMissingKeyGracefully() {
        NotificationTemplate template = templateWith("x", List.of(), List.of("ssn"));
        Map<String, Object> masked = service.maskPiiForAttachments(template, Map.of("case_id", "1"));
        assertThat(masked).containsEntry("case_id", "1");
        assertThat(masked).doesNotContainKey("ssn");
    }

    // --- helpers ---

    private NotificationTemplate templateWith(String subject, List<String> allowed, List<String> pii) {
        NotificationTemplate t = new NotificationTemplate();
        t.setSubjectTemplate(subject);
        t.setBodyTemplate("");
        t.setAllowedVariables(allowed);
        t.setPiiMaskFields(pii);
        return t;
    }
}
