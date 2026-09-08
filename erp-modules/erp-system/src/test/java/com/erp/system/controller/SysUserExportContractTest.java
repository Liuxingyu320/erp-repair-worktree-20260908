package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import com.erp.common.core.annotation.Excel;
import com.erp.system.domain.dto.SysUserManageUpdateRequest;
import com.erp.system.domain.dto.SysUserPiiUpdateRequest;
import com.erp.system.domain.vo.SysUserAdminExportVo;
import com.erp.system.domain.vo.SysUserPiiExportVo;

class SysUserExportContractTest
{
    @Test
    void baseExportHasNoPersonalOrCredentialColumns()
    {
        Set<String> fields = excelFields(SysUserAdminExportVo.class);
        assertThat(fields).containsExactlyInAnyOrder("userId", "userName", "nickName", "deptName",
                "postNames", "status", "setupStatus", "createTime");
        assertThat(fields).doesNotContain("phonenumber", "email", "idNumber", "currentAddress",
                "emergencyContact", "bankAccount", "password");
        assertThat(excelFields(SysUserPiiExportVo.class)).contains("phonenumber", "email",
                "birthDate", "idNumber", "registeredResidence", "currentAddress",
                "healthStatus", "emergencyContact", "emergencyContactPhone",
                "socialSecurityLocation", "housingFundLocation", "bankAccount");
    }

    @Test
    void writeDtosCannotBindCredentialControlFields()
    {
        for (Class<?> type : new Class<?>[] { SysUserManageUpdateRequest.class,
                SysUserPiiUpdateRequest.class })
        {
            Set<String> fields = Arrays.stream(type.getDeclaredFields()).map(Field::getName)
                    .collect(Collectors.toSet());
            assertThat(fields).doesNotContain("password", "credentialState",
                    "temporaryPasswordExpiresAt", "pwdUpdateDate");
        }
    }

    private Set<String> excelFields(Class<?> type)
    {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.getAnnotation(Excel.class) != null)
                .map(Field::getName).collect(Collectors.toSet());
    }
}
