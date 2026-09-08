package com.erp.inventory.domain.dto;

import java.util.List;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class InvReceiveRequest
{
    @NotEmpty(message = "收货明细不能为空")
    @Valid
    private List<InvReceiveItem> items;

    @NotNull(message = "收货仓库不能为空")
    private Long warehouseId;

    @Size(max = 100, message = "供应商批次不能超过100个字符")
    private String supplierBatchNo;

    @Size(max = 100, message = "送货单号不能超过100个字符")
    private String deliveryNoteNo;

    @NotNull(message = "实际到货时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date arrivedTime;

    @Size(max = 500, message = "收货备注不能超过500个字符")
    private String remark;

    public List<InvReceiveItem> getItems() { return items; }
    public void setItems(List<InvReceiveItem> items) { this.items = items; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getSupplierBatchNo() { return supplierBatchNo; }
    public void setSupplierBatchNo(String supplierBatchNo) { this.supplierBatchNo = supplierBatchNo; }
    public String getDeliveryNoteNo() { return deliveryNoteNo; }
    public void setDeliveryNoteNo(String deliveryNoteNo) { this.deliveryNoteNo = deliveryNoteNo; }
    public Date getArrivedTime() { return arrivedTime; }
    public void setArrivedTime(Date arrivedTime) { this.arrivedTime = arrivedTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
