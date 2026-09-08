package com.erp.oa.attendance.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.config.AttendanceV2Properties;
import com.erp.oa.attendance.config.AttendanceRuntimePolicy;
import com.erp.oa.attendance.domain.AttendanceModels.Challenge;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.Evidence;
import com.erp.oa.attendance.domain.AttendanceModels.EmployeeOption;
import com.erp.oa.attendance.domain.AttendanceModels.MonthlySummary;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceModels.Shift;
import com.erp.oa.attendance.domain.AttendanceModels.ShiftSegment;
import com.erp.oa.attendance.domain.AttendanceModels.Site;
import com.erp.oa.attendance.dto.AttendanceRequests.ChallengeCreate;
import com.erp.oa.attendance.dto.AttendanceRequests.ChallengeView;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchStatusRequest;
import com.erp.oa.attendance.dto.AttendanceRequests.ScheduleBatch;
import com.erp.oa.attendance.dto.AttendanceRequests.ScheduleItem;
import com.erp.oa.attendance.dto.AttendanceRequests.SchedulePublish;
import com.erp.oa.attendance.dto.AttendanceViews.PunchResult;
import com.erp.oa.attendance.dto.AttendanceViews.PunchStatusResult;
import com.erp.oa.attendance.dto.AttendanceViews.PunchSlotView;
import com.erp.oa.attendance.dto.AttendanceViews.TodayContext;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.CorrectionSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveRequestState;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.service.AttendanceEvidenceStorageService.StoredEvidence;
import com.erp.oa.attendance.support.AttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceAddressResolver.ResolvedAddress;
import com.erp.oa.attendance.support.AttendanceChallengePolicy;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer.Coordinate;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceGeoFence;
import com.erp.oa.attendance.support.AttendanceLocationAuditPolicy;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy.Coverage;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.attendance.support.AttendanceRuleEngine.PunchWindow;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.system.api.model.LoginUser;

@Service
public class AttendanceV2Service
{
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> ACTIVE_STATES = Set.of("ENABLED", "DISABLED");
    private static final int MAX_BATCH = 500;

    private final AttendanceV2Mapper mapper;
    private final AttendanceRuleEngine rules;
    private final AttendanceLocationAuditPolicy locationPolicy;
    private final AttendanceCoordinateTransformer coordinateTransformer;
    private final AttendanceGeoFence geoFence;
    private final AttendanceAddressResolver addressResolver;
    private final AttendanceChallengePolicy challengePolicy;
    private final AttendanceDaySettlementCalculator settlementCalculator;
    private final AttendanceEvidenceStorageService evidenceStorage;
    private final AttendanceV2Properties properties;
    private final AttendanceRuntimePolicy runtimePolicy;
    private final ShopScopeService shopScopeService;
    private final BusinessFeatureGate featureGate;
    private final Clock clock;

    @Autowired
    public AttendanceV2Service(AttendanceV2Mapper mapper,
            AttendanceRuleEngine rules,
            AttendanceLocationAuditPolicy locationPolicy,
            AttendanceCoordinateTransformer coordinateTransformer,
            AttendanceGeoFence geoFence,
            AttendanceAddressResolver addressResolver,
            AttendanceChallengePolicy challengePolicy,
            AttendanceDaySettlementCalculator settlementCalculator,
            AttendanceEvidenceStorageService evidenceStorage,
            AttendanceV2Properties properties,
            AttendanceRuntimePolicy runtimePolicy,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate)
    {
        this(mapper, rules, locationPolicy, coordinateTransformer, geoFence,
                addressResolver,
                challengePolicy, settlementCalculator, evidenceStorage,
                properties, runtimePolicy, shopScopeService, featureGate,
                Clock.system(BUSINESS_ZONE));
    }

    AttendanceV2Service(AttendanceV2Mapper mapper,
            AttendanceRuleEngine rules,
            AttendanceLocationAuditPolicy locationPolicy,
            AttendanceCoordinateTransformer coordinateTransformer,
            AttendanceGeoFence geoFence,
            AttendanceAddressResolver addressResolver,
            AttendanceChallengePolicy challengePolicy,
            AttendanceDaySettlementCalculator settlementCalculator,
            AttendanceEvidenceStorageService evidenceStorage,
            AttendanceV2Properties properties,
            AttendanceRuntimePolicy runtimePolicy,
            ShopScopeService shopScopeService,
            BusinessFeatureGate featureGate, Clock clock)
    {
        this.mapper = mapper;
        this.rules = rules;
        this.locationPolicy = locationPolicy;
        this.coordinateTransformer = coordinateTransformer;
        this.geoFence = geoFence;
        this.addressResolver = addressResolver;
        this.challengePolicy = challengePolicy;
        this.settlementCalculator = settlementCalculator;
        this.evidenceStorage = evidenceStorage;
        this.properties = properties;
        this.runtimePolicy = runtimePolicy;
        this.shopScopeService = shopScopeService;
        this.featureGate = featureGate;
        this.clock = clock;
    }

    public List<Shift> listShifts(String status)
    {
        List<Shift> values = mapper.selectShifts(blankToNull(status));
        for (Shift shift : values) shift.segments =
                mapper.selectShiftSegments(shift.shiftId);
        return values;
    }

    public Shift getShift(Long shiftId)
    {
        Shift shift = requireShift(shiftId);
        shift.segments = mapper.selectShiftSegments(shiftId);
        return shift;
    }

    @Transactional
    public Shift createShift(Shift shift)
    {
        validateShift(shift, true);
        shift.shiftId = null;
        shift.status = "DISABLED";
        shift.photoRequired = true;
        shift.locationRequired = true;
        shift.setCreateBy(SecurityUtils.getUsername());
        if (mapper.insertShift(shift) != 1)
            throw new ServiceException("SHIFT_CREATE_FAILED");
        replaceSegments(shift);
        return getShift(shift.shiftId);
    }

    @Transactional
    public Shift updateShift(Long shiftId, Shift shift)
    {
        if (shift == null) throw new ServiceException("SHIFT_REQUIRED");
        Shift current = requireShift(shiftId);
        shift.shiftId = shiftId;
        if (shift.rowVersion == null)
            throw new ServiceException("SHIFT_VERSION_REQUIRED");
        shift.status = current.status;
        shift.photoRequired = true;
        shift.locationRequired = true;
        validateShift(shift, false);
        shift.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updateShift(shift) != 1)
            throw new ServiceException("SHIFT_VERSION_CONFLICT");
        replaceSegments(shift);
        return getShift(shiftId);
    }

    @Transactional
    public void deleteShift(Long shiftId, Long rowVersion)
    {
        if (shiftId == null || rowVersion == null)
            throw new ServiceException("SHIFT_DELETE_VERSION_REQUIRED");
        Shift current = mapper.selectShiftByIdForUpdate(shiftId);
        if (current == null) throw new ServiceException("SHIFT_NOT_FOUND");
        if (!"DISABLED".equals(current.status))
            throw new ServiceException("SHIFT_MUST_BE_DISABLED");
        mapper.deleteShiftSegments(shiftId);
        if (mapper.deleteShift(shiftId, rowVersion) != 1)
            throw new ServiceException(
                    "SHIFT_DELETE_BLOCKED_OR_VERSION_CONFLICT");
    }

    @Transactional
    public Shift changeShiftStatus(Long shiftId, String status, Long rowVersion)
    {
        String target = requireStatus(status);
        requireShift(shiftId);
        if (mapper.updateShiftStatus(shiftId, target, rowVersion,
                SecurityUtils.getUsername()) != 1)
            throw new ServiceException("SHIFT_VERSION_CONFLICT");
        return getShift(shiftId);
    }

    public List<Site> listSites(Long shopId, String status,
            Long selectedShopId)
    {
        Long target = shopId;
        List<Long> scope;
        if (target != null)
        {
            requireSameScopedShop(target, selectedShopId);
            scope = List.of(target);
        }
        else
        {
            scope = shopScopeService.resolveScopeDeptIds(selectedShopId);
        }
        return mapper.selectSites(target, blankToNull(status), scope);
    }

    public Site getSite(Long siteId, Long selectedShopId)
    {
        Site site = requireSite(siteId);
        requireSameScopedShop(site.shopId, selectedShopId);
        return site;
    }

    @Transactional
    public Site createSite(Site site, Long selectedShopId)
    {
        if (site == null) throw new ServiceException("SITE_REQUIRED");
        site.shopId = requireSameScopedShop(site.shopId, selectedShopId);
        site.siteId = null;
        site.status = "DISABLED";
        site.rowVersion = 0L;
        validateSite(site);
        site.setCreateBy(SecurityUtils.getUsername());
        if (mapper.insertSite(site) != 1)
            throw new ServiceException("SITE_CREATE_FAILED");
        return getSite(site.siteId, selectedShopId);
    }

    @Transactional
    public Site updateSite(Long siteId, Site site, Long selectedShopId)
    {
        if (site == null) throw new ServiceException("SITE_REQUIRED");
        Site current = requireSite(siteId);
        requireSameScopedShop(current.shopId, selectedShopId);
        site.siteId = siteId;
        site.shopId = current.shopId;
        if (site.rowVersion == null)
            throw new ServiceException("SITE_VERSION_REQUIRED");
        validateSite(site);
        site.setUpdateBy(SecurityUtils.getUsername());
        if (mapper.updateSite(site) != 1)
            throw new ServiceException("SITE_VERSION_CONFLICT");
        return getSite(siteId, selectedShopId);
    }

    @Transactional
    public void deleteSite(Long siteId, Long rowVersion,
            Long selectedShopId)
    {
        if (siteId == null || rowVersion == null)
            throw new ServiceException("SITE_DELETE_VERSION_REQUIRED");
        Long shopId = requireSameScopedShop(null, selectedShopId);
        Site current = mapper.selectSiteByIdForUpdate(siteId);
        if (current == null || !shopId.equals(current.shopId))
            throw new ServiceException("ATTENDANCE_SITE_NOT_FOUND_IN_SHOP");
        if (!"DISABLED".equals(current.status))
            throw new ServiceException("SITE_MUST_BE_DISABLED");
        if (mapper.deleteSite(siteId, shopId, rowVersion) != 1)
            throw new ServiceException(
                    "SITE_DELETE_BLOCKED_OR_VERSION_CONFLICT");
    }

    @Transactional
    public Site changeSiteStatus(Long siteId, String status, Long rowVersion,
            Long selectedShopId)
    {
        Site current = requireSite(siteId);
        requireSameScopedShop(current.shopId, selectedShopId);
        if (mapper.updateSiteStatus(siteId, requireStatus(status), rowVersion,
                SecurityUtils.getUsername()) != 1)
            throw new ServiceException("SITE_VERSION_CONFLICT");
        return getSite(siteId, selectedShopId);
    }

    public List<Schedule> listSchedules(Long shopId, LocalDate dateFrom,
            LocalDate dateTo, Long userId, Long selectedShopId)
    {
        Long target = requireSameScopedShop(shopId, selectedShopId);
        requireDateRange(dateFrom, dateTo);
        List<Schedule> values = mapper.selectSchedules(target, dateFrom,
                dateTo, userId, List.of(target));
        if (values == null) return List.of();
        for (Schedule value : values) hydratePublishedSegments(value, false);
        return values;
    }

    public List<EmployeeOption> employeeOptions(Long shopId, String keyword,
            Long selectedShopId)
    {
        Long target = requireSameScopedShop(shopId, selectedShopId);
        String query = blankToNull(keyword);
        if (query != null && query.length() > 50)
            throw new ServiceException("EMPLOYEE_OPTION_KEYWORD_TOO_LONG");
        return mapper.selectActiveEmployeeOptions(target, query);
    }

    @Transactional
    public List<Schedule> saveScheduleBatch(ScheduleBatch request,
            Long selectedShopId)
    {
        if (request == null || request.items == null
                || request.items.isEmpty() || request.items.size() > MAX_BATCH)
            throw new ServiceException("SCHEDULE_BATCH_SIZE_INVALID");
        Long shopId = requireSameScopedShop(request.shopId, selectedShopId);
        List<Schedule> saved = new ArrayList<>();
        for (ScheduleItem item : request.items)
        {
            if (item == null || item.userId == null
                    || item.businessDate == null || item.shiftId == null
                    || item.siteId == null)
                throw new ServiceException("SCHEDULE_ITEM_INCOMPLETE");
            validateScheduleDate(item.businessDate);
            String userName = mapper.selectActiveEmployeeNameInShop(
                    item.userId, shopId);
            if (userName == null || userName.isBlank())
                throw new ServiceException("EMPLOYEE_NOT_ACTIVE_IN_SHOP");
            Shift shift = requireShift(item.shiftId);
            Schedule existing = mapper.selectScheduleByUserAndDateForUpdate(
                    item.userId, item.businessDate);
            Site site = requireEnabledSiteInShop(item.siteId, shopId);
            if (existing == null)
            {
                Schedule value = new Schedule();
                value.scheduleNo = nextNo("AS");
                value.userId = item.userId;
                value.userName = userName;
                value.shopId = shopId;
                value.businessDate = item.businessDate;
                value.shiftId = shift.shiftId;
                value.siteId = site.siteId;
                value.status = "DRAFT";
                value.setCreateBy(SecurityUtils.getUsername());
                if (mapper.insertDraftSchedule(value) != 1)
                    throw new ServiceException("SCHEDULE_CREATE_FAILED");
                saved.add(mapper.selectScheduleById(value.scheduleId));
            }
            else
            {
                if (!shopId.equals(existing.shopId))
                    throw new ServiceException(
                            "SCHEDULE_CONFLICT_IN_OTHER_SHOP");
                if (!"DRAFT".equals(existing.status))
                    throw new ServiceException("PUBLISHED_SCHEDULE_IMMUTABLE");
                if (item.scheduleId != null
                        && !item.scheduleId.equals(existing.scheduleId))
                    throw new ServiceException("SCHEDULE_ID_CONFLICT");
                existing.userName = userName;
                existing.shopId = shopId;
                existing.shiftId = shift.shiftId;
                existing.siteId = site.siteId;
                if (item.rowVersion == null)
                    throw new ServiceException("SCHEDULE_VERSION_REQUIRED");
                existing.rowVersion = item.rowVersion;
                existing.setUpdateBy(SecurityUtils.getUsername());
                if (mapper.updateDraftSchedule(existing) != 1)
                    throw new ServiceException("SCHEDULE_VERSION_CONFLICT");
                saved.add(mapper.selectScheduleById(existing.scheduleId));
            }
        }
        return saved;
    }

    @Transactional
    public void deleteDraftSchedule(Long scheduleId, Long rowVersion,
            Long selectedShopId)
    {
        if (scheduleId == null || rowVersion == null)
            throw new ServiceException("SCHEDULE_DELETE_VERSION_REQUIRED");
        Long shopId = requireSameScopedShop(null, selectedShopId);
        Schedule current = mapper.selectScheduleByIdForUpdate(scheduleId);
        if (current == null || !shopId.equals(current.shopId))
            throw new ServiceException("SCHEDULE_NOT_FOUND_IN_SHOP");
        if (!"DRAFT".equals(current.status))
            throw new ServiceException("PUBLISHED_SCHEDULE_IMMUTABLE");
        if (mapper.deleteDraftSchedule(scheduleId, shopId, rowVersion) != 1)
            throw new ServiceException("SCHEDULE_VERSION_CONFLICT");
    }

    @Transactional
    public List<Schedule> publishSchedules(SchedulePublish request,
            Long selectedShopId)
    {
        if (request == null || request.scheduleIds == null
                || request.scheduleIds.isEmpty()
                || request.scheduleIds.size() > MAX_BATCH)
            throw new ServiceException("SCHEDULE_PUBLISH_SIZE_INVALID");
        Long shopId = requireSameScopedShop(request.shopId, selectedShopId);
        List<Schedule> published = new ArrayList<>();
        for (Long id : request.scheduleIds)
        {
            Schedule current = mapper.selectScheduleByIdForUpdate(id);
            if (current == null || !shopId.equals(current.shopId))
                throw new ServiceException("SCHEDULE_NOT_FOUND_IN_SHOP");
            if (mapper.countActiveEmployeeInShop(current.userId, shopId) <= 0)
                throw new ServiceException("EMPLOYEE_NOT_ACTIVE_IN_SHOP");
            Shift publishedShift = mapper.selectShiftByIdForUpdate(
                    current.shiftId);
            if (publishedShift == null)
                throw new ServiceException("SHIFT_NOT_FOUND");
            publishedShift.segments = mapper.selectShiftSegments(
                    publishedShift.shiftId);
            validateShift(publishedShift, false);
            requireEnabledSiteInShop(current.siteId, shopId);
            if (mapper.publishSchedule(id, shopId, SecurityUtils.getUserId(),
                    SecurityUtils.getUsername()) != 1)
                throw new ServiceException("SCHEDULE_PUBLISH_PRECONDITION_FAILED");
            mapper.deleteScheduleSegmentSnapshots(id);
            if (mapper.insertScheduleSegmentSnapshotsFromShift(id,
                    publishedShift.shiftId) <= 0)
                throw new ServiceException("SHIFT_SEGMENT_SNAPSHOT_FAILED");
            Schedule value = mapper.selectScheduleById(id);
            hydratePublishedSegments(value, false);
            upsertInitialDayResult(value);
            published.add(value);
        }
        return published;
    }

    public TodayContext today()
    {
        requireEnabled();
        LocalDateTime now = authoritativeNow();
        Long userId = SecurityUtils.getUserId();
        List<Schedule> candidates = mapper
                .selectPublishedScheduleCandidatesForUser(userId,
                        now.toLocalDate(), now.toLocalDate().minusDays(1));
        TodayContext context = new TodayContext();
        context.serverTime = now;
        if (candidates == null || candidates.isEmpty())
            return locked(context, "LOCKED_NO_SCHEDULE", "今日没有已发布排班");
        Schedule schedule = chooseCandidate(candidates, now);
        hydratePublishedSegments(schedule, false);
        context.schedule = schedule;
        context.punchModeSnapshot = rules.normalizePunchMode(
                schedule.punchModeSnapshot);
        if (mapper.countActiveEmployeeInShop(userId, schedule.shopId) <= 0)
            return locked(context, "LOCKED_NOT_ACTIVE", "当前员工不在该门店有效范围");
        context.latestPunch = mapper.selectLatestPunch(schedule.scheduleId);
        if (context.latestPunch != null)
            context.latestEvidenceId = mapper.selectEvidenceIdForPunchInScope(
                    context.latestPunch.punchEventId, schedule.scheduleId,
                    userId);
        context.dayResult = mapper.selectDayResultByScheduleId(schedule.scheduleId);
        if (isPerWorkSegment(schedule))
            return populateSegmentToday(context, schedule, now);
        BoundaryPunchState boundary = boundaryPunchState(schedule, false);
        if (boundary.fullyCovered())
            return locked(context, "ON_LEAVE",
                    "当前班次已由批准请假全额覆盖，无需打卡");
        if (boundary.nextType() == null
                && boundary.requiresRemainingWorkConfirmation())
            return locked(context, "REMAINING_WORK_CONFIRMATION_REQUIRED",
                    "部分请假之间仍有工作时间，等待管理者核验");
        if (boundary.nextType() == null)
            return locked(context, "COMPLETED", "今日打卡已完成");
        context.allowedPunchType = boundary.nextType();
        PunchWindow window = rules.window(schedule, context.allowedPunchType);
        if (now.isBefore(window.opensAt()) || now.isAfter(window.closesAt()))
            return locked(context, "LOCKED_OUTSIDE_WINDOW", "当前不在允许打卡时间内");
        context.canPunch = true;
        context.state = "READY_" + context.allowedPunchType;
        return context;
    }

    @Transactional
    public ChallengeView issueChallenge(ChallengeCreate request)
    {
        requireEnabled();
        if (request == null || request.scheduleId == null)
            throw new ServiceException("CHALLENGE_REQUEST_INVALID");
        String type = rules.normalizeType(request.punchType);
        LocalDateTime now = authoritativeNow();
        Schedule schedule = mapper.selectScheduleById(request.scheduleId);
        requireSelfPublishedSchedule(schedule);
        SegmentPunchBinding segmentBinding = null;
        if (isPerWorkSegment(schedule))
            segmentBinding = requireNextSegmentSlot(schedule,
                    request.punchSlotKey, type, now, false);
        else
        {
            requireBoundarySlotAbsent(request.punchSlotKey);
            requireExpectedBoundaryPunch(schedule, type, false);
            rules.requireWithinWindow(schedule, type, now);
        }
        Challenge challenge = new Challenge();
        challenge.challengeToken = randomToken();
        challenge.scheduleId = schedule.scheduleId;
        challenge.userId = SecurityUtils.getUserId();
        challenge.shopId = schedule.shopId;
        challenge.businessDate = schedule.businessDate;
        challenge.punchType = type;
        if (segmentBinding != null)
        {
            challenge.scheduleSegmentSnapshotId = segmentBinding.segment()
                    .scheduleSegmentSnapshotId;
            challenge.punchSlotKey = segmentBinding.slot().punchSlotKey;
        }
        challenge.status = "ISSUED";
        challenge.issuedAt = now;
        challenge.expiresAt = now.plusSeconds(
                runtimePolicy.challengeTtlSeconds());
        if (mapper.insertChallenge(challenge) != 1)
            throw new ServiceException("CHALLENGE_CREATE_FAILED");
        return new ChallengeView(challenge.challengeToken,
                challenge.expiresAt, challenge.scheduleId, type,
                challenge.punchSlotKey);
    }

    @Transactional
    public PunchResult punch(PunchCommand command, MultipartFile photo)
    {
        requireEnabled();
        if (command == null || command.challengeToken == null
                || command.challengeToken.isBlank())
            throw new ServiceException("PUNCH_CHALLENGE_REQUIRED");
        LocalDateTime now = authoritativeNow();
        String type = rules.normalizeType(command.punchType);
        Challenge challenge = mapper.selectChallengeByTokenForUpdate(
                command.challengeToken);
        challengePolicy.requireUsable(challenge, SecurityUtils.getUserId(),
                type, now);
        requireFreshCaptureTime(command.clientCaptureTime,
                challenge.issuedAt, now);
        Schedule schedule = mapper.selectScheduleByIdForUpdate(
                challenge.scheduleId);
        requireSelfPublishedSchedule(schedule);
        if (!schedule.shopId.equals(challenge.shopId)
                || !schedule.businessDate.equals(challenge.businessDate))
            throw new ServiceException("PUNCH_CHALLENGE_SCOPE_CHANGED");
        DayResult currentDayResult = mapper.selectDayResultByScheduleId(
                schedule.scheduleId);
        if (currentDayResult != null && currentDayResult.settledAt != null)
            throw new ServiceException("PUNCH_DAY_RESULT_ALREADY_SETTLED");
        SegmentPunchBinding segmentBinding = null;
        if (isPerWorkSegment(schedule))
        {
            String requestedSlot = blankToNull(command.punchSlotKey);
            if (requestedSlot == null
                    || !requestedSlot.equals(challenge.punchSlotKey))
                throw new ServiceException("PUNCH_SLOT_CHALLENGE_MISMATCH");
            segmentBinding = requireNextSegmentSlot(schedule, requestedSlot,
                    type, now, true);
            if (!Objects.equals(challenge.scheduleSegmentSnapshotId,
                    segmentBinding.segment().scheduleSegmentSnapshotId))
                throw new ServiceException("PUNCH_SEGMENT_CHALLENGE_MISMATCH");
        }
        else
        {
            requireBoundarySlotAbsent(command.punchSlotKey);
            if (challenge.punchSlotKey != null
                    || challenge.scheduleSegmentSnapshotId != null)
                throw new ServiceException("PUNCH_BOUNDARY_CHALLENGE_INVALID");
            requireExpectedBoundaryPunch(schedule, type, true);
            rules.requireWithinWindow(schedule, type, now);
        }
        String coordinateSystem = requireAuditLocation(command);
        GeofenceResult geofence = requireInsidePublishedGeofence(schedule,
                command, coordinateSystem);
        ResolvedAddress address = requireResolvedAddress(
                addressResolver.resolve(command.latitude,
                        command.longitude, coordinateSystem));

        String eventNo = nextNo("AP");
        String displayName = safeName(schedule.userName);
        String shopOrganizationPath = requiredWatermarkValue(
                mapper.selectShopOrganizationPath(schedule.shopId),
                "ATTENDANCE_SHOP_ORGANIZATION_PATH_UNAVAILABLE");
        String shiftName = requiredWatermarkValue(
                schedule.shiftNameSnapshot,
                "ATTENDANCE_SHIFT_NAME_UNAVAILABLE");
        List<String> watermark = new ArrayList<>();
        watermark.add("员工：" + displayName);
        watermark.add("门店：" + shopOrganizationPath);
        watermark.add("班次：" + shiftName);
        if (segmentBinding != null)
            watermark.add("工作段：" + segmentBinding.slot().segmentLabel);
        watermark.add("时间：" + now.format(DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm:ss")) + "  类型：" + type);
        watermark.add("地址：" + address.formattedAddress());
        watermark.add("凭证编号：" + eventNo);
        StoredEvidence stored = evidenceStorage.store(eventNo,
                schedule.businessDate, photo, watermark);
        registerRollbackCleanup(stored);

        PunchEvent event = new PunchEvent();
        event.eventNo = eventNo;
        event.scheduleId = schedule.scheduleId;
        event.challengeId = challenge.challengeId;
        event.userId = schedule.userId;
        event.userName = schedule.userName;
        event.shopId = schedule.shopId;
        event.businessDate = schedule.businessDate;
        event.punchType = type;
        if (segmentBinding != null)
        {
            event.scheduleSegmentSnapshotId = segmentBinding.segment()
                    .scheduleSegmentSnapshotId;
            event.punchSlotKey = segmentBinding.slot().punchSlotKey;
        }
        event.serverPunchTime = now;
        event.clientCaptureTime = command.clientCaptureTime;
        event.longitude = command.longitude;
        event.latitude = command.latitude;
        event.accuracyMeters = command.accuracyMeters;
        event.distanceMeters = geofence.distanceMeters();
        event.coordinateSystem = coordinateSystem;
        event.resolvedAddress = address.formattedAddress();
        event.geofenceStatus = geofence.status();
        event.verificationStatus = "ACCEPTED";
        event.clientRequestId = defaultClientRequestId(command.clientRequestId,
                challenge.challengeToken);
        event.clientIp = truncate(command.clientIp, 64);
        event.userAgent = truncate(command.userAgent, 500);
        event.deviceId = truncate(command.deviceId, 128);
        event.appVersion = truncate(command.appVersion, 32);
        event.riskFlags = appendRisk(
                clientClockRisk(command.clientCaptureTime, now),
                "ADDRESS_RESOLVED:" + address.provider());
        if (mapper.insertPunchEvent(event) != 1)
            throw new ServiceException("PUNCH_EVENT_CREATE_FAILED");

        Evidence evidence = new Evidence();
        evidence.punchEventId = event.punchEventId;
        evidence.originalName = stored.originalName();
        evidence.originalStoragePath = stored.originalPath();
        evidence.watermarkedStoragePath = stored.watermarkedPath();
        evidence.contentType = "image/jpeg";
        evidence.fileExtension = "jpg";
        evidence.originalSize = stored.originalSize();
        evidence.watermarkedSize = stored.watermarkedSize();
        evidence.originalSha256 = stored.originalSha256();
        evidence.watermarkedSha256 = stored.watermarkedSha256();
        evidence.imageWidth = stored.width();
        evidence.imageHeight = stored.height();
        evidence.watermarkPayload = stored.watermarkPayload();
        evidence.capturedAt = command.clientCaptureTime;
        evidence.uploadedBy = SecurityUtils.getUserId();
        if (mapper.insertEvidence(evidence) != 1)
            throw new ServiceException("PUNCH_EVIDENCE_CREATE_FAILED");

        DayResult result = recalculate(schedule, now);
        if (mapper.consumeChallenge(challenge.challengeId,
                event.punchEventId, now, challenge.rowVersion) != 1)
            throw new ServiceException("PUNCH_CHALLENGE_CONSUME_CONFLICT");
        PunchResult response = new PunchResult();
        response.success = true;
        response.event = event;
        response.dayResult = result;
        response.evidenceId = evidence.evidenceId;
        return response;
    }

    /**
     * Reconciles a punch after the caller lost the original response. The
     * accepted event lookup deliberately happens before the challenge lock so
     * a committed success wins even though its challenge is already consumed.
     * When the first consistent read misses a concurrent insert, the second
     * locking read observes it after the challenge lock is acquired.
     */
    @Transactional
    public PunchStatusResult punchStatus(PunchStatusRequest request,
            Long selectedShopId)
    {
        requireEnabled();
        String clientRequestId = requirePunchStatusRequestId(request);
        String challengeToken = requirePunchStatusChallengeToken(request);
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
        Long shopId = shopScopeService.resolveRequiredShopDept(
                selectedShopId);

        PunchEvent event = mapper.selectPunchByClientRequestForUser(userId,
                clientRequestId, false);
        if (event != null)
        {
            Challenge challenge = mapper.selectChallengeByToken(
                    challengeToken);
            return acceptedPunchStatus(event, challenge, userId, shopId,
                    clientRequestId, challengeToken);
        }

        Challenge challenge = mapper.selectChallengeByTokenForUpdate(
                challengeToken);
        requirePunchStatusChallengeScope(challenge, userId, shopId,
                challengeToken);

        // A concurrent punch may have committed while this transaction waited
        // for the challenge row. A locking read observes that committed row
        // even under MySQL's default REPEATABLE READ isolation.
        event = mapper.selectPunchByClientRequestForUser(userId,
                clientRequestId, true);
        if (event != null)
            return acceptedPunchStatus(event, challenge, userId, shopId,
                    clientRequestId, challengeToken);

        if (challenge.consumedAt != null
                || challenge.consumedEventId != null)
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");

        LocalDateTime now = authoritativeNow();
        if (challenge.expiresAt == null)
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
        boolean expired = challenge.expiresAt.isBefore(now);
        PunchStatusResult response = new PunchStatusResult();
        response.clientRequestId = clientRequestId;
        if ("ISSUED".equals(challenge.status) && !expired)
            response.status = "PENDING";
        else if (("ISSUED".equals(challenge.status)
                || "EXPIRED".equals(challenge.status)) && expired)
            response.status = "NOT_ACCEPTED";
        else
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
        return response;
    }

    private PunchStatusResult acceptedPunchStatus(PunchEvent event,
            Challenge challenge, Long userId, Long shopId,
            String clientRequestId, String challengeToken)
    {
        requirePunchStatusChallengeScope(challenge, userId, shopId,
                challengeToken);
        if (event.punchEventId == null || event.punchEventId <= 0
                || blankToNull(event.resolvedAddress) == null
                || !"ACCEPTED".equals(event.verificationStatus)
                || !Objects.equals(event.userId, userId)
                || !Objects.equals(event.shopId, shopId)
                || !Objects.equals(event.clientRequestId, clientRequestId)
                || !Objects.equals(event.challengeId,
                        challenge.challengeId)
                || !Objects.equals(event.scheduleId, challenge.scheduleId)
                || !Objects.equals(event.businessDate,
                        challenge.businessDate)
                || !Objects.equals(event.punchType, challenge.punchType)
                || !Objects.equals(event.scheduleSegmentSnapshotId,
                        challenge.scheduleSegmentSnapshotId)
                || !Objects.equals(event.punchSlotKey,
                        challenge.punchSlotKey)
                || !"CONSUMED".equals(challenge.status)
                || challenge.consumedAt == null
                || !Objects.equals(challenge.consumedEventId,
                        event.punchEventId))
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
        Long evidenceId = mapper.selectEvidenceIdForPunchInScope(
                event.punchEventId, event.scheduleId, userId);
        if (evidenceId == null || evidenceId <= 0)
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
        PunchStatusResult response = new PunchStatusResult();
        response.status = "ACCEPTED";
        response.clientRequestId = clientRequestId;
        response.event = event;
        response.evidenceId = evidenceId;
        return response;
    }

    private void requirePunchStatusChallengeScope(Challenge challenge,
            Long userId, Long shopId, String challengeToken)
    {
        if (challenge == null
                || !Objects.equals(challenge.challengeToken, challengeToken)
                || !Objects.equals(challenge.userId, userId)
                || !Objects.equals(challenge.shopId, shopId))
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
    }

    private String requirePunchStatusRequestId(PunchStatusRequest request)
    {
        String value = request == null ? null
                : blankToNull(request.clientRequestId);
        if (value == null || value.length() > 64
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}"))
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
        return value;
    }

    private String requirePunchStatusChallengeToken(PunchStatusRequest request)
    {
        String value = request == null ? null
                : blankToNull(request.challengeToken);
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new ServiceException("PUNCH_STATUS_CONTEXT_INVALID");
        return value;
    }

    public EvidenceContent evidenceContent(Long evidenceId,
            Long selectedShopId)
    {
        requireEnabled();
        Evidence evidence = mapper.selectEvidenceById(evidenceId);
        PunchEvent event = mapper.selectPunchByEvidenceId(evidenceId);
        if (evidence == null || event == null)
            throw new ServiceException("ATTENDANCE_EVIDENCE_NOT_FOUND");
        boolean owner = event.userId.equals(SecurityUtils.getUserId());
        boolean sensitiveReader = hasPermission(
                "oa:attendance:record:evidence");
        if (!owner)
        {
            if (!sensitiveReader)
                throw new ServiceException("ATTENDANCE_EVIDENCE_FORBIDDEN");
            requireSameScopedShop(event.shopId, selectedShopId);
        }
        Path path = evidenceStorage.resolveWatermarked(
                evidence.watermarkedStoragePath);
        return new EvidenceContent(path, event.eventNo + ".jpg",
                evidence.watermarkedSize == null ? path.toFile().length()
                        : evidence.watermarkedSize);
    }

    public List<DayResult> dayResults(Long userId, LocalDate dateFrom,
            LocalDate dateTo)
    {
        requireEnabled();
        requireDateRange(dateFrom, dateTo);
        return mapper.selectDayResultsByUserAndRange(userId, dateFrom, dateTo);
    }

    public List<DayResult> listDayResults(Long shopId, LocalDate dateFrom,
            LocalDate dateTo, Long userId, Long selectedShopId)
    {
        requireEnabled();
        Long target = requireSameScopedShop(shopId, selectedShopId);
        requireDateRange(dateFrom, dateTo);
        return mapper.selectDayResultsByShopAndRange(target, dateFrom,
                dateTo, userId);
    }

    public MonthlySummary monthlySummary(Long userId, YearMonth month)
    {
        requireEnabled();
        if (userId == null || month == null)
            throw new ServiceException("ATTENDANCE_MONTH_QUERY_INVALID");
        return mapper.summarizeDayResultsByUserAndRange(userId,
                month.atDay(1), month.atEndOfMonth());
    }

    public int countUnfinalized(Long shopId, YearMonth month,
            Long selectedShopId)
    {
        requireEnabled();
        Long target = requireSameScopedShop(shopId, selectedShopId);
        return mapper.countUnfinalizedDayResults(target, month.atDay(1),
                month.atEndOfMonth());
    }

    private TodayContext populateSegmentToday(TodayContext context,
            Schedule schedule, LocalDateTime now)
    {
        SegmentPunchState state = segmentPunchState(schedule, now, false);
        context.punchSlots = state.slots();
        context.nextPunchSlot = state.nextSlot();
        if (state.nextSlot() == null)
        {
            if (state.allWorkSegmentsExempt())
                return locked(context, "ON_LEAVE",
                        "当前班次的所有工作段均已由批准请假覆盖，无需打卡");
            if (state.hasRemainingWorkPending())
                return locked(context,
                        "REMAINING_WORK_CONFIRMATION_REQUIRED",
                        "部分请假之间仍有工作时间，等待管理者核验");
            if (state.hasMissingSlots())
                return locked(context, "CLOSED_WITH_MISSING",
                        "当日工作段已结束，存在缺卡记录，请申请补卡");
            return locked(context, "COMPLETED", "今日各工作段打卡已完成");
        }
        PunchSlotView next = state.nextSlot();
        context.allowedPunchType = next.punchType;
        context.allowedPunchSlotKey = next.punchSlotKey;
        if (insideWindow(next, now))
        {
            next.status = "READY";
            context.canPunch = true;
            context.state = "READY_" + next.punchType;
            return context;
        }
        if (isBetweenWorkSegments(schedule, state, next, now))
        {
            next.status = "WAITING_BREAK";
            String resumesAt = next.startAt == null ? "下一工作段"
                    : next.startAt.format(DateTimeFormatter.ofPattern("HH:mm"));
            return locked(context, "BETWEEN_SEGMENTS",
                    "休息中，" + resumesAt + " 继续上班");
        }
        next.status = "PENDING";
        return locked(context, "LOCKED_OUTSIDE_WINDOW",
                "当前不在该工作段允许打卡时间内");
    }

    private SegmentPunchBinding requireNextSegmentSlot(Schedule schedule,
            String requestedSlotKey, String punchType, LocalDateTime now,
            boolean lockRows)
    {
        SegmentPunchState state = segmentPunchState(schedule, now, lockRows);
        PunchSlotView next = state.nextSlot();
        if (next == null)
        {
            if (state.allWorkSegmentsExempt())
                throw new ServiceException("PUNCH_BLOCKED_BY_APPROVED_LEAVE");
            if (state.hasRemainingWorkPending())
                throw new ServiceException(
                        "PUNCH_REMAINING_WORK_CONFIRMATION_REQUIRED");
            if (state.hasMissingSlots())
                throw new ServiceException("PUNCH_DAY_CLOSED_WITH_MISSING");
            throw new ServiceException("ALL_PUNCH_SLOTS_COMPLETED");
        }
        String slotKey = blankToNull(requestedSlotKey);
        if (slotKey == null)
            throw new ServiceException("PUNCH_SLOT_REQUIRED");
        if (!slotKey.equals(next.punchSlotKey))
            throw new ServiceException("PUNCH_SLOT_NOT_NEXT");
        if (!next.punchType.equals(punchType))
            throw new ServiceException("PUNCH_SLOT_TYPE_MISMATCH");
        ScheduleSegmentSnapshot segment = state.segmentById().get(
                next.scheduleSegmentSnapshotId);
        if (segment == null)
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_INVALID");
        if (!insideWindow(next, now))
            throw new ServiceException("OUTSIDE_PUNCH_WINDOW");
        return new SegmentPunchBinding(segment, next);
    }

    private SegmentPunchState segmentPunchState(Schedule schedule,
            LocalDateTime now, boolean lockRows)
    {
        if (!isPerWorkSegment(schedule))
            throw new ServiceException("PER_WORK_SEGMENT_SCHEDULE_REQUIRED");
        hydratePublishedSegments(schedule, lockRows);
        List<ScheduleSegmentSnapshot> workSegments = schedule.segmentSnapshots
                .stream().filter(Objects::nonNull)
                .filter(value -> "WORK".equals(value.segmentType))
                .sorted(Comparator.comparing(value -> value.segmentOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (workSegments.isEmpty())
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_REQUIRED");

        AttendanceDaySettlementCalculator.Bounds bounds =
                settlementCalculator.bounds(schedule);
        List<LeaveSegmentSource> leaves = mapper.selectApprovedLeaveSegments(
                schedule.userId, schedule.shopId, bounds.start(), bounds.end(),
                lockRows);
        if (leaves == null) leaves = List.of();

        List<PunchSlotView> slots = new ArrayList<>();
        Map<String, PunchSlotView> slotByKey = new LinkedHashMap<>();
        Map<Long, ScheduleSegmentSnapshot> segmentById =
                new LinkedHashMap<>();
        Set<Long> exemptSegmentIds = new java.util.HashSet<>();
        boolean hasRemainingWorkPending = false;
        for (int index = 0; index < workSegments.size(); index++)
        {
            ScheduleSegmentSnapshot segment = workSegments.get(index);
            // Both calls validate snapshot ownership and immutable offsets.
            LocalDateTime startAt = rules.segmentStart(schedule, segment);
            LocalDateTime endAt = rules.segmentEnd(schedule, segment);
            if (segmentById.put(segment.scheduleSegmentSnapshotId,
                    segment) != null)
                throw new ServiceException(
                        "SCHEDULE_SEGMENT_SNAPSHOT_DUPLICATE");
            Coverage coverage = AttendanceLeaveCoveragePolicy.analyze(startAt,
                    endAt, leaves);
            if (coverage.fullyCovered()) exemptSegmentIds.add(
                    segment.scheduleSegmentSnapshotId);
            boolean confirmationRequired = coverage
                    .requiresRemainingWorkConfirmation();
            hasRemainingWorkPending |= confirmationRequired;
            String label = workSegmentLabel(index, workSegments.size());
            addSegmentSlot(schedule, segment, "IN", label, startAt, endAt,
                    coverage.fullyCovered() || coverage.startCovered(),
                    confirmationRequired, workSegments, slots, slotByKey);
            addSegmentSlot(schedule, segment, "OUT", label, startAt, endAt,
                    coverage.fullyCovered() || coverage.endCovered(),
                    confirmationRequired, workSegments, slots, slotByKey);
        }

        List<PunchEvent> accepted = mapper.selectAcceptedPunches(
                schedule.scheduleId);
        if (accepted == null) accepted = List.of();
        for (PunchEvent event : accepted)
        {
            PunchSlotView slot = event == null ? null
                    : slotByKey.get(event.punchSlotKey);
            if (event == null || slot == null
                    || !"ACCEPTED".equals(event.verificationStatus)
                    || !Objects.equals(schedule.scheduleId, event.scheduleId)
                    || !Objects.equals(schedule.userId, event.userId)
                    || !Objects.equals(schedule.shopId, event.shopId)
                    || !Objects.equals(schedule.businessDate,
                            event.businessDate)
                    || !Objects.equals(slot.scheduleSegmentSnapshotId,
                            event.scheduleSegmentSnapshotId)
                    || !Objects.equals(slot.punchType, event.punchType)
                    || event.serverPunchTime == null)
                throw new ServiceException("ACCEPTED_PUNCH_SLOT_INVALID");
            if (slot.punchEventId != null)
                throw new ServiceException("MULTIPLE_ACCEPTED_PUNCH_FOR_SLOT");
            slot.completed = true;
            slot.status = "COMPLETED";
            slot.punchEventId = event.punchEventId;
        }

        PunchSlotView next = null;
        boolean hasMissing = false;
        for (PunchSlotView slot : slots)
        {
            if (slot.completed) continue;
            if (slot.requiresRemainingWorkConfirmation)
            {
                slot.status = "REMAINING_WORK_PENDING";
                continue;
            }
            if (now != null && slot.closesAt != null
                    && now.isAfter(slot.closesAt))
            {
                slot.status = "MISSED";
                hasMissing = true;
                continue;
            }
            if (next == null)
            {
                next = slot;
                slot.status = now != null && slot.opensAt != null
                        && now.isBefore(slot.opensAt) ? "UPCOMING" : "PENDING";
            }
            else slot.status = "UPCOMING";
        }
        boolean allExempt = exemptSegmentIds.size() == workSegments.size();
        return new SegmentPunchState(List.copyOf(workSegments),
                List.copyOf(slots), next, Map.copyOf(segmentById), allExempt,
                hasMissing, hasRemainingWorkPending);
    }

    private void addSegmentSlot(Schedule schedule,
            ScheduleSegmentSnapshot segment, String punchType, String label,
            LocalDateTime startAt, LocalDateTime endAt, boolean exempt,
            boolean confirmationRequired,
            List<ScheduleSegmentSnapshot> workSegments,
            List<PunchSlotView> slots, Map<String, PunchSlotView> slotByKey)
    {
        PunchWindow window = rules.effectiveSegmentWindow(schedule,
                workSegments, segment, punchType);
        PunchSlotView slot = new PunchSlotView();
        slot.punchSlotKey = rules.slotKey(segment, punchType);
        slot.punchType = punchType;
        slot.scheduleSegmentSnapshotId = segment.scheduleSegmentSnapshotId;
        slot.segmentOrder = segment.segmentOrder;
        slot.segmentLabel = label;
        slot.startAt = startAt;
        slot.endAt = endAt;
        slot.opensAt = window.opensAt();
        slot.closesAt = window.closesAt();
        slot.requiresRemainingWorkConfirmation = confirmationRequired;
        slot.completed = exempt && !confirmationRequired;
        slot.status = confirmationRequired ? "REMAINING_WORK_PENDING"
                : exempt ? "EXEMPT_LEAVE" : "UPCOMING";
        if (slotByKey.put(slot.punchSlotKey, slot) != null)
            throw new ServiceException("PUNCH_SLOT_KEY_DUPLICATE");
        slots.add(slot);
    }

    private boolean coveredByApprovedLeave(LocalDateTime workStart,
            LocalDateTime workEnd, List<LeaveSegmentSource> leaves)
    {
        LocalDateTime cursor = workStart;
        List<LeaveSegmentSource> ordered = leaves.stream()
                .filter(Objects::nonNull)
                .filter(value -> value.startTime != null
                        && value.endTime != null
                        && value.endTime.isAfter(value.startTime))
                .sorted(Comparator.comparing(value -> value.startTime))
                .toList();
        for (LeaveSegmentSource leave : ordered)
        {
            LocalDateTime start = leave.startTime.isBefore(workStart)
                    ? workStart : leave.startTime;
            LocalDateTime end = leave.endTime.isAfter(workEnd)
                    ? workEnd : leave.endTime;
            if (!end.isAfter(cursor)) continue;
            if (start.isAfter(cursor)) return false;
            cursor = end;
            if (!cursor.isBefore(workEnd)) return true;
        }
        return !cursor.isBefore(workEnd);
    }

    private boolean isBetweenWorkSegments(Schedule schedule,
            SegmentPunchState state, PunchSlotView next, LocalDateTime now)
    {
        if (!"IN".equals(next.punchType) || now == null
                || next.startAt == null || !now.isBefore(next.startAt))
            return false;
        int index = -1;
        for (int i = 0; i < state.workSegments().size(); i++)
            if (Objects.equals(state.workSegments().get(i)
                    .scheduleSegmentSnapshotId,
                    next.scheduleSegmentSnapshotId)) index = i;
        if (index <= 0) return false;
        LocalDateTime previousEnd = rules.segmentEnd(schedule,
                state.workSegments().get(index - 1));
        return !now.isBefore(previousEnd);
    }

    private boolean insideWindow(PunchSlotView slot, LocalDateTime now)
    {
        return now != null && slot != null && slot.opensAt != null
                && slot.closesAt != null && !now.isBefore(slot.opensAt)
                && !now.isAfter(slot.closesAt);
    }

    private String workSegmentLabel(int index, int count)
    {
        if (count == 1) return "工作段";
        if (count == 2) return index == 0 ? "上午工作段" : "下午工作段";
        return "第" + (index + 1) + "工作段";
    }

    private void hydratePublishedSegments(Schedule schedule, boolean lockRows)
    {
        if (schedule == null || !"PUBLISHED".equals(schedule.status)) return;
        List<ScheduleSegmentSnapshot> segments = mapper
                .selectScheduleSegmentSnapshots(schedule.scheduleId, lockRows);
        if (segments == null) segments = List.of();
        schedule.segmentSnapshots = new ArrayList<>(segments.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(value -> value.segmentOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
    }

    private boolean isPerWorkSegment(Schedule schedule)
    {
        return AttendanceRuleEngine.PER_WORK_SEGMENT.equals(
                rules.normalizePunchMode(schedule == null ? null
                        : schedule.punchModeSnapshot));
    }

    private void requireBoundarySlotAbsent(String punchSlotKey)
    {
        if (blankToNull(punchSlotKey) != null)
            throw new ServiceException("PUNCH_SLOT_NOT_APPLICABLE");
    }

    private Schedule chooseCandidate(List<Schedule> values,
            LocalDateTime now)
    {
        for (Schedule value : values)
        {
            if (value.businessDate.equals(now.toLocalDate().minusDays(1))
                    && Boolean.TRUE.equals(value.crossDaySnapshot)
                    && isInsideNextPunchWindow(value, now))
                return value;
        }
        for (Schedule value : values)
            if (value.businessDate.equals(now.toLocalDate())
                    && isInsideNextPunchWindow(value, now)) return value;
        for (Schedule value : values)
            if (value.businessDate.equals(now.toLocalDate())) return value;
        return values.get(0);
    }

    private boolean isInsideNextPunchWindow(Schedule schedule,
            LocalDateTime now)
    {
        if (isPerWorkSegment(schedule))
        {
            SegmentPunchState state = segmentPunchState(schedule, now, false);
            return insideWindow(state.nextSlot(), now);
        }
        BoundaryPunchState state = boundaryPunchState(schedule, false);
        String type = state.nextType();
        if (type == null) return false;
        PunchWindow window = rules.window(schedule, type);
        return !now.isBefore(window.opensAt()) && !now.isAfter(
                window.closesAt());
    }

    private boolean fullyCoveredByApprovedLeave(Schedule schedule)
    {
        AttendanceDaySettlementCalculator.Bounds bounds =
                settlementCalculator.bounds(schedule);
        List<ScheduleSegmentSnapshot> segments = mapper
                .selectScheduleSegmentSnapshots(schedule.scheduleId, false);
        List<LeaveSegmentSource> leaves = mapper.selectApprovedLeaveSegments(
                schedule.userId, schedule.shopId, bounds.start(), bounds.end(),
                false);
        return settlementCalculator.fullyCoveredByApprovedLeave(schedule,
                leaves == null ? List.of() : leaves,
                segments == null ? List.of() : segments);
    }

    private void requireExpectedBoundaryPunch(Schedule schedule, String type,
            boolean lockRows)
    {
        BoundaryPunchState state = boundaryPunchState(schedule, lockRows);
        if (state.fullyCovered())
            throw new ServiceException("PUNCH_BLOCKED_BY_APPROVED_LEAVE");
        if (state.nextType() == null
                && state.requiresRemainingWorkConfirmation())
            throw new ServiceException(
                    "PUNCH_REMAINING_WORK_CONFIRMATION_REQUIRED");
        if (state.nextType() == null)
            throw new ServiceException("ALL_PUNCH_SLOTS_COMPLETED");
        if (!state.nextType().equals(type))
            throw new ServiceException("PUNCH_SLOT_TYPE_MISMATCH");
    }

    private BoundaryPunchState boundaryPunchState(Schedule schedule,
            boolean lockRows)
    {
        hydratePublishedSegments(schedule, lockRows);
        List<ScheduleSegmentSnapshot> work = schedule.segmentSnapshots.stream()
                .filter(Objects::nonNull)
                .filter(value -> "WORK".equals(value.segmentType)
                        && Boolean.TRUE.equals(value.paid))
                .sorted(Comparator.comparing(value -> value.segmentOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (work.isEmpty())
            throw new ServiceException("SCHEDULE_WORK_SEGMENT_REQUIRED");
        AttendanceDaySettlementCalculator.Bounds bounds =
                settlementCalculator.bounds(schedule);
        List<LeaveSegmentSource> leaves = mapper.selectApprovedLeaveSegments(
                schedule.userId, schedule.shopId, bounds.start(), bounds.end(),
                lockRows);
        if (leaves == null) leaves = List.of();
        LocalDateTime firstStart = rules.segmentStart(schedule, work.get(0));
        LocalDateTime lastEnd = rules.segmentEnd(schedule,
                work.get(work.size() - 1));
        Coverage outer = AttendanceLeaveCoveragePolicy.analyze(firstStart,
                lastEnd, leaves);
        boolean allCovered = true;
        for (ScheduleSegmentSnapshot segment : work)
            if (!AttendanceLeaveCoveragePolicy.analyze(
                    rules.segmentStart(schedule, segment),
                    rules.segmentEnd(schedule, segment), leaves)
                    .fullyCovered()) allCovered = false;
        PunchEvent in = mapper.selectFirstAcceptedPunch(schedule.scheduleId,
                "IN");
        PunchEvent out = mapper.selectLastAcceptedPunch(schedule.scheduleId,
                "OUT");
        boolean inSatisfied = in != null || outer.startCovered();
        boolean outSatisfied = out != null || outer.endCovered();
        String next = !inSatisfied ? "IN" : !outSatisfied ? "OUT" : null;
        return new BoundaryPunchState(next, allCovered,
                outer.startCovered() && outer.endCovered() && !allCovered);
    }

    private DayResult recalculate(Schedule schedule, LocalDateTime now)
    {
        return recalculateSegmented(schedule, now);
    }

    private DayResult recalculateSegmented(Schedule schedule,
            LocalDateTime now)
    {
        List<ScheduleSegmentSnapshot> segments = mapper
                .selectScheduleSegmentSnapshots(schedule.scheduleId, false);
        if (segments == null) segments = List.of();
        List<PunchEvent> accepted = mapper.selectAcceptedPunches(
                schedule.scheduleId);
        if (accepted == null) accepted = List.of();
        List<CorrectionSource> corrections = mapper
                .selectSettlementCorrectionSources(schedule.scheduleId, false);
        if (corrections == null) corrections = List.of();
        AttendanceDaySettlementCalculator.Bounds bounds =
                settlementCalculator.bounds(schedule);
        List<LeaveRequestState> blockingLeaves = mapper
                .selectBlockingLeaveRequests(schedule.userId, schedule.shopId,
                        bounds.start(), bounds.end(), false);
        if (blockingLeaves == null) blockingLeaves = List.of();
        List<LeaveSegmentSource> approvedLeaves = mapper
                .selectApprovedLeaveSegments(schedule.userId, schedule.shopId,
                        bounds.start(), bounds.end(), false);
        if (approvedLeaves == null) approvedLeaves = List.of();
        List<RemainingWorkConfirmationSource> confirmations = mapper
                .selectRemainingWorkConfirmationSources(schedule.scheduleId,
                        false);
        if (confirmations == null) confirmations = List.of();
        AttendanceDaySettlementCalculator.Evaluation evaluation =
                settlementCalculator.evaluate(schedule, accepted, corrections,
                        blockingLeaves, approvedLeaves, segments,
                        confirmations, now);
        DayResult result = evaluation.result();
        // Punch-time calculation is explicitly provisional.  Before the last
        // segment window closes, expose the detailed missing-slot codes while
        // keeping the day out of final payroll states.
        if (evaluation.issueCodes().contains("SHIFT_NOT_ENDED"))
            result.resultStatus = "PENDING";
        result.settledAt = null;
        result.setUpdateBy(SecurityUtils.getUsername());
        mapper.upsertDayResult(result);
        DayResult saved = mapper.selectDayResultByScheduleId(
                schedule.scheduleId);
        return saved == null ? result : saved;
    }

    private void upsertInitialDayResult(Schedule schedule)
    {
        DayResult result = baseResult(schedule);
        result.resultStatus = "PENDING";
        result.exceptionCodes = "MISSING_IN,MISSING_OUT";
        result.calculationVersion = 1;
        result.calculatedAt = now();
        result.setCreateBy(SecurityUtils.getUsername());
        result.setUpdateBy(SecurityUtils.getUsername());
        mapper.upsertDayResult(result);
    }

    private DayResult baseResult(Schedule schedule)
    {
        DayResult value = new DayResult();
        value.scheduleId = schedule.scheduleId;
        value.userId = schedule.userId;
        value.userName = schedule.userName;
        value.shopId = schedule.shopId;
        value.businessDate = schedule.businessDate;
        value.shiftId = schedule.shiftId;
        value.scheduledMinutes = positive(schedule.standardMinutesSnapshot, 0);
        value.workedMinutes = 0;
        value.paidLeaveMinutes = 0;
        value.unpaidLeaveMinutes = 0;
        value.absenceMinutes = 0;
        value.lateMinutes = 0;
        value.earlyLeaveMinutes = 0;
        return value;
    }

    private void registerRollbackCleanup(StoredEvidence stored)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new ServiceException("PUNCH_TRANSACTION_REQUIRED");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (status != STATUS_COMMITTED)
                        {
                            evidenceStorage.deleteQuietly(stored.originalPath());
                            evidenceStorage.deleteQuietly(stored.watermarkedPath());
                        }
                    }
                });
    }

    private void requireSelfPublishedSchedule(Schedule schedule)
    {
        if (schedule == null || !"PUBLISHED".equals(schedule.status)
                || !SecurityUtils.getUserId().equals(schedule.userId))
            throw new ServiceException("PUBLISHED_SELF_SCHEDULE_REQUIRED");
        if (mapper.countActiveEmployeeInShop(schedule.userId,
                schedule.shopId) <= 0)
            throw new ServiceException("EMPLOYEE_NOT_ACTIVE_IN_SHOP");
    }

    private void validateShift(Shift shift, boolean creating)
    {
        if (shift == null || blankToNull(shift.shiftCode) == null
                || blankToNull(shift.shiftName) == null
                || shift.startTime == null || shift.endTime == null)
            throw new ServiceException("SHIFT_REQUIRED_FIELDS_MISSING");
        shift.shiftCode = shift.shiftCode.trim().toUpperCase(Locale.ROOT);
        shift.shiftName = shift.shiftName.trim();
        shift.punchMode = rules.normalizePunchMode(shift.punchMode);
        if (!shift.shiftCode.matches("[A-Z0-9_-]{2,32}")
                || shift.shiftName.length() > 64)
            throw new ServiceException("SHIFT_NAME_OR_CODE_INVALID");
        shift.crossDay = !shift.endTime.isAfter(shift.startTime);
        int shiftStartOffset = shift.startTime.toSecondOfDay() / 60;
        int shiftEndOffset = shift.endTime.toSecondOfDay() / 60;
        if (Boolean.TRUE.equals(shift.crossDay)
                || shiftEndOffset <= shiftStartOffset)
            shiftEndOffset += 1440;
        int nominalMinutes = shiftEndOffset - shiftStartOffset;
        if (shift.standardMinutes == null || shift.standardMinutes <= 0
                || shift.standardMinutes > 1440)
            throw new ServiceException("SHIFT_STANDARD_MINUTES_INVALID");
        shift.graceInMinutes = bounded(shift.graceInMinutes, 0, 240, 0);
        shift.graceOutMinutes = bounded(shift.graceOutMinutes, 0, 240, 0);
        shift.checkInOpenMinutes = bounded(shift.checkInOpenMinutes, 0, 720, 120);
        shift.checkInCloseMinutes = bounded(shift.checkInCloseMinutes, 0, 720, 120);
        shift.checkOutOpenMinutes = bounded(shift.checkOutOpenMinutes, 0, 720, 120);
        shift.checkOutCloseMinutes = bounded(shift.checkOutCloseMinutes, 0, 720, 120);
        if (shift.effectiveFrom != null && shift.effectiveTo != null
                && shift.effectiveTo.isBefore(shift.effectiveFrom))
            throw new ServiceException("SHIFT_EFFECTIVE_RANGE_INVALID");
        if (shift.segments != null && shift.segments.size() > 16)
            throw new ServiceException("SHIFT_SEGMENT_COUNT_INVALID");
        if (shift.segments == null || shift.segments.isEmpty())
        {
            if (shift.standardMinutes != nominalMinutes)
                throw new ServiceException(
                        "SHIFT_SEGMENT_REQUIRED_FOR_STANDARD_MINUTES");
            ShiftSegment work = new ShiftSegment();
            work.segmentType = "WORK";
            work.startMinuteOffset = shiftStartOffset;
            work.endMinuteOffset = shiftEndOffset;
            work.paid = true;
            shift.segments = new ArrayList<>();
            shift.segments.add(work);
        }
        int previousEnd = -1;
        int paidWorkMinutes = 0;
        if (shift.segments != null)
        {
            for (int i = 0; i < shift.segments.size(); i++)
            {
                ShiftSegment segment = shift.segments.get(i);
                if (segment == null || segment.startMinuteOffset == null
                        || segment.endMinuteOffset == null
                        || segment.startMinuteOffset < 0
                        || segment.endMinuteOffset <= segment.startMinuteOffset
                        || segment.endMinuteOffset > 2880
                        || segment.startMinuteOffset < shiftStartOffset
                        || segment.endMinuteOffset > shiftEndOffset
                        || segment.startMinuteOffset < previousEnd)
                    throw new ServiceException("SHIFT_SEGMENT_INVALID");
                segment.segmentOrder = i + 1;
                segment.segmentType = normalizeSegmentType(segment.segmentType);
                if (segment.paid == null) segment.paid = true;
                if ("WORK".equals(segment.segmentType)
                        && Boolean.TRUE.equals(segment.paid))
                    paidWorkMinutes += segment.endMinuteOffset
                            - segment.startMinuteOffset;
                previousEnd = segment.endMinuteOffset;
            }
        }
        if (paidWorkMinutes != shift.standardMinutes)
            throw new ServiceException(
                    "SHIFT_PAID_WORK_MINUTES_MISMATCH");
        validateEffectiveSegmentWindows(shift);
        if (creating) shift.rowVersion = 0L;
    }

    private void validateEffectiveSegmentWindows(Shift shift)
    {
        if (!AttendanceRuleEngine.PER_WORK_SEGMENT.equals(shift.punchMode))
            return;
        List<ShiftSegment> work = shift.segments.stream()
                .filter(Objects::nonNull)
                .filter(value -> "WORK".equals(value.segmentType))
                .sorted(Comparator.comparing(value -> value.segmentOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (int index = 0; index + 1 < work.size(); index++)
        {
            ShiftSegment previous = work.get(index);
            ShiftSegment next = work.get(index + 1);
            long midpointSeconds = ((long) previous.endMinuteOffset
                    + (long) next.startMinuteOffset) * 30L;
            long previousOpen = ((long) previous.endMinuteOffset
                    - shift.checkOutOpenMinutes) * 60L;
            long previousClose = Math.min(
                    ((long) previous.endMinuteOffset
                            + shift.checkOutCloseMinutes) * 60L,
                    midpointSeconds);
            long nextOpen = Math.max(
                    ((long) next.startMinuteOffset
                            - shift.checkInOpenMinutes) * 60L,
                    midpointSeconds);
            long nextClose = ((long) next.startMinuteOffset
                    + shift.checkInCloseMinutes) * 60L;
            if (previousClose - previousOpen < 60L
                    || nextClose - nextOpen < 60L)
                throw new ServiceException(
                        "PUNCH_EFFECTIVE_WINDOW_TOO_SHORT");
        }
    }

    private void validateSite(Site site)
    {
        if (blankToNull(site.siteCode) == null
                || blankToNull(site.siteName) == null
                || blankToNull(site.address) == null
                || site.longitude == null || site.latitude == null)
            throw new ServiceException("SITE_REQUIRED_FIELDS_MISSING");
        site.siteCode = site.siteCode.trim().toUpperCase(Locale.ROOT);
        site.siteName = site.siteName.trim();
        if (!site.siteCode.matches("[A-Z0-9_-]{2,32}")
                || site.siteName.length() > 64 || site.address.length() > 255
                || site.latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || site.latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || site.longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || site.longitude.compareTo(BigDecimal.valueOf(180)) > 0)
            throw new ServiceException("SITE_VALUE_INVALID");
        site.radiusMeters = bounded(site.radiusMeters, 10, 5000, 200);
        site.maxAccuracyMeters = bounded(site.maxAccuracyMeters, 5, 1000, 100);
        site.coordinateSystem = blankToNull(site.coordinateSystem) == null
                ? "GCJ02" : site.coordinateSystem.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("GCJ02", "WGS84", "BD09").contains(site.coordinateSystem))
            throw new ServiceException("SITE_COORDINATE_SYSTEM_INVALID");
    }

    private void replaceSegments(Shift shift)
    {
        mapper.deleteShiftSegments(shift.shiftId);
        if (shift.segments == null) return;
        for (ShiftSegment segment : shift.segments)
        {
            segment.segmentId = null;
            segment.shiftId = shift.shiftId;
            if (mapper.insertShiftSegment(segment) != 1)
                throw new ServiceException("SHIFT_SEGMENT_CREATE_FAILED");
        }
    }

    private Shift requireShift(Long id)
    {
        Shift value = id == null ? null : mapper.selectShiftById(id);
        if (value == null) throw new ServiceException("SHIFT_NOT_FOUND");
        return value;
    }

    private Site requireSite(Long id)
    {
        Site value = id == null ? null : mapper.selectSiteById(id);
        if (value == null) throw new ServiceException("ATTENDANCE_SITE_NOT_FOUND");
        return value;
    }

    private Site requireEnabledSiteInShop(Long siteId, Long shopId)
    {
        if (siteId == null)
            throw new ServiceException("ATTENDANCE_GEOFENCE_NOT_CONFIGURED");
        Site site = mapper.selectSiteByIdForUpdate(siteId);
        if (site == null || !shopId.equals(site.shopId))
            throw new ServiceException("ATTENDANCE_SITE_NOT_FOUND_IN_SHOP");
        if (!"ENABLED".equals(site.status))
            throw new ServiceException("ATTENDANCE_SITE_MUST_BE_ENABLED");
        requireValidGeofenceSource(site);
        return site;
    }

    private void requireValidGeofenceSource(Site site)
    {
        if (blankToNull(site.siteName) == null
                || blankToNull(site.address) == null
                || site.latitude == null || site.longitude == null
                || blankToNull(site.coordinateSystem) == null
                || site.radiusMeters == null
                || site.maxAccuracyMeters == null)
            throw new ServiceException("ATTENDANCE_GEOFENCE_NOT_CONFIGURED");
        if (site.radiusMeters < 10 || site.radiusMeters > 5000
                || site.maxAccuracyMeters < 5
                || site.maxAccuracyMeters > 1000)
            throw new ServiceException("ATTENDANCE_GEOFENCE_SNAPSHOT_INVALID");
        coordinateTransformer.transform(site.latitude, site.longitude,
                site.coordinateSystem, site.coordinateSystem);
    }

    private GeofenceResult requireInsidePublishedGeofence(Schedule schedule,
            PunchCommand command, String clientCoordinateSystem)
    {
        if (schedule.siteId == null
                || blankToNull(schedule.siteNameSnapshot) == null
                || blankToNull(schedule.addressSnapshot) == null
                || schedule.latitudeSnapshot == null
                || schedule.longitudeSnapshot == null
                || blankToNull(schedule.coordinateSystemSnapshot) == null
                || schedule.radiusMetersSnapshot == null
                || schedule.maxAccuracyMetersSnapshot == null)
            throw new ServiceException("ATTENDANCE_GEOFENCE_NOT_CONFIGURED");
        if (schedule.radiusMetersSnapshot < 10
                || schedule.radiusMetersSnapshot > 5000
                || schedule.maxAccuracyMetersSnapshot < 5
                || schedule.maxAccuracyMetersSnapshot > 1000)
            throw new ServiceException("ATTENDANCE_GEOFENCE_SNAPSHOT_INVALID");
        Coordinate site = coordinateTransformer.transform(
                schedule.latitudeSnapshot, schedule.longitudeSnapshot,
                schedule.coordinateSystemSnapshot,
                schedule.coordinateSystemSnapshot);
        Coordinate device = coordinateTransformer.transform(command.latitude,
                command.longitude, clientCoordinateSystem,
                site.coordinateSystem());
        BigDecimal distance = geoFence.requireInside(device.latitude(),
                device.longitude(), command.accuracyMeters, site.latitude(),
                site.longitude(), schedule.radiusMetersSnapshot,
                schedule.maxAccuracyMetersSnapshot);
        return new GeofenceResult(distance, "INSIDE");
    }

    private record GeofenceResult(BigDecimal distanceMeters, String status) { }

    private Long requireSameScopedShop(Long requested, Long selected)
    {
        Long scope = shopScopeService.resolveRequiredShopDept(selected);
        if (requested != null && !requested.equals(scope))
            throw new ServiceException("ATTENDANCE_SHOP_SCOPE_MISMATCH");
        return scope;
    }

    private void requireDateRange(LocalDate from, LocalDate to)
    {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) > 366)
            throw new ServiceException("ATTENDANCE_DATE_RANGE_INVALID");
    }

    private void validateScheduleDate(LocalDate date)
    {
        LocalDate today = now().toLocalDate();
        if (date.isBefore(today.minusDays(31))
                || date.isAfter(today.plusDays(366)))
            throw new ServiceException("SCHEDULE_DATE_OUT_OF_RANGE");
    }

    private TodayContext locked(TodayContext value, String state,
            String reason)
    {
        value.state = state;
        value.canPunch = false;
        value.lockReason = reason;
        return value;
    }

    private String requireStatus(String status)
    {
        String value = status == null ? ""
                : status.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE_STATES.contains(value))
            throw new ServiceException("ATTENDANCE_STATUS_INVALID");
        return value;
    }

    private String normalizeSegmentType(String value)
    {
        String type = value == null ? "WORK"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("WORK", "BREAK").contains(type))
            throw new ServiceException("SHIFT_SEGMENT_TYPE_INVALID");
        return type;
    }

    private int bounded(Integer value, int min, int max, int fallback)
    {
        int resolved = value == null ? fallback : value;
        if (resolved < min || resolved > max)
            throw new ServiceException("ATTENDANCE_RULE_VALUE_OUT_OF_RANGE");
        return resolved;
    }

    private int positive(Integer value, int fallback)
    { return value == null || value < 0 ? fallback : value; }
    private int value(Integer value) { return value == null ? 0 : value; }
    private String blankToNull(String value)
    { return value == null || value.isBlank() ? null : value.trim(); }
    private String safeName(String value)
    { return value == null || value.isBlank() ? "-" : truncate(value, 80); }
    private String requiredWatermarkValue(String value, String errorCode)
    {
        if (value == null || value.isBlank())
            throw new ServiceException(errorCode);
        return value.trim();
    }
    private String truncate(String value, int size)
    { return value == null ? null : value.length() <= size ? value : value.substring(0, size); }
    private LocalDateTime now()
    { return LocalDateTime.now(clock).withNano(0); }

    private LocalDateTime authoritativeNow()
    {
        LocalDateTime value = mapper.selectDatabaseNow();
        if (value == null)
            throw new ServiceException("ATTENDANCE_SERVER_TIME_UNAVAILABLE");
        return value.withNano(0);
    }

    private String nextNo(String prefix)
    {
        return prefix + now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String randomToken()
    {
        try
        {
            String source = UUID.randomUUID() + ":" + UUID.randomUUID()
                    + ":" + now();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private String defaultClientRequestId(String value, String token)
    {
        return blankToNull(value) == null ? "challenge:" + token.substring(
                0, Math.min(48, token.length()))
                : truncate(value.trim(), 64);
    }

    private String clientClockRisk(LocalDateTime client, LocalDateTime server)
    {
        if (client == null) return "CLIENT_CAPTURE_TIME_MISSING";
        return Math.abs(Duration.between(client, server).toMinutes()) > 5
                ? "CLIENT_CLOCK_SKEW" : null;
    }

    private String requireAuditLocation(PunchCommand command)
    {
        return locationPolicy.requireValid(command.latitude,
                command.longitude, command.accuracyMeters,
                command.clientCoordinateSystem);
    }

    private ResolvedAddress requireResolvedAddress(ResolvedAddress value)
    {
        if (value == null || value.formattedAddress() == null
                || value.provider() == null)
            throw new ServiceException("ATTENDANCE_ADDRESS_RESOLUTION_FAILED");
        String address = value.formattedAddress()
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        String provider = value.provider().trim().toUpperCase(Locale.ROOT);
        if (address.length() < 4 || address.length() > 255
                || provider.isBlank() || provider.length() > 32
                || address.matches("^[+-]?\\d{1,3}(?:\\.\\d+)?\\s*[,，]\\s*[+-]?\\d{1,3}(?:\\.\\d+)?$"))
            throw new ServiceException("ATTENDANCE_ADDRESS_INVALID");
        return new ResolvedAddress(address, provider);
    }

    private String appendRisk(String first, String second)
    {
        if (first == null || first.isBlank()) return second;
        if (second == null || second.isBlank()) return first;
        return first + "," + second;
    }

    private void requireFreshCaptureTime(LocalDateTime client,
            LocalDateTime issuedAt, LocalDateTime server)
    {
        if (client == null)
            throw new ServiceException("CLIENT_CAPTURE_TIME_REQUIRED");
        LocalDateTime earliest = issuedAt == null
                ? server.minusMinutes(5) : issuedAt.minusSeconds(30);
        if (client.isBefore(earliest)
                || client.isAfter(server.plusSeconds(30))
                || Math.abs(Duration.between(client, server).toMinutes()) > 5)
            throw new ServiceException("CLIENT_CAPTURE_TIME_STALE");
    }

    private boolean hasPermission(String permission)
    {
        LoginUser user = SecurityUtils.getLoginUser();
        return SecurityUtils.isAdmin() || user != null
                && user.getPermissions() != null
                && (user.getPermissions().contains(permission)
                        || user.getPermissions().contains("*:*:*"));
    }

    private void requireEnabled()
    { featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2); }

    private record SegmentPunchState(
            List<ScheduleSegmentSnapshot> workSegments,
            List<PunchSlotView> slots,
            PunchSlotView nextSlot,
            Map<Long, ScheduleSegmentSnapshot> segmentById,
            boolean allWorkSegmentsExempt,
            boolean hasMissingSlots,
            boolean hasRemainingWorkPending) { }

    private record SegmentPunchBinding(ScheduleSegmentSnapshot segment,
            PunchSlotView slot) { }

    private record BoundaryPunchState(String nextType, boolean fullyCovered,
            boolean requiresRemainingWorkConfirmation) { }

    public record EvidenceContent(Path path, String fileName, long size) { }
}
