package com.erp.approval.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.approval.domain.ApprovalValidationIssue;
import com.erp.approval.domain.ApprovalValidationRun;

public interface ApprovalValidationMapper
{
    List<ApprovalValidationRun> selectValidationRuns(
            ApprovalValidationRun filter);

    ApprovalValidationRun selectValidationRunById(Long runId);

    List<ApprovalValidationIssue> selectIssuesByRunId(Long runId);

    int insertValidationRun(ApprovalValidationRun run);

    int updateValidationRunResult(
            @Param("run") ApprovalValidationRun run);

    int insertValidationIssue(ApprovalValidationIssue issue);
}
