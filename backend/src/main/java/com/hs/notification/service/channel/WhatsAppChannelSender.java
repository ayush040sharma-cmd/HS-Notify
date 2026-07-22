package com.hs.notification.service.channel;

import com.hs.notification.model.NotificationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WhatsApp channel placeholder. This is the only additional channel planned
 * for the next phase (per product direction — no further Slack/Webhook/SMS
 * work is planned). Wire to the real WhatsApp Business API by injecting a
 * client here and reading credentials from hs-notification.whatsapp.*
 * properties once that integration is scheduled.
 *
 * Currently logs the intent and lets the send be treated as delivered so the
 * rest of the pipeline (audit, retry state machine) still works end-to-end.
 */
@Component
public class WhatsAppChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelSender.class);

    @Override
    public String channel() { return "WHATSAPP"; }

    @Override
    public void send(NotificationJob job) {
        log.warn("WhatsApp channel is not yet wired to the WhatsApp Business API. " +
                 "Job {} would send to {} — configure hs-notification.whatsapp.* to enable.",
                 job.getJobId(), job.getToAddresses());
    }
}
