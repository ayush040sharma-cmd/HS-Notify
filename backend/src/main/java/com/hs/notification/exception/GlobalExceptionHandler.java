package com.hs.notification.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<Object> handleFeatureDisabled(FeatureDisabledException e) {
        return body(HttpStatus.FORBIDDEN, "FEATURE_DISABLED", e.getMessage());
    }

    @ExceptionHandler(RuleNotActiveException.class)
    public ResponseEntity<Object> handleRuleNotActive(RuleNotActiveException e) {
        return body(HttpStatus.CONFLICT, "RULE_NOT_ACTIVE", e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Object> handleRateLimit(RateLimitExceededException e) {
        return body(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", e.getMessage());
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Object> handleTenantNotFound(TenantNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(FormValidationException.class)
    public ResponseEntity<Object> handleFormValidation(FormValidationException e) {
        return body(HttpStatus.BAD_REQUEST, "FORM_VALIDATION_ERROR", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<Object> body(HttpStatus status, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("status", status.value());
        payload.put("error", code);
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }
}
