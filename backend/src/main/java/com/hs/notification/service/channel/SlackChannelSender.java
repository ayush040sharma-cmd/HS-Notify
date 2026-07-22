package com.hs.notification.service.channel;

import com.hs.notification.model.NotificationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Delivers notifications to Slack via incoming webhook URLs.
 * Each URL in to_addresses must be a Slack incoming webhook URL.
 * The rendered HTML body is stripped to plain text before posting.
 */
@Component
public class SlackChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelSender.class);

    private final RestClient restClient;

    public SlackChannelSender(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String channel() { return "SLACK"; }

    @Override
    public void send(NotificationJob job) {
        if (job.getToAddresses() == null || job.getToAddresses().isEmpty()) {
            throw new IllegalStateException(
                    "SLACK channel requires at least one Slack incoming webhook URL in to_addresses for job " + job.getJobId());
        }

        String headerText = job.getSubject() != null ? job.getSubject() : "HS Notification";
        String bodyText = job.getRenderedBody() != null
                ? job.getRenderedBody().replaceAll("<[^>]+>", "").strip()
                : "";

        Map<String, Object> payload = Map.of(
                "text", headerText,
                "blocks", List.of(
                        Map.of("type", "header",
                               "text", Map.of("type", "plain_text", "text", headerText, "emoji", true)),
                        Map.of("type", "section",
                               "text", Map.of("type", "mrkdwn", "text", bodyText)),
                        Map.of("type", "context",
                               "elements", List.of(
                                       Map.of("type", "mrkdwn",
                                              "text", "Source: " + (job.getSourceReference() != null ? job.getSourceReference() : "manual") +
                                                      "  |  Tenant: " + job.getTenant().getTenantCode())))
                )
        );

        for (String webhookUrl : job.getToAddresses()) {
            log.info("Posting Slack notification to {} for job {}", webhookUrl, job.getJobId());
            restClient.post()
                    .uri(webhookUrl)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        }
    }
}
