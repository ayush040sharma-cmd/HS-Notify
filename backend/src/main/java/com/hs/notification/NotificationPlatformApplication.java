package com.hs.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HS Notification Platform.
 *
 * Packaged microservice providing:
 *  - secure APIs for sending email + generating attachments
 *  - DB-driven configuration (rules, templates, recipients, escalation)
 *  - invocation from Alarm/Case/Analyst closure actions
 *  - execution audit logging
 *  - health monitoring, auto-recovery (watchdog), support escalation
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NotificationPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationPlatformApplication.class, args);
    }
}
