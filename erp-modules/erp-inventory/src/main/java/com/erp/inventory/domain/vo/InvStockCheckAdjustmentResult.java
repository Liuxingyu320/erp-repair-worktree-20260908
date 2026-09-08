package com.erp.inventory.domain.vo;

import java.util.ArrayList;
import java.util.List;

public class InvStockCheckAdjustmentResult
{
    private final List<InvStockCheckSnapshotChange> snapshotChanges = new ArrayList<>();
    private final List<InvStockCheckAdjustmentEntry> adjustments = new ArrayList<>();

    public List<InvStockCheckSnapshotChange> getSnapshotChanges()
    {
        return snapshotChanges;
    }

    public boolean hasSnapshotChanges()
    {
        return !snapshotChanges.isEmpty();
    }

    public List<InvStockCheckAdjustmentEntry> getAdjustments()
    {
        return adjustments;
    }
}
