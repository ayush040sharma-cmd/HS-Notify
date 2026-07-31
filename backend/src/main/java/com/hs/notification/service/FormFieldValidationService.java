package com.hs.notification.service;

import com.hs.notification.model.FieldValidation;
import com.hs.notification.model.FormField;
import com.hs.notification.model.FormSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Enforces field_validation rows server-side (see
 * HS_NOTIFICATION_V2_METADATA_DESIGN.md — "Validation" row of the Missing
 * Metadata Matrix). Until now this metadata was client-side-only, rendered
 * by the dashboard wizard but never checked on submit — a caller that skips
 * the wizard (a direct API call, or eventually HyperSense itself) could
 * submit anything. This closes that gap: degraded UX (reject-after-submit
 * rather than a greyed-out button) but real integrity, no HyperSense
 * dependency.
 */
@Service
public class FormFieldValidationService {

    private static final Logger log = LoggerFactory.getLogger(FormFieldValidationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public record ValidationError(String fieldKey, String message) {}

    /**
     * Never throws — a malformed validation_value on the metadata itself
     * (e.g. a non-numeric MIN_LENGTH, an invalid PATTERN regex) is a
     * metadata-authoring bug, not the caller's fault, so that single rule is
     * skipped (logged) rather than failing every submission against the form.
     */
    public List<ValidationError> validate(FormSchema schema, Map<String, Object> submittedValues) {
        List<ValidationError> errors = new ArrayList<>();
        if (schema == null || schema.getFields() == null) {
            return errors;
        }
        Map<String, Object> values = submittedValues == null ? Map.of() : submittedValues;

        for (FormField field : schema.getFields()) {
            Object raw = values.get(field.getFieldKey());
            String value = raw == null ? null : raw.toString();

            if (field.isRequired() && (value == null || value.isBlank())) {
                errors.add(new ValidationError(field.getFieldKey(), field.getLabel() + " is required"));
                continue;
            }
            if (value == null || value.isBlank()) {
                continue; // optional and absent — nothing else to check against a missing value
            }

            List<FieldValidation> rules = field.getValidations() == null ? List.of() : field.getValidations();
            for (FieldValidation rule : rules) {
                String failure = checkRule(rule, value);
                if (failure != null) {
                    String message = rule.getErrorMessage() != null && !rule.getErrorMessage().isBlank()
                            ? rule.getErrorMessage()
                            : field.getLabel() + " " + failure;
                    errors.add(new ValidationError(field.getFieldKey(), message));
                }
            }
        }
        return errors;
    }

    /** @return a human-readable failure description, or null if the rule passes (or can't be evaluated). */
    private String checkRule(FieldValidation rule, String value) {
        String type = rule.getValidationType();
        if (type == null) return null;

        return switch (type) {
            case "REQUIRED" -> value.isBlank() ? "is required" : null;
            case "MIN_LENGTH" -> {
                Integer min = parseInt(rule.getValidationValue());
                yield min != null && value.length() < min ? "is too short" : null;
            }
            case "MAX_LENGTH" -> {
                Integer max = parseInt(rule.getValidationValue());
                yield max != null && value.length() > max ? "is too long" : null;
            }
            case "PATTERN" -> {
                Pattern pattern = compilePattern(rule.getValidationValue());
                yield pattern != null && !pattern.matcher(value).matches() ? "has an invalid format" : null;
            }
            case "MIN" -> {
                Double actual = parseDouble(value);
                Double min = parseDouble(rule.getValidationValue());
                yield (actual != null && min != null && actual < min) ? "is below the minimum" : null;
            }
            case "MAX" -> {
                Double actual = parseDouble(value);
                Double max = parseDouble(rule.getValidationValue());
                yield (actual != null && max != null && actual > max) ? "is above the maximum" : null;
            }
            case "EMAIL_FORMAT" -> !EMAIL_PATTERN.matcher(value).matches() ? "must be a valid email address" : null;
            default -> {
                log.warn("Unknown field_validation.validation_type '{}' — skipping", type);
                yield null;
            }
        };
    }

    private Integer parseInt(String s) {
        try {
            return s == null ? null : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            log.warn("field_validation has a non-numeric length bound '{}' — skipping that rule", s);
            return null;
        }
    }

    private Double parseDouble(String s) {
        try {
            return s == null ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Pattern compilePattern(String regex) {
        try {
            return regex == null ? null : Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            log.warn("field_validation has an invalid PATTERN regex '{}' — skipping that rule", regex);
            return null;
        }
    }
}
