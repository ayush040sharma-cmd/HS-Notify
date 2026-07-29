package com.hs.notification.service.attachment;

import com.hs.notification.service.PrRecordsExportService;
import org.springframework.stereotype.Component;

@Component
public class PrRecordsAttachmentProvider implements AttachmentProvider {

    private final PrRecordsExportService exportService;

    public PrRecordsAttachmentProvider(PrRecordsExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public String key() { return "PR_RECORDS"; }

    @Override
    public String displayName() { return "PR Records (CSV)"; }

    @Override
    public String description() { return "Exports system_pr_results_<catalog_id> rows for the case as CSV, via COPY against the usage DB."; }

    @Override
    public boolean isAvailable() { return exportService.isConfigured(); }

    @Override
    public AttachmentResult generate(AttachmentContext context) {
        Object caseId = context.payload().get("case_id");
        Object catalogId = context.payload().get("catalog_id");

        PrRecordsExportService.ExportResult result = exportService.export(caseId, catalogId);
        if (!result.success()) {
            return AttachmentResult.failure(result.failureReason());
        }

        Object caseTemplateName = context.payload().get("case_template_name");
        String filename = (caseTemplateName != null ? caseTemplateName : "pr_records") + "_" + caseId + ".csv";
        return AttachmentResult.success(result.csvBytes(), filename, "text/csv");
    }
}
