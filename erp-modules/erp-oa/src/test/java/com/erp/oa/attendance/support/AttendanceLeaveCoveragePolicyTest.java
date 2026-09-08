package com.erp.oa.attendance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.support.AttendanceLeaveCoveragePolicy.Coverage;

class AttendanceLeaveCoveragePolicyTest
{
    @Test
    void twoBoundaryLeavesProduceOneUnconfirmedWorkIsland()
    {
        Coverage value = AttendanceLeaveCoveragePolicy.analyze(at(8, 0),
                at(12, 0), List.of(leave(at(8, 0), at(10, 0)),
                        leave(at(11, 0), at(12, 0))));

        assertThat(value.startCovered()).isTrue();
        assertThat(value.endCovered()).isTrue();
        assertThat(value.fullyCovered()).isFalse();
        assertThat(value.requiresRemainingWorkConfirmation()).isTrue();
        assertThat(value.remainingIntervals()).singleElement()
                .satisfies(interval -> {
                    assertThat(interval.start()).isEqualTo(at(10, 0));
                    assertThat(interval.end()).isEqualTo(at(11, 0));
                });
    }

    @Test
    void overlappingAndTouchingLeavesAreConsumedAsOneUnion()
    {
        Coverage value = AttendanceLeaveCoveragePolicy.analyze(at(8, 0),
                at(12, 0), List.of(leave(at(8, 0), at(9, 30)),
                        leave(at(9, 0), at(10, 0)),
                        leave(at(10, 0), at(11, 0))));

        assertThat(value.coveredIntervals()).singleElement()
                .satisfies(interval -> {
                    assertThat(interval.start()).isEqualTo(at(8, 0));
                    assertThat(interval.end()).isEqualTo(at(11, 0));
                });
        assertThat(value.remainingMinutes()).isEqualTo(60);
    }

    @Test
    void leaveTouchingOnlyOutsideAWorkBoundaryDoesNotWaiveItsSlot()
    {
        Coverage value = AttendanceLeaveCoveragePolicy.analyze(at(8, 0),
                at(12, 0), List.of(leave(at(7, 0), at(8, 0)),
                        leave(at(12, 0), at(13, 0))));

        assertThat(value.startCovered()).isFalse();
        assertThat(value.endCovered()).isFalse();
        assertThat(value.remainingMinutes()).isEqualTo(240);
    }

    private LeaveSegmentSource leave(LocalDateTime start, LocalDateTime end)
    {
        LeaveSegmentSource value = new LeaveSegmentSource();
        value.startTime = start;
        value.endTime = end;
        return value;
    }

    private LocalDateTime at(int hour, int minute)
    { return LocalDateTime.of(2026, 8, 20, hour, minute); }
}
