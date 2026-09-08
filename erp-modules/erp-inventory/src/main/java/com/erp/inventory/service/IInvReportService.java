package com.erp.inventory.service;

import java.util.List;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvReportProductOption;
import com.erp.inventory.domain.vo.InvReportSummary;

public interface IInvReportService
{
    InvReportSummary selectReportSummary(InvStock stock, Long selectedShopDeptId);

    List<InvStock> selectStockWarningList(InvStock stock, Long selectedShopDeptId);

    List<InvReportProductOption> selectProductOptions(String keyword, Integer limit,
            Long selectedShopDeptId);
}
