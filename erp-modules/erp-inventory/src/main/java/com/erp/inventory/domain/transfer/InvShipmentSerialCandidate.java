package com.erp.inventory.domain.transfer;

public class InvShipmentSerialCandidate
{
    private Long serialId;
    private Long balanceId;
    private String serialNo;
    private String serialStatus;

    public Long getSerialId() { return serialId; }
    public void setSerialId(Long value) { serialId = value; }
    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long value) { balanceId = value; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String value) { serialNo = value; }
    public String getSerialStatus() { return serialStatus; }
    public void setSerialStatus(String value) { serialStatus = value; }
}
