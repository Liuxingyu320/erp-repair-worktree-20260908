package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignTask;
import com.erp.system.api.model.LoginUser;

@DisplayName("签约角色权限服务端业务边界")
class OaSignHrAccessServiceTest
{
    private final OaSignAutomationSettingsService settings = mock(OaSignAutomationSettingsService.class);
    private final OaSignHrAccessService service = new OaSignHrAccessService(settings);

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("角色持有签约页面权限即可处理业务")
    void shouldAllowRoleAuthorizedBusinessOperator()
    {
        SecurityContextHolder.setUserId("101");
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(Set.of("oa:signTask:list"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertThatCode(service::requireCurrentHr).doesNotThrowAnyException();
        assertThatCode(service::requireCurrentHrOrTechnicalEvidenceReader).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("普通HR只能处理分配给自己的签约任务")
    void shouldRequireCurrentTaskAssignmentForBusinessHandling()
    {
        SecurityContextHolder.setUserId("101");
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(Set.of("oa:signTask:list"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        OaSignTask mine = new OaSignTask();
        mine.setAssignedHrUserId(101L);
        OaSignTask anotherOwner = new OaSignTask();
        anotherOwner.setAssignedHrUserId(202L);

        assertThatCode(() -> service.requireTaskOwner(mine)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireTaskOwner(anotherOwner))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未分配给当前合同经办人");
    }

    @Test
    @DisplayName("角色未配置签约权限时拒绝业务和验真数据")
    void shouldRejectEmployeeWithoutSigningPermission()
    {
        SecurityContextHolder.setUserId("102");

        assertThatThrownBy(service::requireCurrentHr).isInstanceOf(ServiceException.class);
        assertThatThrownBy(service::requireCurrentHrOrTechnicalEvidenceReader)
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("技术证据权限只允许验真，不授予业务操作")
    void shouldKeepTechnicalEvidencePermissionReadOnly()
    {
        SecurityContextHolder.setUserId("102");
        LoginUser loginUser = new LoginUser();
        loginUser.setPermissions(Set.of("oa:signTask:technicalEvidence"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        assertThatThrownBy(service::requireCurrentHr).isInstanceOf(ServiceException.class);
        assertThatCode(service::requireCurrentHrOrTechnicalEvidenceReader).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("系统管理员天然具备签约业务权限")
    void shouldAllowSystemAdmin()
    {
        SecurityContextHolder.setUserId("1");
        OaSignTask anotherOwner = new OaSignTask();
        anotherOwner.setAssignedHrUserId(202L);

        assertThatCode(service::requireCurrentHr).doesNotThrowAnyException();
        assertThatCode(service::requireCurrentHrOrTechnicalEvidenceReader).doesNotThrowAnyException();
        assertThatCode(() -> service.requireTaskOwner(anotherOwner)).doesNotThrowAnyException();
        assertThat(service.currentTaskOwnerFilter()).isNull();
    }

    @Test
    @DisplayName("默认任务接收人配置不影响系统管理员权限")
    void shouldAllowSystemAdminRegardlessOfDefaultReceiver()
    {
        SecurityContextHolder.setUserId("1");
        when(settings.resolveRequiredHrUserId()).thenReturn(940L);

        assertThatCode(service::requireCurrentHr).doesNotThrowAnyException();
        assertThatCode(service::requireCurrentHrOrTechnicalEvidenceReader).doesNotThrowAnyException();
    }
}
