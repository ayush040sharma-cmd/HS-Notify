package com.hs.notification.model;

import jakarta.persistence.*;

@Entity
@Table(name = "recipient_group_member")
public class RecipientGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_group_id", nullable = false)
    private RecipientGroup recipientGroup;

    @Column(name = "recipient_type", nullable = false)
    private String recipientType = "TO";

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "hs_user_ref")
    private String hsUserRef;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public RecipientGroup getRecipientGroup() { return recipientGroup; }
    public void setRecipientGroup(RecipientGroup recipientGroup) { this.recipientGroup = recipientGroup; }

    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getHsUserRef() { return hsUserRef; }
    public void setHsUserRef(String hsUserRef) { this.hsUserRef = hsUserRef; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
