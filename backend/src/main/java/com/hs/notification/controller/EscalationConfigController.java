package com.hs.notification.controller;

import com.hs.notification.dto.EscalationConfigResponse;
import com.hs.notification.dto.UpdateEscalationConfigRequest;
import com.hs.notification.model.EscalationChain;
import com.hs.notification.model.EscalationChainStep;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.EscalationChainRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the tenant's escalation chain. The dashboard models a single
 * editable chain; if a tenant has multiple chains (one per rule), this
 * surfaces the first one found — per-rule chain selection isn't modeled by
 * the current SPA page and would need a UI redesign to support properly.
 */
@RestController
@RequestMapping("/api/v1/escalation-config")
public class EscalationConfigController {

    private final EscalationChainRepository escalationChainRepository;

    public EscalationConfigController(EscalationChainRepository escalationChainRepository) {
        this.escalationChainRepository = escalationChainRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<EscalationConfigResponse> get(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        EscalationChain chain = firstChainOrDefault(tenant);
        return ResponseEntity.ok(toResponse(chain));
    }

    @PutMapping
    @Transactional
    public ResponseEntity<EscalationConfigResponse> update(@RequestBody UpdateEscalationConfigRequest request,
                                                           HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        EscalationChain chain = firstChainOrDefault(tenant);

        // Flush the removal of old steps before inserting new ones — otherwise Hibernate
        // orders the new INSERTs before the old DELETEs in the same flush and trips the
        // (escalation_chain_id, step_order) unique constraint.
        if (chain.getSteps() != null) {
            chain.getSteps().clear();
        } else {
            chain.setSteps(new ArrayList<>());
        }
        escalationChainRepository.saveAndFlush(chain);

        List<EscalationChainStep> steps = new ArrayList<>();
        for (UpdateEscalationConfigRequest.StepUpdate update : request.chain()) {
            EscalationChainStep step = new EscalationChainStep();
            step.setEscalationChain(chain);
            step.setStepOrder(update.order());
            step.setRecipientEmail(update.recipient());
            step.setDelayMinutes(update.delayMinutes());
            steps.add(step);
        }
        chain.getSteps().addAll(steps);

        escalationChainRepository.save(chain);
        return ResponseEntity.ok(toResponse(chain));
    }

    private EscalationChain firstChainOrDefault(Tenant tenant) {
        List<EscalationChain> chains = escalationChainRepository.findByTenant_TenantId(tenant.getTenantId());
        if (!chains.isEmpty()) return chains.get(0);

        EscalationChain chain = new EscalationChain();
        chain.setTenant(tenant);
        chain.setChainCode("DEFAULT_ESCALATION");
        chain.setDescription("Default escalation chain");
        chain.setSteps(new ArrayList<>());
        return escalationChainRepository.save(chain);
    }

    private EscalationConfigResponse toResponse(EscalationChain chain) {
        List<EscalationConfigResponse.EscalationStep> steps = (chain.getSteps() == null ? List.<EscalationChainStep>of() : chain.getSteps())
                .stream()
                .map(s -> new EscalationConfigResponse.EscalationStep(
                        s.getStepOrder(), s.getRecipientEmail(), "EMAIL", s.getDelayMinutes()))
                .toList();
        return new EscalationConfigResponse(chain.getChainCode(), steps);
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
