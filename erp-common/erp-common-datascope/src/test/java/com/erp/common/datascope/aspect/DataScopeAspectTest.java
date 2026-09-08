package com.erp.common.datascope.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataScopeAspectTest
{
    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.admin-role-bypass-enabled");
        System.clearProperty("erp.security.wildcard-permission-bypass-enabled");
        System.clearProperty("erp.security.legacy-user-id-admin");
        SecurityContextHolder.remove();
    }

    @Test
    void adminRoleShouldStillApplyDataScopeByDefault()
    {
        BaseEntity query = new BaseEntity();
        DataScopeAspect aspect = new DataScopeAspect();
        loginAsAdminRoleUser();

        aspect.handleDataScope(joinPoint(query), dataScope());

        assertThat(query.getParams().get(DataScopeAspect.DATA_SCOPE)).asString().contains("u.user_id = 200");
    }

    @Test
    void adminRoleShouldBypassDataScopeOnlyWhenEnabled()
    {
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        BaseEntity query = new BaseEntity();
        DataScopeAspect aspect = new DataScopeAspect();
        loginAsAdminRoleUser();

        aspect.handleDataScope(joinPoint(query), dataScope());

        assertThat(query.getParams().get(DataScopeAspect.DATA_SCOPE)).isNull();
    }

    @Test
    void unknownDataScopeShouldDenyAllData()
    {
        BaseEntity query = new BaseEntity();
        loginAsRoleUser(role("9", UserConstants.ROLE_NORMAL));

        new DataScopeAspect().handleDataScope(joinPoint(query), dataScope());

        assertThat(query.getParams().get(DataScopeAspect.DATA_SCOPE)).asString().contains("d.dept_id = 0");
    }

    @Test
    void nullDataScopeShouldDenyAllData()
    {
        BaseEntity query = new BaseEntity();
        loginAsRoleUser(role(null, UserConstants.ROLE_NORMAL));

        new DataScopeAspect().handleDataScope(joinPoint(query), dataScope());

        assertThat(query.getParams().get(DataScopeAspect.DATA_SCOPE)).asString().contains("d.dept_id = 0");
    }

    @Test
    void invalidRoleShouldNotWidenAValidRoleScope()
    {
        BaseEntity query = new BaseEntity();
        loginAsRoleUser(role("9", UserConstants.ROLE_NORMAL), role("5", UserConstants.ROLE_NORMAL));

        new DataScopeAspect().handleDataScope(joinPoint(query), dataScope());

        assertThat(query.getParams().get(DataScopeAspect.DATA_SCOPE)).asString()
                .contains("u.user_id = 200")
                .doesNotContain("9");
    }

    @Test
    void missingRolesShouldDenyAllData()
    {
        BaseEntity query = new BaseEntity();
        loginAsRoleUser();

        new DataScopeAspect().handleDataScope(joinPoint(query), dataScope());

        assertThat(query.getParams().get(DataScopeAspect.DATA_SCOPE)).asString().contains("d.dept_id = 0");
    }

    private void loginAsAdminRoleUser()
    {
        SysRole role = new SysRole();
        role.setRoleId(2L);
        role.setRoleKey(UserConstants.SUPER_ADMIN_ROLE_KEY);
        role.setStatus(UserConstants.ROLE_NORMAL);
        role.setDataScope("5");

        SysUser user = new SysUser();
        user.setUserId(200L);
        user.setDeptId(20L);
        user.setRoles(Collections.singletonList(role));

        LoginUser loginUser = new LoginUser();
        loginUser.setSysUser(user);
        loginUser.setRoles(new HashSet<>(Collections.singleton(UserConstants.SUPER_ADMIN_ROLE_KEY)));
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private void loginAsRoleUser(SysRole... roles)
    {
        SysUser user = new SysUser();
        user.setUserId(200L);
        user.setDeptId(20L);
        user.setRoles(Arrays.asList(roles));

        LoginUser loginUser = new LoginUser();
        loginUser.setSysUser(user);
        loginUser.setRoles(new HashSet<>(Collections.singleton("common")));
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private SysRole role(String dataScope, String status)
    {
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setRoleKey("common");
        role.setStatus(status);
        role.setDataScope(dataScope);
        return role;
    }

    private DataScope dataScope()
    {
        return new DataScope()
        {
            @Override
            public String userAlias()
            {
                return "u";
            }

            @Override
            public String deptAlias()
            {
                return "d";
            }

            @Override
            public String userField()
            {
                return "user_id";
            }

            @Override
            public String deptField()
            {
                return "dept_id";
            }

            @Override
            public String permission()
            {
                return "";
            }

            @Override
            public Class<? extends Annotation> annotationType()
            {
                return DataScope.class;
            }
        };
    }

    private JoinPoint joinPoint(BaseEntity query)
    {
        return new JoinPoint()
        {
            @Override
            public String toShortString()
            {
                return "test";
            }

            @Override
            public String toLongString()
            {
                return "test";
            }

            @Override
            public Object getThis()
            {
                return this;
            }

            @Override
            public Object getTarget()
            {
                return this;
            }

            @Override
            public Object[] getArgs()
            {
                return new Object[] { query };
            }

            @Override
            public Signature getSignature()
            {
                return null;
            }

            @Override
            public SourceLocation getSourceLocation()
            {
                return null;
            }

            @Override
            public String getKind()
            {
                return "method-execution";
            }

            @Override
            public StaticPart getStaticPart()
            {
                return null;
            }
        };
    }
}
