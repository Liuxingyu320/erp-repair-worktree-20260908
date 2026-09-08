package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("签约任务乐观锁Mapper")
class OaSignTaskOptimisticUpdateTest
{
    @Test
    @DisplayName("状态更新同时匹配当前HR、状态和version")
    void shouldUpdateStatusWithOptimisticConditions() throws Exception
    {
        String xml;
        try (InputStream input = Resources.getResourceAsStream("mapper/oa/OaSignTaskMapper.xml"))
        {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String start = "<update id=\"updateStatusWithVersion\"";
        int from = xml.indexOf(start);
        int to = xml.indexOf("</update>", from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        String update = xml.substring(from, to);

        assertThat(update)
                .contains("status = #{toStatus}")
                .contains("version = version + 1")
                .contains("failure_code = #{failureCode}")
                .contains("failure_detail = #{failureDetail}")
                .contains("next_retry_time = #{nextRetryTime}")
                .contains("where task_id = #{taskId}")
                .contains("and assigned_hr_user_id = #{assignedHrUserId}")
                .contains("and status = #{fromStatus}")
                .contains("and version = #{version}");
    }

    @Test
    @DisplayName("确认写入和清除同时匹配任务当前HR")
    void shouldGuardConfirmationWritesByAssignedHr() throws Exception
    {
        String xml;
        try (InputStream input = Resources.getResourceAsStream("mapper/oa/OaSignTaskMapper.xml"))
        {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(updateBlock(xml, "updateConfirmation"))
                .contains("and assigned_hr_user_id = #{assignedHrUserId}");
        assertThat(updateBlock(xml, "clearConfirmation"))
                .contains("and assigned_hr_user_id = #{assignedHrUserId}");
    }

    private String updateBlock(String xml, String id)
    {
        String start = "<update id=\"" + id + "\"";
        int from = xml.indexOf(start);
        int to = xml.indexOf("</update>", from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return xml.substring(from, to);
    }
}
