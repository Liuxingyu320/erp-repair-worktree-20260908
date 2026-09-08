package com.erp.oa.service;

import java.nio.file.Path;
import com.erp.oa.domain.OaReimbursementExportBatch;
import com.erp.oa.domain.dto.OaReimbursementExportRequest;

public interface OaReimbursementExportService
{
    OaReimbursementExportBatch createExport(
            OaReimbursementExportRequest request, Long selectedShopDeptId);

    ExportContent exportContent(Long batchId);

    record ExportContent(Path path, String fileName, String contentType,
            long size) { }
}
