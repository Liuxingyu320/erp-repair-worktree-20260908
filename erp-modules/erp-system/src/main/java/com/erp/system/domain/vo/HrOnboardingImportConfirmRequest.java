package com.erp.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

public class HrOnboardingImportConfirmRequest
{
    private Integer version;
    private List<RowDecision> rows = new ArrayList<>();
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<RowDecision> getRows() { return rows; }
    public void setRows(List<RowDecision> rows) { this.rows = rows; }

    public static class RowDecision
    {
        private Long rowId;
        private String decision;
        private Long bindUserId;
        public Long getRowId() { return rowId; }
        public void setRowId(Long rowId) { this.rowId = rowId; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public Long getBindUserId() { return bindUserId; }
        public void setBindUserId(Long bindUserId) { this.bindUserId = bindUserId; }
    }
}
