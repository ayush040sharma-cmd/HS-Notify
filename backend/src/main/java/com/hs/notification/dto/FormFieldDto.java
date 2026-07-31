package com.hs.notification.dto;

import com.hs.notification.model.FormField;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record FormFieldDto(
        Long id,
        @NotBlank String fieldKey,
        @NotBlank String label,
        @NotBlank String fieldType,
        String hsFieldType,
        String placeholder,
        String helpText,
        Boolean required,
        Integer displayOrder,
        String defaultValue,
        String lookupSource,
        String conditionalOnFieldKey,
        String conditionalOnValue,
        List<FieldValidationDto> validations,
        List<FieldOptionDto> options
) {
    public static FormFieldDto from(FormField f) {
        return new FormFieldDto(
                f.getId(), f.getFieldKey(), f.getLabel(), f.getFieldType(), f.getHsFieldType(),
                f.getPlaceholder(), f.getHelpText(), f.isRequired(), f.getDisplayOrder(),
                f.getDefaultValue(), f.getLookupSource(),
                f.getConditionalOnFieldKey(), f.getConditionalOnValue(),
                f.getValidations() == null ? List.of() : f.getValidations().stream().map(FieldValidationDto::from).toList(),
                f.getOptions() == null ? List.of() : f.getOptions().stream().map(FieldOptionDto::from).toList()
        );
    }
}
