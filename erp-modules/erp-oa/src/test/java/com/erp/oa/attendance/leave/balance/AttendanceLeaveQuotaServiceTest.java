package com.erp.oa.attendance.leave.balance;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.oa.attendance.leave.*;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaModels.*;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
public class AttendanceLeaveQuotaServiceTest
{
 @BeforeEach void tx(){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);}
 @AfterEach void clear(){TransactionSynchronizationManager.clear();}
 public static class Harness
 {
  public final AttendanceLeaveQuotaMapper mapper=mock(AttendanceLeaveQuotaMapper.class);
  public final AttendanceLeaveMapper leaves=mock(AttendanceLeaveMapper.class);
  public final AttendanceLeaveBalanceMapper balances=mock(AttendanceLeaveBalanceMapper.class);
  public final AttendanceLeaveBalanceService core=mock(AttendanceLeaveBalanceService.class);
  public final AttendanceTimeCreditMapper credits=mock(AttendanceTimeCreditMapper.class);
  public final AttendanceOvertimeTransferSourceGuard sources=mock(AttendanceOvertimeTransferSourceGuard.class);
  public final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
  public final LeaveRequest request=new LeaveRequest();public final LeaveType type=new LeaveType();public final Match match=new Match();
  public final EmployeeContext employee=new EmployeeContext();public final Account account=new Account();
  public final List<Bucket> buckets=new ArrayList<>();public final List<Allocation> allocations=new ArrayList<>();public final List<Ledger> ledger=new ArrayList<>();public final Map<String,Event> events=new HashMap<>();
  public AttendanceLeaveQuotaService service;
  public Harness(){
   request.leaveRequestId=10L;request.userId=11L;request.shopId=20L;request.leaveTypeId=1L;request.businessRound=0;request.status="DRAFT";request.totalMinutes=1440;request.requestedDays=BigDecimal.ONE;request.startTime=LocalDateTime.of(2026,9,13,9,0);request.endTime=request.startTime.plusDays(1);
   type.leaveTypeId=1L;type.rowVersion=1L;type.typeCode="ANNUAL";type.unitMode="DAY";type.balanceRequired=true;type.stepMinutes=1;type.status="ENABLED";
   employee.userId=11L;employee.legalEntityId=30L;employee.deptId=20L;account.accountId=1L;account.userId=11L;account.leaveTypeId=1L;
   match.status="READY";match.rule=new Rule();match.rule.ruleId=1L;match.rule.version=1;match.rule.config=new RuleConfig();match.rule.config.unit="DAYS";match.rule.config.minutesPerDay=new BigDecimal("420");match.mapping=new LocationMapping();match.mapping.mappingId=1L;match.mapping.rowVersion=1L;
   when(core.lockContext(11L)).thenReturn(employee);when(core.match(eq(employee),eq(1L),anyBoolean())).thenAnswer(i->match);when(core.lockAccount(11L,1L)).thenReturn(account);when(balances.selectAccount(11L,1L,true)).thenReturn(account);
   Balance ready=new Balance();ready.status="READY";ready.availableUnits=600_000_000L;when(core.prepareForLeave(11L,1L)).thenReturn(ready);
   buckets.add(bucket(1,200,LocalDate.of(2026,12,31)));buckets.add(bucket(2,400,LocalDate.of(2027,12,31)));when(balances.selectBuckets(1L,true)).thenReturn(buckets);when(balances.updateBucket(any())).thenReturn(1);when(balances.bumpAccount(1L)).thenReturn(1);
   when(balances.insertLedger(any())).thenAnswer(i->{ledger.add(i.getArgument(0));return 1;});
   when(mapper.selectEvent(eq(10L),anyInt(),anyString())).thenAnswer(i->events.get(i.getArgument(1)+":"+i.getArgument(2)));
   when(mapper.insertEvent(any())).thenAnswer(i->{Event e=i.getArgument(0);events.put(e.businessRound+":"+e.eventKey,e);return 1;});
   when(mapper.insertAllocation(any())).thenAnswer(i->{Allocation a=i.getArgument(0);a.allocationId=(long)allocations.size()+1;allocations.add(a);return 1;});
   when(mapper.selectAllocations(eq(10L),anyInt(),eq(true))).thenAnswer(i->allocations.stream().filter(a->a.businessRound.equals(i.getArgument(1))).toList());
   when(mapper.transitionAllocation(anyLong(),anyString(),anyString())).thenAnswer(i->{Allocation a=allocations.stream().filter(x->x.allocationId.equals(i.getArgument(0))).findFirst().orElseThrow();if(!a.status.equals(i.getArgument(1)))return 0;a.status=i.getArgument(2);return 1;});
   when(mapper.updateRequestQuotaStatus(eq(10L),anyInt(),anyString())).thenAnswer(i->{request.quotaStatus=i.getArgument(2);return 1;});when(mapper.returnFailedSubmission(eq(10L),anyInt())).thenReturn(1);
   when(leaves.selectLeaveRequestByIdForUpdate(10L)).thenReturn(request);when(leaves.selectLeaveRequestById(10L)).thenReturn(request);
   service=at(LocalDate.of(2026,9,12));
  }
  public AttendanceLeaveQuotaService at(LocalDate date){return new AttendanceLeaveQuotaService(mapper,leaves,balances,core,credits,sources,json,Clock.fixed(date.atStartOfDay().toInstant(ZoneOffset.UTC),ZoneOffset.UTC));}
  Bucket bucket(long id,long minutes,LocalDate expires){Bucket b=new Bucket();b.bucketId=id;b.accountId=1L;b.userId=11L;b.leaveTypeId=1L;b.legalEntityId=30L;b.ruleId=1L;b.sourceType="ANNUAL";b.sourceKey="ANNUAL|"+expires.getYear();b.contextFingerprint="synthetic";b.grantedUnits=minutes*1_000_000L;b.expiresOn=expires;b.expiryState="OPEN";b.rowVersion=0L;return b;}
  public void reserve(){service.freezeDraft(request,type,null);service.reserve(request,type,1);request.businessRound=1;request.status="PENDING";}
  long reserved(){return buckets.stream().mapToLong(b->b.reservedUnits).sum();}long consumed(){return buckets.stream().mapToLong(b->b.consumedUnits).sum();}
 }
 @Test void savesExplicitDaysAndFrozenServerPolicyWithoutReserving(){Harness h=new Harness();h.service.freezeDraft(h.request,h.type,null);assertThat(h.request.quotaUnits).isEqualTo(420_000_000L);assertThat(h.request.totalMinutes).isEqualTo(1440);assertThat(h.request.quotaPolicySnapshot.ruleVersion).isEqualTo(1);assertThat(h.allocations).isEmpty();}
 @Test void expectedOldRuleCannotSilentlyReinterpretSavedDays(){Harness h=new Harness();h.service.freezeDraft(h.request,h.type,null);PolicySnapshot old=h.service.copyPolicy(h.request.quotaPolicySnapshot);h.match.rule.version=2;h.match.rule.config.minutesPerDay=new BigDecimal("480");assertThatThrownBy(()->h.service.freezeDraft(h.request,h.type,old)).hasMessageContaining("政策或单位已变化");}
 @Test void noTransactionAndUnexpectedOuterIsolationAreRejected(){Harness h=new Harness();TransactionSynchronizationManager.setActualTransactionActive(false);assertThatThrownBy(()->h.service.freezeDraft(h.request,h.type,null)).hasMessageContaining("同一事务");TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);assertThatThrownBy(()->h.service.freezeDraft(h.request,h.type,null)).hasMessageContaining("旧快照");}
 @Test void reservationUsesOriginalBucketsInExpiryOrder(){Harness h=new Harness();h.reserve();assertThat(h.allocations).extracting(a->a.bucketId).containsExactly(1L,2L);assertThat(h.allocations).extracting(a->a.units).containsExactly(200_000_000L,220_000_000L);assertThat(h.reserved()).isEqualTo(420_000_000L);assertThat(h.ledger).hasSize(2);}
 @Test void repeatedSubmissionDoesNotCreateAnotherAllocation(){Harness h=new Harness();h.reserve();h.service.reserve(h.request,h.type,1);assertThat(h.allocations).hasSize(2);assertThat(h.reserved()).isEqualTo(420_000_000L);}
 @Test void insufficientRemainingQuotaCannotBeAccepted(){Harness h=new Harness();h.reserve();h.request.businessRound=2;assertThatThrownBy(()->h.service.reserve(h.request,h.type,2)).hasMessageContaining("可用额度不足");}
 @Test void approvedAllocationBecomesConsumptionWithoutSpendingTwice(){Harness h=new Harness();h.reserve();h.service.decision(10L,1,"APPROVE","approved-001");assertThat(h.reserved()).isZero();assertThat(h.consumed()).isEqualTo(420_000_000L);assertThat(h.request.quotaStatus).isEqualTo("CONSUMED");assertThat(h.ledger.subList(2,4)).extracting(l->l.units).containsOnly(0L);}
 @Test void rejectionReturnsExactlyTheOriginalBucketAmounts(){Harness h=new Harness();h.reserve();h.service.decision(10L,1,"REJECT","rejected-001");assertThat(h.reserved()).isZero();assertThat(h.allocations).allMatch(a->"RELEASED".equals(a.status));assertThat(h.buckets).extracting(b->b.bucketId).containsExactly(1L,2L);}
 @Test void expiredReleasePreservesYearAndDoesNotAdvertiseNewAvailableUnits(){Harness h=new Harness();h.reserve();h.service=h.at(LocalDate.of(2028,1,1));h.service.decision(10L,1,"RETURN","expired-return");assertThat(h.buckets.stream().mapToLong(b->b.expiredUnits).sum()).isEqualTo(420_000_000L);assertThat(h.buckets).extracting(b->b.expiresOn).containsExactly(LocalDate.of(2026,12,31),LocalDate.of(2027,12,31));assertThat(h.ledger.subList(2,4)).extracting(l->l.units).containsOnly(0L);}
 @Test void repeatedApprovalEventReturnsOriginalResult(){Harness h=new Harness();h.reserve();h.service.decision(10L,1,"APPROVE","repeat-event");h.service.decision(10L,1,"APPROVE","repeat-event");assertThat(h.consumed()).isEqualTo(420_000_000L);assertThat(h.ledger).hasSize(4);}
 @Test void oldRoundAndChangedEventActionCannotAffectCurrentQuota(){Harness h=new Harness();h.reserve();assertThatThrownBy(()->h.service.decision(10L,0,"APPROVE","old-round")).hasMessageContaining("轮次");h.service.decision(10L,1,"APPROVE","fixed-event");assertThatThrownBy(()->h.service.decision(10L,1,"WITHDRAW","fixed-event")).hasMessageContaining("事件内容不同");}
 @Test void invalidOvertimeSourcePreventsConsumption(){Harness h=new Harness();h.reserve();h.buckets.get(0).sourceType="OVERTIME";when(h.sources.problem(eq(h.buckets.get(0)),eq(h.employee))).thenReturn("原日结失效");assertThatThrownBy(()->h.service.decision(10L,1,"APPROVE","invalid-source")).hasMessageContaining("原日结失效");assertThat(h.reserved()).isEqualTo(420_000_000L);}
 @Test void payrollBoundaryPreventsTerminatingConsumedLeave(){Harness h=new Harness();h.reserve();h.service.decision(10L,1,"APPROVE","approved-before-payroll");h.request.status="APPROVED";when(h.credits.countSalaryRecords(11L,"2026-09")).thenReturn(1);assertThatThrownBy(()->h.service.decision(10L,1,"TERMINATE","cannot-refund-payroll")).hasMessageContaining("已进入工资");assertThat(h.consumed()).isEqualTo(420_000_000L);}
 @Test void approvedTerminationRefundsOriginalAllocationOnce(){Harness h=new Harness();h.reserve();h.service.decision(10L,1,"APPROVE","approved-before-terminate");h.request.status="APPROVED";h.service.decision(10L,1,"TERMINATE","terminated-once");h.service.decision(10L,1,"TERMINATE","terminated-once");assertThat(h.consumed()).isZero();assertThat(h.allocations).allMatch(a->"REFUNDED".equals(a.status));assertThat(h.ledger).hasSize(6);}
 @Test void changedSourceSetAfterLocksRequiresRetry(){Harness h=new Harness();h.service.freezeDraft(h.request,h.type,null);DayLock d=new DayLock();d.dayResultId=99L;d.shopId=20L;d.businessDate=LocalDate.of(2026,9,1);when(h.mapper.selectOvertimeSourceDays(11L,1L)).thenReturn(List.of(),List.of(d));assertThatThrownBy(()->h.service.reserve(h.request,h.type,1)).hasMessageContaining("来源集合已变化");assertThat(h.allocations).isEmpty();}
 @Test void unknownStartKeepsReservationAndOnlyProvenNotAttemptedReleases(){Harness h=new Harness();h.reserve();h.request.status="SUBMITTING";h.service.reviewUnknown(h.request,1);assertThat(h.request.quotaStatus).isEqualTo("REVIEW");assertThat(h.reserved()).isEqualTo(420_000_000L);h.service.failBeforeRemote(h.request,1,"never-attempted");assertThat(h.reserved()).isZero();verify(h.mapper).returnFailedSubmission(10L,1);}
 @Test void standardAnnualAndCompensatoryCannotDisableBalanceOrHr(){for(String code:List.of("ANNUAL","COMPENSATORY")){LeaveType t=new LeaveType();t.typeCode=code;t.balanceRequired=false;t.approvalRequired=false;AttendanceLeaveAmountPolicy.effectiveType(t);assertThat(t.balanceRequired).isTrue();assertThat(t.approvalRequired).isTrue();}LeaveType custom=new LeaveType();custom.typeCode="CUSTOM_HOUR";custom.balanceRequired=false;custom.approvalRequired=false;AttendanceLeaveAmountPolicy.effectiveType(custom);assertThat(custom.approvalRequired).isFalse();}
}
