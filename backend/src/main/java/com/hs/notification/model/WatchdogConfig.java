package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "watchdog_config")
public class WatchdogConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watchdog_config_id")
    private Long watchdogConfigId;

    @Column(name = "poll_interval_seconds", nullable = false)
    private Integer pollIntervalSeconds = 30;

    @Column(name = "fail_threshold", nullable = false)
    private Integer failThreshold = 3;

    @Column(name = "restart_command")
    private String restartCommand;

    @Column(name = "escalation_recipients", columnDefinition = "text[]")
    private List<String> escalationRecipients;

    @Column(name = "heartbeat_enabled", nullable = false)
    private boolean heartbeatEnabled = true;

    @Column(name = "heartbeat_interval_minutes", nullable = false)
    private Integer heartbeatIntervalMinutes = 60;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getWatchdogConfigId() { return watchdogConfigId; }
    public void setWatchdogConfigId(Long watchdogConfigId) { this.watchdogConfigId = watchdogConfigId; }

    public Integer getPollIntervalSeconds() { return pollIntervalSeconds; }
    public void setPollIntervalSeconds(Integer pollIntervalSeconds) { this.pollIntervalSeconds = pollIntervalSeconds; }

    public Integer getFailThreshold() { return failThreshold; }
    public void setFailThreshold(Integer failThreshold) { this.failThreshold = failThreshold; }

    public String getRestartCommand() { return restartCommand; }
    public void setRestartCommand(String restartCommand) { this.restartCommand = restartCommand; }

    public List<String> getEscalationRecipients() { return escalationRecipients; }
    public void setEscalationRecipients(List<String> escalationRecipients) { this.escalationRecipients = escalationRecipients; }

    public boolean isHeartbeatEnabled() { return heartbeatEnabled; }
    public void setHeartbeatEnabled(boolean heartbeatEnabled) { this.heartbeatEnabled = heartbeatEnabled; }

    public Integer getHeartbeatIntervalMinutes() { return heartbeatIntervalMinutes; }
    public void setHeartbeatIntervalMinutes(Integer heartbeatIntervalMinutes) { this.heartbeatIntervalMinutes = heartbeatIntervalMinutes; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
