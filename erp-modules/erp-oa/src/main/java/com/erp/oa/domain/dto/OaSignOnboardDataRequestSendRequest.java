package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class OaSignOnboardDataRequestSendRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;
    @NotEmpty(message = "请选择需要补资料的导入行")
    @Size(max = 100, message = "一次最多处理100行")
    private List<@Positive(message = "导入行编号无效") Long> rowIds = new ArrayList<>();
    /** Defaults to COMPANY_FIRST; SIGNATURE_FIRST additionally collects a task-scoped sample. */
    private String signingSequence;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public List<Long> getRowIds() { return new ArrayList<>(rowIds); }
    public void setRowIds(List<Long> rowIds)
    {
        this.rowIds = rowIds == null ? new ArrayList<>() : new ArrayList<>(rowIds);
    }
    public String getSigningSequence() { return signingSequence; }
    public void setSigningSequence(String value) { signingSequence = value; }
}
