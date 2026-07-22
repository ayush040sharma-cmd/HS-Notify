package com.hs.notification.scheduler;

import com.hs.notification.model.WatchdogConfig;
import com.hs.notification.model.WatchdogHealthLog;
import com.hs.notification.model.WatchdogState;
import com.hs.notification.repository.WatchdogConfigRepository;
import com.hs.notification.repository.WatchdogHealthLogRepository;
import com.hs.notification.repository.WatchdogStateRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class WatchdogScheduler {

    private static final Logger log = LoggerFactory.getLogger(WatchdogScheduler.class);

    private final HealthEndpoint healthEndpoint;
    private final WatchdogHealthLogRepository healthLogRepository;
    private final WatchdogConfigRepository configRepository;
    private final WatchdogStateRepository stateRepository;
    private final JavaMailSender mailSender;

    private OffsetDateTime lastHeartbeatSentAt;

    public WatchdogScheduler(HealthEndpoint healthEndpoint,
                             WatchdogHealthLogRepository healthLogRepository,
                             WatchdogConfigRepository configRepository,
                             WatchdogStateRepository stateRepository,
                             JavaMailSender mailSender) {
        this.healthEndpoint = healthEndpoint;
        this.healthLogRepository = healthLogRepository;
        this.configRepository = configRepository;
        this.stateRepository = stateRepository;
        this.mailSender = mailSender;
    }

    @Scheduled(fixedDelayString = "${hs-notification.watchdog.poll-interval-ms:30000}")
    @Transactional
    public void checkHealth() {
        WatchdogConfig config = configRepository.findFirstByOrderByWatchdogConfigIdAsc()
                .orElseGet(this::defaultConfig);
        WatchdogState state = stateRepository.findFirstByOrderByWatchdogStateIdAsc()
                .orElseGet(this::defaultState);

        long start = System.currentTimeMillis();
        boolean up;
        String detail;
        try {
            Status status = healthEndpoint.health().getStatus();
            up = Status.UP.equals(status);
            detail = "Internal health check returned " + status;
        } catch (Exception e) {
            up = false;
            detail = "Health check threw exception: " + e.getMessage();
        }
        int elapsed = (int) (System.currentTimeMillis() - start);

        WatchdogHealthLog logEntry = new WatchdogHealthLog();
        logEntry.setCheckedAt(OffsetDateTime.now());
        logEntry.setStatus(up ? "UP" : "DOWN");
        logEntry.setResponseTimeMs(elapsed);
        logEntry.setDetail(detail);

        if (up) {
            state.setConsecutiveFailures(0);
            state.setLastUpAt(OffsetDateTime.now());
            logEntry.setConsecutiveFailures(0);
            logEntry.setActionTaken("NONE");
        } else {
            state.setConsecutiveFailures(state.getConsecutiveFailures() + 1);
            state.setLastDownAt(OffsetDateTime.now());
            logEntry.setConsecutiveFailures(state.getConsecutiveFailures());

            if (state.getConsecutiveFailures() <= config.getFailThreshold()) {
                state.setTotalRestarts(state.getTotalRestarts() + 1);
                logEntry.setActionTaken("RESTART_ATTEMPTED");
                log.warn("Watchdog: health check failed ({} consecutive). Restart attempt #{}",
                        state.getConsecutiveFailures(), state.getTotalRestarts());
            }

            if (state.getConsecutiveFailures() >= config.getFailThreshold()) {
                state.setEscalationsSent(state.getEscalationsSent() + 1);
                logEntry.setActionTaken("ESCALATED");
                escalateServiceDown(config, state.getConsecutiveFailures());
            }
        }

        state.setUpdatedAt(OffsetDateTime.now());
        stateRepository.save(state);
        healthLogRepository.save(logEntry);

        maybeSendHeartbeat(config, up);
    }

    private void escalateServiceDown(WatchdogConfig config, int consecutiveFailures) {
        if (config.getEscalationRecipients() == null || config.getEscalationRecipients().isEmpty()) {
            log.error("Watchdog wants to escalate ({} consecutive failures) but no escalation recipients configured",
                    consecutiveFailures);
            return;
        }
        for (String recipient : config.getEscalationRecipients()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message);
                helper.setTo(recipient);
                helper.setSubject("[CRITICAL] HS Notification Platform service down");
                helper.setText("<p>The HS Notification Platform has failed " + consecutiveFailures +
                        " consecutive health checks and exceeded the configured failure threshold.</p>" +
                        "<p>Please investigate immediately.</p>", true);
                mailSender.send(message);
            } catch (Exception e) {
                log.error("Watchdog escalation email failed to send to {}", recipient, e);
            }
        }
    }

    private void maybeSendHeartbeat(WatchdogConfig config, boolean up) {
        if (!config.isHeartbeatEnabled() || !up) return;
        OffsetDateTime now = OffsetDateTime.now();
        if (lastHeartbeatSentAt != null &&
                now.isBefore(lastHeartbeatSentAt.plusMinutes(config.getHeartbeatIntervalMinutes()))) {
            return;
        }
        lastHeartbeatSentAt = now;
        log.info("Watchdog heartbeat OK at {}", now);
    }

    private WatchdogConfig defaultConfig() {
        WatchdogConfig config = new WatchdogConfig();
        return configRepository.save(config);
    }

    private WatchdogState defaultState() {
        WatchdogState state = new WatchdogState();
        return stateRepository.save(state);
    }
}
