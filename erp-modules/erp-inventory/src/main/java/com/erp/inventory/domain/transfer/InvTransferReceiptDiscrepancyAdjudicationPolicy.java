package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;

/** Pure independent adjudication plan; it never applies physical effects. */
public final class InvTransferReceiptDiscrepancyAdjudicationPolicy
{
    public static final String REQUIRED_PERMISSION =
            "inv:transfer:discrepancy:adjudicate";
    public static final String PLAN_STATUS = "adjudication_planned";
    public static final String COVERAGE_RESOLUTION = "resolution";
    public static final String COVERAGE_RESPONSIBILITY = "responsibility";

    private static final Set<String> RESPONSIBLE_PARTIES = Set.of(
            "source", "target", "carrier", "company");
    private static final Set<String> COMMON_ACTIONS = Set.of(
            "responsibility_adjustment", "transport_loss_write_off");

    private InvTransferReceiptDiscrepancyAdjudicationPolicy()
    {
    }

    public static PreparedAdjudication prepare(Request request,
            Resolved resolved, Actor actor, Instant createdAt)
    {
        requireInputs(request, resolved, actor, createdAt);
        InvTransferReceiptDiscrepancyReadFact fact = resolved.fact();
        Snapshot source = resolved.source();
        Snapshot target = resolved.target();
        requireAuthority(resolved, actor, source, target);
        requireRequestFacts(request, resolved);

        List<ActionInput> sorted = new ArrayList<>(request.actions());
        sorted.sort(Comparator.nullsFirst(Comparator.comparing(
                ActionInput::sequence,
                Comparator.nullsFirst(Integer::compareTo))));
        List<PreparedAction> actions = actions(sorted, fact);
        long versionAfter;
        try
        {
            versionAfter = Math.addExact(resolved.boundary().caseVersion(),
                    1L);
        }
        catch (ArithmeticException overflow)
        {
            throw new ServiceException("差异事项版本无法继续推进");
        }
        String note = requiredText(request.adjudicationNote(), 500,
                "裁决说明不能为空或超过500字");
        String evidence = requiredText(request.evidenceRefs(), 2000,
                "裁决证据不能为空或超过2000字");
        String fingerprint = fingerprint(
                resolved.boundary().discrepancyCaseId(),
                resolved.boundary().caseVersion(),
                resolved.boundary().factFingerprint(), source.eventId(),
                target.eventId(), actions, note, evidence);
        return new PreparedAdjudication(request.requestId(),
                fact.getDiscrepancyCaseId(),
                resolved.boundary().caseVersion(), versionAfter,
                fact.getFactFingerprint(), source.eventId(),
                target.eventId(), fact.getDiscrepancyType(),
                normalized(fact.getDiscrepancyQuantity(), 4),
                normalized(fact.getSourceCostPrice(), 6),
                normalized(fact.getDiscrepancyAmount(), 6),
                fingerprint, note, evidence, REQUIRED_PERMISSION,
                actor.userId(), actor.userName().trim(), createdAt,
                resolved.boundary().caseStatus(), PLAN_STATUS, actions);
    }

    /** Revalidates an immutable stored plan for exact idempotent replay. */
    public static PreparedAdjudication replay(Request request,
            InvTransferReceiptDiscrepancyStoredAdjudication stored,
            List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
                    storedActions,
            Actor actor)
    {
        Instant createdAt = stored == null || stored.getCreateTime() == null
                ? null : stored.getCreateTime().toInstant();
        requireRequestActor(request, actor, createdAt);
        requireStoredHeader(request, stored, actor);

        List<ActionInput> sorted = new ArrayList<>(request.actions());
        sorted.sort(Comparator.nullsFirst(Comparator.comparing(
                ActionInput::sequence,
                Comparator.nullsFirst(Integer::compareTo))));
        List<PreparedAction> actions = actions(sorted,
                stored.getDiscrepancyType(),
                stored.getDiscrepancyQuantity(),
                stored.getSourceCostPrice(),
                stored.getDiscrepancyAmount());
        requireStoredActions(stored, storedActions, actions);
        String fingerprint = fingerprint(stored.getCaseId(),
                stored.getCaseVersionBefore(), stored.getFactFingerprint(),
                stored.getSourceConfirmationEventId(),
                stored.getTargetConfirmationEventId(), actions,
                stored.getAdjudicationNote().trim(),
                stored.getEvidenceRefs().trim());
        if (!Objects.equals(fingerprint, stored.getDecisionFingerprint()))
        {
            throw new ServiceException("已存差异裁决决定指纹不可核验");
        }
        return new PreparedAdjudication(stored.getRequestId(),
                stored.getCaseId(), stored.getCaseVersionBefore(),
                stored.getCaseVersionAfter(), stored.getFactFingerprint(),
                stored.getSourceConfirmationEventId(),
                stored.getTargetConfirmationEventId(),
                stored.getDiscrepancyType(),
                normalized(stored.getDiscrepancyQuantity(), 4),
                normalized(stored.getSourceCostPrice(), 6),
                normalized(stored.getDiscrepancyAmount(), 6), fingerprint,
                stored.getAdjudicationNote().trim(),
                stored.getEvidenceRefs().trim(), stored.getRequiredPermission(),
                stored.getAdjudicatorUserId(),
                stored.getAdjudicatorName().trim(), createdAt,
                "awaiting_confirmation", stored.getPlanStatus(), actions);
    }

    private static void requireInputs(Request request, Resolved resolved,
            Actor actor, Instant createdAt)
    {
        requireRequestActor(request, actor, createdAt);
        if (resolved == null)
        {
            throw new ServiceException("独立差异裁决请求或权限无效");
        }
    }

    private static void requireRequestActor(Request request, Actor actor,
            Instant createdAt)
    {
        if (request == null || actor == null || createdAt == null
                || !requestId(request.requestId())
                || request.actions() == null || request.actions().isEmpty()
                || request.actions().size() > 20
                || actor.userId() == null || actor.userId() <= 0
                || actor.userName() == null || actor.userName().isBlank()
                || actor.userName().trim().length() > 64
                || actor.permissions() == null
                || !actor.permissions().contains(REQUIRED_PERMISSION))
        {
            throw new ServiceException("独立差异裁决请求或权限无效");
        }
    }

    private static void requireStoredHeader(Request request,
            InvTransferReceiptDiscrepancyStoredAdjudication stored,
            Actor actor)
    {
        long expectedAfter;
        try
        {
            expectedAfter = Math.addExact(stored.getCaseVersionBefore(),
                    1L);
        }
        catch (NullPointerException | ArithmeticException invalid)
        {
            throw new ServiceException("已存差异裁决头不可核验");
        }
        BigDecimal quantity = normalized(stored.getDiscrepancyQuantity(), 4);
        BigDecimal price = normalized(stored.getSourceCostPrice(), 6);
        BigDecimal amount = normalized(stored.getDiscrepancyAmount(), 6);
        if (stored.getAdjudicationId() == null
                || stored.getAdjudicationId() <= 0
                || !Objects.equals(stored.getRequestId(), request.requestId())
                || !Objects.equals(stored.getCaseId(), request.caseId())
                || !Objects.equals(stored.getCaseVersionBefore(),
                        request.caseVersion())
                || !Objects.equals(stored.getCaseVersionAfter(),
                        expectedAfter)
                || !Objects.equals(stored.getFactFingerprint(),
                        request.factFingerprint())
                || !hash(stored.getFactFingerprint())
                || !positive(stored.getSourceConfirmationEventId())
                || !positive(stored.getTargetConfirmationEventId())
                || Objects.equals(stored.getSourceConfirmationEventId(),
                        stored.getTargetConfirmationEventId())
                || !Set.of("damaged", "shortage").contains(
                        stored.getDiscrepancyType())
                || quantity.signum() <= 0 || price.signum() < 0
                || amount.signum() < 0
                || price.multiply(quantity)
                        .setScale(6, RoundingMode.HALF_UP)
                        .compareTo(amount) != 0
                || !Objects.equals(requiredText(
                        stored.getAdjudicationNote(), 500,
                        "已存差异裁决头不可核验"),
                        requiredText(request.adjudicationNote(), 500,
                                "裁决说明不能为空或超过500字"))
                || !Objects.equals(requiredText(stored.getEvidenceRefs(),
                        2000, "已存差异裁决头不可核验"),
                        requiredText(request.evidenceRefs(), 2000,
                                "裁决证据不能为空或超过2000字"))
                || !Objects.equals(stored.getRequiredPermission(),
                        REQUIRED_PERMISSION)
                || !Objects.equals(stored.getAdjudicatorUserId(),
                        actor.userId())
                || stored.getAdjudicatorName() == null
                || !Objects.equals(stored.getAdjudicatorName().trim(),
                        actor.userName().trim())
                || !PLAN_STATUS.equals(stored.getPlanStatus())
                || !hash(stored.getDecisionFingerprint()))
        {
            throw new ServiceException("幂等标识已被不同差异裁决占用");
        }
    }

    private static void requireStoredActions(
            InvTransferReceiptDiscrepancyStoredAdjudication stored,
            List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
                    storedActions,
            List<PreparedAction> prepared)
    {
        if (storedActions == null || storedActions.size() != prepared.size())
        {
            throw new ServiceException("已存差异裁决动作不可核验");
        }
        for (int index = 0; index < prepared.size(); index++)
        {
            InvTransferReceiptDiscrepancyStoredAdjudicationAction actual =
                    storedActions.get(index);
            PreparedAction expected = prepared.get(index);
            if (actual == null || !positive(actual.getActionId())
                    || !Objects.equals(actual.getAdjudicationId(),
                            stored.getAdjudicationId())
                    || !Objects.equals(actual.getCaseId(),
                            stored.getCaseId())
                    || !Objects.equals(actual.getSequence(),
                            expected.sequence())
                    || !Objects.equals(actual.getActionType(),
                            expected.actionType())
                    || !Objects.equals(actual.getCoverageKind(),
                            expected.coverageKind())
                    || actual.getQuantity() == null
                    || actual.getQuantity().compareTo(
                            expected.quantity()) != 0
                    || actual.getAmount() == null
                    || actual.getAmount().compareTo(expected.amount()) != 0
                    || !Objects.equals(actual.getResponsibleParty(),
                            expected.responsibleParty())
                    || actual.getNote() == null
                    || !Objects.equals(actual.getNote().trim(),
                            expected.note())
                    || !Objects.equals(actual.getExecutionStatus(),
                            expected.executionStatus()))
            {
                throw new ServiceException("已存差异裁决动作不可核验");
            }
        }
    }

    private static void requireAuthority(Resolved resolved, Actor actor,
            Snapshot source, Snapshot target)
    {
        if (!resolved.projection().readyForAdjudication()
                || source == null || target == null
                || !resolved.projection().source().currentFact()
                || !resolved.projection().target().currentFact()
                || !"confirmed".equals(source.decision())
                || !"confirmed".equals(target.decision())
                || Objects.equals(actor.userId(), source.operatorUserId())
                || Objects.equals(actor.userId(), target.operatorUserId()))
        {
            throw new ServiceException("双方事实确认或独立裁决职责不可核验");
        }
    }

    private static void requireRequestFacts(Request request,
            Resolved resolved)
    {
        if (!Objects.equals(request.caseId(),
                resolved.boundary().discrepancyCaseId())
                || !Objects.equals(request.caseVersion(),
                        resolved.boundary().caseVersion())
                || !Objects.equals(request.factFingerprint(),
                        resolved.boundary().factFingerprint()))
        {
            throw new ServiceException("差异裁决依据已变化，请刷新后重试");
        }
    }

    private static List<PreparedAction> actions(List<ActionInput> values,
            InvTransferReceiptDiscrepancyReadFact fact)
    {
        return actions(values, fact.getDiscrepancyType(),
                fact.getDiscrepancyQuantity(), fact.getSourceCostPrice(),
                fact.getDiscrepancyAmount());
    }

    private static List<PreparedAction> actions(List<ActionInput> values,
            String discrepancyType, BigDecimal quantity,
            BigDecimal sourceCostPrice, BigDecimal discrepancyAmount)
    {
        BigDecimal totalQuantity = normalized(quantity, 4);
        BigDecimal totalAmount = normalized(discrepancyAmount, 6);
        BigDecimal price = normalized(sourceCostPrice, 6);
        BigDecimal resolutionQuantity = BigDecimal.ZERO;
        BigDecimal responsibilityQuantity = BigDecimal.ZERO;
        List<String> coverageKinds = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            ActionInput value = values.get(index);
            if (value == null || value.sequence() == null
                    || value.sequence() != index + 1
                    || !quantity(value.quantity())
                    || !compatible(discrepancyType, value.actionType())
                    || value.responsibleParty() == null
                    || !RESPONSIBLE_PARTIES.contains(
                            value.responsibleParty()))
            {
                throw new ServiceException("裁决动作序号、类型、数量或责任方无效");
            }
            requiredText(value.note(), 500,
                    "裁决动作说明不能为空或超过500字");
            String coverageKind = coverageKind(discrepancyType,
                    value.actionType());
            coverageKinds.add(coverageKind);
            if (COVERAGE_RESOLUTION.equals(coverageKind))
            {
                resolutionQuantity = resolutionQuantity.add(
                        value.quantity());
            }
            else
            {
                responsibilityQuantity = responsibilityQuantity.add(
                        value.quantity());
            }
        }
        if (resolutionQuantity.compareTo(totalQuantity) != 0)
        {
            throw new ServiceException("裁决处置动作数量未完整覆盖差异数量");
        }
        if (responsibilityQuantity.compareTo(totalQuantity) > 0)
        {
            throw new ServiceException("裁决责任调整数量超过差异数量");
        }

        List<PreparedAction> result = new ArrayList<>();
        BigDecimal assignedResolution = BigDecimal.ZERO.setScale(6);
        BigDecimal assignedResponsibility = BigDecimal.ZERO.setScale(6);
        int lastResolution = -1;
        for (int index = 0; index < coverageKinds.size(); index++)
        {
            if (COVERAGE_RESOLUTION.equals(coverageKinds.get(index)))
            {
                lastResolution = index;
            }
        }
        for (int index = 0; index < values.size(); index++)
        {
            ActionInput value = values.get(index);
            BigDecimal actionQuantity = normalized(value.quantity(), 4);
            String coverageKind = coverageKinds.get(index);
            BigDecimal amount;
            if (COVERAGE_RESOLUTION.equals(coverageKind))
            {
                amount = index == lastResolution
                        ? totalAmount.subtract(assignedResolution)
                                .setScale(6)
                        : price.multiply(actionQuantity)
                                .setScale(6, RoundingMode.HALF_UP);
                assignedResolution = assignedResolution.add(amount);
            }
            else
            {
                amount = price.multiply(actionQuantity)
                        .setScale(6, RoundingMode.HALF_UP);
                assignedResponsibility = assignedResponsibility.add(
                        amount);
            }
            if (amount.signum() < 0)
            {
                throw new ServiceException("裁决动作金额尾差不可核验");
            }
            result.add(new PreparedAction(value.sequence(),
                    value.actionType(), coverageKind, actionQuantity,
                    amount, value.responsibleParty(), value.note().trim(),
                    "pending"));
        }
        if (assignedResolution.compareTo(totalAmount) != 0)
        {
            throw new ServiceException("裁决处置动作金额与差异金额不守恒");
        }
        if (assignedResponsibility.compareTo(totalAmount) > 0)
        {
            throw new ServiceException("裁决责任调整金额超过差异金额");
        }
        return List.copyOf(result);
    }

    static String coverageKind(String discrepancyType, String actionType)
    {
        if (!compatible(discrepancyType, actionType))
        {
            throw new ServiceException("裁决动作与差异类型不兼容");
        }
        if ("damaged".equals(discrepancyType)
                && "responsibility_adjustment".equals(actionType))
        {
            return COVERAGE_RESPONSIBILITY;
        }
        return COVERAGE_RESOLUTION;
    }

    private static boolean compatible(String discrepancyType,
            String actionType)
    {
        if (discrepancyType == null || actionType == null)
        {
            return false;
        }
        if (COMMON_ACTIONS.contains(actionType))
        {
            return Set.of("damaged", "shortage").contains(discrepancyType);
        }
        if ("shortage".equals(discrepancyType))
        {
            return "reship".equals(actionType);
        }
        if ("damaged".equals(discrepancyType))
        {
            return Set.of("return_to_source", "damage_write_off")
                    .contains(actionType);
        }
        return false;
    }

    private static String fingerprint(Long caseId, Long caseVersion,
            String factFingerprint, Long sourceEventId, Long targetEventId,
            List<PreparedAction> actions, String note, String evidence)
    {
        StringBuilder canonical = new StringBuilder();
        add(canonical, caseId);
        add(canonical, caseVersion);
        add(canonical, factFingerprint);
        add(canonical, sourceEventId);
        add(canonical, targetEventId);
        add(canonical, note);
        add(canonical, evidence);
        add(canonical, REQUIRED_PERMISSION);
        for (PreparedAction action : actions)
        {
            add(canonical, action.sequence());
            add(canonical, action.actionType());
            add(canonical, decimal(action.quantity()));
            add(canonical, decimal(action.amount()));
            add(canonical, action.responsibleParty());
            add(canonical, action.note());
        }
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void add(StringBuilder target, Object value)
    {
        String text = value == null ? "" : value.toString();
        target.append(text.length()).append(':').append(text);
    }

    private static BigDecimal normalized(BigDecimal value, int scale)
    {
        if (value == null || value.scale() > scale)
        {
            throw new ServiceException("差异裁决数量或金额精度无效");
        }
        return value.setScale(scale);
    }

    private static String decimal(BigDecimal value)
    {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static boolean quantity(BigDecimal value)
    {
        return value != null && value.signum() > 0 && value.scale() <= 4;
    }

    private static String requiredText(String value, int max,
            String message)
    {
        if (value == null || value.isBlank() || value.trim().length() > max)
        {
            throw new ServiceException(message);
        }
        return value.trim();
    }

    private static boolean requestId(String value)
    {
        return value != null && value.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    }

    private static boolean hash(String value)
    {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    public record Request(
            String requestId,
            Long caseId,
            Long caseVersion,
            String factFingerprint,
            String adjudicationNote,
            String evidenceRefs,
            List<ActionInput> actions)
    {
        public Request
        {
            actions = actions == null ? null : Collections.unmodifiableList(
                    new ArrayList<>(actions));
        }
    }

    public record ActionInput(
            Integer sequence,
            String actionType,
            BigDecimal quantity,
            String responsibleParty,
            String note)
    {
    }

    public record Actor(
            Long userId,
            String userName,
            Set<String> permissions)
    {
        public Actor
        {
            permissions = permissions == null ? null
                    : Set.copyOf(permissions);
        }
    }

    public record PreparedAction(
            Integer sequence,
            String actionType,
            String coverageKind,
            BigDecimal quantity,
            BigDecimal amount,
            String responsibleParty,
            String note,
            String executionStatus)
    {
    }

    public record PreparedAdjudication(
            String requestId,
            Long caseId,
            Long caseVersionBefore,
            Long caseVersionAfter,
            String factFingerprint,
            Long sourceConfirmationEventId,
            Long targetConfirmationEventId,
            String discrepancyType,
            BigDecimal discrepancyQuantity,
            BigDecimal sourceCostPrice,
            BigDecimal discrepancyAmount,
            String decisionFingerprint,
            String adjudicationNote,
            String evidenceRefs,
            String requiredPermission,
            Long adjudicatorUserId,
            String adjudicatorName,
            Instant createdAt,
            String caseStatusBefore,
            String planStatus,
            List<PreparedAction> actions)
    {
        public PreparedAdjudication
        {
            actions = List.copyOf(actions);
        }
    }
}
