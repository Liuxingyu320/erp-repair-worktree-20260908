package com.erp.inventory.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Only missing warehouse assignments may be repaired; commercial fields are not accepted. */
public class InvSalesWarehouseRepairRequest
{
    @NotNull private Long version;
    @NotEmpty @Valid private List<Assignment> assignments;
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<Assignment> getAssignments() { return assignments; }
    public void setAssignments(List<Assignment> assignments) { this.assignments = assignments; }
    public static class Assignment
    {
        @NotNull @Positive private Long detailId;
        @NotNull @Positive private Long warehouseId;
        public Long getDetailId() { return detailId; }
        public void setDetailId(Long detailId) { this.detailId = detailId; }
        public Long getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    }
}
