package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferApprovalTask;
import com.erp.inventory.domain.vo.InvTransferApprovalSummary;

public interface InvTransferApprovalTaskMapper
{
    int insertTask(InvTransferApprovalTask task);
    InvTransferApprovalTask selectTaskByIdForUpdate(@Param("taskId") Long taskId);
    List<InvTransferApprovalTask> selectPendingTasksByInstanceAndNodeForUpdate(
            @Param("instanceId") Long instanceId, @Param("nodeOrder") Integer nodeOrder);
    List<InvTransferApprovalTask> selectTasksByInstanceAndNode(
            @Param("instanceId") Long instanceId, @Param("nodeOrder") Integer nodeOrder);
    List<InvTransferApprovalTask> selectTasksByInstanceId(@Param("instanceId") Long instanceId);
    List<InvTransferApprovalTask> selectTasksByInstanceIds(@Param("instanceIds") List<Long> instanceIds);
    List<InvTransferApprovalSummary> selectApprovalSummariesByTransferIds(@Param("transferIds") List<Long> transferIds);
    int skipPendingTasksByTransferId(@Param("transferId") Long transferId,
            @Param("updateBy") String updateBy, @Param("reason") String reason);
    int updateTaskApproval(InvTransferApprovalTask task);
}
