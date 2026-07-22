package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "watchdog_health_log")
public class WatchdogHealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "health_log_id")
    private Long healthLogId;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt = OffsetDateTime.now();

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;

    @Column(name = "action_taken")
    private String actionTaken;

    public Long getHealthLogId() { return healthLogId; }
    public void setHealthLogId(Long healthLogId) { this.healthLogId = healthLogId; }

    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(OffsetDateTime checkedAt) { this.checkedAt = checkedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Integer getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(Integer consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
}
