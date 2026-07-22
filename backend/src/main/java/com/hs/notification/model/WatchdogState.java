package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "watchdog_state")
public class WatchdogState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watchdog_state_id")
    private Long watchdogStateId;

    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;

    @Column(name = "total_restarts", nullable = false)
    private Integer totalRestarts = 0;

    @Column(name = "escalations_sent", nullable = false)
    private Integer escalationsSent = 0;

    @Column(name = "last_up_at")
    private OffsetDateTime lastUpAt;

    @Column(name = "last_down_at")
    private OffsetDateTime lastDownAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getWatchdogStateId() { return watchdogStateId; }
    public void setWatchdogStateId(Long watchdogStateId) { this.watchdogStateId = watchdogStateId; }

    public Integer getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(Integer consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public Integer getTotalRestarts() { return totalRestarts; }
    public void setTotalRestarts(Integer totalRestarts) { this.totalRestarts = totalRestarts; }

    public Integer getEscalationsSent() { return escalationsSent; }
    public void setEscalationsSent(Integer escalationsSent) { this.escalationsSent = escalationsSent; }

    public OffsetDateTime getLastUpAt() { return lastUpAt; }
    public void setLastUpAt(OffsetDateTime lastUpAt) { this.lastUpAt = lastUpAt; }

    public OffsetDateTime getLastDownAt() { return lastDownAt; }
    public void setLastDownAt(OffsetDateTime lastDownAt) { this.lastDownAt = lastDownAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
