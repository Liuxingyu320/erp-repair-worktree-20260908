package com.erp.oa.attendance.timecredit;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.support.AttendanceClientRequestSupport;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.Adjustment;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.Context;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.SourceCandidate;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Apply;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Reverse;
import com.erp.oa.service.BusinessFeatureGate;

/**
 * Store-scoped append-only ledger that exchanges earned overtime for later
 * early-leave minutes inside one salary month.
 */
@Service
public class AttendanceTimeCreditService
{
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern(
            "uuuu-MM");
    private final AttendanceTimeCreditMapper mapper;
    private final ShopScopeService shopScopeService;
    private final BusinessFeatureGate featureGate;

    public AttendanceTimeCreditService(AttendanceTimeCreditMapper mapper,
            ShopScopeService shopScopeService, BusinessFeatureGate featureGate)
    {
        this.mapper = mapper;
        this.shopScopeService = shopScopeService;
        this.featureGate = featureGate;
    }

    @Transactional(readOnly = true)
    public Context context(Long targetDayResultId, Long selectedShopId)
    {
        requireEnabled();
        Long shopId = shopScopeService.resolveRequiredShopDept(selectedShopId);
        DayResult target = requireScopedSettledResult(targetDayResultId,
                shopId, mapper.selectDayResultById(targetDayResultId));
        String salaryMonth = month(target);
        int rawEarly = nonNegative(target.earlyLeaveMinutes);
        int offset = value(mapper.selectNetTargetOffset(target.dayResultId));
        boolean ledgerInconsistent = offset < 0 || offset > rawEarly;

        Context value = new Context();
        value.target = target;
        value.targetRawEarlyLeaveMinutes = rawEarly;
        value.targetOffsetMinutes = offset;
        value.targetRemainingEarlyLeaveMinutes = ledgerInconsistent
                ? 0 : rawEarly - offset;
        value.ledgerInconsistent = ledgerInconsistent;
        value.ledgerWarning = ledgerInconsistent
                ? "日结重算后原抵扣已超过当前早退分钟；只能先撤销失效调整，不能继续新增抵扣"
                : null;
        value.salaryLocked = mapper.countSalaryRecords(target.userId,
                salaryMonth) > 0;
        if (!ledgerInconsistent)
            value.sources = safe(mapper.selectSourceCandidates(shopId,
                    target.userId,
                    YearMonth.parse(salaryMonth, MONTH).atDay(1),
                    target.businessDate)).stream()
                    .filter(source -> !Objects.equals(source.dayResultId,
                            target.dayResultId))
                    .toList();
        value.history = safe(mapper.selectHistoryByTargetDayResultId(
                target.dayResultId));
        return value;
    }

    @Transactional
    public Adjustment apply(Apply command, Long selectedShopId)
    {
        requireEnabled();
        if (command == null || command.sourceDayResultId == null
                || command.targetDayResultId == null
                || command.adjustmentMinutes == null
                || command.adjustmentMinutes <= 0)
            throw new ServiceException("抵扣参数无效");
        Long shopId = shopScopeService.resolveRequiredShopDept(selectedShopId);
        Long operatorUserId = SecurityUtils.getUserId();
        String operatorName = SecurityUtils.getUsername();
        String clientRequestId = requireClientRequestId(
                command.clientRequestId);
        String reason = requireReason(command.reason);

        DayResult targetHint = requireScopedSettledResult(
                command.targetDayResultId, shopId,
                mapper.selectDayResultById(command.targetDayResultId));
        String salaryMonth = month(targetHint);
        lockPeriod(shopId, salaryMonth);

        String fingerprint = AttendanceClientRequestSupport.fingerprint(
                "ATTENDANCE_TIME_CREDIT_APPLY_V1", shopId, operatorUserId,
                command.sourceDayResultId, command.targetDayResultId,
                command.adjustmentMinutes, reason);
        Adjustment replay = mapper.selectByClientRequest(shopId,
                operatorUserId, clientRequestId);
        if (replay != null)
            return requireReplay(replay, fingerprint, "APPLY");

        Map<Long, DayResult> locked = lockResults(
                command.sourceDayResultId, command.targetDayResultId);
        DayResult source = requireScopedSettledResult(
                command.sourceDayResultId, shopId,
                locked.get(command.sourceDayResultId));
        DayResult target = requireScopedSettledResult(
                command.targetDayResultId, shopId,
                locked.get(command.targetDayResultId));
        validatePair(source, target, salaryMonth);
        requireSalaryOpen(target.userId, salaryMonth);

        int rawOvertime = Math.max(0, nonNegative(source.workedMinutes)
                - nonNegative(source.scheduledMinutes));
        int used = value(mapper.selectNetSourceUsed(source.dayResultId));
        int rawEarly = nonNegative(target.earlyLeaveMinutes);
        int offset = value(mapper.selectNetTargetOffset(target.dayResultId));
        validateLedgerBounds(rawOvertime, used, rawEarly, offset);
        if (command.adjustmentMinutes > rawOvertime - used)
            throw new ServiceException("抵扣分钟超过该加班日可用余额");
        if (command.adjustmentMinutes > rawEarly - offset)
            throw new ServiceException("抵扣分钟超过该早退日剩余分钟");

        LocalDateTime now = databaseNow();
        Adjustment value = new Adjustment();
        value.adjustmentNo = number("TCA", now);
        value.clientRequestId = clientRequestId;
        value.requestFingerprint = fingerprint;
        value.adjustmentAction = "APPLY";
        value.shopId = shopId;
        value.userId = target.userId;
        value.userName = target.userName;
        value.salaryMonth = salaryMonth;
        value.sourceDayResultId = source.dayResultId;
        value.sourceScheduleId = source.scheduleId;
        value.sourceBusinessDate = source.businessDate;
        value.targetDayResultId = target.dayResultId;
        value.targetScheduleId = target.scheduleId;
        value.targetBusinessDate = target.businessDate;
        value.adjustmentMinutes = command.adjustmentMinutes;
        value.reason = reason;
        value.operatorUserId = operatorUserId;
        value.operatorName = operatorName;
        value.setCreateBy(operatorName);
        if (mapper.insertAdjustment(value) != 1)
            throw new ServiceException("加班抵扣保存失败");
        return mapper.selectAdjustmentById(value.adjustmentId);
    }

    @Transactional
    public Adjustment reverse(Long adjustmentId, Reverse command,
            Long selectedShopId)
    {
        requireEnabled();
        if (adjustmentId == null || adjustmentId <= 0 || command == null)
            throw new ServiceException("撤销参数无效");
        Long shopId = shopScopeService.resolveRequiredShopDept(selectedShopId);
        Long operatorUserId = SecurityUtils.getUserId();
        String operatorName = SecurityUtils.getUsername();
        String clientRequestId = requireClientRequestId(
                command.clientRequestId);
        String reason = requireReason(command.reason);

        Adjustment hint = mapper.selectAdjustmentById(adjustmentId);
        if (hint == null || !"APPLY".equals(hint.adjustmentAction)
                || !Objects.equals(shopId, hint.shopId))
            throw new ServiceException("原抵扣记录不存在或不属于当前门店");
        lockPeriod(shopId, hint.salaryMonth);

        String fingerprint = AttendanceClientRequestSupport.fingerprint(
                "ATTENDANCE_TIME_CREDIT_REVERSE_V1", shopId,
                operatorUserId, adjustmentId, reason);
        Adjustment replay = mapper.selectByClientRequest(shopId,
                operatorUserId, clientRequestId);
        if (replay != null)
            return requireReplay(replay, fingerprint, "REVERSE");

        Adjustment original = mapper.selectAdjustmentByIdForUpdate(
                adjustmentId);
        if (original == null || !"APPLY".equals(original.adjustmentAction)
                || !Objects.equals(shopId, original.shopId))
            throw new ServiceException("原抵扣记录不存在或不属于当前门店");
        requireSalaryOpen(original.userId, original.salaryMonth);
        if (mapper.countReverse(original.adjustmentId) > 0)
            throw new ServiceException("该抵扣记录已经撤销");
        lockResults(original.sourceDayResultId,
                original.targetDayResultId);

        LocalDateTime now = databaseNow();
        Adjustment value = new Adjustment();
        value.adjustmentNo = number("TCR", now);
        value.clientRequestId = clientRequestId;
        value.requestFingerprint = fingerprint;
        value.adjustmentAction = "REVERSE";
        value.originalAdjustmentId = original.adjustmentId;
        value.shopId = original.shopId;
        value.userId = original.userId;
        value.userName = original.userName;
        value.salaryMonth = original.salaryMonth;
        value.sourceDayResultId = original.sourceDayResultId;
        value.sourceScheduleId = original.sourceScheduleId;
        value.sourceBusinessDate = original.sourceBusinessDate;
        value.targetDayResultId = original.targetDayResultId;
        value.targetScheduleId = original.targetScheduleId;
        value.targetBusinessDate = original.targetBusinessDate;
        value.adjustmentMinutes = original.adjustmentMinutes;
        value.reason = reason;
        value.operatorUserId = operatorUserId;
        value.operatorName = operatorName;
        value.setCreateBy(operatorName);
        if (mapper.insertAdjustment(value) != 1)
            throw new ServiceException("抵扣撤销保存失败");
        return mapper.selectAdjustmentById(value.adjustmentId);
    }

    private void validatePair(DayResult source, DayResult target,
            String salaryMonth)
    {
        if (!Objects.equals(source.userId, target.userId)
                || !Objects.equals(source.shopId, target.shopId))
            throw new ServiceException("加班来源与早退目标必须属于同一员工和门店");
        if (source.businessDate.isAfter(target.businessDate))
            throw new ServiceException("不能使用未来日期的加班抵扣此前早退");
        if (!salaryMonth.equals(month(source))
                || !salaryMonth.equals(month(target)))
            throw new ServiceException("加班与早退必须在同一工资月份");
        if (Objects.equals(source.dayResultId, target.dayResultId))
            throw new ServiceException("加班来源日和早退目标日不能是同一条日结记录");
    }

    private void validateLedgerBounds(int rawOvertime, int used,
            int rawEarly, int offset)
    {
        if (used < 0 || offset < 0 || used > rawOvertime
                || offset > rawEarly)
            throw new ServiceException("抵扣台账与当前日结结果不一致，请先撤销失效调整");
    }

    private Map<Long, DayResult> lockResults(Long... ids)
    {
        List<Long> ordered = java.util.Arrays.stream(ids)
                .filter(Objects::nonNull).distinct().sorted().toList();
        List<DayResult> rows = safe(mapper.selectDayResultsForUpdate(ordered));
        Map<Long, DayResult> values = new LinkedHashMap<>();
        rows.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(value -> value.dayResultId))
                .forEach(value -> values.put(value.dayResultId, value));
        return values;
    }

    private DayResult requireScopedSettledResult(Long id, Long shopId,
            DayResult value)
    {
        if (id == null || id <= 0 || value == null
                || !Objects.equals(shopId, value.shopId))
            throw new ServiceException("考勤日结记录不存在或不属于当前门店");
        if (value.settledAt == null || value.businessDate == null
                || value.userId == null)
            throw new ServiceException("考勤日结尚未完成，不能进行抵扣");
        return value;
    }

    private Adjustment requireReplay(Adjustment value, String fingerprint,
            String action)
    {
        if (!Objects.equals(fingerprint, value.requestFingerprint)
                || !Objects.equals(action, value.adjustmentAction))
            throw new ServiceException("客户端请求编号已被其他抵扣操作使用");
        return value;
    }

    private void lockPeriod(Long shopId, String salaryMonth)
    {
        mapper.ensurePeriodLock(shopId, salaryMonth);
        mapper.lockPeriod(shopId, salaryMonth);
    }

    private void requireSalaryOpen(Long userId, String salaryMonth)
    {
        if (mapper.countSalaryRecords(userId, salaryMonth) > 0)
            throw new ServiceException("该员工本月工资已生成，不能再调整考勤抵扣");
    }

    private String month(DayResult value)
    { return YearMonth.from(value.businessDate).format(MONTH); }

    private String requireClientRequestId(String value)
    {
        String normalized = AttendanceClientRequestSupport.normalizeOptional(
                value, "客户端请求编号格式无效");
        if (normalized == null)
            throw new ServiceException("客户端请求编号不能为空");
        return normalized;
    }

    private String requireReason(String value)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 2 || normalized.length() > 500)
            throw new ServiceException("调整原因需填写2至500个字符");
        return normalized;
    }

    private String number(String prefix, LocalDateTime now)
    {
        return prefix + now.format(DateTimeFormatter.ofPattern(
                "yyyyMMddHHmmss")) + UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 8)
                        .toUpperCase(Locale.ROOT);
    }

    private LocalDateTime databaseNow()
    {
        LocalDateTime value = mapper.selectDatabaseNow();
        if (value == null)
            throw new ServiceException("考勤服务端时间不可用");
        return value.withNano(0);
    }

    private int nonNegative(Integer value)
    { return value == null ? 0 : Math.max(0, value); }

    private int value(Integer value)
    { return value == null ? 0 : value; }

    private <T> List<T> safe(List<T> values)
    { return values == null ? new ArrayList<>() : values; }

    private void requireEnabled()
    { featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2); }
}
