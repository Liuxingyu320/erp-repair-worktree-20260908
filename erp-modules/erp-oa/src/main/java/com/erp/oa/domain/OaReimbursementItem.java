package com.erp.oa.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

public class OaReimbursementItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long itemId;
    private Long reimbursementId;
    private Long sourceInvoiceId;

    @NotBlank(message = "费用类型不能为空")
    @Size(max = 32, message = "费用类型长度不能超过32")
    private String expenseType;

    @NotNull(message = "费用日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expenseDate;

    @Size(max = 120, message = "商户名称长度不能超过120")
    private String merchantName;

    @NotBlank(message = "费用说明不能为空")
    @Size(max = 300, message = "费用说明长度不能超过300")
    private String description;

    @NotNull(message = "报销金额不能为空")
    @DecimalMin(value = "0.01", message = "报销金额必须大于0")
    private BigDecimal claimedAmount;

    private Integer sortNo;

    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getReimbursementId() { return reimbursementId; }
    public void setReimbursementId(Long value) { reimbursementId = value; }
    public Long getSourceInvoiceId() { return sourceInvoiceId; }
    public void setSourceInvoiceId(Long value) { sourceInvoiceId = value; }
    public String getExpenseType() { return expenseType; }
    public void setExpenseType(String value) { expenseType = value; }
    public Date getExpenseDate() { return expenseDate; }
    public void setExpenseDate(Date value) { expenseDate = value; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String value) { merchantName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public void setClaimedAmount(BigDecimal value) { claimedAmount = value; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer value) { sortNo = value; }
}
