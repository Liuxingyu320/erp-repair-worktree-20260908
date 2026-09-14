package com.erp.system.support;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.system.domain.vo.HrHealthCertificateVo;

class HrHealthCertificateSelectionTest
{
    private static final LocalDate DAY = LocalDate.of(2026,9,13);
    static HrHealthCertificateVo row(long id, LocalDate from, LocalDate to, String current)
    {
        HrHealthCertificateVo r=new HrHealthCertificateVo(); r.setCertificateId(id);r.setUserId(7L);
        r.setIssuedDate(from);r.setValidFrom(from);r.setExpiresOn(to);r.setCurrentFlag(current);
        r.setReviewStatus("APPROVED");r.setDelFlag("0");r.setReviewedTime(new Date(id));return r;
    }
    @Test void futureApprovalPreservesCurrentAndCrossDayDoesNotRequireAnotherApproval()
    {
        var old=row(1,DAY.minusDays(20),DAY,"Y");var next=row(2,DAY.plusDays(1),DAY.plusYears(1),null);
        assertThat(HrHealthCertificateSelection.choose(List.of(next,old),DAY)).isSameAs(old);
        assertThat(HrHealthCertificateSelection.effective(next,DAY)).isFalse();
        assertThat(HrHealthCertificateSelection.choose(List.of(old,next),DAY.plusDays(1))).isSameAs(next);
    }
    @Test void uniqueEffectiveCurrentWinsOverNewerOverlappingCertificate()
    {
        var old=row(1,DAY.minusDays(20),DAY.plusDays(10),"Y");var next=row(2,DAY,DAY.plusYears(1),null);
        assertThat(HrHealthCertificateSelection.choose(List.of(next,old),DAY)).isSameAs(old);
        old.setCurrentFlag(null);assertThat(HrHealthCertificateSelection.choose(List.of(old,next),DAY)).isSameAs(next);
    }
    @Test void multipleFlagsDoNotOverrideStableDateReviewAndIdOrder()
    {
        var a=row(1,DAY,DAY.plusDays(3),"Y");var b=row(2,DAY,DAY.plusDays(4),"Y");
        assertThat(HrHealthCertificateSelection.choose(List.of(a,b),DAY)).isSameAs(b);
        b.setReviewedTime(a.getReviewedTime());assertThat(HrHealthCertificateSelection.choose(List.of(b,a),DAY)).isSameAs(b);
    }
    @Test void expiredFallbackPrecedesEarliestFutureAndFutureIsNeverEffective()
    {
        var old=row(1,DAY.minusDays(30),DAY.minusDays(1),null);
        var near=row(2,DAY.plusDays(1),DAY.plusDays(30),null);var far=row(3,DAY.plusDays(20),DAY.plusYears(1),"Y");
        assertThat(HrHealthCertificateSelection.choose(List.of(far,near,old),DAY)).isSameAs(old);
        assertThat(HrHealthCertificateSelection.choose(List.of(far,near),DAY)).isSameAs(near);
        assertThat(HrHealthCertificateSelection.effective(far,DAY)).isFalse();
    }
    @Test void deletedUnapprovedAndMalformedDatesAreExcluded()
    {
        var deleted=row(1,DAY,DAY,"Y");deleted.setDelFlag("2");
        var pending=row(2,DAY,DAY,"Y");pending.setReviewStatus("APPROVAL_PENDING");
        var invalid=row(3,DAY.plusDays(1),DAY,"Y");
        assertThat(HrHealthCertificateSelection.choose(List.of(deleted,pending,invalid),DAY)).isNull();
    }
    @Test void nullValidFromFallsBackToIssuedDateAndLeapDayIncludesBothBoundaries()
    {
        LocalDate leap=LocalDate.of(2028,2,29);var r=row(1,leap,leap,null);r.setValidFrom(null);
        assertThat(HrHealthCertificateSelection.effective(r,leap)).isTrue();
        assertThat(HrHealthCertificateSelection.effective(r,leap.minusDays(1))).isFalse();
        assertThat(HrHealthCertificateSelection.effective(r,leap.plusDays(1))).isFalse();
    }
}
