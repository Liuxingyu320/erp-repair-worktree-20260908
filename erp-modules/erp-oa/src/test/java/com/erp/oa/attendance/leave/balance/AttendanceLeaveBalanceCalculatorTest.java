package com.erp.oa.attendance.leave.balance;

import static com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttendanceLeaveBalanceCalculatorTest
{
    final AttendanceLeaveBalanceCalculator calculator = new AttendanceLeaveBalanceCalculator();
    static Rule rule()
    {
        Rule r = new Rule(); r.ruleId=31L; r.familyId=31L; r.version=1; r.rowVersion=0L; r.ownerDeptId=10L;
        r.legalEntityId=20L; r.leaveTypeId=1L; r.priority=1; r.status="PUBLISHED"; r.name="合成规则"; r.locationCode="TEST_LOCATION";
        r.effectiveFrom=LocalDate.of(2020,1,1); r.effectiveTo=LocalDate.of(2030,12,31);
        RuleConfig c = new RuleConfig(); r.config=c; c.leaveCategory="ANNUAL"; c.calculation="FIXED"; c.tenureBasis="WORK_START";
        c.tenureAt="AS_OF_DATE"; c.unit="DAYS"; c.minutesPerDay=new BigDecimal("420"); c.amount=new BigDecimal("8");
        c.grantStepMinutes=new BigDecimal("1"); c.proration="NONE"; c.grantTiming="UPFRONT"; c.rounding="DOWN";
        c.expiryMonthsAfterYear=0; c.carryExpiryMonths=3; c.carryLimit=new BigDecimal("2"); return r;
    }
    static EmployeeContext employee()
    {
        EmployeeContext e = new EmployeeContext(); e.userId=11L; e.deptId=10L; e.profileId=101L; e.legalEntityId=20L;
        e.workLocation="合成工作地"; e.employeeStatus="正式"; e.userStatus="0"; e.delFlag="0";
        e.workStartDate=LocalDate.of(2021,9,12); e.entryDate=LocalDate.of(2024,1,1); return e;
    }
    static Tier tier(int from, Integer to, String amount) { Tier t=new Tier();t.minYears=from;t.maxYearsExclusive=to;t.amount=new BigDecimal(amount);return t; }
    @Test void convertsConfiguredDaysAndHoursExactly() {
        assertThat(AttendanceLeaveBalanceCalculator.units(new BigDecimal("0.125"),"DAYS",new BigDecimal("420"))).isEqualTo(52_500_000L);
        assertThat(AttendanceLeaveBalanceCalculator.units(new BigDecimal("1.25"),"HOURS",null)).isEqualTo(75_000_000L);
        assertThat(AttendanceLeaveBalanceCalculator.units(new BigDecimal("0.000001"),"MINUTES",null)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"0.0000001","99999999999999999999999"})
    void rejectsLossyOrOverflowingInternalConversion(String input) {
        assertThatThrownBy(()->AttendanceLeaveBalanceCalculator.units(new BigDecimal(input),"MINUTES",null)).hasMessageContaining("精确换算");
    }
    @Test void noUnconfiguredNationalAmountOrDailyMinutes() {
        Rule r=rule();r.config.amount=null;assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("明确配置");
        r.config.amount=BigDecimal.ONE;r.config.minutesPerDay=null;assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("每日分钟");
    }
    @Test void exactTenureAnniversaryChangesConfiguredTier() {
        Rule r=rule();r.config.calculation="TENURE";r.tiers=List.of(tier(0,5,"4"),tier(5,null,"9"));
        assertThat(calculator.calculate(r,employee(),LocalDate.of(2026,9,11))).isEqualTo(4*420_000_000L);
        assertThat(calculator.calculate(r,employee(),LocalDate.of(2026,9,12))).isEqualTo(9*420_000_000L);
    }
    @Test void entryBasisAndPeriodStartAreExplicitChoices() {
        Rule r=rule();r.config.calculation="TENURE";r.tiers=List.of(tier(0,5,"4"),tier(5,null,"9"));r.config.tenureAt="PERIOD_START";
        assertThat(calculator.calculate(r,employee(),LocalDate.of(2026,9,12))).isEqualTo(4*420_000_000L);
        r.config.tenureAt="AS_OF_DATE";r.config.tenureBasis="ENTRY";
        assertThat(calculator.calculate(r,employee(),LocalDate.of(2026,9,12))).isEqualTo(4*420_000_000L);
    }
    @Test void requiresRawEmploymentDates() { EmployeeContext e=employee();e.workStartDate=null;assertThatThrownBy(()->calculator.calculate(rule(),e,LocalDate.of(2026,9,12))).hasMessageContaining("基础"); }
    @Test void rejectsFutureEntryInsteadOfNegativeTenure() {EmployeeContext e=employee();e.entryDate=LocalDate.of(2027,1,1);assertThatThrownBy(()->calculator.calculate(rule(),e,LocalDate.of(2026,9,12))).hasMessageContaining("尚未入职");}
    @Test void leapYearProrationUsesActualCalendarAndConfiguredRounding() {
        Rule r=rule();r.config.unit="MINUTES";r.config.amount=new BigDecimal("366");r.config.proration="CALENDAR_DAYS";
        EmployeeContext e=employee();e.entryDate=LocalDate.of(2024,7,1);
        assertThat(calculator.calculate(r,e,LocalDate.of(2024,9,12))).isEqualTo(184_000_000L);
        r.config.grantTiming="EARNED_DAILY";
        assertThat(calculator.calculate(r,e,LocalDate.of(2024,7,1))).isEqualTo(1_000_000L);
    }
    @Test void ruleEffectiveRangeBoundsCalendarProration() {
        Rule r=rule();r.config.unit="MINUTES";r.config.amount=new BigDecimal("365");r.config.proration="CALENDAR_DAYS";
        r.effectiveFrom=LocalDate.of(2026,9,1);r.effectiveTo=LocalDate.of(2026,9,30);
        assertThat(calculator.calculate(r,employee(),LocalDate.of(2026,9,12))).isEqualTo(30_000_000L);
    }
    @Test void compensatoryNeverAutomaticallyGrantsAttendanceOvertime() {
        Rule r=rule();r.config.leaveCategory="COMPENSATORY";assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("主管核定");
        r.config.calculation="SOURCE_ONLY";r.config.amount=null;assertThat(calculator.calculate(r,employee(),LocalDate.of(2026,9,12))).isZero();
    }
    @Test void tierOverlapAndGapRejected() {
        Rule r=rule();r.config.calculation="TENURE";r.tiers=List.of(tier(0,5,"4"),tier(4,null,"9"));assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("不能重叠");
        r.tiers=List.of(tier(0,4,"4"),tier(5,null,"9"));assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("连续");
    }
    @Test void openFinalTierRequired() {Rule r=rule();r.config.calculation="TENURE";r.tiers=List.of(tier(0,5,"4"));assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("最后");}
    @Test void tierDatabasePrecisionCannotSilentlyRoundAnExactConvertedAmount() {
        Rule r=rule();r.config.calculation="TENURE";r.tiers=List.of(tier(0,null,"0.00000005"));
        assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("不能静默舍入");
    }
    @Test void arbitraryScriptNotAnAllowedCalculation() {Rule r=rule();r.config.calculation="eval('grant')";assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("不受支持");}
    @Test void expiryCarryAndGrantStepMustBeConfigured() {Rule r=rule();r.config.carryLimit=null;assertThatThrownBy(()->calculator.validate(r)).hasMessageContaining("结转");}
}
