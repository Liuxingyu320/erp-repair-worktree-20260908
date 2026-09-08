package com.erp.inventory.domain.transfer;

/** Unique dimension for one target accepted or damaged derived lot. */
public record InvTransferReceiptDerivedLotKey(
        Long warehouseId,
        String itemType,
        Long itemId,
        Long sourceLotId,
        String disposition)
{
}
