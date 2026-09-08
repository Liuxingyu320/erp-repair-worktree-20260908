package com.erp.inventory.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class MobileTransferApprovalTodo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long transferId;
    private String orderNo;
    private Long fromDeptId;
    private String fromDeptName;
    private Long toDeptId;
    private String toDeptName;
    private String nodeName;
    private String status;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private String transferType;
    private Date submittedTime;
    private Date createTime;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getFromDeptId() { return fromDeptId; }
    public void setFromDeptId(Long fromDeptId) { this.fromDeptId = fromDeptId; }
    public String getFromDeptName() { return fromDeptName; }
    public void setFromDeptName(String fromDeptName) { this.fromDeptName = fromDeptName; }
    public Long getToDeptId() { return toDeptId; }
    public void setToDeptId(Long toDeptId) { this.toDeptId = toDeptId; }
    public String getToDeptName() { return toDeptName; }
    public void setToDeptName(String toDeptName) { this.toDeptName = toDeptName; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date submittedTime) { this.submittedTime = submittedTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
