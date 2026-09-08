package com.erp.common.datascope.aspect;

import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.erp.common.core.constant.Constants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/**
 * 数据过滤处理
 * 
 * @author erp
 */
@Aspect
@Component
public class DataScopeAspect
{
    private static final Logger log = LoggerFactory.getLogger(DataScopeAspect.class);

    /**
     * 数据权限过滤关键字
     */
    public static final String DATA_SCOPE = "dataScope";

    @Before("@annotation(controllerDataScope)")
    public void doBefore(JoinPoint point, DataScope controllerDataScope) throws Throwable
    {
        clearDataScope(point);
        handleDataScope(point, controllerDataScope);
    }

    protected void handleDataScope(final JoinPoint joinPoint, DataScope controllerDataScope)
    {
        // 获取当前的用户
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNotNull(loginUser))
        {
            SysUser currentUser = loginUser.getSysUser();
            // 如果是超级管理员，则不过滤数据
            if (StringUtils.isNotNull(currentUser) && !SecurityUtils.isAdmin())
            {
                String permission = StringUtils.defaultIfEmpty(controllerDataScope.permission(), SecurityContextHolder.getPermission());
                dataScopeFilter(joinPoint, currentUser, controllerDataScope.userAlias(), controllerDataScope.deptAlias(), controllerDataScope.userField(), controllerDataScope.deptField(), permission);
            }
        }
    }

    /**
     * 数据范围过滤
     * 
     * @param joinPoint 切点
     * @param user 用户
     * @param deptAlias 部门别名
     * @param userAlias 用户别名
     * @param permission 权限字符
     */
    public static void dataScopeFilter(JoinPoint joinPoint, SysUser user, String userAlias, String deptAlias, String userField, String deptField, String permission)
    {
        StringBuilder sqlString = new StringBuilder();
        List<String> conditions = new ArrayList<String>();
        List<String> scopeCustomIds = new ArrayList<String>();
        List<SysRole> roles = user.getRoles();
        if (roles == null || roles.isEmpty())
        {
            appendDenyCondition(sqlString, deptAlias, deptField);
            applyDataScope(joinPoint, sqlString);
            return;
        }

        roles.forEach(role -> {
            if (role == null)
            {
                return;
            }
            if (Constants.Dept.DATA_SCOPE_CUSTOM.equals(role.getDataScope())
                    && StringUtils.isNotNull(role.getRoleId())
                    && StringUtils.equals(role.getStatus(), UserConstants.ROLE_NORMAL)
                    && (StringUtils.isEmpty(permission) || StringUtils.containsAny(role.getPermissions(), Convert.toStrArray(permission))))
            {
                scopeCustomIds.add(Convert.toStr(role.getRoleId()));
            }
        });

        for (SysRole role : roles)
        {
            if (role == null)
            {
                log.warn("拒绝空角色产生的数据权限范围，userId={}", user.getUserId());
                continue;
            }
            String dataScope = role.getDataScope();
            if (StringUtils.equals(role.getStatus(), UserConstants.ROLE_DISABLE))
            {
                continue;
            }
            if (StringUtils.isNotEmpty(permission) && !StringUtils.containsAny(role.getPermissions(), Convert.toStrArray(permission)))
            {
                continue;
            }
            if (!Constants.Dept.isValidDataScope(dataScope))
            {
                log.warn("拒绝非法数据权限范围，userId={}，roleId={}，dataScope={}", user.getUserId(), role.getRoleId(), dataScope);
                continue;
            }
            if (Constants.Dept.DATA_SCOPE_CUSTOM.equals(dataScope) && StringUtils.isNull(role.getRoleId()))
            {
                log.warn("拒绝缺少角色ID的自定义数据权限范围，userId={}", user.getUserId());
                continue;
            }
            if (conditions.contains(dataScope))
            {
                continue;
            }
            if (Constants.Dept.DATA_SCOPE_ALL.equals(dataScope))
            {
                sqlString = new StringBuilder();
                conditions.add(dataScope);
                break;
            }
            else if (Constants.Dept.DATA_SCOPE_CUSTOM.equals(dataScope))
            {
                if (scopeCustomIds.size() > 1)
                {
                    // 多个自定数据权限使用in查询，避免多次拼接。
                    sqlString.append(StringUtils.format(" OR {}.{} IN ( SELECT dept_id FROM sys_role_dept WHERE role_id in ({}) ) ", deptAlias, deptField, String.join(",", scopeCustomIds)));
                }
                else
                {
                    sqlString.append(StringUtils.format(" OR {}.{} IN ( SELECT dept_id FROM sys_role_dept WHERE role_id = {} ) ", deptAlias, deptField, role.getRoleId()));
                }
            }
            else if (Constants.Dept.DATA_SCOPE_DEPT.equals(dataScope))
            {
                if (StringUtils.isNotNull(user.getDeptId()))
                {
                    sqlString.append(StringUtils.format(" OR {}.{} = {} ", deptAlias, deptField, user.getDeptId()));
                }
                else
                {
                    appendDenyCondition(sqlString, deptAlias, deptField);
                }
            }
            else if (Constants.Dept.DATA_SCOPE_DEPT_AND_CHILD.equals(dataScope))
            {
                if (StringUtils.isNotNull(user.getDeptId()))
                {
                    sqlString.append(StringUtils.format(" OR {}.{} IN ( SELECT dept_id FROM sys_dept WHERE dept_id = {} or find_in_set( {} , ancestors ) )", deptAlias, deptField, user.getDeptId(), user.getDeptId()));
                }
                else
                {
                    appendDenyCondition(sqlString, deptAlias, deptField);
                }
            }
            else if (Constants.Dept.DATA_SCOPE_SELF.equals(dataScope))
            {
                if (StringUtils.isNotBlank(userAlias) && StringUtils.isNotNull(user.getUserId()))
                {
                    sqlString.append(StringUtils.format(" OR {}.{} = {} ", userAlias, userField, user.getUserId()));
                }
                else
                {
                    // 数据权限为仅本人且没有userAlias别名不查询任何数据
                    appendDenyCondition(sqlString, deptAlias, deptField);
                }
            }
            conditions.add(dataScope);
        }

        // 角色都不包含传递过来的权限字符，这个时候sqlString也会为空，所以要限制一下,不查询任何数据
        if (StringUtils.isEmpty(conditions))
        {
            appendDenyCondition(sqlString, deptAlias, deptField);
        }

        applyDataScope(joinPoint, sqlString);
    }

    private static void appendDenyCondition(StringBuilder sqlString, String deptAlias, String deptField)
    {
        sqlString.append(StringUtils.format(" OR {}.{} = 0 ", deptAlias, deptField));
    }

    private static void applyDataScope(JoinPoint joinPoint, StringBuilder sqlString)
    {
        if (StringUtils.isNotBlank(sqlString.toString()))
        {
            Object params = joinPoint.getArgs()[0];
            if (StringUtils.isNotNull(params) && params instanceof BaseEntity)
            {
                BaseEntity baseEntity = (BaseEntity) params;
                baseEntity.getParams().put(DATA_SCOPE, " AND (" + sqlString.substring(4) + ")");
            }
        }
    }

    /**
     * 拼接权限sql前先清空params.dataScope参数防止注入
     */
    private void clearDataScope(final JoinPoint joinPoint)
    {
        Object params = joinPoint.getArgs()[0];
        if (StringUtils.isNotNull(params) && params instanceof BaseEntity)
        {
            BaseEntity baseEntity = (BaseEntity) params;
            baseEntity.getParams().put(DATA_SCOPE, "");
        }
    }
}
