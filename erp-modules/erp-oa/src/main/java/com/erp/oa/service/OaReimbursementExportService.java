package com.erp.oa.service;

import java.nio.file.Path;
import com.erp.oa.domain.OaReimbursementExportBatch;
import com.erp.oa.domain.dto.OaReimbursementExportRequest;

public interface OaReimbursementExportService
{
    OaReimbursementExportBatch createExport(
            OaReimbursementExportRequest request, Long selectedShopDeptId);

    ExportContent exportContent(Long batchId);

    default java.util.List<OaReimbursementExportBatch> exportHistory(boolean allCreators)
    { throw new UnsupportedOperationException("导出历史当前不可用"); }

    default OaReimbursementExportBatch exportByRequestId(String requestId)
    { throw new UnsupportedOperationException("导出回执查询当前不可用"); }

    record ExportContent(Path path, String fileName, String contentType,
            long size) { }
}
