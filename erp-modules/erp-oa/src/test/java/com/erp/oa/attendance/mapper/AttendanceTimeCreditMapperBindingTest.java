package com.erp.oa.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditModels.Adjustment;

class AttendanceTimeCreditMapperBindingTest
{
    private static final String XML =
            "mapper/oa/AttendanceTimeCreditMapper.xml";

    @Test
    void locksPeriodAndFactsBeforeAppendingOnly() throws Exception
    {
        Configuration configuration = parse();
        Map<String, Object> params = new HashMap<>();
        params.put("shopId", 201L);
        params.put("salaryMonth", "2026-08");
        String period = sql(configuration, "lockPeriod", params);
        assertThat(period).contains(
                "oa_attendance_time_credit_period_lock",
                "shop_id = ?", "salary_month = ?", "for update");

        params.clear();
        params.put("dayResultIds", List.of(1L, 2L));
        String dayLocks = sql(configuration, "selectDayResultsForUpdate",
                params);
        assertThat(dayLocks).contains("day_result_id in ( ? , ? )",
                "order by day_result_id", "for update");

        String insert = sql(configuration, "insertAdjustment",
                new Adjustment());
        assertThat(insert).startsWith(
                "insert into oa_attendance_time_credit_adjustment")
                .contains("adjustment_action", "original_adjustment_id",
                        "source_day_result_id", "target_day_result_id")
                .doesNotContain("update oa_attendance_day_result",
                        "delete from");
    }

    @Test
    void sourceAndTargetBalancesUseApplyMinusReverse() throws Exception
    {
        Configuration configuration = parse();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceDayResultId", 1L);
        String source = sql(configuration, "selectNetSourceUsed", params);
        assertThat(source).contains(
                "when adjustment_action = 'APPLY' then adjustment_minutes",
                "when adjustment_action = 'REVERSE' then -adjustment_minutes",
                "source_day_result_id = ?");

        params.clear();
        params.put("shopId", 201L);
        params.put("userId", 11L);
        params.put("monthStart", LocalDate.of(2026, 8, 1));
        params.put("targetDate", LocalDate.of(2026, 8, 23));
        String candidates = sql(configuration, "selectSourceCandidates",
                params);
        assertThat(candidates).contains("d.shop_id = ?", "d.user_id = ?",
                "d.business_date between ? and ?", "d.settled_at is not null",
                "availableMinutes");
    }

    @Test
    void salaryLockFollowsGlobalUserMonthUniqueness() throws Exception
    {
        Configuration configuration = parse();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 11L);
        params.put("salaryMonth", "2026-08");

        String salary = sql(configuration, "countSalaryRecords", params);

        assertThat(salary).contains("from oa_salary_record", "user_id = ?",
                "salary_month = ?").doesNotContain("shop_dept_id");
    }

    private Configuration parse() throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String sql(Configuration configuration, String id, Object params)
    {
        return configuration.getMappedStatement(
                AttendanceTimeCreditMapper.class.getName() + "." + id)
                .getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
    }
}
