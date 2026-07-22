package com.hs.notification.controller;

import com.hs.notification.dto.WatchdogStatusResponse;
import com.hs.notification.model.WatchdogConfig;
import com.hs.notification.model.WatchdogHealthLog;
import com.hs.notification.model.WatchdogState;
import com.hs.notification.repository.WatchdogConfigRepository;
import com.hs.notification.repository.WatchdogHealthLogRepository;
import com.hs.notification.repository.WatchdogStateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/watchdog")
public class WatchdogController {

    private final WatchdogConfigRepository configRepository;
    private final WatchdogStateRepository stateRepository;
    private final WatchdogHealthLogRepository healthLogRepository;

    public WatchdogController(WatchdogConfigRepository configRepository,
                              WatchdogStateRepository stateRepository,
                              WatchdogHealthLogRepository healthLogRepository) {
        this.configRepository = configRepository;
        this.stateRepository = stateRepository;
        this.healthLogRepository = healthLogRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<WatchdogStatusResponse> status() {
        WatchdogConfig config = configRepository.findFirstByOrderByWatchdogConfigIdAsc()
                .orElse(new WatchdogConfig());
        WatchdogState state = stateRepository.findFirstByOrderByWatchdogStateIdAsc()
                .orElse(new WatchdogState());

        String currentStatus = state.getConsecutiveFailures() == 0 ? "UP"
                : state.getConsecutiveFailures() >= config.getFailThreshold() ? "DOWN" : "DEGRADED";

        return ResponseEntity.ok(new WatchdogStatusResponse(
                state.getConsecutiveFailures(),
                state.getTotalRestarts(),
                state.getEscalationsSent(),
                config.getPollIntervalSeconds(),
                config.getFailThreshold(),
                state.getLastUpAt(),
                state.getLastDownAt(),
                currentStatus
        ));
    }

    @GetMapping("/health-log")
    public ResponseEntity<Page<WatchdogHealthLog>> healthLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(healthLogRepository.findAllByOrderByCheckedAtDesc(PageRequest.of(page, size)));
    }
}
