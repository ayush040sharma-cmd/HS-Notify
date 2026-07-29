package com.hs.notification.service.attachment;

import com.hs.notification.service.PrRecordsExportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Same data as PrRecordsAttachmentProvider, rendered as a real .xlsx instead
 * of .csv — this is the only genuinely new capability in Phase 4 (the others
 * reuse existing HTTP/DB integrations); adds Apache POI as the one new
 * dependency this phase needs.
 */
@Component
public class ExcelExportAttachmentProvider implements AttachmentProvider {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportAttachmentProvider.class);

    private final PrRecordsExportService exportService;

    public ExcelExportAttachmentProvider(PrRecordsExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public String key() { return "EXCEL_EXPORT"; }

    @Override
    public String displayName() { return "PR Records (Excel)"; }

    @Override
    public String description() { return "Same PR-records export as PR_RECORDS, rendered as a .xlsx workbook instead of CSV."; }

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

        try {
            byte[] xlsx = csvToXlsx(result.csvBytes());
            Object caseTemplateName = context.payload().get("case_template_name");
            String filename = (caseTemplateName != null ? caseTemplateName : "pr_records") + "_" + caseId + ".xlsx";
            return AttachmentResult.success(xlsx, filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (IOException e) {
            log.warn("Failed to convert PR records CSV to Excel: {}", e.getMessage());
            return AttachmentResult.failure("Failed to build Excel workbook: " + e.getMessage());
        }
    }

    private byte[] csvToXlsx(byte[] csvBytes) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PR Records");
            int rowNum = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Row row = sheet.createRow(rowNum++);
                    List<String> cells = splitCsvLine(line);
                    for (int i = 0; i < cells.size(); i++) {
                        row.createCell(i).setCellValue(cells.get(i));
                    }
                }
            }
            for (int i = 0; i < 20; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Minimal RFC4180-ish split — handles quoted fields with embedded commas, sufficient for COPY's CSV output. */
    private List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
