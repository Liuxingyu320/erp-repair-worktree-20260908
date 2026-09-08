package com.erp.inventory.domain.vo;

public class InvTransferSourceConfirmResult
{
    private Long transferId;
    private String sourceConfirmStatus;
    private Long reselectionTransferId;
    private String reselectionOrderNo;

    public Long getTransferId()
    {
        return transferId;
    }

    public void setTransferId(Long transferId)
    {
        this.transferId = transferId;
    }

    public String getSourceConfirmStatus()
    {
        return sourceConfirmStatus;
    }

    public void setSourceConfirmStatus(String sourceConfirmStatus)
    {
        this.sourceConfirmStatus = sourceConfirmStatus;
    }

    public Long getReselectionTransferId()
    {
        return reselectionTransferId;
    }

    public void setReselectionTransferId(Long reselectionTransferId)
    {
        this.reselectionTransferId = reselectionTransferId;
    }

    public String getReselectionOrderNo()
    {
        return reselectionOrderNo;
    }

    public void setReselectionOrderNo(String reselectionOrderNo)
    {
        this.reselectionOrderNo = reselectionOrderNo;
    }
}
