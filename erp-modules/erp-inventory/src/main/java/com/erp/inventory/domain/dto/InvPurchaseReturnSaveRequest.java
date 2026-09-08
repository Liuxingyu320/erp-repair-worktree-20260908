package com.erp.inventory.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.erp.inventory.domain.InvPurchaseReturn;
import com.erp.inventory.domain.InvPurchaseReturnDetail;

public class InvPurchaseReturnSaveRequest extends InvPurchaseReturn
{
    private static final long serialVersionUID = 1L;

    private List<InvPurchaseReturnDetail> details;

    @Override
    @NotNull(message = "请选择原采购单")
    public Long getPurchaseOrderId() { return super.getPurchaseOrderId(); }

    @Override
    @NotNull(message = "请选择退货日期")
    public java.util.Date getReturnDate() { return super.getReturnDate(); }

    @Override
    @NotBlank(message = "请填写退货原因")
    @Size(max = 500, message = "退货原因不能超过500个字符")
    public String getReturnReason() { return super.getReturnReason(); }

    @Override
    @NotBlank(message = "请选择责任归属")
    @Pattern(regexp = "supplier|warehouse|transport|other", message = "责任归属无效")
    public String getResponsibility() { return super.getResponsibility(); }

    @Override
    @Valid
    @NotEmpty(message = "至少一条退货明细不能为空")
    @Size(max = 200, message = "退货明细不能超过200条")
    public List<InvPurchaseReturnDetail> getDetails() { return details; }
    @Override
    public void setDetails(List<InvPurchaseReturnDetail> details) { this.details = details; }
}
