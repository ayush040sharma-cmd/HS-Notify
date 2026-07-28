package com.hs.notification.model;

import jakarta.persistence.*;

@Entity
@Table(name = "field_validation")
public class FieldValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_field_id", nullable = false)
    private FormField formField;

    /** REQUIRED | MIN_LENGTH | MAX_LENGTH | PATTERN | MIN | MAX | EMAIL_FORMAT */
    @Column(name = "validation_type", nullable = false)
    private String validationType;

    @Column(name = "validation_value")
    private String validationValue;

    @Column(name = "error_message")
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FormField getFormField() { return formField; }
    public void setFormField(FormField formField) { this.formField = formField; }

    public String getValidationType() { return validationType; }
    public void setValidationType(String validationType) { this.validationType = validationType; }

    public String getValidationValue() { return validationValue; }
    public void setValidationValue(String validationValue) { this.validationValue = validationValue; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
