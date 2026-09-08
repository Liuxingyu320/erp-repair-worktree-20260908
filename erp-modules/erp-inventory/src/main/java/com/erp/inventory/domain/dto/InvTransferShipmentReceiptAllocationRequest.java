package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Receipt classifications for one immutable V2 shipment allocation. */
public class InvTransferShipmentReceiptAllocationRequest
{
    @NotNull(message = "发货分配ID不能为空")
    @Positive(message = "发货分配ID必须为正整数")
    private Long shipmentAllocationId;

    @Positive(message = "合格收货库位ID必须为正整数")
    private Long acceptedLocationId;

    @NotNull(message = "合格收货数量不能为空")
    @DecimalMin(value = "0", message = "合格收货数量不能小于0")
    @Digits(integer = 14, fraction = 4,
            message = "合格收货数量最多14位整数和4位小数")
    private BigDecimal acceptedQuantity;

    @Positive(message = "残损隔离库位ID必须为正整数")
    private Long quarantineLocationId;

    @NotNull(message = "残损数量不能为空")
    @DecimalMin(value = "0", message = "残损数量不能小于0")
    @Digits(integer = 14, fraction = 4,
            message = "残损数量最多14位整数和4位小数")
    private BigDecimal damagedQuantity;

    @NotNull(message = "显式短缺数量不能为空")
    @DecimalMin(value = "0", message = "显式短缺数量不能小于0")
    @Digits(integer = 14, fraction = 4,
            message = "显式短缺数量最多14位整数和4位小数")
    private BigDecimal shortageQuantity;

    @NotNull(message = "合格序列号列表不能为空")
    @Size(max = 10000, message = "合格序列号数量不能超过10000")
    private List<@NotNull(message = "序列号ID不能为空")
            @Positive(message = "序列号ID必须为正整数") Long>
                    acceptedSerialIds = new ArrayList<>();

    @NotNull(message = "残损序列号列表不能为空")
    @Size(max = 10000, message = "残损序列号数量不能超过10000")
    private List<@NotNull(message = "序列号ID不能为空")
            @Positive(message = "序列号ID必须为正整数") Long>
                    damagedSerialIds = new ArrayList<>();

    @NotNull(message = "短缺序列号列表不能为空")
    @Size(max = 10000, message = "短缺序列号数量不能超过10000")
    private List<@NotNull(message = "序列号ID不能为空")
            @Positive(message = "序列号ID必须为正整数") Long>
                    shortageSerialIds = new ArrayList<>();

    @Size(max = 500, message = "差异说明不能超过500个字符")
    private String discrepancyNote;

    @Size(max = 2000, message = "附件引用不能超过2000个字符")
    private String attachmentRefs;

    public Long getShipmentAllocationId() { return shipmentAllocationId; }
    public void setShipmentAllocationId(Long value) {
        shipmentAllocationId = value;
    }
    public Long getAcceptedLocationId() { return acceptedLocationId; }
    public void setAcceptedLocationId(Long value) {
        acceptedLocationId = value;
    }
    public BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(BigDecimal value) {
        acceptedQuantity = value;
    }
    public Long getQuarantineLocationId() { return quarantineLocationId; }
    public void setQuarantineLocationId(Long value) {
        quarantineLocationId = value;
    }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(BigDecimal value) {
        damagedQuantity = value;
    }
    public BigDecimal getShortageQuantity() { return shortageQuantity; }
    public void setShortageQuantity(BigDecimal value) {
        shortageQuantity = value;
    }
    public List<Long> getAcceptedSerialIds() { return acceptedSerialIds; }
    public void setAcceptedSerialIds(List<Long> value) {
        acceptedSerialIds = copy(value);
    }
    public List<Long> getDamagedSerialIds() { return damagedSerialIds; }
    public void setDamagedSerialIds(List<Long> value) {
        damagedSerialIds = copy(value);
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
