package com.hs.notification.controller;

import com.hs.notification.logging.InMemoryLogAppender;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/logs")
public class LogsController {

    private final InMemoryLogAppender appender;

    public LogsController(InMemoryLogAppender appender) {
        this.appender = appender;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String level) {
        return ResponseEntity.ok(Map.of("content", appender.snapshot(level)));
    }
}
