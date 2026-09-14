package com.erp.oa.attendance.leave.balance;

import static com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import static com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceCalculatorTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceRequests.Adjustment;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceLeaveBalanceServiceTest
{
    static class Harness
    {
        final AttendanceLeaveBalanceMapper mapper=mock(AttendanceLeaveBalanceMapper.class);
        final AttendanceLeaveBalanceAccess access=mock(AttendanceLeaveBalanceAccess.class);
        final BusinessFeatureGate gate=mock(BusinessFeatureGate.class);
        final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        final EmployeeContext context=employee(); final Account account=new Account();
        final List<Bucket> buckets=new ArrayList<>(); final List<Ledger> ledger=new ArrayList<>();
        final Map<String,Command> commands=new HashMap<>(); final Map<Long,Rule> rules=new HashMap<>();
        final LocationMapping mapping=new LocationMapping();
        Rule rule=rule(); final AttendanceLeaveBalanceService service;
        Harness() throws Exception {this("2026-09-12T00:00:00Z");}
        Harness(String now) throws Exception
        {
            account.accountId=51L;account.userId=11L;account.leaveTypeId=1L;account.rowVersion=0L;
            mapping.mappingId=41L;mapping.rowVersion=0L;mapping.ownerDeptId=10L;mapping.legalEntityId=20L;mapping.workLocation=context.workLocation;mapping.locationCode="TEST_LOCATION";
            saveRule();
            when(access.actor()).thenReturn(99L);
            when(mapper.lockUser(11L)).thenReturn(11L);when(mapper.lockProfile(11L)).thenReturn(101L);
            when(mapper.selectContext(11L)).thenAnswer(i->context);when(mapper.countActiveType(1L)).thenReturn(1);
            when(mapper.matchLocations(any(),anyBoolean())).thenAnswer(i->new ArrayList<>(List.of(mapping)));
            when(mapper.matchRules(any(),eq(1L),anyString(),any(),anyBoolean())).thenAnswer(i->new ArrayList<>(List.of(rule)));
            when(mapper.selectTiers(anyLong())).thenReturn(List.of());when(mapper.selectRule(anyLong(),anyBoolean())).thenAnswer(i->rules.get(i.getArgument(0)));
            when(mapper.selectAccount(eq(11L),eq(1L),anyBoolean())).thenReturn(account);
            when(mapper.selectBuckets(eq(51L),anyBoolean())).thenAnswer(i->new ArrayList<>(buckets));
            when(mapper.ensureAccount(any())).thenReturn(1);when(mapper.bumpAccount(51L)).thenAnswer(i->{account.rowVersion++;return 1;});
            when(mapper.insertBucket(any())).thenAnswer(i->{Bucket b=i.getArgument(0);b.bucketId=100L+buckets.size();buckets.add(b);return 1;});
            when(mapper.updateBucket(any())).thenReturn(1);
            when(mapper.insertLedger(any())).thenAnswer(i->{Ledger l=i.getArgument(0);assertThat(ledger).noneMatch(old->old.eventKey.equals(l.eventKey));ledger.add(l);return 1;});
            when(mapper.selectCommand(eq(51L),anyString())).thenAnswer(i->commands.get(i.getArgument(1)));
            when(mapper.insertCommand(any())).thenAnswer(i->{Command c=i.getArgument(0);assertThat(commands.put(c.commandKey,c)).isNull();return 1;});
            service=new AttendanceLeaveBalanceService(mapper,access,new AttendanceLeaveBalanceCalculator(),gate,json,new AttendanceOvertimeTransferSourceGuard(mock(AttendanceOvertimeTransferMapper.class)),Clock.fixed(Instant.parse(now),ZoneOffset.UTC));
        }
        void saveRule() throws Exception {rule.configJson=json.writeValueAsString(rule.config);rules.put(rule.ruleId,rule);}
        Balance recalc(){return service.recalculateEmployee(11L,1L);}
        Adjustment adjustment(String key,String amount){Adjustment a=new Adjustment();a.leaveTypeId=1L;a.bucketId=buckets.get(0).bucketId;a.clientRequestId=key;a.amount=new BigDecimal(amount);a.reason="合成调整原因";Bucket b=buckets.get(0);a.bucketVersion=b.rowVersion;a.ruleId=b.ruleId;a.ruleVersion=rules.get(b.ruleId).version;a.displayUnit=b.displayUnit;a.minutesPerDay=b.minutesPerDay;return a;}
        Bucket oldBucket(long reserved) {
            Bucket b=new Bucket();b.bucketId=80L;b.accountId=51L;b.userId=11L;b.leaveTypeId=1L;b.ruleId=31L;b.mappingId=41L;b.mappingVersion=0L;
            b.ownerDeptId=10L;b.legalEntityId=20L;b.contextFingerprint="original-context";b.sourceKey="ANNUAL|2025";b.sourceType="ANNUAL";b.periodYear=2025;
            b.expiresOn=LocalDate.of(2025,12,31);b.expiryState="OPEN";b.rowVersion=0L;b.grantedUnits=3*420_000_000L;b.ruleGrantedUnits=b.grantedUnits;
            b.reservedUnits=reserved;b.displayUnit="DAYS";b.minutesPerDay=new BigDecimal("420");buckets.add(b);return b;
        }
    }
    @Test void missingProfileAndMappingAreUnknownNotZero() throws Exception {
        Harness h=new Harness();h.context.profileId=null;Balance b=h.recalc();assertThat(b.status).isEqualTo("MISSING_PROFILE");assertThat(b.availableUnits).isNull();verify(h.mapper,never()).ensureAccount(any());
        h.context.profileId=101L;when(h.mapper.matchLocations(any(),anyBoolean())).thenReturn(List.of());assertThat(h.recalc().status).isEqualTo("MISSING_MAPPING");
    }
    @Test void ambiguousMappingAndRulesProduceNoGrant() throws Exception {
        Harness h=new Harness();when(h.mapper.matchLocations(any(),anyBoolean())).thenReturn(List.of(h.mapping,h.mapping));assertThat(h.recalc().status).isEqualTo("AMBIGUOUS_MAPPING");
        when(h.mapper.matchLocations(any(),anyBoolean())).thenReturn(List.of(h.mapping));when(h.mapper.matchRules(any(),anyLong(),anyString(),any(),anyBoolean())).thenReturn(List.of(h.rule,h.rule));
        assertThat(h.recalc().status).isEqualTo("AMBIGUOUS_RULE");assertThat(h.ledger).isEmpty();
    }
    @Test void configuredZeroBalanceStillReturnsReadableRuleAndExactUnitConversion() throws Exception {
        Harness h=new Harness();Balance b=h.service.employee(11L,1L);assertThat(b.availableUnits).isZero();assertThat(b.ruleName).isEqualTo("合成规则");assertThat(b.displayUnit).isEqualTo("DAYS");assertThat(b.minutesPerDay).isEqualByComparingTo("420");assertThat(h.ledger).isEmpty();
    }
    @Test void grantReplayDoesNotRepeatOrOverwriteLedger() throws Exception {
        Harness h=new Harness();assertThat(h.recalc().availableUnits).isEqualTo(8*420_000_000L);h.recalc();h.recalc();assertThat(h.buckets).hasSize(1);assertThat(h.ledger).hasSize(1);assertThat(h.commands).hasSize(1);
        assertThat(h.ledger.get(0).contextJson).contains("workStartDate","mapping","ruleVersion").doesNotContain("salary","idCard","bank");
    }
    @Test void newRuleVersionGrantsDifferenceNotAnotherWholeYear() throws Exception {
        Harness h=new Harness();h.recalc();h.rule=rule();h.rule.ruleId=32L;h.rule.version=2;h.rule.config.amount=new BigDecimal("10");h.saveRule();
        assertThat(h.recalc().availableUnits).isEqualTo(10*420_000_000L);assertThat(h.ledger.get(1).units).isEqualTo(2*420_000_000L);assertThat(h.buckets).hasSize(1);
    }
    @Test void auditedAdjustmentSurvivesLaterRuleRecalculation() throws Exception {
        Harness h=new Harness();h.recalc();h.service.adjust(11L,h.adjustment("adjust-1","1"));h.context.profileUpdatedAt=java.time.LocalDateTime.of(2026,9,13,0,0);
        assertThat(h.recalc().availableUnits).isEqualTo(9*420_000_000L);h.rule=rule();h.rule.ruleId=32L;h.rule.version=2;h.rule.config.amount=new BigDecimal("10");h.saveRule();
        assertThat(h.recalc().availableUnits).isEqualTo(11*420_000_000L);
    }
    @Test void returningToPriorContextReconcilesAgainRatherThanReusingOldCommand() throws Exception {
        Harness h=new Harness();h.recalc();Rule original=h.rule;h.rule=rule();h.rule.ruleId=32L;h.rule.config.amount=new BigDecimal("10");h.saveRule();h.recalc();h.rule=original;
        assertThat(h.recalc().availableUnits).isEqualTo(8*420_000_000L);assertThat(h.ledger).hasSize(3);
    }
    @Test void adjustmentReplaysSamePayloadButRejectsChangedPayload() throws Exception {
        Harness h=new Harness();h.recalc();Adjustment a=h.adjustment("stable-key","1");Command first=h.service.adjust(11L,a);assertThat(h.service.adjust(11L,a)).isSameAs(first);a.amount=new BigDecimal("2");
        assertThatThrownBy(()->h.service.adjust(11L,a)).hasMessageContaining("内容不同");assertThat(h.ledger).hasSize(2);
    }
    @Test void adjustmentCannotSpendReservedOrConsumedUnits() throws Exception {
        Harness h=new Harness();h.recalc();h.buckets.get(0).reservedUnits=7*420_000_000L;
        assertThatThrownBy(()->h.service.adjust(11L,h.adjustment("negative","-2"))).hasMessageContaining("可用额度不足");assertThat(h.buckets.get(0).grantedUnits).isEqualTo(8*420_000_000L);
    }
    @Test void downwardRuleChangeCannotEraseUsage() throws Exception {
        Harness h=new Harness();h.recalc();h.buckets.get(0).consumedUnits=7*420_000_000L;h.rule=rule();h.rule.ruleId=32L;h.rule.config.amount=new BigDecimal("6");h.saveRule();
        assertThatThrownBy(h::recalc).hasMessageContaining("历史未被覆盖");assertThat(h.buckets.get(0).consumedUnits).isEqualTo(7*420_000_000L);
    }
    @Test void expirationPreservesReservedOriginalBucket() throws Exception {
        Harness h=new Harness("2026-01-15T00:00:00Z");Bucket old=h.oldBucket(420_000_000L);Balance result=h.recalc();
        assertThat(old.expiryState).isEqualTo("WAITING_RESERVED");assertThat(old.expiredUnits).isZero();assertThat(old.reservedUnits).isEqualTo(420_000_000L);assertThat(result.status).isEqualTo("EXPIRY_HAS_RESERVATIONS");
    }
    @Test void carryRetainsOriginalExpiryIdentityAndRunsOnce() throws Exception {
        Harness h=new Harness("2026-01-15T00:00:00Z");Bucket old=h.oldBucket(0);h.recalc();h.recalc();
        Bucket carry=h.buckets.stream().filter(b->"CARRY".equals(b.sourceType)).findFirst().orElseThrow();
        assertThat(carry.sourceKey).isEqualTo("CARRY|80");assertThat(carry.expiresOn).isEqualTo(LocalDate.of(2026,3,31));assertThat(carry.grantedUnits).isEqualTo(2*420_000_000L);
        assertThat(old.expiredUnits).isEqualTo(420_000_000L);assertThat(old.carriedUnits).isEqualTo(2*420_000_000L);assertThat(h.buckets).hasSize(3);
    }
    @Test void elapsedCarryWindowDoesNotCreateFreshSpendableBalance() throws Exception {
        Harness h=new Harness();Bucket old=h.oldBucket(0);h.recalc();assertThat(old.expiredUnits).isEqualTo(3*420_000_000L);assertThat(h.buckets).noneMatch(b->"CARRY".equals(b.sourceType));
    }
    @Test void scheduledAccrualUsesNoForgedPrincipal() throws Exception {
        Harness h=new Harness();h.service.recalculateScheduled(11L,1L);verifyNoInteractions(h.access);assertThat(h.ledger.get(0).operatorUserId).isNull();
    }
    @Test void ownershipIsCheckedAfterSourceLocks() throws Exception {
        Harness h=new Harness();h.recalc();var order=inOrder(h.mapper,h.access);order.verify(h.mapper).lockUser(11L);order.verify(h.mapper).lockProfile(11L);order.verify(h.mapper).selectContext(11L);order.verify(h.access).employee(h.context,false,"adjust");
    }
    @Test void oneRecalculationFreezesServerDateAcrossNewYear() throws Exception {
        Harness h=new Harness();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        Clock changing=new Clock(){public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(java.time.ZoneId zone){return this;}public Instant instant(){return Instant.parse(calls.getAndIncrement()==0?"2026-12-31T23:59:59Z":"2027-01-01T00:00:01Z");}};
        var service=new AttendanceLeaveBalanceService(h.mapper,h.access,new AttendanceLeaveBalanceCalculator(),h.gate,h.json,new AttendanceOvertimeTransferSourceGuard(mock(AttendanceOvertimeTransferMapper.class)),changing);
        service.recalculateEmployee(11L,1L);assertThat(h.buckets.get(0).sourceKey).isEqualTo("ANNUAL|2026");assertThat(h.buckets.get(0).expiresOn).isEqualTo(LocalDate.of(2026,12,31));assertThat(calls.get()).isEqualTo(1);
    }
    @Test void sourceOnlyPolicyDoesNotMintAnyQuota() throws Exception {
        Harness h=new Harness();h.rule.config.leaveCategory="COMPENSATORY";h.rule.config.calculation="SOURCE_ONLY";h.rule.config.amount=null;h.saveRule();assertThat(h.recalc().availableUnits).isZero();assertThat(h.buckets).isEmpty();
    }
    @Test void ruleReconciliationAndAdjustmentRetainTransactionBoundary() throws Exception {
        for(String name:List.of("recalculateMy","recalculateEmployee","recalculateScheduled","adjust","saveRule","publishRule","saveLocation"))
            assertThat(Arrays.stream(AttendanceLeaveBalanceService.class.getMethods()).filter(m->m.getName().equals(name)).allMatch(m->m.isAnnotationPresent(Transactional.class))).isTrue();
    }
}
