package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteConfigService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;

@DisplayName("默认签约任务接收人配置")
class OaSignAutomationSettingsServiceTest
{
    @Test
    @DisplayName("有效配置必须指向一个存在且启用的用户")
    void shouldResolveConfiguredActiveHrUser()
    {
        RemoteConfigService configService = mock(RemoteConfigService.class);
        RemoteUserService userService = mock(RemoteUserService.class);
        when(configService.getConfigKey("sign.hr.user-id", SecurityConstants.INNER))
                .thenReturn(R.ok("101"));
        SignCandidateUser user = new SignCandidateUser();
        user.setUserId(101L);
        when(userService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(List.of(user)));
        OaSignAutomationSettingsService service = new OaSignAutomationSettingsService(configService, userService);

        assertThat(service.resolveRequiredHrUserId()).isEqualTo(101L);
        ArgumentCaptor<SignCandidateUserQuery> query = ArgumentCaptor.forClass(SignCandidateUserQuery.class);
        verify(userService).listSignCandidates(query.capture(), eq(SecurityConstants.INNER));
        assertThat(query.getValue().getUserIds()).containsExactly(101L);
        assertThat(query.getValue().getLimit()).isEqualTo(1);
    }

    @Test
    @DisplayName("配置缺失、非数字或用户不存在时返回中文配置提示")
    void shouldRejectMissingInvalidOrUnknownHrConfiguration()
    {
        RemoteConfigService configService = mock(RemoteConfigService.class);
        RemoteUserService userService = mock(RemoteUserService.class);
        OaSignAutomationSettingsService service = new OaSignAutomationSettingsService(configService, userService);

        when(configService.getConfigKey("sign.hr.user-id", SecurityConstants.INNER))
                .thenReturn(R.ok(""));
        assertMissing(() -> service.resolveRequiredHrUserId());

        when(configService.getConfigKey("sign.hr.user-id", SecurityConstants.INNER))
                .thenReturn(R.ok("not-a-number"));
        assertMissing(() -> service.resolveRequiredHrUserId());

        when(configService.getConfigKey("sign.hr.user-id", SecurityConstants.INNER))
                .thenReturn(R.ok("101"));
        when(userService.listSignCandidates(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(List.of()));
        assertMissing(() -> service.resolveRequiredHrUserId());

        when(configService.getConfigKey("sign.hr.user-id", SecurityConstants.INNER))
                .thenReturn(R.fail("system unavailable"));
        assertMissing(() -> service.resolveRequiredHrUserId());
    }

    private void assertMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable)
    {
        assertThatThrownBy(callable)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("默认签约任务接收人");
    }
}
