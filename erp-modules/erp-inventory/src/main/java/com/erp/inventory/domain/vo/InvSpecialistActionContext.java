package com.erp.inventory.domain.vo;

import java.math.BigDecimal;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvPurchaseReturn;
import com.erp.inventory.domain.InvSalesReturn;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Explicit minimal action header. Never a mutable draft or a full detail projection. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InvSpecialistActionContext
{
    private String orderId;
    private String returnId;
    private String orderNo;
    private String returnNo;
    private String status;
    private String shopDeptId;
    private String qcStatus;
    private BigDecimal receivedQuantity;
    private BigDecimal remainingQuantity;
    public static InvSpecialistActionContext purchase(InvPurchaseOrder row)
    {
        var result=new InvSpecialistActionContext();result.orderId=id(row.getOrderId());result.orderNo=row.getOrderNo();
        result.status=row.getStatus();result.shopDeptId=id(row.getShopDeptId());result.qcStatus=row.getQcStatus();
        result.receivedQuantity=row.getReceivedQuantity();result.remainingQuantity=row.getRemainingQuantity();return result;
    }
    public static InvSpecialistActionContext purchaseReturn(InvPurchaseReturn row)
    {
        var result=new InvSpecialistActionContext();result.returnId=id(row.getReturnId());result.returnNo=row.getReturnNo();
        result.status=row.getStatus();result.shopDeptId=id(row.getShopDeptId());return result;
    }
    public static InvSpecialistActionContext salesReturn(InvSalesReturn row)
    {
        var result=new InvSpecialistActionContext();result.returnId=id(row.getReturnId());result.returnNo=row.getReturnNo();
        result.status=row.getStatus();result.shopDeptId=id(row.getShopDeptId());return result;
    }
    private static String id(Long value){return value==null?null:value.toString();}
    public String getOrderId(){return orderId;} public String getReturnId(){return returnId;}
    public String getOrderNo(){return orderNo;} public String getReturnNo(){return returnNo;}
    public String getStatus(){return status;} public String getShopDeptId(){return shopDeptId;}
    public String getQcStatus(){return qcStatus;} public BigDecimal getReceivedQuantity(){return receivedQuantity;}
    public BigDecimal getRemainingQuantity(){return remainingQuantity;}
    @JsonProperty("_specialistSummaryOnly") public boolean isSpecialistSummaryOnly(){return true;}
}
