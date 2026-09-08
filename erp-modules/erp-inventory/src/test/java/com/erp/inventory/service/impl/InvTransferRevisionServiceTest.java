package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferRevisionMapper;

@DisplayName("调拨不可变业务版本")
class InvTransferRevisionServiceTest
{
    @Test
    @DisplayName("草稿快照按明细稳定排序并规范化小数")
    void shouldProduceCanonicalDraftSnapshot()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        order.setTotalQuantity(new BigDecimal("3.000"));

        service.syncDraft(order, List.of(
                detail(12L, "2.00", 2),
                detail(11L, "1.0", 1)), "maker");
        String firstHash = revisionMapper.latest().getSnapshotHash();

        order.setTotalQuantity(new BigDecimal("3.0"));
        service.syncDraft(order, List.of(
                detail(11L, "1.0000", 1),
                detail(12L, "2.0", 2)), "maker");

        InvTransferRevision draft = revisionMapper.latest();
        assertThat(draft.getSnapshotHash()).isEqualTo(firstHash);
        assertThat(draft.getDetailSnapshot().indexOf("\"detailId\":11"))
                .isLessThan(draft.getDetailSnapshot()
                        .indexOf("\"detailId\":12"));
        assertThat(draft.getDetailSnapshot())
                .contains("\"quantity\":\"1\"")
                .doesNotContain("1.0000");
    }

    @Test
    @DisplayName("驳回封存原提交内容并派生可编辑子草稿")
    void shouldPreserveRejectedSubmissionAndCreateChildDraft()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        List<InvTransferDetail> submittedDetails =
                List.of(detail(11L, "5", 1));
        detailMapper.rows = new ArrayList<>(submittedDetails);

        service.syncDraft(order, submittedDetails, "maker");
        service.sealForSubmission(order, submittedDetails, 1, 700L,
                false, 1L, "maker");
        InvTransferRevision submitted = revisionMapper.latest();
        String submittedSnapshot = submitted.getDetailSnapshot();
        String submittedHash = submitted.getSnapshotHash();
        order.setApprovalRound(1);
        order.setApprovalInstanceId(700L);

        service.recordApprovalOutcome(order,
                InvTransferRevisionStatuses.REJECTED, "draft", "reject",
                "数量需要调整", 9L, "approver", 700L);

        assertThat(revisionMapper.rows).hasSize(2);
        InvTransferRevision rejected = revisionMapper.rows.get(0);
        InvTransferRevision child = revisionMapper.rows.get(1);
        assertThat(rejected.getStatus())
                .isEqualTo(InvTransferRevisionStatuses.REJECTED);
        assertThat(rejected.getDetailSnapshot()).isEqualTo(submittedSnapshot);
        assertThat(rejected.getSnapshotHash()).isEqualTo(submittedHash);
        assertThat(child.getStatus())
                .isEqualTo(InvTransferRevisionStatuses.DRAFT);
        assertThat(child.getParentRevisionId())
                .isEqualTo(rejected.getRevisionId());
        assertThat(child.getApprovalRound()).isNull();

        order.setTotalQuantity(new BigDecimal("7"));
        detailMapper.rows = new ArrayList<>(
                List.of(detail(11L, "7", 1)));
        service.syncDraft(order, detailMapper.rows, "maker");

        assertThat(rejected.getDetailSnapshot()).isEqualTo(submittedSnapshot);
        assertThat(rejected.getSnapshotHash()).isEqualTo(submittedHash);
        assertThat(child.getDetailSnapshot())
                .contains("\"quantity\":\"7\"");
    }

    @Test
    @DisplayName("审批通过只结束当前封存版本且不新建草稿")
    void shouldApproveWithoutCreatingDraft()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        List<InvTransferDetail> details = List.of(detail(11L, "5", 1));
        detailMapper.rows = new ArrayList<>(details);
        service.syncDraft(order, details, "maker");
        service.sealForSubmission(order, details, 1, 700L, false, 1L,
                "maker");
        order.setApprovalRound(1);

        service.recordApprovalOutcome(order,
                InvTransferRevisionStatuses.APPROVED, "approved", "approve",
                "同意", 9L, "approver", 700L);

        assertThat(revisionMapper.rows).hasSize(1);
        assertThat(revisionMapper.latest().getStatus())
                .isEqualTo(InvTransferRevisionStatuses.APPROVED);
        assertThat(revisionMapper.latest().getDecisionAction())
                .isEqualTo("APPROVE");
    }

    @Test
    @DisplayName("历史审批单缺少版本时按当前轮次补录封存版本")
    void shouldBackfillLegacySubmissionBeforeOutcome()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        order.setApprovalRound(2);
        order.setApprovalInstanceId(701L);
        detailMapper.rows = new ArrayList<>(
                List.of(detail(11L, "5", 1)));

        service.recordApprovalOutcome(order,
                InvTransferRevisionStatuses.RETURNED, "draft", "returned",
                "补充附件", 9L, "approver", 701L);

        assertThat(revisionMapper.rows).hasSize(2);
        InvTransferRevision sealed = revisionMapper.rows.get(0);
        assertThat(sealed.getApprovalRound()).isEqualTo(2);
        assertThat(sealed.getApprovalInstanceId()).isEqualTo(701L);
        assertThat(sealed.getStatus())
                .isEqualTo(InvTransferRevisionStatuses.RETURNED);
        assertThat(revisionMapper.rows.get(1).getParentRevisionId())
                .isEqualTo(sealed.getRevisionId());
    }

    @Test
    @DisplayName("只要存在封存历史就拒绝物理删除主单")
    void shouldRejectPhysicalDeleteAfterSubmission()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        List<InvTransferDetail> details = List.of(detail(11L, "5", 1));
        service.syncDraft(order, details, "maker");
        service.sealForSubmission(order, details, 1, 700L, false, 1L,
                "maker");

        assertThatThrownBy(() -> service
                .deleteDraftForPhysicalDelete(order.getTransferId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("禁止物理删除");
        assertThat(revisionMapper.rows).hasSize(1);
    }

    @Test
    @DisplayName("空明细草稿可以取消且不冒充历史审批轮次")
    void shouldCancelEmptyDraftWithoutApprovalRound()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        order.setStatus(InvStatusConstants.DRAFT);
        order.setApprovalRound(3);
        order.setApprovalInstanceId(703L);

        service.recordCancellation(order, "maker");

        assertThat(revisionMapper.rows).hasSize(1);
        InvTransferRevision cancelled = revisionMapper.latest();
        assertThat(cancelled.getStatus())
                .isEqualTo(InvTransferRevisionStatuses.CANCELLED);
        assertThat(cancelled.getApprovalRound()).isNull();
        assertThat(cancelled.getApprovalInstanceId()).isNull();
        assertThat(cancelled.getDetailSnapshot()).isEqualTo("[]");
    }

    @Test
    @DisplayName("历史读取校验哈希并返回强类型业务快照")
    void shouldReadVerifiedTypedHistory()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        List<InvTransferDetail> details = List.of(detail(11L, "5.00", 1));
        service.syncDraft(order, details, "maker");

        InvTransferRevisionHistoryVo history = service.getHistory(order);

        assertThat(history.getTransferId()).isEqualTo(900L);
        assertThat(history.getOrderNo()).isEqualTo("TF202608020001");
        assertThat(history.getCurrentRevisionNo()).isEqualTo(1);
        assertThat(history.getCurrentRevisionStatus())
                .isEqualTo(InvTransferRevisionStatuses.DRAFT);
        assertThat(history.getRevisions()).hasSize(1);
        assertThat(history.getRevisions().get(0).getHeader()
                .getFromDeptName()).isEqualTo("总仓");
        assertThat(history.getRevisions().get(0).getDetails())
                .extracting(item -> item.getQuantity())
                .containsExactly("5");
    }

    @Test
    @DisplayName("历史读取拒绝被篡改的快照和断裂父版本")
    void shouldRejectTamperedOrBrokenHistory()
    {
        FakeRevisionMapper revisionMapper = new FakeRevisionMapper();
        FakeDetailMapper detailMapper = new FakeDetailMapper();
        InvTransferRevisionService service = service(revisionMapper,
                detailMapper);
        InvTransferOrder order = order();
        List<InvTransferDetail> details = List.of(detail(11L, "5", 1));
        detailMapper.rows = new ArrayList<>(details);
        service.syncDraft(order, details, "maker");
        InvTransferRevision draft = revisionMapper.latest();
        draft.setDetailSnapshot("[]");

        assertThatThrownBy(() -> service.getHistory(order))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("完整性校验失败");

        revisionMapper.rows.clear();
        service.syncDraft(order, details, "maker");
        service.sealForSubmission(order, details, 1, 700L, false, 1L,
                "maker");
        order.setApprovalRound(1);
        service.recordApprovalOutcome(order,
                InvTransferRevisionStatuses.REJECTED, "draft", "reject",
                "退回", 9L, "approver", 700L);
        revisionMapper.latest().setParentRevisionId(999L);

        assertThatThrownBy(() -> service.getHistory(order))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("父子关系校验失败");

        revisionMapper.latest().setParentRevisionId(null);
        assertThatThrownBy(() -> service.getHistory(order))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("父子关系校验失败");
    }

    private static InvTransferRevisionService service(
            FakeRevisionMapper revisionMapper,
            FakeDetailMapper detailMapper)
    {
        return new InvTransferRevisionService(revisionMapper, detailMapper,
                new ObjectMapper());
    }

    private static InvTransferOrder order()
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setTransferId(900L);
        order.setOrderNo("TF202608020001");
        order.setFromDeptId(201L);
        order.setFromDeptName("总仓");
        order.setFromWarehouseId(301L);
        order.setToDeptId(202L);
        order.setToDeptName("门店");
        order.setToWarehouseId(302L);
        order.setTransferType("warehouse");
        order.setTotalQuantity(new BigDecimal("5"));
        order.setTotalAmount(new BigDecimal("10"));
        return order;
    }

    private static InvTransferDetail detail(Long detailId, String quantity,
            int sortOrder)
    {
        InvTransferDetail detail = new InvTransferDetail();
        detail.setDetailId(detailId);
        detail.setTransferId(900L);
        detail.setItemType("product");
        detail.setItemId(1000L + detailId);
        detail.setItemCode("P-" + detailId);
        detail.setItemName("商品" + detailId);
        detail.setProductId(1000L + detailId);
        detail.setProductCode("P-" + detailId);
        detail.setProductName("商品" + detailId);
        detail.setQuantity(new BigDecimal(quantity));
        detail.setCostPrice(new BigDecimal("2.00"));
        detail.setAmount(new BigDecimal(quantity)
                .multiply(new BigDecimal("2")));
        detail.setSortOrder(sortOrder);
        detail.setUnit("件");
        return detail;
    }

    private static final class FakeRevisionMapper
            implements InvTransferRevisionMapper
    {
        private final List<InvTransferRevision> rows = new ArrayList<>();
        private long nextId = 1L;

        InvTransferRevision latest()
        {
            return rows.stream().max(Comparator.comparing(
                    InvTransferRevision::getRevisionNo)).orElse(null);
        }

        @Override
        public InvTransferRevision selectLatestByTransferIdForUpdate(
                Long transferId)
        {
            return rows.stream()
                    .filter(row -> transferId.equals(row.getTransferId()))
                    .max(Comparator.comparing(
                            InvTransferRevision::getRevisionNo))
                    .orElse(null);
        }

        @Override
        public InvTransferRevision selectByTransferRoundForUpdate(
                Long transferId, Integer approvalRound)
        {
            return rows.stream()
                    .filter(row -> transferId.equals(row.getTransferId()))
                    .filter(row -> approvalRound.equals(
                            row.getApprovalRound()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<InvTransferRevision> selectByTransferId(Long transferId)
        {
            return rows.stream()
                    .filter(row -> transferId.equals(row.getTransferId()))
                    .sorted(Comparator.comparing(
                            InvTransferRevision::getRevisionNo)).toList();
        }

        @Override
        public int insertRevision(InvTransferRevision revision)
        {
            revision.setRevisionId(nextId++);
            rows.add(revision);
            return 1;
        }

        @Override
        public int updateDraftSnapshot(Long revisionId,
                String headerSnapshot, String detailSnapshot,
                String snapshotHash, String username)
        {
            InvTransferRevision revision = byId(revisionId);
            if (revision == null || !InvTransferRevisionStatuses.DRAFT
                    .equals(revision.getStatus()))
            {
                return 0;
            }
            revision.setHeaderSnapshot(headerSnapshot);
            revision.setDetailSnapshot(detailSnapshot);
            revision.setSnapshotHash(snapshotHash);
            revision.setUpdateBy(username);
            return 1;
        }

        @Override
        public int sealDraft(Long revisionId, Integer approvalRound,
                Long approvalInstanceId, String targetStatus,
                String headerSnapshot, String detailSnapshot,
                String snapshotHash, String decisionAction,
                Long decisionUserId, String decisionUsername,
                String username)
        {
            InvTransferRevision revision = byId(revisionId);
            if (revision == null || !InvTransferRevisionStatuses.DRAFT
                    .equals(revision.getStatus()))
            {
                return 0;
            }
            revision.setApprovalRound(approvalRound);
            revision.setApprovalInstanceId(approvalInstanceId);
            revision.setStatus(targetStatus);
            revision.setHeaderSnapshot(headerSnapshot);
            revision.setDetailSnapshot(detailSnapshot);
            revision.setSnapshotHash(snapshotHash);
            revision.setDecisionAction(decisionAction);
            revision.setDecisionUserId(decisionUserId);
            revision.setDecisionUsername(decisionUsername);
            revision.setUpdateBy(username);
            return 1;
        }

        @Override
        public int transitionRevision(Long revisionId, String expectedStatus,
                String targetStatus, Long approvalInstanceId,
                String decisionAction, String decisionReason,
                Long decisionUserId, String decisionUsername,
                String username)
        {
            InvTransferRevision revision = byId(revisionId);
            if (revision == null
                    || !expectedStatus.equals(revision.getStatus()))
            {
                return 0;
            }
            revision.setStatus(targetStatus);
            if (revision.getApprovalInstanceId() == null)
            {
                revision.setApprovalInstanceId(approvalInstanceId);
            }
            revision.setDecisionAction(decisionAction);
            revision.setDecisionReason(decisionReason);
            revision.setDecisionUserId(decisionUserId);
            revision.setDecisionUsername(decisionUsername);
            revision.setUpdateBy(username);
            return 1;
        }

        @Override
        public int countSealedByTransferId(Long transferId)
        {
            return (int) rows.stream()
                    .filter(row -> transferId.equals(row.getTransferId()))
                    .filter(row -> !InvTransferRevisionStatuses.DRAFT
                            .equals(row.getStatus())).count();
        }

        @Override
        public int deleteDraftByTransferId(Long transferId)
        {
            int before = rows.size();
            rows.removeIf(row -> transferId.equals(row.getTransferId())
                    && InvTransferRevisionStatuses.DRAFT
                            .equals(row.getStatus()));
            return before - rows.size();
        }

        private InvTransferRevision byId(Long revisionId)
        {
            return rows.stream()
                    .filter(row -> revisionId.equals(row.getRevisionId()))
                    .findFirst().orElse(null);
        }
    }

    private static final class FakeDetailMapper
            implements InvTransferDetailMapper
    {
        private List<InvTransferDetail> rows = new ArrayList<>();

        @Override
        public List<InvTransferDetail> selectByTransferId(Long transferId)
        {
            return rows;
        }

        @Override
        public List<InvTransferDetail> selectByTransferIdForUpdate(
                Long transferId)
        {
            return rows;
        }

        @Override
        public int insertInvTransferDetail(InvTransferDetail detail)
        {
            rows.add(detail);
            return 1;
        }

        @Override
        public int batchInsertInvTransferDetail(
                List<InvTransferDetail> details)
        {
            rows.addAll(details);
            return details.size();
        }

        @Override
        public int updateDeliveredQuantity(InvTransferDetail detail)
        {
            return 1;
        }

        @Override
        public int decreaseDeliveredQuantity(InvTransferDetail detail)
        {
            return 1;
        }

        @Override
        public int updateReceivedQuantity(InvTransferDetail detail)
        {
            return 1;
        }

        @Override
        public int deleteByTransferId(Long transferId)
        {
            int size = rows.size();
            rows.clear();
            return size;
        }
    }
}
