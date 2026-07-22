package com.hs.notification.model;

import jakarta.persistence.*;

@Entity
@Table(name = "escalation_chain_step")
public class EscalationChainStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id")
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalation_chain_id", nullable = false)
    private EscalationChain escalationChain;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes = 0;

    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }

    public EscalationChain getEscalationChain() { return escalationChain; }
    public void setEscalationChain(EscalationChain escalationChain) { this.escalationChain = escalationChain; }

    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public Integer getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; }
}
