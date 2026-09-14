package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferModels.Transfer;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferRequests.*;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.fasterxml.jackson.databind.ObjectMapper;

class AttendanceOvertimeTransferServiceTest
{
    final AttendanceOvertimeTransferMapper mapper=mock(AttendanceOvertimeTransferMapper.class);
    final AttendanceTimeCreditMapper credits=mock(AttendanceTimeCreditMapper.class);
    final AttendanceLeaveBalanceService balances=mock(AttendanceLeaveBalanceService.class);
    final AttendanceLeaveBalanceMapper balanceMapper=mock(AttendanceLeaveBalanceMapper.class);
    final AttendanceLeaveBalanceAccess access=mock(AttendanceLeaveBalanceAccess.class);
    final ShopScopeService shops=mock(ShopScopeService.class);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final Map<Long,Transfer> saved=new HashMap<>();final Map<String,Transfer> requests=new HashMap<>();
    final EmployeeContext employee=AttendanceLeaveBalanceCalculatorTest.employee();
    final DayResult source=new DayResult();final Match match=new Match();
    AttendanceOvertimeTransferService service;long next=70;
    @BeforeEach void setup() {
        source.dayResultId=1L;source.scheduleId=21L;source.userId=11L;source.shopId=10L;source.businessDate=LocalDate.of(2026,9,10);
        source.rowVersion=9L;source.settledAt=LocalDateTime.of(2026,9,11,1,0);source.workedMinutes=540;source.scheduledMinutes=480;
        match.status="READY";match.rule=AttendanceLeaveBalanceCalculatorTest.rule();match.rule.config.leaveCategory="COMPENSATORY";match.rule.config.calculation="SOURCE_ONLY";
        when(access.actor()).thenReturn(99L);when(shops.resolveRequiredShopDept(10L)).thenReturn(10L);
        when(credits.selectDayResultById(1L)).thenReturn(source);when(credits.selectDayResultsForUpdate(List.of(1L))).thenReturn(List.of(source));
        when(balanceMapper.selectContext(11L)).thenReturn(employee);when(balances.lockContext(11L)).thenReturn(employee);when(balances.match(eq(employee),eq(1L),anyBoolean())).thenReturn(match);
        when(mapper.countCurrentOwnership(eq(11L),eq(10L),eq(20L),anyBoolean())).thenReturn(1);when(mapper.countPublishedSource(1L)).thenReturn(1);
        when(mapper.selectRequest(eq(10L),eq(99L),anyString())).thenAnswer(i->requests.get(i.getArgument(2)));
        when(mapper.selectTransfer(anyLong(),anyBoolean())).thenAnswer(i->saved.get(i.getArgument(0)));
        when(balances.grantOvertime(any(),any(),any())).thenAnswer(i->{Transfer t=i.getArgument(2);t.bucketId=100L+next;t.ruleId=31L;t.mappingId=41L;t.mappingVersion=0L;return new Bucket();});
        when(mapper.insertTransfer(any())).thenAnswer(i->{Transfer t=i.getArgument(0);t.transferId=next++;Transfer copy=json.convertValue(t,Transfer.class);saved.put(t.transferId,copy);requests.put(t.clientRequestId,copy);return 1;});
        service=new AttendanceOvertimeTransferService(mapper,credits,balances,balanceMapper,access,shops,mock(BusinessFeatureGate.class),json);
    }
    Apply input(String key,int minutes){Apply a=new Apply();a.sourceDayResultId=1L;a.sourceVersion=9L;a.leaveTypeId=1L;a.transferMinutes=minutes;a.reason="合成主管核定";a.clientRequestId=key;return a;}
    Reverse reverse(String key){Reverse r=new Reverse();r.clientRequestId=key;r.reason="合成撤销核定";return r;}
    @Test void confirmationUsesFrozenSourceAndLocksPeriodBeforeDayAndBalance() {
        Transfer result=service.apply(input("confirm-001",30),10L);assertThat(result.transferMinutes).isEqualTo(30);assertThat(result.sourceVersion).isEqualTo(9);assertThat(result.legalEntityId).isEqualTo(20);
        var order=inOrder(credits,balances,mapper);order.verify(credits).lockPeriod(10L,"2026-09");order.verify(credits).selectDayResultsForUpdate(List.of(1L));order.verify(balances).lockContext(11L);order.verify(balances).match(employee,1L,true);order.verify(balances).grantOvertime(eq(employee),eq(match),any());order.verify(mapper).insertTransfer(any());
    }
    @Test void inputMutationDuringLockWaitCannotChangeApprovedMinutesOrVersion() {
        Apply input=input("freeze-001",30);doAnswer(i->{input.transferMinutes=999;input.sourceVersion=999L;input.reason="changed";return 1;}).when(credits).lockPeriod(10L,"2026-09");
        Transfer result=service.apply(input,10L);assertThat(result.transferMinutes).isEqualTo(30);assertThat(result.sourceVersion).isEqualTo(9);assertThat(result.reason).isEqualTo("合成主管核定");
    }
    @Test void unknownSuccessfulRetryReturnsOriginalEvenAfterSourceInvalidation() {
        Apply input=input("retry-001",30);Transfer first=service.apply(input,10L);source.settledAt=null;source.rowVersion=10L;
        assertThat(service.apply(input,10L).transferId).isEqualTo(first.transferId);verify(balances,times(1)).grantOvertime(any(),any(),any());
        input.transferMinutes=31;assertThatThrownBy(()->service.apply(input,10L)).hasMessageContaining("内容不同");
    }
    @Test void sourceVersionChangedBeforeConfirmationCannotGrant() {
        source.rowVersion=10L;assertThatThrownBy(()->service.apply(input("version-001",30),10L)).hasMessageContaining("版本");verify(balances,never()).grantOvertime(any(),any(),any());
    }
    @Test void unfinalizedDayCannotBecomeAutomaticOvertime() {
        source.settledAt=null;assertThatThrownBy(()->service.apply(input("unsettled-001",30),10L)).hasMessageContaining("日结尚未完成");verify(mapper,never()).insertTransfer(any());
    }
    @Test void existingOffsetAndConversionBothReduceSourceAvailability() {
        when(credits.selectNetSourceUsed(1L)).thenReturn(30);when(credits.selectNetSourceTransferred(1L)).thenReturn(20);
        assertThatThrownBy(()->service.apply(input("overspend-001",11),10L)).hasMessageContaining("扣除早退抵扣和已有转休");
        assertThat(service.apply(input("remaining-001",10),10L).transferMinutes).isEqualTo(10);
    }
    @Test void invalidatedPriorTransferBlocksAdditionalAllocation() {
        when(credits.countInvalidSourceTransfers(1L)).thenReturn(1);assertThatThrownBy(()->service.apply(input("invalid-001",10),10L)).hasMessageContaining("已核定版本失效");verify(mapper,never()).insertTransfer(any());
    }
    @Test void originalStoreScopeIsCheckedBeforeEmployeeHistory() {
        source.shopId=12L;assertThatThrownBy(()->service.context(1L,1L,10L)).hasMessageContaining("不属于当前门店");verify(mapper,never()).selectHistory(anyLong());verify(balanceMapper,never()).selectContext(anyLong());
    }
    @Test void changedCompanyOrSourceDayTransferRequiresReview() {
        when(mapper.countCurrentOwnership(eq(11L),eq(10L),eq(20L),anyBoolean())).thenReturn(0);assertThatThrownBy(()->service.apply(input("company-001",10),10L)).hasMessageContaining("原门店及法人");
        when(mapper.countCurrentOwnership(eq(11L),eq(10L),eq(20L),anyBoolean())).thenReturn(1);when(mapper.countLaterTransfer(11L,source.businessDate)).thenReturn(1);
        assertThatThrownBy(()->service.apply(input("transfer-day-001",10),10L)).hasMessageContaining("来源当日");verify(mapper,never()).insertTransfer(any());
    }
    @Test void salaryRecordStopsNewAllocationBeforeBalanceWrite() {
        when(credits.countSalaryRecords(11L,"2026-09")).thenReturn(1);assertThatThrownBy(()->service.apply(input("salary-001",10),10L)).hasMessageContaining("工资已生成");verify(balances,never()).lockContext(anyLong());
    }
    @Test void missingOrAnnualRuleNeverDefaultsToCompensatoryGrant() {
        match.status="NO_RULE";match.reason="没有生效规则";assertThatThrownBy(()->service.apply(input("missing-rule-001",10),10L)).hasMessageContaining("没有生效规则");
        match.status="READY";match.rule.config.leaveCategory="ANNUAL";assertThatThrownBy(()->service.apply(input("wrong-rule-001",10),10L)).hasMessageContaining("调休来源核定规则");verify(mapper,never()).insertTransfer(any());
    }
    @Test void reversalKeepsOriginalIdentityAndIsReplayable() {
        Transfer original=service.apply(input("for-reverse-001",30),10L);Transfer result=service.reverse(original.transferId,reverse("reverse-001"),10L);
        assertThat(result.originalTransferId).isEqualTo(original.transferId);assertThat(result.sourceVersion).isEqualTo(9);assertThat(result.action).isEqualTo("REVERSE");assertThat(result.transferMinutes).isEqualTo(30);
        assertThat(service.reverse(original.transferId,reverse("reverse-001"),10L).transferId).isEqualTo(result.transferId);verify(balances,times(1)).reverseOvertime(any(),any());
    }
    @Test void reversalAfterPayrollOrPriorReversalDoesNotReleaseAgain() {
        Transfer original=service.apply(input("reversal-guard-001",30),10L);when(credits.countSalaryRecords(11L,"2026-09")).thenReturn(1);
        assertThatThrownBy(()->service.reverse(original.transferId,reverse("blocked-reverse-001"),10L)).hasMessageContaining("工资已生成");
        when(credits.countSalaryRecords(11L,"2026-09")).thenReturn(0);when(mapper.countReversed(original.transferId)).thenReturn(1);
        assertThatThrownBy(()->service.reverse(original.transferId,reverse("blocked-reverse-002"),10L)).hasMessageContaining("已撤销");verify(balances,never()).reverseOvertime(any(),any());
    }
    @Test void sourceAndBalanceWritesHaveExplicitRollbackBoundary() throws Exception {
        for(String name:List.of("apply","reverse"))for(var method:AttendanceOvertimeTransferService.class.getDeclaredMethods())if(method.getName().equals(name))
            assertThat(method.getAnnotation(Transactional.class).rollbackFor()).containsExactly(Exception.class);
    }
}
