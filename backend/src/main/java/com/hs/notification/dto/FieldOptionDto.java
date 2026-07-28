package com.hs.notification.dto;

import com.hs.notification.model.FieldOption;
import jakarta.validation.constraints.NotBlank;

public record FieldOptionDto(
        Long id,
        @NotBlank String optionValue,
        @NotBlank String optionLabel,
        Integer displayOrder
) {
    public static FieldOptionDto from(FieldOption o) {
        return new FieldOptionDto(o.getId(), o.getOptionValue(), o.getOptionLabel(), o.getDisplayOrder());
    }
}
