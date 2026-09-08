package com.erp.oa.attendance.approval;

/** Minimal locked business state used by the unified-approval callback. */
public class OaAttendanceApprovalState
{
    private Long businessId;
    private Long userId;
    private Long shopId;
    private String status;
    private Integer businessRound;
    private Long approvalInstanceId;
    private Long rowVersion;
    private String lastApprovalEventKey;

    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer businessRound) { this.businessRound = businessRound; }
    public Long getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(Long approvalInstanceId) { this.approvalInstanceId = approvalInstanceId; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
    public String getLastApprovalEventKey() { return lastApprovalEventKey; }
    public void setLastApprovalEventKey(String lastApprovalEventKey) { this.lastApprovalEventKey = lastApprovalEventKey; }
}
