package com.erp.inventory.domain.transfer;

/** Unique target lot-location dimension used for deterministic locking. */
public record InvTransferReceiptBalanceKey(Long lotId, Long locationId)
{
}
