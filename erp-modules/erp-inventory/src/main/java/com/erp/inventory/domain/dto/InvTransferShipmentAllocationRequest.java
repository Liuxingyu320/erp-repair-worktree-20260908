package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class InvTransferShipmentAllocationRequest
{
    @NotNull(message = "调拨明细ID不能为空")
    @Positive(message = "调拨明细ID必须为正整数")
    private Long transferDetailId;

    @NotNull(message = "库存余额ID不能为空")
    @Positive(message = "库存余额ID必须为正整数")
    private Long balanceId;

    @NotNull(message = "库存批次ID不能为空")
    @Positive(message = "库存批次ID必须为正整数")
    private Long lotId;

    @NotNull(message = "库存库位ID不能为空")
    @Positive(message = "库存库位ID必须为正整数")
    private Long locationId;

    @NotNull(message = "发货分配数量不能为空")
    @DecimalMin(value = "0", inclusive = false,
            message = "发货分配数量必须大于0")
    @Digits(integer = 14, fraction = 4,
            message = "发货分配数量最多14位整数和4位小数")
    private BigDecimal quantity;

    @NotNull(message = "序列号ID列表不能为空")
    @Size(max = 10000, message = "单个分配的序列号数量不能超过10000")
    private List<@NotNull(message = "序列号ID不能为空")
            @Positive(message = "序列号ID必须为正整数") Long> serialIds =
                    new ArrayList<>();

    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long value) { transferDetailId = value; }
    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long value) { balanceId = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long value) { locationId = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public List<Long> getSerialIds() { return serialIds; }
    public void setSerialIds(List<Long> value) {
        serialIds = value == null ? null : new ArrayList<>(value);
    }
}
