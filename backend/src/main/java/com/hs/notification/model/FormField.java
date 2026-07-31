package com.hs.notification.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "form_fields")
public class FormField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_schema_id", nullable = false)
    private FormSchema formSchema;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "label", nullable = false)
    private String label;

    /** TEXTBOX | TEXTAREA | DROPDOWN | CHECKBOX | RADIO | DATE | EMAIL | FILE_UPLOAD | DYNAMIC_LOOKUP */
    @Column(name = "field_type", nullable = false)
    private String fieldType;

    /**
     * The down-leveled primitive HyperSense's Analyst Actions panel actually
     * renders (STRING | BOOLEAN | INTEGER | ARRAY | OBJECT), independent of
     * fieldType above. Nullable — only meaningful for fields on a form_schema
     * linked to a hypersense_exposed notification_action.
     */
    @Column(name = "hs_field_type")
    private String hsFieldType;

    @Column(name = "placeholder")
    private String placeholder;

    @Column(name = "help_text")
    private String helpText;

    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "lookup_source")
    private String lookupSource;

    @Column(name = "conditional_on_field_key")
    private String conditionalOnFieldKey;

    @Column(name = "conditional_on_value")
    private String conditionalOnValue;

    @OneToMany(mappedBy = "formField", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldValidation> validations;

    @OneToMany(mappedBy = "formField", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldOption> options;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FormSchema getFormSchema() { return formSchema; }
    public void setFormSchema(FormSchema formSchema) { this.formSchema = formSchema; }

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public String getHsFieldType() { return hsFieldType; }
    public void setHsFieldType(String hsFieldType) { this.hsFieldType = hsFieldType; }

    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }

    public String getHelpText() { return helpText; }
    public void setHelpText(String helpText) { this.helpText = helpText; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getLookupSource() { return lookupSource; }
    public void setLookupSource(String lookupSource) { this.lookupSource = lookupSource; }

    public String getConditionalOnFieldKey() { return conditionalOnFieldKey; }
    public void setConditionalOnFieldKey(String conditionalOnFieldKey) { this.conditionalOnFieldKey = conditionalOnFieldKey; }

    public String getConditionalOnValue() { return conditionalOnValue; }
    public void setConditionalOnValue(String conditionalOnValue) { this.conditionalOnValue = conditionalOnValue; }

    public List<FieldValidation> getValidations() { return validations; }
    public void setValidations(List<FieldValidation> validations) { this.validations = validations; }

    public List<FieldOption> getOptions() { return options; }
    public void setOptions(List<FieldOption> options) { this.options = options; }
}
