package com.hs.notification.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "recipient_group")
public class RecipientGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipient_group_id")
    private Long recipientGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "recipientGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipientGroupMember> members;

    public Long getRecipientGroupId() { return recipientGroupId; }
    public void setRecipientGroupId(Long recipientGroupId) { this.recipientGroupId = recipientGroupId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<RecipientGroupMember> getMembers() { return members; }
    public void setMembers(List<RecipientGroupMember> members) { this.members = members; }
}
