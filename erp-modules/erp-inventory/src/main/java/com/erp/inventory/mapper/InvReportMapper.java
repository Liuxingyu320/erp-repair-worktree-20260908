package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvReportProductOption;
import com.erp.inventory.domain.vo.InvReportItemOption;
import com.erp.inventory.domain.vo.InvReportSummary;

public interface InvReportMapper
{
    List<InvReportItemOption> selectReportItemOptions(InvStock stock);

    InvReportSummary selectReportSummary(InvStock stock);

    List<InvStock> selectStockWarningList(InvStock stock);

    List<InvReportProductOption> selectReportProductOptions(InvStock stock);
}
