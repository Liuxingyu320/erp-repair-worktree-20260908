package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Browser-safe command body for one fixed quarantine return shipment. */
public class
        InvTransferReceiptDiscrepancyReturnShipmentCreateRequest
{
    @NotNull(message = "退回发货规划版本不能为空")
    @Pattern(regexp = "[a-f0-9]{64}", message = "退回发货规划版本格式无效")
    private String planVersion;

    @NotNull(message = "退回发货依据不能为空")
    @Pattern(regexp = "return-quarantine-reservation-v2",
            message = "当前只允许接受退回隔离预留服务端规划")
    private String basis;

    @NotNull(message = "退回发货数量不能为空")
    @DecimalMin(value = "0.0001", message = "退回发货数量必须大于0")
    @Digits(integer = 14, fraction = 4,
            message = "退回发货数量最多14位整数和4位小数")
    private BigDecimal quantity;

    public String getPlanVersion() { return planVersion; }
    public void setPlanVersion(String value) { planVersion = value; }
    public String getBasis() { return basis; }
    public void setBasis(String value) { basis = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
}
