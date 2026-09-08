package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;
import com.erp.inventory.domain.vo.InvTransferRevisionDetailVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHeaderVo;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferRevisionMapper;

/**
 * Owns the immutable business-content history of a stable transfer order.
 *
 * <p>The current draft is the only mutable revision. Submission seals it.
 * Approval outcomes only append decision metadata, and a back-to-draft
 * outcome creates a child draft instead of reopening or overwriting the
 * submitted content.</p>
 */
@Service
public class InvTransferRevisionService
{
    private static final Comparator<InvTransferDetail> DETAIL_ORDER =
            Comparator.comparing(InvTransferDetail::getSortOrder,
                            Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(InvTransferDetail::getDetailId,
                            Comparator.nullsLast(Long::compareTo))
                    .thenComparing(InvTransferDetail::getItemType,
                            Comparator.nullsLast(String::compareTo))
                    .thenComparing(InvTransferDetail::getItemId,
                            Comparator.nullsLast(Long::compareTo));

    private final InvTransferRevisionMapper revisionMapper;
    private final InvTransferDetailMapper detailMapper;
    private final ObjectMapper canonicalMapper;

    public InvTransferRevisionService(
            InvTransferRevisionMapper revisionMapper,
            InvTransferDetailMapper detailMapper,
            ObjectMapper objectMapper)
    {
        this.revisionMapper = revisionMapper;
        this.detailMapper = detailMapper;
        ObjectMapper mapperCopy = objectMapper.copy();
        mapperCopy.setConfig(mapperCopy.getSerializationConfig()
                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
        mapperCopy.setConfig(mapperCopy.getDeserializationConfig()
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        this.canonicalMapper = mapperCopy;
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public InvTransferRevisionHistoryVo getHistory(InvTransferOrder order)
    {
        requireOrder(order);
        List<InvTransferRevision> rows = revisionMapper
                .selectByTransferId(order.getTransferId());
        List<InvTransferRevisionVo> revisions = new ArrayList<>();
        Set<Long> knownRevisionIds = new HashSet<>();
        int expectedRevisionNo = 1;
        Long expectedParentRevisionId = null;
        if (rows != null)
        {
            for (InvTransferRevision row : rows)
            {
                validateHistoryRow(order, row, expectedRevisionNo,
                        expectedParentRevisionId, knownRevisionIds);
                revisions.add(toHistoryItem(order, row));
                knownRevisionIds.add(row.getRevisionId());
                expectedParentRevisionId = row.getRevisionId();
                expectedRevisionNo++;
            }
        }

        InvTransferRevisionHistoryVo history =
                new InvTransferRevisionHistoryVo();
        history.setTransferId(order.getTransferId());
        history.setOrderNo(order.getOrderNo());
        history.setRevisions(revisions);
        if (!revisions.isEmpty())
        {
            InvTransferRevisionVo current = revisions
                    .get(revisions.size() - 1);
            history.setCurrentRevisionNo(current.getRevisionNo());
            history.setCurrentRevisionStatus(current.getStatus());
        }
        return history;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void syncDraft(InvTransferOrder order,
            List<InvTransferDetail> details, String username)
    {
        requireOrder(order);
        String operator = requireOperator(username);
        Snapshot snapshot = snapshot(order, details, "draft");
        InvTransferRevision latest = revisionMapper
                .selectLatestByTransferIdForUpdate(order.getTransferId());
        if (latest == null)
        {
            insertRevision(order.getTransferId(), 1, null, null, null,
                    InvTransferRevisionStatuses.DRAFT, snapshot, null, null,
                    null, null, operator, null);
            return;
        }
        if (!InvTransferRevisionStatuses.DRAFT.equals(latest.getStatus()))
        {
            throw new ServiceException("调拨单当前不存在可编辑草稿版本");
        }
        if (revisionMapper.updateDraftSnapshot(latest.getRevisionId(),
                snapshot.header(), snapshot.details(), snapshot.hash(),
                operator) != 1)
        {
            throw new ServiceException("调拨草稿版本已变化，请刷新后重试");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void sealForSubmission(InvTransferOrder order,
            List<InvTransferDetail> details, int approvalRound,
            Long approvalInstanceId, boolean autoApproved,
            Long decisionUserId, String username)
    {
        requireOrder(order);
        if (approvalRound <= 0)
        {
            throw new ServiceException("调拨审批轮次无效");
        }
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("调拨业务版本缺少明细");
        }
        String operator = requireOperator(username);
        Snapshot snapshot = snapshot(order, details, "submitted");
        InvTransferRevision latest = revisionMapper
                .selectLatestByTransferIdForUpdate(order.getTransferId());
        if (latest == null)
        {
            latest = insertRevision(order.getTransferId(), 1, null, null,
                    null, InvTransferRevisionStatuses.DRAFT, snapshot, null,
                    null, null, null, operator, null);
        }
        if (!InvTransferRevisionStatuses.DRAFT.equals(latest.getStatus()))
        {
            throw new ServiceException("调拨提交版本已封存，请勿重复提交");
        }
        String targetStatus = autoApproved
                ? InvTransferRevisionStatuses.APPROVED
                : InvTransferRevisionStatuses.SUBMITTED;
        String decisionAction = autoApproved ? "AUTO_APPROVED" : null;
        if (revisionMapper.sealDraft(latest.getRevisionId(), approvalRound,
                approvalInstanceId, targetStatus, snapshot.header(),
                snapshot.details(), snapshot.hash(), decisionAction,
                autoApproved ? decisionUserId : null,
                autoApproved ? operator : null, operator) != 1)
        {
            throw new ServiceException("调拨提交版本封存冲突，请刷新后重试");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void recordApprovalOutcome(InvTransferOrder order,
            String revisionStatus, String targetDocumentStatus,
            String decisionAction, String decisionReason,
            Long decisionUserId, String decisionUsername,
            Long approvalInstanceId)
    {
        requireOrder(order);
        String outcome = requireApprovalOutcome(revisionStatus);
        String operator = requireOperator(decisionUsername);
        String reason = validateDecisionReason(decisionReason);
        int approvalRound = normalizedApprovalRound(order);
        List<InvTransferDetail> details = lockedDetails(order.getTransferId());
        InvTransferRevision submitted = resolveSubmittedRevision(order,
                details, approvalRound, approvalInstanceId, operator);

        if (!outcome.equals(submitted.getStatus()))
        {
            if (!InvTransferRevisionStatuses.SUBMITTED.equals(
                    submitted.getStatus()))
            {
                throw new ServiceException("调拨审批版本状态与回调结果不一致");
            }
            if (revisionMapper.transitionRevision(submitted.getRevisionId(),
                    InvTransferRevisionStatuses.SUBMITTED, outcome,
                    approvalInstanceId,
                    requireDecisionAction(decisionAction), reason,
                    decisionUserId, operator, operator) != 1)
            {
                throw new ServiceException("调拨审批版本更新冲突，请刷新后重试");
            }
            submitted.setStatus(outcome);
        }

        if (isDraftDocumentStatus(targetDocumentStatus))
        {
            appendChildDraft(order, details, submitted, operator);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void recordCancellation(InvTransferOrder order, String username)
    {
        requireOrder(order);
        String operator = requireOperator(username);
        List<InvTransferDetail> details = detailMapper
                .selectByTransferIdForUpdate(order.getTransferId());
        if (details == null)
        {
            details = List.of();
        }
        InvTransferRevision latest = revisionMapper
                .selectLatestByTransferIdForUpdate(order.getTransferId());
        if (latest == null)
        {
            Snapshot snapshot = snapshot(order, details, "cancelled");
            boolean submitted = InvStatusConstants.SUBMITTED.equals(
                    order.getStatus());
            insertRevision(order.getTransferId(), 1, null,
                    submitted ? order.getApprovalRound() : null,
                    submitted ? order.getApprovalInstanceId() : null,
                    InvTransferRevisionStatuses.CANCELLED, snapshot,
                    "CANCELLED", null, null, operator, operator, new Date());
            return;
        }
        if (InvTransferRevisionStatuses.CANCELLED.equals(latest.getStatus()))
        {
            return;
        }
        String expected = latest.getStatus();
        if (!InvTransferRevisionStatuses.DRAFT.equals(expected)
                && !InvTransferRevisionStatuses.SUBMITTED.equals(expected))
        {
            throw new ServiceException("当前调拨版本不允许取消");
        }
        if (revisionMapper.transitionRevision(latest.getRevisionId(),
                expected, InvTransferRevisionStatuses.CANCELLED,
                order.getApprovalInstanceId(), "CANCELLED", null, null,
                operator, operator) != 1)
        {
            throw new ServiceException("调拨取消版本更新冲突，请刷新后重试");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void sealSystemDelivered(InvTransferOrder order,
            List<InvTransferDetail> details, String username)
    {
        requireOrder(order);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("调拨业务版本缺少明细");
        }
        String operator = requireOperator(username);
        Snapshot snapshot = snapshot(order, details, "delivered");
        InvTransferRevision latest = revisionMapper
                .selectLatestByTransferIdForUpdate(order.getTransferId());
        if (latest == null)
        {
            latest = insertRevision(order.getTransferId(), 1, null, null,
                    null, InvTransferRevisionStatuses.DRAFT, snapshot, null,
                    null, null, null, operator, null);
        }
        if (!InvTransferRevisionStatuses.DRAFT.equals(latest.getStatus()))
        {
            throw new ServiceException("系统调拨版本已封存，请勿重复生成");
        }
        if (revisionMapper.sealDraft(latest.getRevisionId(), null, null,
                InvTransferRevisionStatuses.DELIVERED, snapshot.header(),
                snapshot.details(), snapshot.hash(), "SYSTEM_DELIVERED",
                null, operator, operator) != 1)
        {
            throw new ServiceException("系统调拨版本封存冲突，请刷新后重试");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void assertPhysicalDeleteAllowed(Long transferId)
    {
        requireTransferId(transferId);
        if (revisionMapper.countSealedByTransferId(transferId) > 0)
        {
            throw new ServiceException("调拨单已有封存历史，禁止物理删除");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void deleteDraftForPhysicalDelete(Long transferId)
    {
        assertPhysicalDeleteAllowed(transferId);
        int deleted = revisionMapper.deleteDraftByTransferId(transferId);
        if (deleted > 1)
        {
            throw new ServiceException("调拨草稿版本数据异常，已拒绝删除");
        }
    }

    private InvTransferRevision resolveSubmittedRevision(
            InvTransferOrder order, List<InvTransferDetail> details,
            int approvalRound, Long approvalInstanceId, String operator)
    {
        InvTransferRevision revision = revisionMapper
                .selectByTransferRoundForUpdate(order.getTransferId(),
                        approvalRound);
        if (revision != null)
        {
            return revision;
        }

        InvTransferRevision latest = revisionMapper
                .selectLatestByTransferIdForUpdate(order.getTransferId());
        Snapshot snapshot = snapshot(order, details, "submitted");
        if (latest == null)
        {
            return insertRevision(order.getTransferId(), 1, null,
                    approvalRound, approvalInstanceId,
                    InvTransferRevisionStatuses.SUBMITTED, snapshot, null,
                    null, null, null, operator, new Date());
        }
        if (!InvTransferRevisionStatuses.DRAFT.equals(latest.getStatus()))
        {
            throw new ServiceException("调拨审批轮次缺少对应封存版本");
        }
        if (revisionMapper.sealDraft(latest.getRevisionId(), approvalRound,
                approvalInstanceId, InvTransferRevisionStatuses.SUBMITTED,
                snapshot.header(), snapshot.details(), snapshot.hash(), null,
                null, null, operator) != 1)
        {
            throw new ServiceException("调拨历史提交版本补录冲突");
        }
        latest.setApprovalRound(approvalRound);
        latest.setApprovalInstanceId(approvalInstanceId);
        latest.setStatus(InvTransferRevisionStatuses.SUBMITTED);
        latest.setHeaderSnapshot(snapshot.header());
        latest.setDetailSnapshot(snapshot.details());
        latest.setSnapshotHash(snapshot.hash());
        return latest;
    }

    private void appendChildDraft(InvTransferOrder order,
            List<InvTransferDetail> details, InvTransferRevision parent,
            String operator)
    {
        InvTransferRevision latest = revisionMapper
                .selectLatestByTransferIdForUpdate(order.getTransferId());
        if (latest != null
                && InvTransferRevisionStatuses.DRAFT.equals(latest.getStatus())
                && Objects.equals(latest.getParentRevisionId(),
                        parent.getRevisionId()))
        {
            return;
        }
        if (latest == null
                || !Objects.equals(latest.getRevisionId(),
                        parent.getRevisionId()))
        {
            throw new ServiceException("调拨新草稿版本链不连续");
        }
        Snapshot draftSnapshot = snapshot(order, details, "draft");
        insertRevision(order.getTransferId(), latest.getRevisionNo() + 1,
                parent.getRevisionId(), null, null,
                InvTransferRevisionStatuses.DRAFT, draftSnapshot, null, null,
                null, null, operator, null);
    }

    private InvTransferRevision insertRevision(Long transferId,
            int revisionNo, Long parentRevisionId, Integer approvalRound,
            Long approvalInstanceId, String status, Snapshot snapshot,
            String decisionAction, String decisionReason,
            Long decisionUserId, String decisionUsername, String operator,
            Date sealedTime)
    {
        InvTransferRevision revision = new InvTransferRevision();
        revision.setTransferId(transferId);
        revision.setRevisionNo(revisionNo);
        revision.setParentRevisionId(parentRevisionId);
        revision.setApprovalRound(approvalRound);
        revision.setApprovalInstanceId(approvalInstanceId);
        revision.setStatus(status);
        revision.setHeaderSnapshot(snapshot.header());
        revision.setDetailSnapshot(snapshot.details());
        revision.setSnapshotHash(snapshot.hash());
        revision.setDecisionAction(decisionAction);
        revision.setDecisionReason(decisionReason);
        revision.setDecisionUserId(decisionUserId);
        revision.setDecisionUsername(decisionUsername);
        revision.setDecisionTime(decisionAction == null ? null : new Date());
        revision.setSealedTime(sealedTime);
        revision.setCreateBy(operator);
        revision.setUpdateBy(operator);
        if (revisionMapper.insertRevision(revision) != 1
                || revision.getRevisionId() == null)
        {
            throw new ServiceException("调拨业务版本写入失败");
        }
        return revision;
    }

    private List<InvTransferDetail> lockedDetails(Long transferId)
    {
        List<InvTransferDetail> details = detailMapper
                .selectByTransferIdForUpdate(transferId);
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("调拨业务版本缺少明细");
        }
        return details;
    }

    private Snapshot snapshot(InvTransferOrder order,
            List<InvTransferDetail> details, String documentStatus)
    {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("attachmentNodeIds", order.getAttachmentNodeIds());
        header.put("documentStatus", documentStatus);
        header.put("fromDeptId", order.getFromDeptId());
        header.put("fromDeptName", order.getFromDeptName());
        header.put("fromWarehouseId", order.getFromWarehouseId());
        header.put("orderNo", order.getOrderNo());
        header.put("purchaseId", order.getPurchaseId());
        header.put("remark", order.getRemark());
        header.put("returnReasonCode", order.getReturnReasonCode());
        header.put("returnReasonText", order.getReturnReasonText());
        header.put("sourceBusinessId", order.getSourceBusinessId());
        header.put("sourceBusinessType", order.getSourceBusinessType());
        header.put("toDeptId", order.getToDeptId());
        header.put("toDeptName", order.getToDeptName());
        header.put("toWarehouseId", order.getToWarehouseId());
        header.put("totalAmount", decimal(order.getTotalAmount()));
        header.put("totalQuantity", decimal(order.getTotalQuantity()));
        header.put("transferId", order.getTransferId());
        header.put("transferType", order.getTransferType());

        List<InvTransferDetail> sorted = details == null
                ? new ArrayList<>() : new ArrayList<>(details);
        sorted.sort(DETAIL_ORDER);
        List<Map<String, Object>> detailValues = new ArrayList<>();
        for (InvTransferDetail detail : sorted)
        {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("amount", decimal(detail.getAmount()));
            value.put("conditionNote", detail.getConditionNote());
            value.put("costPrice", decimal(detail.getCostPrice()));
            value.put("detailId", detail.getDetailId());
            value.put("goodsCondition", detail.getGoodsCondition());
            value.put("grade", detail.getGrade());
            value.put("itemCode", detail.getItemCode());
            value.put("itemId", detail.getItemId());
            value.put("itemName", detail.getItemName());
            value.put("itemType", detail.getItemType());
            value.put("lotId", detail.getLotId());
            value.put("productCode", detail.getProductCode());
            value.put("productId", detail.getProductId());
            value.put("productName", detail.getProductName());
            value.put("quantity", decimal(detail.getQuantity()));
            value.put("sortOrder", detail.getSortOrder());
            value.put("sourceLocationId", detail.getSourceLocationId());
            value.put("spec", detail.getSpec());
            value.put("unit", detail.getUnit());
            detailValues.add(value);
        }

        String headerJson = serialize(header);
        String detailsJson = serialize(detailValues);
        return new Snapshot(headerJson, detailsJson,
                sha256(headerJson + "\n" + detailsJson));
    }

    private String serialize(Object value)
    {
        try
        {
            return canonicalMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("调拨业务版本快照生成失败");
        }
    }

    private InvTransferRevisionVo toHistoryItem(InvTransferOrder order,
            InvTransferRevision row)
    {
        try
        {
            InvTransferRevisionHeaderVo header = canonicalMapper.readValue(
                    row.getHeaderSnapshot(),
                    InvTransferRevisionHeaderVo.class);
            if (!Objects.equals(header.getTransferId(),
                    order.getTransferId()))
            {
                throw new ServiceException(
                        "调拨业务版本抬头归属校验失败");
            }
            List<InvTransferRevisionDetailVo> details = canonicalMapper
                    .readerForListOf(InvTransferRevisionDetailVo.class)
                    .readValue(row.getDetailSnapshot());
            InvTransferRevisionVo item = new InvTransferRevisionVo();
            item.setRevisionId(row.getRevisionId());
            item.setRevisionNo(row.getRevisionNo());
            item.setParentRevisionId(row.getParentRevisionId());
            item.setApprovalRound(row.getApprovalRound());
            item.setApprovalInstanceId(row.getApprovalInstanceId());
            item.setStatus(row.getStatus());
            item.setSnapshotHash(row.getSnapshotHash());
            item.setDecisionAction(row.getDecisionAction());
            item.setDecisionReason(row.getDecisionReason());
            item.setDecisionUserId(row.getDecisionUserId());
            item.setDecisionUsername(row.getDecisionUsername());
            item.setDecisionTime(row.getDecisionTime());
            item.setSealedTime(row.getSealedTime());
            item.setCreateTime(row.getCreateTime());
            item.setUpdateTime(row.getUpdateTime());
            item.setHeader(header);
            item.setDetails(details);
            return item;
        }
        catch (JsonProcessingException | IllegalArgumentException exception)
        {
            throw new ServiceException("调拨业务版本快照无法安全读取");
        }
    }

    private void validateHistoryRow(InvTransferOrder order,
            InvTransferRevision row, int expectedRevisionNo,
            Long expectedParentRevisionId,
            Set<Long> knownRevisionIds)
    {
        if (row == null || row.getRevisionId() == null
                || row.getRevisionId() <= 0
                || knownRevisionIds.contains(row.getRevisionId())
                || !Objects.equals(row.getTransferId(),
                        order.getTransferId())
                || !Objects.equals(row.getRevisionNo(), expectedRevisionNo)
                || !InvTransferRevisionStatuses.isKnown(row.getStatus())
                || row.getHeaderSnapshot() == null
                || row.getDetailSnapshot() == null
                || row.getSnapshotHash() == null)
        {
            throw new ServiceException("调拨业务版本链校验失败");
        }
        if (!Objects.equals(row.getParentRevisionId(),
                expectedParentRevisionId))
        {
            throw new ServiceException("调拨业务版本父子关系校验失败");
        }
        String actualHash = sha256(row.getHeaderSnapshot() + "\n"
                + row.getDetailSnapshot());
        if (!actualHash.equals(row.getSnapshotHash()))
        {
            throw new ServiceException("调拨业务版本快照完整性校验失败");
        }
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static void requireOrder(InvTransferOrder order)
    {
        if (order == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
        requireTransferId(order.getTransferId());
    }

    private static void requireTransferId(Long transferId)
    {
        if (transferId == null || transferId <= 0)
        {
            throw new ServiceException("调拨单标识无效");
        }
    }

    private static String requireOperator(String username)
    {
        String operator = username == null ? null : username.trim();
        if (StringUtils.isEmpty(operator) || operator.length() > 64)
        {
            throw new ServiceException("调拨版本缺少有效操作人");
        }
        return operator;
    }

    private static String requireApprovalOutcome(String status)
    {
        String normalized = status == null ? null
                : status.trim().toUpperCase(Locale.ROOT);
        if (!InvTransferRevisionStatuses.isApprovalOutcome(normalized))
        {
            throw new ServiceException("调拨审批版本结果无效");
        }
        return normalized;
    }

    private static String requireDecisionAction(String action)
    {
        String normalized = action == null ? null
                : action.trim().toUpperCase(Locale.ROOT);
        if (StringUtils.isEmpty(normalized) || normalized.length() > 32)
        {
            throw new ServiceException("调拨审批版本缺少有效决策动作");
        }
        return normalized;
    }

    private static String validateDecisionReason(String reason)
    {
        if (reason != null && reason.length() > 500)
        {
            throw new ServiceException("调拨审批意见不能超过500个字符");
        }
        return reason;
    }

    private static int normalizedApprovalRound(InvTransferOrder order)
    {
        return order.getApprovalRound() == null
                || order.getApprovalRound() <= 0
                ? 1 : order.getApprovalRound();
    }

    private static boolean isDraftDocumentStatus(String status)
    {
        return status != null && "draft".equalsIgnoreCase(status.trim());
    }

    private record Snapshot(String header, String details, String hash)
    {
    }
}
