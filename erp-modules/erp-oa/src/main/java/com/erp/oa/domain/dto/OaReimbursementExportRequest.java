package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class OaReimbursementExportRequest
{
    @NotEmpty(message = "请选择需要导出的报销单")
    @Size(max = 200, message = "单次最多导出200张报销单")
    private List<Long> reimbursementIds = new ArrayList<>();

    public List<Long> getReimbursementIds()
    {
        return reimbursementIds;
    }

    public void setReimbursementIds(List<Long> reimbursementIds)
    {
        this.reimbursementIds = reimbursementIds == null
                ? new ArrayList<>() : reimbursementIds;
    }
}
