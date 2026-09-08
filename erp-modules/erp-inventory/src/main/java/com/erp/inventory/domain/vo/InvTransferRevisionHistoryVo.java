package com.erp.inventory.domain.vo;

import java.util.ArrayList;
import java.util.List;

public class InvTransferRevisionHistoryVo
{
    private Long transferId;
    private String orderNo;
    private Integer currentRevisionNo;
    private String currentRevisionStatus;
    private List<InvTransferRevisionVo> revisions = new ArrayList<>();

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String value) { orderNo = value; }
    public Integer getCurrentRevisionNo() { return currentRevisionNo; }
    public void setCurrentRevisionNo(Integer value) { currentRevisionNo = value; }
    public String getCurrentRevisionStatus() { return currentRevisionStatus; }
    public void setCurrentRevisionStatus(String value) { currentRevisionStatus = value; }
    public List<InvTransferRevisionVo> getRevisions() { return revisions; }
    public void setRevisions(List<InvTransferRevisionVo> value) {
        revisions = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
