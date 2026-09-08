package com.erp.inventory.domain.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Browser command for one server-planned expected damaged-return receipt. */
public class InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
{
    @NotNull(message = "退回收货规划版本不能为空")
    @Pattern(regexp = "[a-f0-9]{64}", message = "退回收货规划版本格式无效")
    private String returnReceiptPlanVersion;

    @NotNull(message = "退回收货依据不能为空")
    @Pattern(regexp = "return-receipt-quarantine-plan-v1",
            message = "当前只允许接受退回隔离收货服务端规划")
    private String basis;

    @NotNull(message = "退回实际到货时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date arrivedTime;

    @NotNull(message = "是否完成本批退回收货不能为空")
    private Boolean finalizeShipment;

    @NotEmpty(message = "退回收货分配不能为空")
    @Size(max = 10000, message = "单次退回收货分配不能超过10000条")
    @Valid
    private List<InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest>
            allocations = new ArrayList<>();

    @Size(max = 500, message = "退回收货备注不能超过500个字符")
    private String remark;

    public String getReturnReceiptPlanVersion() {
        return returnReceiptPlanVersion;
    }
    public void setReturnReceiptPlanVersion(String value) {
        returnReceiptPlanVersion = value;
    }
    public String getBasis() { return basis; }
    public void setBasis(String value) { basis = value; }
    public Date getArrivedTime() { return arrivedTime; }
    public void setArrivedTime(Date value) { arrivedTime = value; }
    public Boolean getFinalizeShipment() { return finalizeShipment; }
    public void setFinalizeShipment(Boolean value) {
        finalizeShipment = value;
    }
    public List<InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest>
            getAllocations() {
        return allocations;
    }
    public void setAllocations(
            List<InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest>
                    value) {
        allocations = value == null ? null : new ArrayList<>(value);
    }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
