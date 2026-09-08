package com.erp.inventory.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class InvTransferShipmentCreateRequest
{
    @NotNull(message = "发货规划版本不能为空")
    @Pattern(regexp = "[a-f0-9]{64}", message = "发货规划版本格式无效")
    private String planVersion;

    @NotNull(message = "封存修订ID不能为空")
    @Positive(message = "封存修订ID必须为正整数")
    private Long sealedRevisionId;

    @NotNull(message = "发货依据不能为空")
    @Pattern(regexp = "server-recommendation",
            message = "当前只允许接受服务端发货建议")
    private String basis;

    @NotEmpty(message = "发货分配不能为空")
    @Size(max = 10000, message = "单次发货分配不能超过10000条")
    @Valid
    private List<InvTransferShipmentAllocationRequest> allocations =
            new ArrayList<>();

    public String getPlanVersion() { return planVersion; }
    public void setPlanVersion(String value) { planVersion = value; }
    public Long getSealedRevisionId() { return sealedRevisionId; }
    public void setSealedRevisionId(Long value) { sealedRevisionId = value; }
    public String getBasis() { return basis; }
    public void setBasis(String value) { basis = value; }
    public List<InvTransferShipmentAllocationRequest> getAllocations() {
        return allocations;
    }
    public void setAllocations(
            List<InvTransferShipmentAllocationRequest> value) {
        allocations = value == null ? null : new ArrayList<>(value);
    }
}
