package com.hs.notification.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "escalation_chain")
public class EscalationChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "escalation_chain_id")
    private Long escalationChainId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "chain_code", nullable = false)
    private String chainCode;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "escalationChain", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<EscalationChainStep> steps;

    public Long getEscalationChainId() { return escalationChainId; }
    public void setEscalationChainId(Long escalationChainId) { this.escalationChainId = escalationChainId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getChainCode() { return chainCode; }
    public void setChainCode(String chainCode) { this.chainCode = chainCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<EscalationChainStep> getSteps() { return steps; }
    public void setSteps(List<EscalationChainStep> steps) { this.steps = steps; }
}
