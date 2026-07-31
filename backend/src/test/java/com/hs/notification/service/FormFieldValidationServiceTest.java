package com.hs.notification.service;

import com.hs.notification.model.FieldValidation;
import com.hs.notification.model.FormField;
import com.hs.notification.model.FormSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormFieldValidationServiceTest {

    private FormFieldValidationService service;

    @BeforeEach
    void setUp() {
        service = new FormFieldValidationService();
    }

    @Test
    void requiredFieldMissingFails() {
        FormSchema schema = schemaWith(field("subject", "Subject", true, List.of()));
        var errors = service.validate(schema, Map.of());
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).fieldKey()).isEqualTo("subject");
        assertThat(errors.get(0).message()).contains("required");
    }

    @Test
    void requiredFieldPresentPasses() {
        FormSchema schema = schemaWith(field("subject", "Subject", true, List.of()));
        var errors = service.validate(schema, Map.of("subject", "Fraud Alert"));
        assertThat(errors).isEmpty();
    }

    @Test
    void optionalFieldAbsentIsNotChecked() {
        FormField f = field("notes", "Notes", false,
                List.of(validation("MIN_LENGTH", "10", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of());
        assertThat(errors).isEmpty();
    }

    @Test
    void minLengthViolationFails() {
        FormField f = field("comment", "Comment", false,
                List.of(validation("MIN_LENGTH", "10", "Comment must be at least 10 characters")));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("comment", "short"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).isEqualTo("Comment must be at least 10 characters");
    }

    @Test
    void maxLengthViolationFails() {
        FormField f = field("subject", "Subject", false,
                List.of(validation("MAX_LENGTH", "5", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("subject", "too long a value"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("too long");
    }

    @Test
    void patternViolationFails() {
        FormField f = field("severity", "Severity", false,
                List.of(validation("PATTERN", "^(LOW|MEDIUM|HIGH)$", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("severity", "CRITICAL"));
        assertThat(errors).hasSize(1);
    }

    @Test
    void patternMatchPasses() {
        FormField f = field("severity", "Severity", false,
                List.of(validation("PATTERN", "^(LOW|MEDIUM|HIGH)$", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("severity", "HIGH"));
        assertThat(errors).isEmpty();
    }

    @Test
    void minNumericViolationFails() {
        FormField f = field("priority_score", "Priority Score", false,
                List.of(validation("MIN", "1", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("priority_score", "0"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("below the minimum");
    }

    @Test
    void maxNumericViolationFails() {
        FormField f = field("priority_score", "Priority Score", false,
                List.of(validation("MAX", "10", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("priority_score", "11"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("above the maximum");
    }

    @Test
    void nonNumericValueAgainstMinMaxDoesNotCrash() {
        FormField f = field("priority_score", "Priority Score", false,
                List.of(validation("MIN", "1", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("priority_score", "not-a-number"));
        // can't be evaluated -> gracefully skipped, not a false failure and not a crash
        assertThat(errors).isEmpty();
    }

    @Test
    void emailFormatViolationFails() {
        FormField f = field("to_address", "To Address", false,
                List.of(validation("EMAIL_FORMAT", null, "Must be a valid email address")));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("to_address", "not-an-email"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).isEqualTo("Must be a valid email address");
    }

    @Test
    void emailFormatValidPasses() {
        FormField f = field("to_address", "To Address", false,
                List.of(validation("EMAIL_FORMAT", null, null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("to_address", "analyst@subex.com"));
        assertThat(errors).isEmpty();
    }

    @Test
    void unknownValidationTypeIsSkippedGracefully() {
        FormField f = field("weird", "Weird", false,
                List.of(validation("SOME_FUTURE_TYPE", "x", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("weird", "anything"));
        assertThat(errors).isEmpty();
    }

    @Test
    void malformedValidationValueIsSkippedRatherThanRejectingSubmission() {
        // MIN_LENGTH with a non-numeric bound is a metadata authoring bug, not the caller's fault
        FormField f = field("comment", "Comment", false,
                List.of(validation("MIN_LENGTH", "not-a-number", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("comment", "hi"));
        assertThat(errors).isEmpty();
    }

    @Test
    void invalidRegexIsSkippedRatherThanRejectingSubmission() {
        FormField f = field("code", "Code", false,
                List.of(validation("PATTERN", "[unclosed", null)));
        FormSchema schema = schemaWith(f);
        var errors = service.validate(schema, Map.of("code", "anything"));
        assertThat(errors).isEmpty();
    }

    @Test
    void multipleFieldsReportAllErrorsTogether() {
        FormField subject = field("subject", "Subject", true, List.of());
        FormField email = field("to_address", "To Address", false,
                List.of(validation("EMAIL_FORMAT", null, null)));
        FormSchema schema = schemaWith(subject, email);

        var errors = service.validate(schema, Map.of("to_address", "not-an-email"));

        assertThat(errors).hasSize(2);
        assertThat(errors).extracting("fieldKey").containsExactlyInAnyOrder("subject", "to_address");
    }

    // --- helpers ---

    private FormSchema schemaWith(FormField... fields) {
        FormSchema schema = new FormSchema();
        schema.setFields(List.of(fields));
        return schema;
    }

    private FormField field(String key, String label, boolean required, List<FieldValidation> validations) {
        FormField f = new FormField();
        f.setFieldKey(key);
        f.setLabel(label);
        f.setFieldType("TEXTBOX");
        f.setRequired(required);
        f.setValidations(validations);
        return f;
    }

    private FieldValidation validation(String type, String value, String errorMessage) {
        FieldValidation v = new FieldValidation();
        v.setValidationType(type);
        v.setValidationValue(value);
        v.setErrorMessage(errorMessage);
        return v;
    }
}
