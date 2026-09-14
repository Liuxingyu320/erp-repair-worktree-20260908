package com.erp.inventory.domain.vo;

import org.springframework.beans.BeanUtils;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.erp.inventory.domain.*;

/** Precise identities for new purpose reads only; old domain JSON stays compatible. */
public final class InvSpecialistReadVo
{
    private InvSpecialistReadVo() {}
    public static InvPurchaseOrder purchase(InvPurchaseOrder source)
    {
        InvPurchaseOrder result = new InvPurchaseOrderView(); BeanUtils.copyProperties(source, result);
        if (source.getDetails() != null) result.setDetails(source.getDetails().stream().map(row -> {
            InvPurchaseDetail copy = new InvPurchaseDetailView(); BeanUtils.copyProperties(row, copy); return copy;
        }).toList());
        return result;
    }
    public static InvPurchaseReturn purchaseReturn(InvPurchaseReturn source)
    {
        InvPurchaseReturn result = new InvPurchaseReturnView(); BeanUtils.copyProperties(source, result);
        if (source.getDetails() != null) result.setDetails(source.getDetails().stream().map(row -> {
            InvPurchaseReturnDetail copy = new InvPurchaseReturnDetailView(); BeanUtils.copyProperties(row, copy); return copy;
        }).toList());
        return result;
    }
    public static InvSalesReturn salesReturn(InvSalesReturn source)
    {
        InvSalesReturn result = new InvSalesReturnView(); BeanUtils.copyProperties(source, result);
        if (source.getDetails() != null) result.setDetails(source.getDetails().stream().map(row -> {
            InvSalesReturnDetail copy = new InvSalesReturnDetailView(); BeanUtils.copyProperties(row, copy); return copy;
        }).toList());
        return result;
    }
    public static class InvPurchaseOrderView extends InvPurchaseOrder
    {
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getOrderId() { return super.getOrderId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getSupplierId() { return super.getSupplierId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getShopDeptId() { return super.getShopDeptId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantId() { return super.getApplicantId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantDeptId() { return super.getApplicantDeptId(); }
    }
    public static class InvPurchaseDetailView extends InvPurchaseDetail
    {
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getDetailId() { return super.getDetailId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getOrderId() { return super.getOrderId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getItemId() { return super.getItemId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getProductId() { return super.getProductId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getWarehouseId() { return super.getWarehouseId(); }
    }
    public static class InvPurchaseReturnView extends InvPurchaseReturn
    {
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getReturnId() { return super.getReturnId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getPurchaseOrderId() { return super.getPurchaseOrderId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getShopDeptId() { return super.getShopDeptId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantId() { return super.getApplicantId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantDeptId() { return super.getApplicantDeptId(); }
    }
    public static class InvPurchaseReturnDetailView extends InvPurchaseReturnDetail
    {
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getDetailId() { return super.getDetailId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getReturnId() { return super.getReturnId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getPurchaseDetailId() { return super.getPurchaseDetailId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getItemId() { return super.getItemId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getProductId() { return super.getProductId(); }
    }
    public static class InvSalesReturnView extends InvSalesReturn
    {
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getReturnId() { return super.getReturnId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getSalesOrderId() { return super.getSalesOrderId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getShopDeptId() { return super.getShopDeptId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantId() { return super.getApplicantId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantDeptId() { return super.getApplicantDeptId(); }
    }
    public static class InvSalesReturnDetailView extends InvSalesReturnDetail
    {
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getDetailId() { return super.getDetailId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getReturnId() { return super.getReturnId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getSalesDetailId() { return super.getSalesDetailId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getItemId() { return super.getItemId(); }
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getProductId() { return super.getProductId(); }
    }
}
