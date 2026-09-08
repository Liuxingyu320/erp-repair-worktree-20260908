package com.erp.approval.api.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class LegacyApprovalPage implements Serializable
{
    private static final long serialVersionUID = 1L;
    private List<LegacyApprovalInstanceSummary> rows = new ArrayList<>();
    private long total;

    public List<LegacyApprovalInstanceSummary> getRows() { return rows; }
    public void setRows(List<LegacyApprovalInstanceSummary> value) {
        rows = value == null ? new ArrayList<>() : value;
    }
    public long getTotal() { return total; }
    public void setTotal(long value) { total = value; }
}
