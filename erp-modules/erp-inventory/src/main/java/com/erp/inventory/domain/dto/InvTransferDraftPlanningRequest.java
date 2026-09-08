package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Read-only input used to preview the authoritative stock gate for a draft. */
public class InvTransferDraftPlanningRequest
{
    @NotBlank(message = "调拨类型不能为空")
    @Pattern(regexp = "warehouse|store_return|cross_store",
            message = "调拨类型不受支持")
    private String transferType;

    @NotNull(message = "调出组织不能为空")
    @Positive(message = "调出组织必须为正整数")
    private Long fromDeptId;

    @NotNull(message = "调入组织不能为空")
    @Positive(message = "调入组织必须为正整数")
    private Long toDeptId;

    @Size(max = 64, message = "返仓原因编码不能超过64个字符")
    private String returnReasonCode;

    @Size(max = 500, message = "返仓原因说明不能超过500个字符")
    private String returnReasonText;

    @NotEmpty(message = "调拨明细不能为空")
    @Size(max = 10000, message = "调拨明细不能超过10000条")
    @Valid
    private List<Line> details = new ArrayList<>();

    public String getTransferType() { return transferType; }
    public void setTransferType(String value) { transferType = value; }
    public Long getFromDeptId() { return fromDeptId; }
    public void setFromDeptId(Long value) { fromDeptId = value; }
    public Long getToDeptId() { return toDeptId; }
    public void setToDeptId(Long value) { toDeptId = value; }
    public String getReturnReasonCode() { return returnReasonCode; }
    public void setReturnReasonCode(String value) { returnReasonCode = value; }
    public String getReturnReasonText() { return returnReasonText; }
    public void setReturnReasonText(String value) { returnReasonText = value; }
    public List<Line> getDetails() { return details; }
    public void setDetails(List<Line> value) {
        details = value == null ? null : new ArrayList<>(value);
    }

    public static class Line
    {
        @NotBlank(message = "物料类型不能为空")
        @Pattern(regexp = "product|oe|gift", message = "物料类型不受支持")
        private String itemType;

        @NotNull(message = "物料标识不能为空")
        @Positive(message = "物料标识必须为正整数")
        private Long itemId;

        @Positive(message = "商品标识必须为正整数")
        private Long productId;

        @NotNull(message = "申请数量不能为空")
        @DecimalMin(value = "0", inclusive = false,
                message = "申请数量必须大于0")
        @Digits(integer = 14, fraction = 4,
                message = "申请数量最多14位整数和4位小数")
        private BigDecimal quantity;

        public String getItemType() { return itemType; }
        public void setItemType(String value) { itemType = value; }
        public Long getItemId() { return itemId; }
        public void setItemId(Long value) { itemId = value; }
        public Long getProductId() { return productId; }
        public void setProductId(Long value) { productId = value; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal value) { quantity = value; }
    }
}
