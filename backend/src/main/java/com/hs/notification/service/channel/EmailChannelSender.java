package com.hs.notification.service.channel;

import com.hs.notification.model.NotificationJob;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailChannelSender implements ChannelSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailChannelSender(JavaMailSender mailSender,
                              @Value("${hs-notification.mail.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public String channel() { return "EMAIL"; }

    @Override
    public void send(NotificationJob job) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, job.getAttachmentPath() != null);

        helper.setFrom(fromAddress);
        helper.setTo(job.getToAddresses().toArray(new String[0]));
        if (job.getCcAddresses() != null && !job.getCcAddresses().isEmpty()) {
            helper.setCc(job.getCcAddresses().toArray(new String[0]));
        }
        helper.setSubject(job.getSubject());
        helper.setText(job.getRenderedBody(), true);

        if (job.getAttachmentPath() != null) {
            java.io.File file = new java.io.File(job.getAttachmentPath());
            String storedName = file.getName();
            int marker = storedName.indexOf("__");
            String displayName = marker > 0 ? storedName.substring(marker + 2) : "report.pdf";
            helper.addAttachment(displayName, file);
        }
        mailSender.send(message);
    }
}
