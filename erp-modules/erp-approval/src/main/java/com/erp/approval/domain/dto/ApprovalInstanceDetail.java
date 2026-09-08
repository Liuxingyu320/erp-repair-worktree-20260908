package com.erp.approval.domain.dto;

import java.util.ArrayList;
import java.util.List;
import com.erp.approval.domain.ApprovalActionLog;
import com.erp.approval.domain.ApprovalCallbackOutbox;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.domain.ApprovalTask;
import com.erp.approval.domain.ApprovalTaskCandidate;

public class ApprovalInstanceDetail
{
    private ApprovalInstance instance;
    private List<ApprovalTask> tasks = new ArrayList<>();
    private List<ApprovalTaskCandidate> candidates = new ArrayList<>();
    private List<ApprovalActionLog> actions = new ArrayList<>();
    private List<ApprovalCallbackOutbox> callbacks = new ArrayList<>();

    public ApprovalInstance getInstance() { return instance; }
    public void setInstance(ApprovalInstance instance) { this.instance = instance; }
    public List<ApprovalTask> getTasks() { return tasks; }
    public void setTasks(List<ApprovalTask> value) { tasks = value == null ? new ArrayList<>() : value; }
    public List<ApprovalTaskCandidate> getCandidates() { return candidates; }
    public void setCandidates(List<ApprovalTaskCandidate> value) { candidates = value == null ? new ArrayList<>() : value; }
    public List<ApprovalActionLog> getActions() { return actions; }
    public void setActions(List<ApprovalActionLog> value) { actions = value == null ? new ArrayList<>() : value; }
    public List<ApprovalCallbackOutbox> getCallbacks() { return callbacks; }
    public void setCallbacks(List<ApprovalCallbackOutbox> value) { callbacks = value == null ? new ArrayList<>() : value; }
}
