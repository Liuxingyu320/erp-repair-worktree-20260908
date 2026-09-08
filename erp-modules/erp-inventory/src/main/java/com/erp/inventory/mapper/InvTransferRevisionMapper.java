package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvTransferRevision;

public interface InvTransferRevisionMapper
{
    InvTransferRevision selectLatestByTransferIdForUpdate(
            @Param("transferId") Long transferId);

    InvTransferRevision selectByTransferRoundForUpdate(
            @Param("transferId") Long transferId,
            @Param("approvalRound") Integer approvalRound);

    List<InvTransferRevision> selectByTransferId(
            @Param("transferId") Long transferId);

    int insertRevision(InvTransferRevision revision);

    int updateDraftSnapshot(@Param("revisionId") Long revisionId,
            @Param("headerSnapshot") String headerSnapshot,
            @Param("detailSnapshot") String detailSnapshot,
            @Param("snapshotHash") String snapshotHash,
            @Param("username") String username);

    int sealDraft(@Param("revisionId") Long revisionId,
            @Param("approvalRound") Integer approvalRound,
            @Param("approvalInstanceId") Long approvalInstanceId,
            @Param("targetStatus") String targetStatus,
            @Param("headerSnapshot") String headerSnapshot,
            @Param("detailSnapshot") String detailSnapshot,
            @Param("snapshotHash") String snapshotHash,
            @Param("decisionAction") String decisionAction,
            @Param("decisionUserId") Long decisionUserId,
            @Param("decisionUsername") String decisionUsername,
            @Param("username") String username);

    int transitionRevision(@Param("revisionId") Long revisionId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("approvalInstanceId") Long approvalInstanceId,
            @Param("decisionAction") String decisionAction,
            @Param("decisionReason") String decisionReason,
            @Param("decisionUserId") Long decisionUserId,
            @Param("decisionUsername") String decisionUsername,
            @Param("username") String username);

    int countSealedByTransferId(@Param("transferId") Long transferId);

    int deleteDraftByTransferId(@Param("transferId") Long transferId);
}
