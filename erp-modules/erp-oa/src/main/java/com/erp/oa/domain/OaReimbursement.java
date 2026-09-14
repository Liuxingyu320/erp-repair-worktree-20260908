package com.erp.oa.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import com.erp.common.core.web.domain.BaseEntity;

public class OaReimbursement extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long reimbursementId;
    private String reimbursementNo;

    @Size(max = 120, message = "报销标题长度不能超过120")
    private String title;

    @Size(max = 500, message = "报销事由长度不能超过500")
    private String purpose;

    private Long applicantId;
    private String applicantName;
    private String applicantNickName;
    private Long applicantDeptId;
    private String applicantDeptName;
    private Long shopDeptId;
    private String shopDeptName;
    private BigDecimal totalAmount;
    private String status;
    private String exportStatus;
    private Date submittedTime;
    private Date approvedTime;
    private Date lastExportTime;
    private Long approvalInstanceId;
    private Integer approvalRound;
    private Long rowVersion;
    // Request-only optimistic command boundary; intentionally not persisted by the mapper.
    private Integer expectedBaseRound;
    private Long expectedVersion;
    public Integer getExpectedBaseRound() { return expectedBaseRound; }
    public void setExpectedBaseRound(Integer value) { expectedBaseRound = value; }
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long value) { expectedVersion = value; }
    private String lastApprovalEventKey;
    private Integer invoiceCount;

    @Valid
    private List<OaReimbursementItem> items = new ArrayList<>();
    private List<OaReimbursementInvoice> invoices = new ArrayList<>();

    public Long getReimbursementId() { return reimbursementId; }
    public void setReimbursementId(Long value) { reimbursementId = value; }
    public String getReimbursementNo() { return reimbursementNo; }
    public void setReimbursementNo(String value) { reimbursementNo = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { purpose = value; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long value) { applicantId = value; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String value) { applicantName = value; }
    public String getApplicantNickName() { return applicantNickName; }
    public void setApplicantNickName(String value) { applicantNickName = value; }
    public Long getApplicantDeptId() { return applicantDeptId; }
    public void setApplicantDeptId(Long value) { applicantDeptId = value; }
    public String getApplicantDeptName() { return applicantDeptName; }
    public void setApplicantDeptName(String value) { applicantDeptName = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String value) { shopDeptName = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { totalAmount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getExportStatus() { return exportStatus; }
    public void setExportStatus(String value) { exportStatus = value; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date value) { submittedTime = value; }
    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date value) { approvedTime = value; }
    public Date getLastExportTime() { return lastExportTime; }
    public void setLastExportTime(Date value) { lastExportTime = value; }
    public Long getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(Long value) { approvalInstanceId = value; }
    public Integer getApprovalRound() { return approvalRound; }
    public void setApprovalRound(Integer value) { approvalRound = value; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }
    public String getLastApprovalEventKey() { return lastApprovalEventKey; }
    public void setLastApprovalEventKey(String value) { lastApprovalEventKey = value; }
    public Integer getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(Integer value) { invoiceCount = value; }
    public List<OaReimbursementItem> getItems() { return items; }
    public void setItems(List<OaReimbursementItem> value)
    {
        items = value == null ? new ArrayList<>() : value;
    }
    public List<OaReimbursementInvoice> getInvoices() { return invoices; }
    public void setInvoices(List<OaReimbursementInvoice> value)
    {
        invoices = value == null ? new ArrayList<>() : value;
    }
}
