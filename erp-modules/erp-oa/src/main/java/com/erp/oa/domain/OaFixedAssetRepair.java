package com.erp.oa.domain;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class OaFixedAssetRepair extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long repairId;

    @NotNull(message = "店铺不能为空")
    private Long shopDeptId;

    @Excel(name = "店铺")
    private String shopDeptName;

    @NotNull(message = "OE器皿不能为空")
    private Long oeItemId;

    private String oeItemCode;

    @Excel(name = "固定资产")
    private String oeItemName;

    @NotNull(message = "坏掉数量不能为空")
    @DecimalMin(value = "0.01", message = "坏掉数量必须大于0")
    @Excel(name = "坏掉数量")
    private BigDecimal repairQuantity;

    @Excel(name = "系统计算占用额度")
    private BigDecimal estimatedRepairAmount;

    private BigDecimal availableQuotaAmount;

    @NotBlank(message = "故障说明不能为空")
    @Excel(name = "故障说明")
    private String faultDescription;

    private String imageUrls;
    private String exceptionApproved;
    private String exceptionType;
    private Integer advanceMonths;

    @Excel(name = "状态", readConverterExp = "draft=草稿,pending_confirm=待确认上报,submitted=已上报,rejected=已驳回,cancelled=已取消")
    private String status;

    private Long applicantId;
    private String applicantName;
    private String approvedBy;
    private Date approvedTime;
    private Date submittedTime;

    public Long getRepairId()
    {
        return repairId;
    }

    public void setRepairId(Long repairId)
    {
        this.repairId = repairId;
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

    public Long getOeItemId()
    {
        return oeItemId;
    }

    public void setOeItemId(Long oeItemId)
    {
        this.oeItemId = oeItemId;
    }

    public String getOeItemCode()
    {
        return oeItemCode;
    }

    public void setOeItemCode(String oeItemCode)
    {
        this.oeItemCode = oeItemCode;
    }

    public String getOeItemName()
    {
        return oeItemName;
    }

    public void setOeItemName(String oeItemName)
    {
        this.oeItemName = oeItemName;
    }

    public BigDecimal getRepairQuantity()
    {
        return repairQuantity;
    }

    public void setRepairQuantity(BigDecimal repairQuantity)
    {
        this.repairQuantity = repairQuantity;
    }

    public BigDecimal getEstimatedRepairAmount()
    {
        return estimatedRepairAmount;
    }

    public void setEstimatedRepairAmount(BigDecimal estimatedRepairAmount)
    {
        this.estimatedRepairAmount = estimatedRepairAmount;
    }

    public BigDecimal getAvailableQuotaAmount()
    {
        return availableQuotaAmount;
    }

    public void setAvailableQuotaAmount(BigDecimal availableQuotaAmount)
    {
        this.availableQuotaAmount = availableQuotaAmount;
    }

    public String getFaultDescription()
    {
        return faultDescription;
    }

    public void setFaultDescription(String faultDescription)
    {
        this.faultDescription = faultDescription;
    }

    public String getImageUrls()
    {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls)
    {
        this.imageUrls = imageUrls;
    }

    public String getExceptionApproved()
    {
        return exceptionApproved;
    }

    public void setExceptionApproved(String exceptionApproved)
    {
        this.exceptionApproved = exceptionApproved;
    }

    public String getExceptionType()
    {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType)
    {
        this.exceptionType = exceptionType;
    }

    public Integer getAdvanceMonths()
    {
        return advanceMonths;
    }

    public void setAdvanceMonths(Integer advanceMonths)
    {
        this.advanceMonths = advanceMonths;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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

    public String getApprovedBy()
    {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy)
    {
        this.approvedBy = approvedBy;
    }

    public Date getApprovedTime()
    {
        return approvedTime;
    }

    public void setApprovedTime(Date approvedTime)
    {
        this.approvedTime = approvedTime;
    }

    public Date getSubmittedTime()
    {
        return submittedTime;
    }

    public void setSubmittedTime(Date submittedTime)
    {
        this.submittedTime = submittedTime;
    }
}
