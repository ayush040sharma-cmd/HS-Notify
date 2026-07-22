package com.hs.notification.service.channel;

import com.hs.notification.model.NotificationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Delivers notifications via HTTP POST. Each URL in to_addresses receives
 * a JSON payload containing the job's subject, rendered body, and metadata.
 * Use this channel when the recipient system exposes a webhook endpoint.
 */
@Component
public class WebhookChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookChannelSender.class);

    private final RestClient restClient;

    public WebhookChannelSender(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String channel() { return "WEBHOOK"; }

    @Override
    public void send(NotificationJob job) {
        if (job.getToAddresses() == null || job.getToAddresses().isEmpty()) {
            throw new IllegalStateException(
                    "WEBHOOK channel requires at least one target URL in to_addresses for job " + job.getJobId());
        }

        Map<String, Object> payload = Map.of(
                "jobId",           job.getJobId(),
                "tenantCode",      job.getTenant().getTenantCode(),
                "subject",         job.getSubject() != null ? job.getSubject() : "",
                "body",            job.getRenderedBody() != null ? job.getRenderedBody() : "",
                "sourceReference", job.getSourceReference() != null ? job.getSourceReference() : "",
                "sourceType",      job.getSourceType() != null ? job.getSourceType() : ""
        );

        for (String url : job.getToAddresses()) {
            log.info("Posting webhook notification to {} for job {}", url, job.getJobId());
            restClient.post()
                    .uri(url)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        }
    }
}
