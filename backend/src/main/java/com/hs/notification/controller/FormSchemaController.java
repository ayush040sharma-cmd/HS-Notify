package com.hs.notification.controller;

import com.hs.notification.dto.FormFieldDto;
import com.hs.notification.dto.FormSchemaResponse;
import com.hs.notification.dto.UpsertFormSchemaRequest;
import com.hs.notification.model.FieldOption;
import com.hs.notification.model.FieldValidation;
import com.hs.notification.model.FormField;
import com.hs.notification.model.FormSchema;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.FormSchemaRepository;
import com.hs.notification.repository.NotificationActionRepository;
import com.hs.notification.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD for the Form Metadata Engine (Phase 2 of the metadata-driven platform
 * rollout) — reusable form definitions a notification_action can point to
 * via its form_schema_id, so the frontend wizard renders fields from
 * metadata instead of hardcoding a component per action. See also
 * NotificationActionController#schemaForAction, the GET
 * /api/v1/actions/{code}/schema read path most callers actually want.
 */
@RestController
@RequestMapping("/api/v1/form-schemas")
public class FormSchemaController {

    private static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "TEXTBOX", "TEXTAREA", "DROPDOWN", "CHECKBOX", "RADIO", "DATE", "EMAIL", "FILE_UPLOAD", "DYNAMIC_LOOKUP");

    private final FormSchemaRepository formSchemaRepository;
    private final NotificationActionRepository actionRepository;
    private final AuditService auditService;

    public FormSchemaController(FormSchemaRepository formSchemaRepository,
                                 NotificationActionRepository actionRepository,
                                 AuditService auditService) {
        this.formSchemaRepository = formSchemaRepository;
        this.actionRepository = actionRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<FormSchemaResponse>> list() {
        return ResponseEntity.ok(formSchemaRepository.findAll()
                .stream().map(FormSchemaResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FormSchemaResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(FormSchemaResponse.from(requireSchema(id)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@Valid @RequestBody UpsertFormSchemaRequest request, HttpServletRequest httpRequest) {
        String typeError = validateFieldTypes(request);
        if (typeError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", typeError));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);

        FormSchema schema = new FormSchema();
        schema.setCreatedBy(actor);
        applyFields(schema, request);
        schema = formSchemaRepository.save(schema);

        auditService.log(tenant, null, null, "FORM_SCHEMA_CHANGED",
                "Form schema " + schema.getName() + " (id=" + schema.getId() + ") created", actor, null);
        return ResponseEntity.ok(FormSchemaResponse.from(schema));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody UpsertFormSchemaRequest request,
                                     HttpServletRequest httpRequest) {
        String typeError = validateFieldTypes(request);
        if (typeError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", typeError));
        }

        FormSchema schema = requireSchema(id);
        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);

        applyFields(schema, request);
        schema.setVersion(schema.getVersion() + 1);
        schema.setUpdatedOn(OffsetDateTime.now());
        schema = formSchemaRepository.save(schema);

        auditService.log(tenant, null, null, "FORM_SCHEMA_CHANGED",
                "Form schema " + schema.getName() + " (id=" + id + ") edited (v" + schema.getVersion() + ")", actor, null);
        return ResponseEntity.ok(FormSchemaResponse.from(schema));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        FormSchema schema = requireSchema(id);

        if (actionRepository.existsByFormSchemaId(id)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Form schema is linked to one or more notification actions and cannot be deleted",
                    "formSchemaId", id));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);
        String name = schema.getName();

        formSchemaRepository.delete(schema);

        auditService.log(tenant, null, null, "FORM_SCHEMA_CHANGED",
                "Form schema " + name + " (id=" + id + ") deleted", actor, null);
        return ResponseEntity.noContent().build();
    }

    /** Replaces the whole field/validation/option graph — simplest correct semantics for a form-builder "save" action. */
    private void applyFields(FormSchema schema, UpsertFormSchemaRequest request) {
        schema.setName(request.name());
        schema.setDescription(request.description());

        if (schema.getFields() == null) {
            schema.setFields(new ArrayList<>());
        } else {
            schema.getFields().clear();
        }

        if (request.fields() == null) return;

        for (FormFieldDto dto : request.fields()) {
            FormField field = new FormField();
            field.setFormSchema(schema);
            field.setFieldKey(dto.fieldKey());
            field.setLabel(dto.label());
            field.setFieldType(dto.fieldType());
            field.setPlaceholder(dto.placeholder());
            field.setHelpText(dto.helpText());
            field.setRequired(dto.required() != null && dto.required());
            field.setDisplayOrder(dto.displayOrder() != null ? dto.displayOrder() : 0);
            field.setDefaultValue(dto.defaultValue());
            field.setLookupSource(dto.lookupSource());
            field.setConditionalOnFieldKey(dto.conditionalOnFieldKey());
            field.setConditionalOnValue(dto.conditionalOnValue());

            List<FieldValidation> validations = new ArrayList<>();
            if (dto.validations() != null) {
                for (var v : dto.validations()) {
                    FieldValidation validation = new FieldValidation();
                    validation.setFormField(field);
                    validation.setValidationType(v.validationType());
                    validation.setValidationValue(v.validationValue());
                    validation.setErrorMessage(v.errorMessage());
                    validations.add(validation);
                }
            }
            field.setValidations(validations);

            List<FieldOption> options = new ArrayList<>();
            if (dto.options() != null) {
                for (var o : dto.options()) {
                    FieldOption option = new FieldOption();
                    option.setFormField(field);
                    option.setOptionValue(o.optionValue());
                    option.setOptionLabel(o.optionLabel());
                    option.setDisplayOrder(o.displayOrder() != null ? o.displayOrder() : 0);
                    options.add(option);
                }
            }
            field.setOptions(options);

            schema.getFields().add(field);
        }
    }

    private String validateFieldTypes(UpsertFormSchemaRequest request) {
        if (request.fields() == null) return null;
        for (FormFieldDto field : request.fields()) {
            if (!SUPPORTED_FIELD_TYPES.contains(field.fieldType())) {
                return "Unsupported fieldType '" + field.fieldType() + "' for field '" + field.fieldKey() +
                        "' — must be one of " + SUPPORTED_FIELD_TYPES;
            }
        }
        return null;
    }

    private FormSchema requireSchema(Long id) {
        return formSchemaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Form schema not found: " + id));
    }

    private String actorOf(HttpServletRequest request) {
        return request.getRemoteUser() != null ? request.getRemoteUser() : "operator";
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
