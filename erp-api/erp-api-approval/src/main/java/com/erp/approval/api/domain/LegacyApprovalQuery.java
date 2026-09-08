package com.erp.approval.api.domain;

import java.io.Serializable;

/** Filter used by the central monitor when reading legacy engines. */
public class LegacyApprovalQuery implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String businessCode;
    private String businessId;
    private String status;
    private Integer pageNum = 1;
    private Integer pageSize = 20;

    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String value) { businessCode = value; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String value) { businessId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer value) { pageNum = value; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer value) { pageSize = value; }
}
