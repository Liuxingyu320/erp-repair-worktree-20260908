package com.erp.approval.api.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class LegacyApprovalDetail implements Serializable
{
    private static final long serialVersionUID = 1L;
    private LegacyApprovalInstanceSummary instance;
    private List<LegacyApprovalTaskSummary> tasks = new ArrayList<>();

    public LegacyApprovalInstanceSummary getInstance() { return instance; }
    public void setInstance(LegacyApprovalInstanceSummary value) { instance = value; }
    public List<LegacyApprovalTaskSummary> getTasks() { return tasks; }
    public void setTasks(List<LegacyApprovalTaskSummary> value) {
        tasks = value == null ? new ArrayList<>() : value;
    }
}
