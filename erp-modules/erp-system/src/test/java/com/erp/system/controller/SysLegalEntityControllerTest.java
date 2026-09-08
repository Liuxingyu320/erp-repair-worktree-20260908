package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysLegalEntity;

class SysLegalEntityControllerTest
{
    @Test
    void signingCompanyReadAndWriteUseIndependentPermissions() throws Exception
    {
        Method options = SysLegalEntityController.class.getMethod("options");
        Method list = SysLegalEntityController.class.getMethod("list", SysLegalEntity.class);
        Method detail = SysLegalEntityController.class.getMethod("detail", Long.class);
        Method add = SysLegalEntityController.class.getMethod("add", SysLegalEntity.class);
        Method edit = SysLegalEntityController.class.getMethod("edit", SysLegalEntity.class);

        assertPermissions(options, "system:dept:list", "oa:signCompany:list");
        assertPermissions(list, "system:dept:list", "oa:signCompany:list");
        assertPermissions(detail, "system:dept:query", "oa:signCompany:list");
        assertPermissions(add, "system:dept:edit", "oa:signCompany:edit");
        assertPermissions(edit, "system:dept:edit", "oa:signCompany:edit");
    }

    private static void assertPermissions(Method method, String systemPermission,
            String signingPermission)
    {
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        assertThat(permissions).isNotNull();
        assertThat(permissions.logical()).isEqualTo(Logical.OR);
        assertThat(permissions.value()).containsExactly(systemPermission, signingPermission);
    }
}
