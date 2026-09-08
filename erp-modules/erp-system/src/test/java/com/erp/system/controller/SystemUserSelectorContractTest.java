package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import com.erp.system.domain.vo.SysNoticeReadUserVo;
import com.erp.system.domain.vo.SysSalaryUserOptionVo;
import com.erp.system.domain.vo.SysUserAssignmentVo;

class SystemUserSelectorContractTest
{
    @Test
    void selectorDtosExposeOnlyTaskSpecificFields()
    {
        assertThat(fields(SysUserAssignmentVo.class)).containsExactlyInAnyOrder(
                "userId", "deptId", "userName", "nickName", "phonenumber",
                "deptName", "status", "createTime");
        assertThat(fields(SysSalaryUserOptionVo.class)).containsExactlyInAnyOrder(
                "userId", "deptId", "userName", "nickName", "phonenumber",
                "deptName", "status");
        assertThat(fields(SysNoticeReadUserVo.class)).containsExactlyInAnyOrder(
                "userId", "nickName", "deptName", "readTime");
    }

    private Set<String> fields(Class<?> type)
    {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName)
                .collect(Collectors.toSet());
    }
}
