package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("签约任务七指标聚合")
class OaSignTaskMetricsMapperTest
{
    @Test
    @DisplayName("Mapper以同一条受HR和组织范围约束的SQL准确聚合七项")
    void shouldAggregateSevenMetricsInOneScopedQuery() throws Exception
    {
        String xml = new String(new ClassPathResource("mapper/oa/OaSignTaskMapper.xml")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(Arrays.stream(OaSignTaskMapper.class.getMethods()).map(method -> method.getName()))
                .contains("selectTaskMetrics");
        assertThat(xml)
                .contains("<select id=\"selectTaskMetrics\"")
                .contains("status = 'NEEDS_DATA'")
                .contains("status = 'PENDING_COMPANY'")
                .contains("status = 'FAILED'")
                .contains("status = 'VIEWED'")
                .contains("status IN ('PENDING_SIGN', 'VIEWED', 'PENDING_FINAL_CONFIRM')")
                .contains("sign_deadline &gt;= sysdate()")
                .contains("date_add(sysdate(), interval 3 day)")
                .contains("status = 'REFUSED'")
                .contains("status = 'SIGNED'")
                .contains("completed_time &gt;= date_format(current_date, '%Y-%m-01')")
                .contains("assigned_hr_user_id = #{assignedHrUserId}")
                .contains("params.scopeDeptIds");
    }
}
