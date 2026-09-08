package com.erp.oa.service;

import java.util.List;
import com.erp.oa.domain.dto.OaSignBatchCreateDraftsRequest;
import com.erp.oa.domain.dto.OaSignBatchCreateDraftsResult;
import com.erp.oa.domain.dto.OaSignBatchPreviewRequest;
import com.erp.oa.domain.dto.OaSignBatchPreviewRow;

public interface IOaSignBatchService
{
    List<OaSignBatchPreviewRow> preview(OaSignBatchPreviewRequest request, Long selectedShopDeptId);

    OaSignBatchCreateDraftsResult createDrafts(OaSignBatchCreateDraftsRequest request, Long selectedShopDeptId);
}
