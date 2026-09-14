package com.erp.oa.attendance.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.attendance.domain.AttendanceModels.Challenge;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.Evidence;
import com.erp.oa.attendance.domain.AttendanceModels.EmployeeOption;
import com.erp.oa.attendance.domain.AttendanceModels.MonthlySummary;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.RemainingWorkAttachment;
import com.erp.oa.attendance.domain.AttendanceModels.RemainingWorkConfirmation;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceModels.Shift;
import com.erp.oa.attendance.domain.AttendanceModels.ShiftSegment;
import com.erp.oa.attendance.domain.AttendanceModels.Site;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.CorrectionSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveRequestState;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource;

public interface AttendanceV2Mapper
{
    LocalDateTime selectDatabaseNow();
    com.erp.oa.attendance.domain.AttendanceModels.DatabaseClock selectDatabaseClock();

    List<Shift> selectShifts(@Param("status") String status);
    Shift selectShiftById(@Param("shiftId") Long shiftId);
    Shift selectShiftByIdForUpdate(@Param("shiftId") Long shiftId);
    List<ShiftSegment> selectShiftSegments(@Param("shiftId") Long shiftId);
    int countPublishedSchedulesByShift(@Param("shiftId") Long shiftId);
    int insertShift(Shift shift);
    int updateShift(Shift shift);
    int updateShiftStatus(@Param("shiftId") Long shiftId,
            @Param("status") String status,
            @Param("rowVersion") Long rowVersion,
            @Param("updateBy") String updateBy);
    int deleteShift(@Param("shiftId") Long shiftId,
            @Param("rowVersion") Long rowVersion);
    int deleteShiftSegments(@Param("shiftId") Long shiftId);
    int insertShiftSegment(ShiftSegment segment);

    List<Site> selectSites(@Param("shopId") Long shopId,
            @Param("status") String status,
            @Param("scopeShopIds") List<Long> scopeShopIds);
    Site selectSiteById(@Param("siteId") Long siteId);
    Site selectSiteByIdForUpdate(@Param("siteId") Long siteId);
    int insertSite(Site site);
    int updateSite(Site site);
    int updateSiteStatus(@Param("siteId") Long siteId,
            @Param("status") String status,
            @Param("rowVersion") Long rowVersion,
            @Param("updateBy") String updateBy);
    int deleteSite(@Param("siteId") Long siteId,
            @Param("shopId") Long shopId,
            @Param("rowVersion") Long rowVersion);

    List<Schedule> selectSchedules(@Param("shopId") Long shopId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("userId") Long userId,
            @Param("scopeShopIds") List<Long> scopeShopIds);
    List<Schedule> selectPublishedSchedulesForSettlement(
            @Param("shopId") Long shopId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    Schedule selectScheduleById(@Param("scheduleId") Long scheduleId);
    Schedule selectScheduleByIdForUpdate(@Param("scheduleId") Long scheduleId);
    String selectShopOrganizationPath(@Param("shopId") Long shopId);
    Schedule selectScheduleByUserAndDateForUpdate(
            @Param("userId") Long userId,
            @Param("businessDate") LocalDate businessDate);
    Schedule selectPublishedScheduleForUser(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            @Param("yesterday") LocalDate yesterday);
    List<Schedule> selectPublishedScheduleCandidatesForUser(
            @Param("userId") Long userId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    int insertDraftSchedule(Schedule schedule);
    int updateDraftSchedule(Schedule schedule);
    int deleteDraftSchedule(@Param("scheduleId") Long scheduleId,
            @Param("shopId") Long shopId,
            @Param("rowVersion") Long rowVersion);
    int publishSchedule(@Param("scheduleId") Long scheduleId,
            @Param("shopId") Long shopId,
            @Param("publishedBy") Long publishedBy,
            @Param("updateBy") String updateBy);
    int deleteScheduleSegmentSnapshots(@Param("scheduleId") Long scheduleId);
    int insertScheduleSegmentSnapshotsFromShift(
            @Param("scheduleId") Long scheduleId,
            @Param("shiftId") Long shiftId);
    List<ScheduleSegmentSnapshot> selectScheduleSegmentSnapshots(
            @Param("scheduleId") Long scheduleId,
            @Param("lockRows") boolean lockRows);
    ScheduleSegmentSnapshot selectScheduleSegmentSnapshotById(
            @Param("scheduleId") Long scheduleId,
            @Param("scheduleSegmentSnapshotId") Long snapshotId,
            @Param("lockRows") boolean lockRows);
    int countActiveEmployeeInShop(@Param("userId") Long userId,
            @Param("shopId") Long shopId);
    String selectActiveEmployeeNameInShop(@Param("userId") Long userId,
            @Param("shopId") Long shopId);
    List<EmployeeOption> selectActiveEmployeeOptions(
            @Param("shopId") Long shopId,
            @Param("keyword") String keyword);
    long countActiveEmployeeOptions(@Param("shopId") Long shopId,
            @Param("keyword") String keyword);
    List<EmployeeOption> selectActiveEmployeeOptionsPage(
            @Param("shopId") Long shopId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize);

    int insertChallenge(Challenge challenge);
    Challenge selectChallengeByTokenForUpdate(
            @Param("challengeToken") String challengeToken);
    Challenge selectChallengeByToken(
            @Param("challengeToken") String challengeToken);
    int consumeChallenge(@Param("challengeId") Long challengeId,
            @Param("eventId") Long eventId,
            @Param("consumedAt") LocalDateTime consumedAt,
            @Param("rowVersion") Long rowVersion);

    int insertPunchEvent(PunchEvent event);
    PunchEvent selectLatestPunch(@Param("scheduleId") Long scheduleId);
    List<PunchEvent> selectAcceptedPunches(
            @Param("scheduleId") Long scheduleId);
    PunchEvent selectAcceptedPunchBySlot(
            @Param("scheduleId") Long scheduleId,
            @Param("punchSlotKey") String punchSlotKey);
    PunchEvent selectPunchByClientRequestForUser(
            @Param("userId") Long userId,
            @Param("clientRequestId") String clientRequestId,
            @Param("lockRows") boolean lockRows);
    Long selectEvidenceIdForPunchInScope(
            @Param("punchEventId") Long punchEventId,
            @Param("scheduleId") Long scheduleId,
            @Param("userId") Long userId);
    PunchEvent selectFirstAcceptedPunch(@Param("scheduleId") Long scheduleId,
            @Param("punchType") String punchType);
    PunchEvent selectLastAcceptedPunch(@Param("scheduleId") Long scheduleId,
            @Param("punchType") String punchType);
    int insertEvidence(Evidence evidence);
    Evidence selectEvidenceById(@Param("evidenceId") Long evidenceId);
    PunchEvent selectPunchByEvidenceId(@Param("evidenceId") Long evidenceId);

    int insertRemainingWorkConfirmation(RemainingWorkConfirmation value);
    int invalidateDayResultForRemainingWork(
            @Param("scheduleId") Long scheduleId,
            @Param("updateBy") String updateBy);
    RemainingWorkConfirmation selectRemainingWorkConfirmationById(
            @Param("confirmationId") Long confirmationId);
    RemainingWorkConfirmation selectLatestRemainingWorkConfirmation(
            @Param("scheduleId") Long scheduleId,
            @Param("remainingStart") LocalDateTime remainingStart,
            @Param("remainingEnd") LocalDateTime remainingEnd,
            @Param("lockRows") boolean lockRows);
    List<RemainingWorkConfirmation> selectRemainingWorkConfirmations(
            @Param("scheduleId") Long scheduleId);
    List<RemainingWorkConfirmationSource>
            selectRemainingWorkConfirmationSources(
                    @Param("scheduleId") Long scheduleId,
                    @Param("lockRows") boolean lockRows);
    int insertRemainingWorkAttachment(RemainingWorkAttachment value);
    RemainingWorkAttachment selectRemainingWorkAttachmentById(
            @Param("attachmentId") Long attachmentId);
    List<RemainingWorkAttachment> selectRemainingWorkAttachments(
            @Param("confirmationId") Long confirmationId);

    DayResult selectDayResultByScheduleId(
            @Param("scheduleId") Long scheduleId);
    DayResult selectDayResultByScheduleIdForUpdate(
            @Param("scheduleId") Long scheduleId);
    List<CorrectionSource> selectSettlementCorrectionSources(
            @Param("scheduleId") Long scheduleId,
            @Param("lockRows") boolean lockRows);
    List<LeaveRequestState> selectBlockingLeaveRequests(
            @Param("userId") Long userId,
            @Param("shopId") Long shopId,
            @Param("shiftStart") LocalDateTime shiftStart,
            @Param("shiftEnd") LocalDateTime shiftEnd,
            @Param("lockRows") boolean lockRows);
    List<LeaveSegmentSource> selectApprovedLeaveSegments(
            @Param("userId") Long userId,
            @Param("shopId") Long shopId,
            @Param("shiftStart") LocalDateTime shiftStart,
            @Param("shiftEnd") LocalDateTime shiftEnd,
            @Param("lockRows") boolean lockRows);
    int upsertDayResult(DayResult result);
    List<DayResult> selectDayResultsByUserAndRange(
            @Param("userId") Long userId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    List<DayResult> selectDayResultsByShopAndRange(
            @Param("shopId") Long shopId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("userId") Long userId);
    MonthlySummary summarizeDayResultsByUserAndRange(
            @Param("userId") Long userId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
    int countUnfinalizedDayResults(@Param("shopId") Long shopId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
}
