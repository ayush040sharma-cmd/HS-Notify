package com.hs.notification.dto;

import com.hs.notification.model.FieldValidation;
import jakarta.validation.constraints.NotBlank;

public record FieldValidationDto(
        Long id,
        @NotBlank String validationType,
        String validationValue,
        String errorMessage
) {
    public static FieldValidationDto from(FieldValidation v) {
        return new FieldValidationDto(v.getId(), v.getValidationType(), v.getValidationValue(), v.getErrorMessage());
    }
}
