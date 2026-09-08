package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SysUserCredentialMapperBindingTest
{
    @Test
    void userQueriesAndWritesIncludeExplicitPasswordChangeState() throws Exception
    {
        String xml = new String(new ClassPathResource("mapper/system/SysUserMapper.xml")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(xml).contains("property=\"mustChangePassword\" column=\"must_change_password\"");
        assertThat(xml).contains("u.must_change_password");
        assertThat(xml).contains("must_change_password = #{mustChangePassword}");
        assertThat(xml).contains("pwd_update_date = sysdate()");
        assertThat(SysUserMapper.class.getMethod("resetUserPwd", Long.class, String.class, String.class))
                .isNotNull();
    }
}
