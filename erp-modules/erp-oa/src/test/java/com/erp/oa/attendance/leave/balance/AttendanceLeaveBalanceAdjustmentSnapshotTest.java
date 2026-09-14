package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceServiceTest.Harness;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceRequests.Adjustment;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;

class AttendanceLeaveBalanceAdjustmentSnapshotTest
{
    void switchToEquivalentHours(Harness h) throws Exception {
        h.rule=AttendanceLeaveBalanceCalculatorTest.rule();h.rule.ruleId=32L;h.rule.version=2;
        h.rule.config.unit="HOURS";h.rule.config.amount=new BigDecimal("56");h.saveRule();h.recalc();
    }
    @Test void oldDaysInputCannotBeReinterpretedAsHoursAfterRuleReconciliation() throws Exception {
        Harness h=new Harness();h.recalc();Adjustment old=h.adjustment("stale-days","1");switchToEquivalentHours(h);
        long before=h.buckets.get(0).grantedUnits;int entries=h.ledger.size();
        assertThatThrownBy(()->h.service.adjust(11L,old)).hasMessageContaining("调整依据已变化");
        assertThat(h.buckets.get(0).grantedUnits).isEqualTo(before);assertThat(h.ledger).hasSize(entries);
        assertThat(h.service.employee(11L,1L).buckets.get(0).ruleVersion).isEqualTo(2);
        Adjustment fresh=h.adjustment("fresh-hours","1");h.service.adjust(11L,fresh);assertThat(h.buckets.get(0).grantedUnits-before).isEqualTo(60_000_000L);
    }
    @Test void successfulOldRequestReplaysOriginalAmountAfterRuleAndBucketVersionsChange() throws Exception {
        Harness h=new Harness();h.recalc();Adjustment old=h.adjustment("replay-days","1");Command first=h.service.adjust(11L,old);switchToEquivalentHours(h);
        long before=h.buckets.get(0).grantedUnits;int entries=h.ledger.size();
        assertThat(h.service.adjust(11L,old)).isSameAs(first);assertThat(first.resultUnits).isEqualTo(420_000_000L);
        assertThat(h.buckets.get(0).grantedUnits).isEqualTo(before);assertThat(h.ledger).hasSize(entries);
        old.displayUnit="HOURS";assertThatThrownBy(()->h.service.adjust(11L,old)).hasMessageContaining("内容不同");
    }
    @Test void legacyUnprocessedAndChangedDailyConversionAreRejected() throws Exception {
        Harness h=new Harness();h.recalc();Adjustment old=h.adjustment("old-format","1");old.bucketVersion=null;old.ruleId=null;old.ruleVersion=null;old.displayUnit=null;old.minutesPerDay=null;
        assertThatThrownBy(()->h.service.adjust(11L,old)).hasMessageContaining("调整依据");
        Adjustment conversion=h.adjustment("wrong-conversion","1");conversion.minutesPerDay=new BigDecimal("480");
        assertThatThrownBy(()->h.service.adjust(11L,conversion)).hasMessageContaining("调整依据");assertThat(h.ledger).hasSize(1);
    }
    @Test void originalLegacyCommandStillReplaysWithoutInventingNewAdjustment() throws Exception {
        Harness h=new Harness();h.recalc();Adjustment old=h.adjustment("legacy-success","1");old.bucketVersion=null;old.ruleId=null;old.ruleVersion=null;old.displayUnit=null;old.minutesPerDay=null;
        var digest=java.security.MessageDigest.getInstance("SHA-256");String canonical=old.bucketId+"|1|"+old.reason;
        Command prior=new Command();prior.fingerprint=java.util.HexFormat.of().formatHex(digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));prior.resultUnits=420_000_000L;
        h.commands.put("ADJUST|99|legacy-success",prior);assertThat(h.service.adjust(11L,old)).isSameAs(prior);assertThat(h.ledger).hasSize(1);
    }
}
