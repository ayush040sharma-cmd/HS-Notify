package com.hs.notification.service.attachment;

import com.hs.notification.service.AttachmentService;
import org.springframework.stereotype.Component;

@Component
public class CasePdfAttachmentProvider implements AttachmentProvider {

    private final AttachmentService attachmentService;

    public CasePdfAttachmentProvider(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Override
    public String key() { return "CASE_PDF"; }

    @Override
    public String displayName() { return "Case PDF (Report Service)"; }

    @Override
    public String description() { return "Fetches a rendered case PDF from the HS report service — same integration point rule-based PR_RECORD attachments already use."; }

    @Override
    public boolean isAvailable() { return attachmentService.isReportServiceConfigured(); }

    @Override
    public AttachmentResult generate(AttachmentContext context) {
        Object reportIdentifier = context.payload().get("report_identifier");
        if (reportIdentifier == null || reportIdentifier.toString().isBlank()) {
            return AttachmentResult.failure("report_identifier missing from context/payload");
        }
        Object caseId = context.payload().get("case_id");

        try {
            byte[] bytes = attachmentService.fetchReportBytes(
                    reportIdentifier.toString(), context.tenant().getTenantId(),
                    caseId != null ? caseId.toString() : null);
            String filename = reportIdentifier + (caseId != null ? "_" + caseId : "") + ".pdf";
            return AttachmentResult.success(bytes, filename, "application/pdf");
        } catch (RuntimeException e) {
            return AttachmentResult.failure(e.getMessage());
        }
    }
}
