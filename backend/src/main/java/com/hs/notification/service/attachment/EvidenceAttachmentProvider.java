package com.hs.notification.service.attachment;

import com.hs.notification.service.CaseArtifactExportService;
import org.springframework.stereotype.Component;

/**
 * Case-evidence files (case_doc_artifact, via CaseArtifactExportService) —
 * un-stubbed per HS_NOTIFICATION_V2_METADATA_DESIGN.md once the real chain
 * (case_id -> case_workstep -> case_artifact -> case_doc_artifact) was
 * verified live against the casemanagement DB. Current volume is thin (12
 * rows DB-wide as of that verification) — most cases will have no evidence
 * files, which surfaces as a normal "not attached" notice, not a failure.
 */
@Component
public class EvidenceAttachmentProvider implements AttachmentProvider {

    private final CaseArtifactExportService exportService;

    public EvidenceAttachmentProvider(CaseArtifactExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public String key() { return "EVIDENCE"; }

    @Override
    public String displayName() { return "Case Evidence Files"; }

    @Override
    public String description() {
        return "Evidence files attached to a case in HyperSense case management (case_artifact/case_doc_artifact). " +
                "Low current volume — most cases will have none.";
    }

    @Override
    public boolean isAvailable() { return exportService.isConfigured(); }

    @Override
    public AttachmentResult generate(AttachmentContext context) {
        Object caseId = context.payload().get("case_id");
        CaseArtifactExportService.ExportResult result = exportService.export(caseId);
        if (!result.success()) {
            return AttachmentResult.failure(result.failureReason());
        }
        return AttachmentResult.success(result.bytes(), result.filename(), result.contentType());
    }
}
