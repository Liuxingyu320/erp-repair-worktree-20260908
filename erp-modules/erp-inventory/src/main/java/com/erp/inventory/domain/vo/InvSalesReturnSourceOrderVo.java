package com.erp.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.beans.BeanUtils;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.InvSalesDetail;

/** Only the new return-source API changes Long serialization; legacy sales JSON is unchanged. */
public class InvSalesReturnSourceOrderVo extends InvSalesOrder
{
    @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getOrderId(){return super.getOrderId();}
    @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getCustomerId(){return super.getCustomerId();}
    @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getShopDeptId(){return super.getShopDeptId();}
    @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getTargetDeptId(){return super.getTargetDeptId();}
    @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantId(){return super.getApplicantId();}
    @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getApplicantDeptId(){return super.getApplicantDeptId();}
    public static InvSalesReturnSourceOrderVo from(InvSalesOrder order)
    {
        InvSalesReturnSourceOrderVo result=new InvSalesReturnSourceOrderVo();BeanUtils.copyProperties(order,result);
        if(order.getDetails()!=null) result.setDetails(order.getDetails().stream().map(row -> {
            InvSalesDetail detail=new Detail();BeanUtils.copyProperties(row,detail);return detail;
        }).toList());
        return result;
    }
    public static class Detail extends InvSalesDetail
    {
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getDetailId(){return super.getDetailId();}
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getOrderId(){return super.getOrderId();}
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getItemId(){return super.getItemId();}
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getProductId(){return super.getProductId();}
        @Override @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class) @JsonSerialize(using=ToStringSerializer.class) public Long getWarehouseId(){return super.getWarehouseId();}
    }
}
