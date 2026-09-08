package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Strict command for adjusting an existing stock row.
 *
 * The stock identity and organization are resolved from the server-side row;
 * callers cannot redirect the mutation by posting item or organization ids.
 */
public class InvExistingStockAdjustRequest
{
    private Long expectedVersion;

    private BigDecimal adjustQuantity;

    private String reason;

    @NotNull(message = "库存版本不能为空")
    @PositiveOrZero(message = "库存版本必须大于或等于0")
    public Long getExpectedVersion()
    {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion)
    {
        this.expectedVersion = expectedVersion;
    }

    @NotNull(message = "调整数量不能为空")
    @Digits(integer = 14, fraction = 4, message = "调整数量最多14位整数和4位小数")
    public BigDecimal getAdjustQuantity()
    {
        return adjustQuantity;
    }

    public void setAdjustQuantity(BigDecimal adjustQuantity)
    {
        this.adjustQuantity = adjustQuantity;
    }

    @NotBlank(message = "调整原因不能为空")
    @Size(min = 2, max = 500, message = "调整原因长度必须在2到500个字符之间")
    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
