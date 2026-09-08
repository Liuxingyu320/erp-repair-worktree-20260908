package com.erp.approval.constant;

/** Closed runtime states persisted by the unified approval engine. */
public final class ApprovalRuntimeConstants
{
    public static final String INSTANCE_RUNNING = "RUNNING";
    public static final String INSTANCE_COMPLETING = "COMPLETING";
    public static final String INSTANCE_RETURNING = "RETURNING";
    public static final String INSTANCE_REJECTING = "REJECTING";
    public static final String INSTANCE_WITHDRAWING = "WITHDRAWING";
    public static final String INSTANCE_TERMINATING = "TERMINATING";
    public static final String INSTANCE_APPROVED = "APPROVED";
    public static final String INSTANCE_RETURNED = "RETURNED";
    public static final String INSTANCE_REJECTED = "REJECTED";
    public static final String INSTANCE_WITHDRAWN = "WITHDRAWN";
    public static final String INSTANCE_TERMINATED = "TERMINATED";
    public static final String INSTANCE_INVALIDATED = "INVALIDATED";

    public static final String TASK_WAITING = "WAITING";
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_APPROVED = "APPROVED";
    public static final String TASK_RETURNED = "RETURNED";
    public static final String TASK_REJECTED = "REJECTED";
    public static final String TASK_SKIPPED = "SKIPPED";
    public static final String TASK_CANCELLED = "CANCELLED";
    public static final String TASK_REASSIGNED = "REASSIGNED";

    public static final String CALLBACK_NONE = "NONE";
    public static final String CALLBACK_PENDING = "PENDING";
    public static final String CALLBACK_PROCESSING = "PROCESSING";
    public static final String CALLBACK_RETRY = "RETRY";
    public static final String CALLBACK_SUCCEEDED = "SUCCEEDED";
    public static final String CALLBACK_DEAD = "DEAD";

    public static final String CANDIDATE_PENDING = "PENDING";
    public static final String CANDIDATE_APPROVED = "APPROVED";
    public static final String CANDIDATE_RETURNED = "RETURNED";
    public static final String CANDIDATE_REJECTED = "REJECTED";
    public static final String CANDIDATE_CANCELLED = "CANCELLED";
    public static final String CANDIDATE_REASSIGNED = "REASSIGNED";

    public static final String ACTION_START = "START";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_RETURN = "RETURN";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_WITHDRAW = "WITHDRAW";
    public static final String ACTION_TERMINATE = "TERMINATE";
    public static final String ACTION_REASSIGN = "REASSIGN";
    public static final String ACTION_SKIP = "SKIP";
    public static final String ACTION_CALLBACK = "CALLBACK";
    public static final String ACTION_CALLBACK_REPLAY = "CALLBACK_REPLAY";

    private ApprovalRuntimeConstants()
    {
    }
}
