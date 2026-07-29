package com.hs.notification.scheduler;

import com.hs.notification.model.NotificationJob;
import com.hs.notification.repository.NotificationJobRepository;
import com.hs.notification.service.MailDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final NotificationJobRepository jobRepository;
    private final MailDispatchService mailDispatchService;

    public RetryScheduler(NotificationJobRepository jobRepository,
                          MailDispatchService mailDispatchService) {
        this.jobRepository = jobRepository;
        this.mailDispatchService = mailDispatchService;
    }

    /**
     * @Transactional here (not just on attemptSend) keeps findDueForRetry's
     * jobs attached to the same Hibernate session as every attemptSend()
     * call below — without it, jobs come back detached (their own query
     * runs in its own auto-transaction), and job.getRule() throws
     * LazyInitializationException the moment attemptSend -> resolveChannel
     * touches it, since attemptSend's own @Transactional starts a *new*
     * session that was never populated with this detached entity's proxies.
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void processRetries() {
        List<NotificationJob> due = jobRepository.findDueForRetry(OffsetDateTime.now());
        if (due.isEmpty()) return;

        log.info("Processing {} job(s) due for retry", due.size());
        for (NotificationJob job : due) {
            try {
                mailDispatchService.attemptSend(job);
            } catch (Exception e) {
                log.error("Unexpected error retrying job {}", job.getJobId(), e);
            }
        }
    }
}
