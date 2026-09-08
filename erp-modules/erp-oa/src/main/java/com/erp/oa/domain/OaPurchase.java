package com.erp.oa.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class OaPurchase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long purchaseId;

    @NotBlank(message = "申请标题不能为空")
    @Size(max = 120, message = "标题长度不能超过120")
    @Excel(name = "申请标题")
    private String title;

    private Long applicantId;

    @Excel(name = "申请人账号")
    private String applicantName;

    @Excel(name = "申请人姓名")
    private String applicantNickName;

    private Long applicantDeptId;

    @Excel(name = "申请部门")
    private String applicantDeptName;

    private Long shopDeptId;

    @Excel(name = "申请店铺")
    private String shopDeptName;

    @Excel(name = "采购金额")
    private BigDecimal amount;

    @Excel(name = "采购说明")
    private String reason;

    @Excel(name = "状态", readConverterExp = "draft=草稿,submitting=提交中,pending=审批中,approved=已通过,returned=已退回,rejected=已拒绝,withdrawn=已撤回,terminated=已终止,cancelled=已关闭")
    private String status;

    /** 当前或最近一轮统一审批实例。 */
    private Long approvalInstanceId;

    /** 当前或最近一次提交轮次。 */
    private Integer approvalRound;

    /** 业务行乐观锁版本。 */
    private Long rowVersion;

    /** 最近成功消费的统一审批回调事件键。 */
    private String lastApprovalEventKey;

    public Long getPurchaseId()
    {
        return purchaseId;
    }

    public void setPurchaseId(Long purchaseId)
    {
        this.purchaseId = purchaseId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getApplicantId()
    {
        return applicantId;
    }

    public void setApplicantId(Long applicantId)
    {
        this.applicantId = applicantId;
    }

    public String getApplicantName()
    {
        return applicantName;
    }

    public void setApplicantName(String applicantName)
    {
        this.applicantName = applicantName;
    }

    public String getApplicantNickName()
    {
        return applicantNickName;
    }

    public void setApplicantNickName(String applicantNickName)
    {
        this.applicantNickName = applicantNickName;
    }

    public Long getApplicantDeptId()
    {
        return applicantDeptId;
    }

    public void setApplicantDeptId(Long applicantDeptId)
    {
        this.applicantDeptId = applicantDeptId;
    }

    public String getApplicantDeptName()
    {
        return applicantDeptName;
    }

    public void setApplicantDeptName(String applicantDeptName)
    {
        this.applicantDeptName = applicantDeptName;
    }

    public Long getShopDeptId()
    {
        return shopDeptId;
    }

    public void setShopDeptId(Long shopDeptId)
    {
        this.shopDeptId = shopDeptId;
    }

    public String getShopDeptName()
    {
        return shopDeptName;
    }

    public void setShopDeptName(String shopDeptName)
    {
        this.shopDeptName = shopDeptName;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getApprovalInstanceId()
    {
        return approvalInstanceId;
    }

    public void setApprovalInstanceId(Long approvalInstanceId)
    {
        this.approvalInstanceId = approvalInstanceId;
    }

    public Integer getApprovalRound()
    {
        return approvalRound;
    }

    public void setApprovalRound(Integer approvalRound)
    {
        this.approvalRound = approvalRound;
    }

    public Long getRowVersion()
    {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion)
    {
        this.rowVersion = rowVersion;
    }

    public String getLastApprovalEventKey()
    {
        return lastApprovalEventKey;
    }

    public void setLastApprovalEventKey(String lastApprovalEventKey)
    {
        this.lastApprovalEventKey = lastApprovalEventKey;
    }
}
