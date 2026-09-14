package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.mapper.SysUserProfileMapper;

class SysUserSalaryWriteBoundaryTest
{
    @Test void ordinaryEmployeeSaveRejectsChangedSalaryButAcceptsOmittedOrUnchangedSalary()
    {
        var mapper=mock(SysUserProfileMapper.class);
        var service=new SysUserServiceImpl(); ReflectionTestUtils.setField(service,"profileMapper",mapper);
        var current=new SysUserProfile(); current.setSalaryTotal(new BigDecimal("6000"));
        when(mapper.selectUserProfileByUserId(11L)).thenReturn(current);
        var employee=new SysUser(); employee.setUserId(11L); var submitted=new SysUserProfile(); employee.setProfile(submitted);
        submitted.setSalaryTotal(new BigDecimal("8000"));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,"saveUserProfile",employee,false)).hasMessageContaining("不能修改工资");
        verify(mapper,never()).updateUserProfile(any());
        submitted.setSalaryTotal(null); ReflectionTestUtils.invokeMethod(service,"saveUserProfile",employee,false);
        submitted.setSalaryTotal(new BigDecimal("6000.00")); ReflectionTestUtils.invokeMethod(service,"saveUserProfile",employee,false);
        verify(mapper,times(2)).updateUserProfile(any());
    }
    @Test void generalMapperCannotClearOrInsertSalariesEvenWhenCalledDirectly() throws Exception
    {
        var config=new Configuration(); config.getTypeAliasRegistry().registerAlias("SysUserProfile",SysUserProfile.class);
        String resource="mapper/system/SysUserProfileMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
        }
        var profile=new SysUserProfile(); profile.setUserId(11L); profile.setSalaryTotal(new BigDecimal("99999"));
        var update=config.getMappedStatement("com.erp.system.mapper.SysUserProfileMapper.updateUserProfile").getBoundSql(profile);
        assertThat(update.getSql()).doesNotContain("salary_total", "base_salary", "post_salary", "performance_salary", "field_allowance");
        var insert=config.getMappedStatement("com.erp.system.mapper.SysUserProfileMapper.insertUserProfile").getBoundSql(profile);
        assertThat(insert.getParameterMappings()).noneMatch(parameter -> java.util.Set.of("salaryTotal","baseSalary","postSalary","performanceSalary","fieldAllowance","salaryVersion").contains(parameter.getProperty()));
    }
}
