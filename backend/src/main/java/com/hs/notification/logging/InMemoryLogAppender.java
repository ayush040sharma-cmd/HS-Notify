package com.hs.notification.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Captures live application log events into a bounded in-memory buffer so the
 * dashboard's System Logs page has something real to show without adding a
 * DB-backed log table. Resets on restart — this is a live tail, not an archive.
 */
@Component
public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_ENTRIES = 500;

    private final ConcurrentLinkedDeque<LogEntry> buffer = new ConcurrentLinkedDeque<>();
    private final AtomicLong idSeq = new AtomicLong();

    @PostConstruct
    public void install() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLoggerName().startsWith("com.hs.notification")) return;

        String logger = event.getLoggerName();
        String component = logger.substring(logger.lastIndexOf('.') + 1);

        buffer.addFirst(new LogEntry(
                idSeq.incrementAndGet(),
                OffsetDateTime.now(),
                event.getLevel().toString(),
                component,
                event.getFormattedMessage()));

        while (buffer.size() > MAX_ENTRIES) buffer.pollLast();
    }

    public List<LogEntry> snapshot(String levelFilter) {
        return buffer.stream()
                .filter(e -> levelFilter == null || levelFilter.isBlank() || e.level().equalsIgnoreCase(levelFilter))
                .collect(Collectors.toList());
    }

    public record LogEntry(long logId, OffsetDateTime ts, String level, String component, String message) {}
}
