package com.erp.system.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("员工列表 JSON 标识精度")
class SysUserListVoJsonTest
{
    @Test
    @DisplayName("超过 JavaScript 安全整数的员工ID按字符串返回")
    void shouldSerializeEmployeeIdAsString() throws Exception
    {
        SysUserListVo row = new SysUserListVo();
        row.setUserId(9007199254740993L);

        assertThat(new ObjectMapper().writeValueAsString(row))
                .contains("\"userId\":\"9007199254740993\"");
    }

    @Test
    @DisplayName("人力员工列表也按字符串返回员工ID")
    void shouldSerializeHrEmployeeIdAsString() throws Exception
    {
        HrEmployeeListVo row = new HrEmployeeListVo();
        row.setUserId(9007199254740993L);

        assertThat(new ObjectMapper().writeValueAsString(row))
                .contains("\"userId\":\"9007199254740993\"");
    }

    @Test
    @DisplayName("人力员工详情按字符串返回员工ID")
    void shouldSerializeHrEmployeeDetailIdAsString() throws Exception
    {
        HrEmployeeProfileVo detail = new HrEmployeeProfileVo();
        detail.setUserId(9007199254740993L);

        assertThat(new ObjectMapper().writeValueAsString(detail))
                .contains("\"userId\":\"9007199254740993\"");
    }
}
