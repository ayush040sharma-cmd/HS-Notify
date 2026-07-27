package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "case_watch_checkpoint")
public class CaseWatchCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checkpoint_id")
    private Long checkpointId;

    @Column(name = "last_seen_case_id", nullable = false)
    private Long lastSeenCaseId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getCheckpointId() { return checkpointId; }
    public void setCheckpointId(Long checkpointId) { this.checkpointId = checkpointId; }

    public Long getLastSeenCaseId() { return lastSeenCaseId; }
    public void setLastSeenCaseId(Long lastSeenCaseId) { this.lastSeenCaseId = lastSeenCaseId; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
