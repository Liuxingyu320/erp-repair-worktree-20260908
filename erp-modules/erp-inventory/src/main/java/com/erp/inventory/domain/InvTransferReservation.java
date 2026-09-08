package com.erp.inventory.domain;

import java.math.BigDecimal;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * Per-transfer ownership record for product-level stock reservations.
 *
 * <p>The stock row stores the aggregate locked quantity. This ledger keeps
 * ownership attributable to one immutable transfer detail so release and
 * shipment never consume another command's reservation.</p>
 */
public class InvTransferReservation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long reservationId;
    private Long transferId;
    private Long transferDetailId;
    private Integer reservationRound;
    private Long stockId;
    private String itemType;
    private Long itemId;
    private Long sourceLocationDeptId;
    private BigDecimal reservedQuantity;
    private BigDecimal consumedQuantity;
    private BigDecimal releasedQuantity;
    private String status;
    private Long version;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long value) { reservationId = value; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long value) { transferDetailId = value; }
    public Integer getReservationRound() { return reservationRound; }
    public void setReservationRound(Integer value) { reservationRound = value; }
    public Long getStockId() { return stockId; }
    public void setStockId(Long value) { stockId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getSourceLocationDeptId() { return sourceLocationDeptId; }
    public void setSourceLocationDeptId(Long value) { sourceLocationDeptId = value; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal value) { reservedQuantity = value; }
    public BigDecimal getConsumedQuantity() { return consumedQuantity; }
    public void setConsumedQuantity(BigDecimal value) { consumedQuantity = value; }
    public BigDecimal getReleasedQuantity() { return releasedQuantity; }
    public void setReleasedQuantity(BigDecimal value) { releasedQuantity = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
