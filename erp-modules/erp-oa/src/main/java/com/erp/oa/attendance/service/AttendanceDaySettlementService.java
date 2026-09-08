package com.erp.oa.attendance.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.CorrectionSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveRequestState;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource;
import com.erp.oa.attendance.dto.AttendanceRequests.DaySettlementCommand;
import com.erp.oa.attendance.dto.AttendanceSettlementViews.Item;
import com.erp.oa.attendance.dto.AttendanceSettlementViews.Preflight;
import com.erp.oa.attendance.dto.AttendanceSettlementViews.Result;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator.Bounds;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator.Evaluation;
import com.erp.oa.service.BusinessFeatureGate;

/** Guarded, all-or-nothing final settlement after approval preflight. */
@Service
public class AttendanceDaySettlementService
{
    private static final int MAX_RANGE_DAYS = 31;

    private final AttendanceV2Mapper mapper;
    private final AttendanceDaySettlementCalculator calculator;
    private final ShopScopeService shopScopeService;
    private final BusinessFeatureGate featureGate;

    @Autowired
    public AttendanceDaySettlementService(AttendanceV2Mapper mapper,
            AttendanceDaySettlementCalculator calculator,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate)
    {
        this.mapper = mapper;
        this.calculator = calculator;
        this.shopScopeService = shopScopeService;
        this.featureGate = featureGate;
    }

    public Preflight preflight(Long shopId, LocalDate dateFrom,
            LocalDate dateTo, Long selectedShopId)
    {
        requireEnabled();
        Long target = requireSelectedShop(shopId, selectedShopId);
        requireRange(dateFrom, dateTo);
        LocalDateTime evaluatedAt = authoritativeNow();
        Preflight response = basePreflight(target, dateFrom, dateTo);
        for (Schedule schedule : schedules(target, dateFrom, dateTo))
        {
            DayResult current = mapper.selectDayResultByScheduleId(
                    schedule.scheduleId);
            Evaluation evaluation = evaluate(schedule, false, evaluatedAt);
            Item item = item(schedule, current, evaluation);
            response.items.add(item);
            if (item.settled) response.settledCount++;
            if (item.ready) response.readyCount++;
            else response.blockedCount++;
        }
        response.totalSchedules = response.items.size();
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result settle(DaySettlementCommand command,
            Long selectedShopId)
    {
        requireEnabled();
        if (command == null)
            throw new ServiceException("DAY_SETTLEMENT_COMMAND_REQUIRED");
        Long shopId = requireSelectedShop(command.shopId, selectedShopId);
        requireRange(command.dateFrom, command.dateTo);
        LocalDateTime settledAt = authoritativeNow();
        List<PendingWrite> writes = new ArrayList<>();
        Result response = new Result();
        response.shopId = shopId;
        response.dateFrom = command.dateFrom;
        response.dateTo = command.dateTo;
        response.recalculated = command.recalculate;

        for (Schedule reference : schedules(shopId, command.dateFrom,
                command.dateTo))
        {
            Schedule schedule = mapper.selectScheduleByIdForUpdate(
                    reference.scheduleId);
            if (schedule == null || !"PUBLISHED".equals(schedule.status)
                    || !shopId.equals(schedule.shopId)
                    || schedule.businessDate.isBefore(command.dateFrom)
                    || schedule.businessDate.isAfter(command.dateTo))
                throw new ServiceException(
                        "DAY_SETTLEMENT_SCHEDULE_SCOPE_CHANGED");
            DayResult current = mapper.selectDayResultByScheduleIdForUpdate(
                    schedule.scheduleId);
            if (current != null && current.settledAt != null
                    && !command.recalculate)
            {
                response.skippedCount++;
                response.skippedScheduleIds.add(schedule.scheduleId);
                continue;
            }
            Evaluation evaluation = evaluate(schedule, true, settledAt);
            if (!evaluation.ready())
                throw new ServiceException(
                        "DAY_SETTLEMENT_PREFLIGHT_BLOCKED:"
                                + schedule.scheduleId + ":"
                                + String.join(",",
                                        evaluation.issueCodes()));
            writes.add(new PendingWrite(schedule, current, evaluation));
        }

        String operator = SecurityUtils.getUsername();
        for (PendingWrite write : writes)
        {
            DayResult value = write.evaluation().result();
            value.settledAt = settledAt;
            value.setUpdateBy(operator);
            if (write.current() == null) value.setCreateBy(operator);
            if (mapper.upsertDayResult(value) <= 0)
                throw new ServiceException("DAY_SETTLEMENT_SAVE_FAILED");
            DayResult saved = mapper.selectDayResultByScheduleId(
                    write.schedule().scheduleId);
            if (saved == null || saved.settledAt == null)
                throw new ServiceException("DAY_SETTLEMENT_VERIFY_FAILED");
            response.dayResults.add(saved);
            response.settledCount++;
        }
        return response;
    }

    private Evaluation evaluate(Schedule schedule, boolean lockRows,
            LocalDateTime evaluatedAt)
    {
        List<ScheduleSegmentSnapshot> segments = safe(
                mapper.selectScheduleSegmentSnapshots(schedule.scheduleId,
                        lockRows));
        Bounds bounds = calculator.bounds(schedule);
        List<PunchEvent> acceptedPunches = safe(
                mapper.selectAcceptedPunches(schedule.scheduleId));
        List<CorrectionSource> corrections = safe(
                mapper.selectSettlementCorrectionSources(schedule.scheduleId,
                        lockRows));
        List<LeaveRequestState> blockingLeaves = safe(
                mapper.selectBlockingLeaveRequests(schedule.userId,
                        schedule.shopId, bounds.start(), bounds.end(),
                        lockRows));
        List<LeaveSegmentSource> approvedLeaves = safe(
                mapper.selectApprovedLeaveSegments(schedule.userId,
                        schedule.shopId, bounds.start(), bounds.end(),
                        lockRows));
        List<RemainingWorkConfirmationSource> confirmations = safe(
                mapper.selectRemainingWorkConfirmationSources(
                        schedule.scheduleId, lockRows));
        return calculator.evaluate(schedule, acceptedPunches, corrections,
                blockingLeaves, approvedLeaves, segments, confirmations,
                evaluatedAt);
    }

    private Item item(Schedule schedule, DayResult current,
            Evaluation evaluation)
    {
        Item value = new Item();
        value.scheduleId = schedule.scheduleId;
        value.userId = schedule.userId;
        value.userName = schedule.userName;
        value.businessDate = schedule.businessDate;
        value.settled = current != null && current.settledAt != null;
        value.ready = evaluation.ready();
        value.issueCodes.addAll(evaluation.issueCodes());
        value.current = current;
        value.preview = evaluation.result();
        value.state = value.ready ? value.settled ? "SETTLED" : "READY"
                : "BLOCKED";
        return value;
    }

    private Preflight basePreflight(Long shopId, LocalDate dateFrom,
            LocalDate dateTo)
    {
        Preflight value = new Preflight();
        value.shopId = shopId;
        value.dateFrom = dateFrom;
        value.dateTo = dateTo;
        return value;
    }

    private List<Schedule> schedules(Long shopId, LocalDate dateFrom,
            LocalDate dateTo)
    {
        return safe(mapper.selectPublishedSchedulesForSettlement(shopId,
                dateFrom, dateTo));
    }

    private Long requireSelectedShop(Long requested, Long selected)
    {
        Long resolved = shopScopeService.resolveRequiredShopDept(selected);
        if (requested == null || !requested.equals(resolved))
            throw new ServiceException("ATTENDANCE_SHOP_SCOPE_MISMATCH");
        return resolved;
    }

    private void requireRange(LocalDate from, LocalDate to)
    {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS)
            throw new ServiceException("DAY_SETTLEMENT_RANGE_INVALID");
    }

    private <T> List<T> safe(List<T> values)
    { return values == null ? List.of() : values; }
    private LocalDateTime authoritativeNow()
    {
        LocalDateTime value = mapper.selectDatabaseNow();
        if (value == null)
            throw new ServiceException("ATTENDANCE_SERVER_TIME_UNAVAILABLE");
        return value.withNano(0);
    }
    private void requireEnabled()
    { featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2); }

    private record PendingWrite(Schedule schedule, DayResult current,
            Evaluation evaluation) { }
}
