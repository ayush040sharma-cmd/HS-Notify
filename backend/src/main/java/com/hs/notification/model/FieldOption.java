package com.hs.notification.model;

import jakarta.persistence.*;

@Entity
@Table(name = "field_options")
public class FieldOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_field_id", nullable = false)
    private FormField formField;

    @Column(name = "option_value", nullable = false)
    private String optionValue;

    @Column(name = "option_label", nullable = false)
    private String optionLabel;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FormField getFormField() { return formField; }
    public void setFormField(FormField formField) { this.formField = formField; }

    public String getOptionValue() { return optionValue; }
    public void setOptionValue(String optionValue) { this.optionValue = optionValue; }

    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
