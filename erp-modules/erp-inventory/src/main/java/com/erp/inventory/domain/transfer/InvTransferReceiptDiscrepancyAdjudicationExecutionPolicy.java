package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;

/** Pure lifecycle and conservation policy for one adjudication action. */
public final class
        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
{
    public static final String REQUIRED_PERMISSION =
            "inv:transfer:discrepancy:execute";

    public static final String COMMAND_DISPATCH = "dispatch";
    public static final String COMMAND_COMPLETE = "complete";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    public static final String PLAN_PLANNED = "adjudication_planned";
    public static final String PLAN_EXECUTING = "adjudication_executing";
    public static final String PLAN_RESOLVED = "resolved";

    public static final String EFFECT_RESHIP = "reship_workflow";
    public static final String EFFECT_RETURN = "return_workflow";
    public static final String EFFECT_QUARANTINE_WRITE_OFF =
            "quarantine_write_off_and_loss_ledger";
    public static final String EFFECT_RESPONSIBILITY =
            "responsibility_ledger";
    public static final String EFFECT_SHORTAGE_LOSS =
            "shortage_loss_ledger";

    private static final Set<String> RESPONSIBLE_PARTIES = Set.of(
            "source", "target", "carrier", "company");
    private static final Set<String> ACTION_STATUSES = Set.of(
            STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_COMPLETED);

    private InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy()
    {
    }

    public static PreparedExecution prepare(Request request,
            Boundary boundary, Actor actor, Instant executedAt)
    {
        requireInputs(request, boundary, actor, executedAt);
        List<ActionSnapshot> actions = validatedActions(boundary);
        requireVersionChain(boundary, actions);
        String statusBefore = aggregateStatus(actions);
        if (!Objects.equals(statusBefore, boundary.caseStatus())
                || !Objects.equals(statusBefore, boundary.planStatus()))
        {
            throw new ServiceException("差异裁决执行聚合状态不可核验");
        }
        ActionSnapshot target = actions.stream()
                .filter(action -> Objects.equals(action.actionId(),
                        request.actionId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(
                        "差异裁决执行动作不存在"));
        if (!Objects.equals(request.executionVersion(),
                target.executionVersion()))
        {
            throw new ServiceException("差异裁决执行动作版本已变化");
        }

        String effectKind = effectKind(boundary.discrepancyType(),
                target.actionType());
        Transition transition = transition(request, target, effectKind);
        List<ActionSnapshot> resulting = replace(actions, target,
                transition);
        String statusAfter = aggregateStatus(resulting);
        long caseVersionAfter = increment(boundary.caseVersion(),
                "差异事项版本无法继续推进");
        long executionVersionAfter = increment(
                target.executionVersion(), "差异裁决动作版本无法继续推进");
        String executorName = actor.userName().trim();
        String fingerprint = fingerprint(request, boundary, target,
                actions, transition, effectKind, statusBefore, statusAfter,
                caseVersionAfter, executionVersionAfter, actor.userId(),
                executorName, executedAt);

        return new PreparedExecution(request.requestId(),
                boundary.caseId(), boundary.caseVersion(),
                caseVersionAfter, statusBefore, statusAfter,
                boundary.adjudicationId(), boundary.decisionFingerprint(),
                statusBefore, statusAfter, target.actionId(),
                target.sequence(), target.actionType(),
                target.coverageKind(),
                boundary.discrepancyType(), effectKind, request.command(),
                transition.effectReference(), target.executionStatus(),
                transition.statusAfter(), target.executionVersion(),
                executionVersionAfter, target.quantity(),
                boundary.sourceCostPrice(), target.amount(),
                target.responsibleParty(), REQUIRED_PERMISSION,
                actor.userId(), executorName, executedAt, fingerprint,
                resulting);
    }

    public static StoredEventSnapshot replay(Request request,
            Boundary boundary, StoredEventSnapshot stored, Actor actor)
    {
        if (!validReplayInputs(request, boundary, stored, actor))
        {
            throw new ServiceException("幂等标识已被不同差异执行占用");
        }
        List<ActionSnapshot> actions = validatedActions(boundary);
        requireVersionChain(boundary, actions);
        String aggregate = aggregateStatus(actions);
        if (!Objects.equals(aggregate, boundary.caseStatus())
                || !Objects.equals(aggregate, boundary.planStatus()))
        {
            throw new ServiceException("差异裁决执行重放状态不可核验");
        }
        ActionSnapshot target = actions.stream()
                .filter(action -> Objects.equals(action.actionId(),
                        stored.actionId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(
                        "差异裁决执行重放动作不存在"));
        String expectedEffect = effectKind(stored.discrepancyType(),
                stored.actionType());
        if (!Objects.equals(expectedEffect, stored.effectKind())
                || !Objects.equals(coverageKind(
                        stored.discrepancyType(), stored.actionType()),
                        stored.coverageKind())
                || !validStoredTransition(stored, expectedEffect)
                || !Objects.equals(target.sequence(),
                        stored.actionSequence())
                || !Objects.equals(target.actionType(),
                        stored.actionType())
                || !Objects.equals(target.coverageKind(),
                        stored.coverageKind())
                || target.quantity().compareTo(stored.quantity()) != 0
                || target.amount().compareTo(stored.amount()) != 0
                || !Objects.equals(target.responsibleParty(),
                        stored.responsibleParty())
                || target.executionVersion()
                        < stored.executionVersionAfter()
                || boundary.caseVersion() < stored.caseVersionAfter())
        {
            throw new ServiceException("差异裁决执行重放事实不可核验");
        }
        return stored;
    }

    private static boolean validReplayInputs(Request request,
            Boundary boundary, StoredEventSnapshot stored, Actor actor)
    {
        if (request == null || boundary == null || stored == null
                || !validActor(actor) || !requestId(request.requestId())
                || !positive(request.caseId())
                || !positive(request.adjudicationId())
                || !positive(request.actionId())
                || request.caseVersion() == null
                || request.caseVersion() < 0
                || request.executionVersion() == null
                || request.executionVersion() < 0
                || request.command() == null
                || !Set.of(COMMAND_DISPATCH, COMMAND_COMPLETE).contains(
                        request.command())
                || !positive(boundary.caseId())
                || !positive(boundary.adjudicationId())
                || boundary.caseVersionAtPlan() == null
                || boundary.caseVersionAtPlan() < 0
                || boundary.caseVersion() == null
                || boundary.caseVersion() < 0
                || !hash(boundary.decisionFingerprint())
                || boundary.discrepancyType() == null
                || !Set.of("shortage", "damaged").contains(
                        boundary.discrepancyType())
                || !quantity(boundary.discrepancyQuantity())
                || !amount(boundary.sourceCostPrice())
                || !amount(boundary.discrepancyAmount())
                || boundary.discrepancyAmount().compareTo(
                        boundary.sourceCostPrice()
                                .multiply(boundary.discrepancyQuantity())
                                .setScale(6, RoundingMode.HALF_UP)) != 0
                || !positive(stored.eventId())
                || stored.executedAt() == null
                || !hash(stored.eventFingerprint())
                || !hash(stored.decisionFingerprint())
                || !positive(stored.caseId())
                || !positive(stored.adjudicationId())
                || !positive(stored.actionId())
                || stored.actionSequence() == null
                || stored.actionSequence() <= 0
                || stored.caseVersionBefore() == null
                || stored.caseVersionBefore() < 0
                || stored.executionVersionBefore() == null
                || stored.executionVersionBefore() < 0
                || !successor(stored.caseVersionBefore(),
                        stored.caseVersionAfter())
                || !successor(stored.executionVersionBefore(),
                        stored.executionVersionAfter())
                || !quantity(stored.quantity())
                || !amount(stored.sourceCostPrice())
                || !amount(stored.amount())
                || stored.discrepancyType() == null
                || stored.actionType() == null
                || !compatible(stored.discrepancyType(),
                        stored.actionType())
                || stored.coverageKind() == null
                || stored.effectKind() == null
                || stored.command() == null
                || stored.responsibleParty() == null
                || !RESPONSIBLE_PARTIES.contains(
                        stored.responsibleParty())
                || !Objects.equals(stored.requiredPermission(),
                        REQUIRED_PERMISSION)
                || !Objects.equals(stored.executorUserId(),
                        actor.userId())
                || !Objects.equals(stored.executorName(),
                        actor.userName().trim())
                || !Objects.equals(request.requestId(),
                        stored.requestId())
                || !Objects.equals(request.caseId(), stored.caseId())
                || !Objects.equals(request.caseVersion(),
                        stored.caseVersionBefore())
                || !Objects.equals(request.adjudicationId(),
                        stored.adjudicationId())
                || !Objects.equals(request.actionId(),
                        stored.actionId())
                || !Objects.equals(request.executionVersion(),
                        stored.executionVersionBefore())
                || !Objects.equals(request.command(), stored.command())
                || !Objects.equals(request.effectReference(),
                        stored.effectReference())
                || !Objects.equals(boundary.caseId(), stored.caseId())
                || !Objects.equals(boundary.adjudicationId(),
                        stored.adjudicationId())
                || !Objects.equals(boundary.decisionFingerprint(),
                        stored.decisionFingerprint())
                || !Objects.equals(boundary.discrepancyType(),
                        stored.discrepancyType())
                || boundary.sourceCostPrice() == null
                || boundary.sourceCostPrice().compareTo(
                        stored.sourceCostPrice()) != 0
                || boundary.actions() == null
                || boundary.actions().isEmpty()
                || boundary.actions().size() > 20)
        {
            return false;
        }
        return true;
    }

    private static boolean validStoredTransition(StoredEventSnapshot stored,
            String effectKind)
    {
        if (stored.caseStatusBefore() == null
                || stored.caseStatusAfter() == null
                || stored.planStatusBefore() == null
                || stored.planStatusAfter() == null
                || stored.actionStatusBefore() == null
                || stored.actionStatusAfter() == null
                || !Objects.equals(stored.caseStatusBefore(),
                stored.planStatusBefore())
                || !Objects.equals(stored.caseStatusAfter(),
                        stored.planStatusAfter())
                || !Set.of(PLAN_PLANNED, PLAN_EXECUTING).contains(
                        stored.caseStatusBefore())
                || !Set.of(PLAN_EXECUTING, PLAN_RESOLVED).contains(
                        stored.caseStatusAfter())
                || !ACTION_STATUSES.contains(stored.actionStatusBefore())
                || !ACTION_STATUSES.contains(stored.actionStatusAfter()))
        {
            return false;
        }
        if (!async(effectKind))
        {
            return COMMAND_DISPATCH.equals(stored.command())
                    && STATUS_PENDING.equals(
                            stored.actionStatusBefore())
                    && STATUS_COMPLETED.equals(
                            stored.actionStatusAfter())
                    && stored.effectReference() == null;
        }
        try
        {
            requiredReference(stored.effectReference(), effectKind);
        }
        catch (ServiceException invalid)
        {
            return false;
        }
        return (COMMAND_DISPATCH.equals(stored.command())
                && STATUS_PENDING.equals(stored.actionStatusBefore())
                && STATUS_IN_PROGRESS.equals(stored.actionStatusAfter()))
                || (COMMAND_COMPLETE.equals(stored.command())
                && STATUS_IN_PROGRESS.equals(stored.actionStatusBefore())
                && STATUS_COMPLETED.equals(stored.actionStatusAfter()));
    }

    private static void requireInputs(Request request, Boundary boundary,
            Actor actor, Instant executedAt)
    {
        if (request == null || boundary == null || actor == null
                || executedAt == null || !requestId(request.requestId())
                || !positive(request.caseId())
                || !positive(request.adjudicationId())
                || !positive(request.actionId())
                || request.caseVersion() == null
                || request.caseVersion() < 0
                || request.executionVersion() == null
                || request.executionVersion() < 0
                || !Objects.equals(request.caseId(), boundary.caseId())
                || !Objects.equals(request.adjudicationId(),
                        boundary.adjudicationId())
                || !Objects.equals(request.caseVersion(),
                        boundary.caseVersion())
                || !positive(boundary.caseId())
                || !positive(boundary.adjudicationId())
                || boundary.caseVersion() == null
                || boundary.caseVersion() < 0
                || boundary.caseVersionAtPlan() == null
                || boundary.caseVersionAtPlan() < 0
                || !hash(boundary.decisionFingerprint())
                || !Set.of("shortage", "damaged").contains(
                        boundary.discrepancyType())
                || !quantity(boundary.discrepancyQuantity())
                || !amount(boundary.sourceCostPrice())
                || !amount(boundary.discrepancyAmount())
                || boundary.discrepancyAmount().compareTo(
                        boundary.sourceCostPrice()
                                .multiply(boundary.discrepancyQuantity())
                                .setScale(6, RoundingMode.HALF_UP)) != 0
                || boundary.actions() == null
                || boundary.actions().isEmpty()
                || boundary.actions().size() > 20
                || !validActor(actor))
        {
            throw new ServiceException("差异裁决执行请求、边界或权限无效");
        }
    }

    private static List<ActionSnapshot> validatedActions(
            Boundary boundary)
    {
        List<ActionSnapshot> actions = new ArrayList<>(boundary.actions());
        actions.sort(Comparator.nullsFirst(Comparator.comparing(
                ActionSnapshot::sequence,
                Comparator.nullsFirst(Integer::compareTo))));
        Set<Long> ids = new HashSet<>();
        BigDecimal resolutionQuantity = BigDecimal.ZERO;
        BigDecimal responsibilityQuantity = BigDecimal.ZERO;
        BigDecimal resolutionAmount = BigDecimal.ZERO;
        BigDecimal responsibilityAmount = BigDecimal.ZERO;
        int lastResolution = -1;
        for (int index = 0; index < actions.size(); index++)
        {
            ActionSnapshot action = actions.get(index);
            String expectedCoverage = action == null ? null
                    : coverageKind(boundary.discrepancyType(),
                            action.actionType());
            if (action == null || !positive(action.actionId())
                    || !ids.add(action.actionId())
                    || action.sequence() == null
                    || action.sequence() != index + 1
                    || !compatible(boundary.discrepancyType(),
                            action.actionType())
                    || !Objects.equals(action.coverageKind(),
                            expectedCoverage)
                    || !quantity(action.quantity())
                    || !amount(action.amount())
                    || action.responsibleParty() == null
                    || !RESPONSIBLE_PARTIES.contains(
                            action.responsibleParty())
                    || !ACTION_STATUSES.contains(
                            action.executionStatus())
                    || action.executionVersion() == null
                    || action.executionVersion() < 0
                    || !validEffectReference(boundary.discrepancyType(),
                            action)
                    || !validActionState(boundary.discrepancyType(),
                            action))
            {
                throw new ServiceException(
                        "差异裁决执行动作序号、类型、状态或版本无效");
            }
            if (InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .COVERAGE_RESOLUTION.equals(action.coverageKind()))
            {
                resolutionQuantity = resolutionQuantity.add(
                        action.quantity());
                resolutionAmount = resolutionAmount.add(action.amount());
                lastResolution = index;
            }
            else
            {
                responsibilityQuantity = responsibilityQuantity.add(
                        action.quantity());
                responsibilityAmount = responsibilityAmount.add(
                        action.amount());
                BigDecimal expectedAmount = boundary.sourceCostPrice()
                        .multiply(action.quantity())
                        .setScale(6, RoundingMode.HALF_UP);
                if (expectedAmount.compareTo(action.amount()) != 0)
                {
                    throw new ServiceException(
                            "差异裁决责任调整金额不可核验");
                }
            }
        }
        if (lastResolution < 0
                || resolutionQuantity.compareTo(
                        boundary.discrepancyQuantity()) != 0
                || resolutionAmount.compareTo(
                        boundary.discrepancyAmount()) != 0
                || responsibilityQuantity.compareTo(
                        boundary.discrepancyQuantity()) > 0
                || responsibilityAmount.compareTo(
                        boundary.discrepancyAmount()) > 0)
        {
            throw new ServiceException(
                    "差异裁决执行覆盖维度数量或金额不守恒");
        }
        requireResolutionAmounts(boundary, actions, lastResolution);
        return List.copyOf(actions);
    }

    private static void requireResolutionAmounts(Boundary boundary,
            List<ActionSnapshot> actions, int lastResolution)
    {
        BigDecimal assigned = BigDecimal.ZERO.setScale(6);
        for (int index = 0; index < actions.size(); index++)
        {
            ActionSnapshot action = actions.get(index);
            if (!InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .COVERAGE_RESOLUTION.equals(action.coverageKind()))
            {
                continue;
            }
            BigDecimal expected = index == lastResolution
                    ? boundary.discrepancyAmount().subtract(assigned)
                            .setScale(6)
                    : boundary.sourceCostPrice()
                            .multiply(action.quantity())
                            .setScale(6, RoundingMode.HALF_UP);
            if (expected.signum() < 0
                    || expected.compareTo(action.amount()) != 0)
            {
                throw new ServiceException(
                        "差异裁决处置动作金额不可核验");
            }
            assigned = assigned.add(action.amount());
        }
    }

    private static void requireVersionChain(Boundary boundary,
            List<ActionSnapshot> actions)
    {
        long expected = boundary.caseVersionAtPlan();
        try
        {
            for (ActionSnapshot action : actions)
            {
                expected = Math.addExact(expected,
                        action.executionVersion());
            }
        }
        catch (ArithmeticException overflow)
        {
            throw new ServiceException("差异裁决执行版本链不可核验");
        }
        if (expected != boundary.caseVersion())
        {
            throw new ServiceException("差异裁决执行版本链不可核验");
        }
    }

    private static boolean validActionState(String discrepancyType,
            ActionSnapshot action)
    {
        if (STATUS_PENDING.equals(action.executionStatus()))
        {
            return action.executionVersion() == 0;
        }
        String effect = effectKind(discrepancyType, action.actionType());
        if (async(effect))
        {
            return (STATUS_IN_PROGRESS.equals(action.executionStatus())
                    && action.executionVersion() == 1)
                    || (STATUS_COMPLETED.equals(action.executionStatus())
                    && action.executionVersion() == 2);
        }
        return STATUS_COMPLETED.equals(action.executionStatus())
                && action.executionVersion() == 1;
    }

    private static boolean validEffectReference(String discrepancyType,
            ActionSnapshot action)
    {
        String effect = effectKind(discrepancyType, action.actionType());
        if (!async(effect))
        {
            return action.effectReference() == null;
        }
        if (STATUS_PENDING.equals(action.executionStatus()))
        {
            return action.effectReference() == null;
        }
        try
        {
            return Objects.equals(action.effectReference(),
                    requiredReference(action.effectReference(), effect));
        }
        catch (ServiceException invalid)
        {
            return false;
        }
    }

    private static Transition transition(Request request,
            ActionSnapshot target, String effectKind)
    {
        if (COMMAND_DISPATCH.equals(request.command()))
        {
            if (!STATUS_PENDING.equals(target.executionStatus()))
            {
                throw new ServiceException("差异裁决动作不可重复调度");
            }
            if (async(effectKind))
            {
                return new Transition(STATUS_IN_PROGRESS,
                        requiredReference(request.effectReference(),
                                effectKind));
            }
            if (request.effectReference() != null)
            {
                throw new ServiceException("差异裁决原子效果不得绑定子流程");
            }
            return new Transition(STATUS_COMPLETED, null);
        }
        if (COMMAND_COMPLETE.equals(request.command()))
        {
            if (!async(effectKind)
                    || !STATUS_IN_PROGRESS.equals(
                            target.executionStatus()))
            {
                throw new ServiceException("差异裁决动作不可直接完成");
            }
            String reference = requiredReference(
                    request.effectReference(), effectKind);
            if (!Objects.equals(reference, target.effectReference()))
            {
                throw new ServiceException("差异裁决异步效果引用已变化");
            }
            return new Transition(STATUS_COMPLETED, reference);
        }
        throw new ServiceException("差异裁决执行命令无效");
    }

    private static List<ActionSnapshot> replace(List<ActionSnapshot> actions,
            ActionSnapshot target, Transition transition)
    {
        List<ActionSnapshot> result = new ArrayList<>(actions.size());
        for (ActionSnapshot action : actions)
        {
            if (Objects.equals(action.actionId(), target.actionId()))
            {
                result.add(new ActionSnapshot(action.actionId(),
                        action.sequence(), action.actionType(),
                        action.coverageKind(), action.quantity(),
                        action.amount(),
                        action.responsibleParty(), transition.statusAfter(),
                        increment(action.executionVersion(),
                                "差异裁决动作版本无法继续推进"),
                        transition.effectReference()));
            }
            else
            {
                result.add(action);
            }
        }
        return List.copyOf(result);
    }

    private static String aggregateStatus(List<ActionSnapshot> actions)
    {
        long completed = actions.stream().filter(action ->
                STATUS_COMPLETED.equals(action.executionStatus())).count();
        if (completed == actions.size())
        {
            return PLAN_RESOLVED;
        }
        boolean started = actions.stream().anyMatch(action ->
                !STATUS_PENDING.equals(action.executionStatus()));
        return started ? PLAN_EXECUTING : PLAN_PLANNED;
    }

    private static String effectKind(String discrepancyType,
            String actionType)
    {
        if ("shortage".equals(discrepancyType)
                && "reship".equals(actionType))
        {
            return EFFECT_RESHIP;
        }
        if ("damaged".equals(discrepancyType)
                && "return_to_source".equals(actionType))
        {
            return EFFECT_RETURN;
        }
        if ("damaged".equals(discrepancyType)
                && Set.of("damage_write_off",
                        "transport_loss_write_off").contains(actionType))
        {
            return EFFECT_QUARANTINE_WRITE_OFF;
        }
        if ("responsibility_adjustment".equals(actionType)
                && Set.of("shortage", "damaged").contains(
                        discrepancyType))
        {
            return EFFECT_RESPONSIBILITY;
        }
        if ("shortage".equals(discrepancyType)
                && "transport_loss_write_off".equals(actionType))
        {
            return EFFECT_SHORTAGE_LOSS;
        }
        throw new ServiceException("差异裁决执行动作与事项类型不兼容");
    }

    private static boolean compatible(String discrepancyType,
            String actionType)
    {
        try
        {
            effectKind(discrepancyType, actionType);
            return true;
        }
        catch (ServiceException invalid)
        {
            return false;
        }
    }

    private static String coverageKind(String discrepancyType,
            String actionType)
    {
        try
        {
            return InvTransferReceiptDiscrepancyAdjudicationPolicy
                    .coverageKind(discrepancyType, actionType);
        }
        catch (ServiceException invalid)
        {
            return null;
        }
    }

    private static boolean async(String effectKind)
    {
        return Set.of(EFFECT_RESHIP, EFFECT_RETURN).contains(effectKind);
    }

    private static String requiredReference(String value, String effectKind)
    {
        String prefix = EFFECT_RESHIP.equals(effectKind)
                ? "reship_transfer:" : "return_transfer:";
        if (value == null || !value.matches(
                java.util.regex.Pattern.quote(prefix)
                        + "[1-9][0-9]{0,18}"))
        {
            throw new ServiceException("差异裁决异步效果引用无效");
        }
        try
        {
            if (Long.parseLong(value.substring(prefix.length())) <= 0)
            {
                throw new ServiceException("差异裁决异步效果引用无效");
            }
        }
        catch (NumberFormatException invalid)
        {
            throw new ServiceException("差异裁决异步效果引用无效");
        }
        return value;
    }

    private static String fingerprint(Request request, Boundary boundary,
            ActionSnapshot target, List<ActionSnapshot> actions,
            Transition transition,
            String effectKind, String statusBefore, String statusAfter,
            long caseVersionAfter, long executionVersionAfter,
            Long executorUserId, String executorName, Instant executedAt)
    {
        StringBuilder canonical = new StringBuilder();
        add(canonical, request.requestId());
        add(canonical, boundary.caseId());
        add(canonical, boundary.caseVersionAtPlan());
        add(canonical, boundary.caseVersion());
        add(canonical, caseVersionAfter);
        add(canonical, statusBefore);
        add(canonical, statusAfter);
        add(canonical, boundary.adjudicationId());
        add(canonical, boundary.decisionFingerprint());
        add(canonical, decimal(boundary.sourceCostPrice()));
        for (ActionSnapshot action : actions)
        {
            add(canonical, action.actionId());
            add(canonical, action.sequence());
            add(canonical, action.actionType());
            add(canonical, action.executionStatus());
            add(canonical, action.executionVersion());
            add(canonical, action.effectReference());
        }
        add(canonical, target.actionId());
        add(canonical, target.sequence());
        add(canonical, target.actionType());
        add(canonical, boundary.discrepancyType());
        add(canonical, effectKind);
        add(canonical, request.command());
        add(canonical, transition.effectReference());
        add(canonical, target.executionStatus());
        add(canonical, transition.statusAfter());
        add(canonical, target.executionVersion());
        add(canonical, executionVersionAfter);
        add(canonical, decimal(target.quantity()));
        add(canonical, decimal(target.amount()));
        add(canonical, target.responsibleParty());
        add(canonical, REQUIRED_PERMISSION);
        add(canonical, executorUserId);
        add(canonical, executorName);
        add(canonical, executedAt);
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable",
                    impossible);
        }
    }

    private static void add(StringBuilder target, Object value)
    {
        String text = value == null ? "" : value.toString();
        target.append(text.length()).append(':').append(text);
    }

    private static String decimal(BigDecimal value)
    {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0"
                : normalized.toPlainString();
    }

    private static long increment(Long value, String message)
    {
        try
        {
            return Math.addExact(value, 1L);
        }
        catch (NullPointerException | ArithmeticException invalid)
        {
            throw new ServiceException(message);
        }
    }

    private static boolean successor(Long before, Long after)
    {
        return before != null && after != null && before < Long.MAX_VALUE
                && after == before + 1;
    }

    private static boolean validActor(Actor actor)
    {
        return actor != null && positive(actor.userId())
                && actor.userName() != null
                && !actor.userName().isBlank()
                && actor.userName().trim().length() <= 64
                && actor.permissions() != null
                && actor.permissions().contains(REQUIRED_PERMISSION);
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

    private static boolean quantity(BigDecimal value)
    {
        return value != null && value.signum() > 0 && value.scale() <= 4;
    }

    private static boolean amount(BigDecimal value)
    {
        return value != null && value.signum() >= 0 && value.scale() <= 6;
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private record Transition(String statusAfter, String effectReference)
    {
    }

    public record Request(
            String requestId,
            Long caseId,
            Long caseVersion,
            Long adjudicationId,
            Long actionId,
            Long executionVersion,
            String command,
            String effectReference)
    {
    }

    public record Actor(Long userId, String userName,
            Set<String> permissions)
    {
        public Actor
        {
            permissions = permissions == null ? null
                    : Set.copyOf(permissions);
        }
    }

    public record ActionSnapshot(
            Long actionId,
            Integer sequence,
            String actionType,
            String coverageKind,
            BigDecimal quantity,
            BigDecimal amount,
            String responsibleParty,
            String executionStatus,
            Long executionVersion,
            String effectReference)
    {
    }

    public record Boundary(
            Long caseId,
            Long caseVersionAtPlan,
            Long caseVersion,
            String caseStatus,
            Long adjudicationId,
            String decisionFingerprint,
            String discrepancyType,
            BigDecimal discrepancyQuantity,
            BigDecimal sourceCostPrice,
            BigDecimal discrepancyAmount,
            String planStatus,
            List<ActionSnapshot> actions)
    {
        public Boundary
        {
            actions = actions == null ? null : List.copyOf(actions);
        }
    }

    public record PreparedExecution(
            String requestId,
            Long caseId,
            Long caseVersionBefore,
            Long caseVersionAfter,
            String caseStatusBefore,
            String caseStatusAfter,
            Long adjudicationId,
            String decisionFingerprint,
            String planStatusBefore,
            String planStatusAfter,
            Long actionId,
            Integer actionSequence,
            String actionType,
            String coverageKind,
            String discrepancyType,
            String effectKind,
            String command,
            String effectReference,
            String actionStatusBefore,
            String actionStatusAfter,
            Long executionVersionBefore,
            Long executionVersionAfter,
            BigDecimal quantity,
            BigDecimal sourceCostPrice,
            BigDecimal amount,
            String responsibleParty,
            String requiredPermission,
            Long executorUserId,
            String executorName,
            Instant executedAt,
            String eventFingerprint,
            List<ActionSnapshot> resultingActions)
    {
        public PreparedExecution
        {
            resultingActions = List.copyOf(resultingActions);
        }
    }

    public record StoredEventSnapshot(
            Long eventId,
            String requestId,
            Long caseId,
            Long caseVersionBefore,
            Long caseVersionAfter,
            String caseStatusBefore,
            String caseStatusAfter,
            Long adjudicationId,
            String decisionFingerprint,
            String planStatusBefore,
            String planStatusAfter,
            Long actionId,
            Integer actionSequence,
            String actionType,
            String coverageKind,
            String discrepancyType,
            String command,
            String effectKind,
            String effectReference,
            String actionStatusBefore,
            String actionStatusAfter,
            Long executionVersionBefore,
            Long executionVersionAfter,
            BigDecimal quantity,
            BigDecimal sourceCostPrice,
            BigDecimal amount,
            String responsibleParty,
            String requiredPermission,
            Long executorUserId,
            String executorName,
            String eventFingerprint,
            Instant executedAt)
    {
    }
}
