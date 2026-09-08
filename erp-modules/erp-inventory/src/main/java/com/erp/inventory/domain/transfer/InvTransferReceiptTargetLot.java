package com.erp.inventory.domain.transfer;

import java.util.Date;

/** Source or derived target lot fact used by the receipt lock boundary. */
public class InvTransferReceiptTargetLot
{
    private Long lotId;
    private String lotNo;
    private String itemType;
    private Long itemId;
    private Long productId;
    private Long warehouseId;
    private Long sourceLotId;
    private String receiptDisposition;
    private String supplierBatchNo;
    private Date productionDate;
    private Date expiryDate;
    private String qcStatus;
    private String lotStatus;

    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String value) { lotNo = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public Long getSourceLotId() { return sourceLotId; }
    public void setSourceLotId(Long value) { sourceLotId = value; }
    public String getReceiptDisposition() { return receiptDisposition; }
    public void setReceiptDisposition(String value) {
        receiptDisposition = value;
    }
    public String getSupplierBatchNo() { return supplierBatchNo; }
    public void setSupplierBatchNo(String value) { supplierBatchNo = value; }
    public Date getProductionDate() { return productionDate; }
    public void setProductionDate(Date value) { productionDate = value; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date value) { expiryDate = value; }
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String value) { qcStatus = value; }
    public String getLotStatus() { return lotStatus; }
    public void setLotStatus(String value) { lotStatus = value; }
}
