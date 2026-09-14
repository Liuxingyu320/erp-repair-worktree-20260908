package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OaReimbursementExportRequest
{
    @NotEmpty(message = "请选择需要导出的报销单")
    @Size(max = 200, message = "单次最多导出200张报销单")
    private List<Long> reimbursementIds = new ArrayList<>();

    @NotBlank(message = "导出请求号不能为空，请刷新页面后重试")
    @Size(max = 64, message = "导出请求号不能超过64个字符")
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }

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
