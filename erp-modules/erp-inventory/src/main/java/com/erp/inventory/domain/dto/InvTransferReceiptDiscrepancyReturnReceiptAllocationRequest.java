package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Expected damaged-return receipt for one immutable shipment allocation. */
public class InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest
{
    @NotNull(message = "退回发货分配ID不能为空")
    @Positive(message = "退回发货分配ID必须为正整数")
    private Long shipmentAllocationId;

    @Positive(message = "退回隔离库位ID必须为正整数")
    private Long quarantineLocationId;

    @NotNull(message = "预期退回实收数量不能为空")
    @DecimalMin(value = "0", message = "预期退回实收数量不能小于0")
    @Digits(integer = 14, fraction = 4,
            message = "预期退回实收数量最多14位整数和4位小数")
    private BigDecimal returnedQuantity;

    @NotNull(message = "退回意外短缺数量不能为空")
    @DecimalMin(value = "0", message = "退回意外短缺数量不能小于0")
    @Digits(integer = 14, fraction = 4,
            message = "退回意外短缺数量最多14位整数和4位小数")
    private BigDecimal shortageQuantity;

    @NotNull(message = "退回实收序列号列表不能为空")
    @Size(max = 10000, message = "退回实收序列号数量不能超过10000")
    private List<@NotNull(message = "序列号ID不能为空")
            @Positive(message = "序列号ID必须为正整数") Long>
                    returnedSerialIds = new ArrayList<>();

    @NotNull(message = "退回短缺序列号列表不能为空")
    @Size(max = 10000, message = "退回短缺序列号数量不能超过10000")
    private List<@NotNull(message = "序列号ID不能为空")
            @Positive(message = "序列号ID必须为正整数") Long>
                    shortageSerialIds = new ArrayList<>();

    @Size(max = 500, message = "退回短缺说明不能超过500个字符")
    private String discrepancyNote;

    @Size(max = 2000, message = "退回短缺附件引用不能超过2000个字符")
    private String attachmentRefs;

    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) {
        shipmentAllocationId = value;
    }
    public Long getQuarantineLocationId() { return quarantineLocationId; }
    public void setQuarantineLocationId(Long value) {
        quarantineLocationId = value;
    }
    public BigDecimal getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(BigDecimal value) {
        returnedQuantity = value;
    }
    public BigDecimal getShortageQuantity() { return shortageQuantity; }
    public void setShortageQuantity(BigDecimal value) {
        shortageQuantity = value;
    }
    public List<Long> getReturnedSerialIds() { return returnedSerialIds; }
    public void setReturnedSerialIds(List<Long> value) {
        returnedSerialIds = copy(value);
    }
    public List<Long> getShortageSerialIds() { return shortageSerialIds; }
    public void setShortageSerialIds(List<Long> value) {
        shortageSerialIds = copy(value);
    }
    public String getDiscrepancyNote() { return discrepancyNote; }
    public void setDiscrepancyNote(String value) {
        discrepancyNote = value;
    }
    public String getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(String value) { attachmentRefs = value; }

    private static List<Long> copy(List<Long> value)
    {
        return value == null ? null : new ArrayList<>(value);
    }
}
