package com.erp.file.drive.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.dto.DriveQuotaRequest;
import com.erp.file.drive.domain.vo.DriveSpaceVo;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveFeatureGuard;
import com.erp.file.drive.service.DriveSpaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

@DisplayName("云盘空间接口")
class DriveSpaceControllerTest
{
    @Test
    @DisplayName("空间列表和额度接口声明最小权限与稳定路由")
    void shouldDeclareRoutesAndPermissions() throws Exception
    {
        Method spaces = DriveSpaceController.class.getMethod("spaces");
        RequiresPermissions listPermission = spaces.getAnnotation(RequiresPermissions.class);
        assertThat(listPermission.value()).containsExactly(DriveConstants.PERMISSION_ACCESS);
        assertThat(DriveSpaceController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/drive");

        Method quota = DriveSpaceController.class.getMethod(
                "updateQuota", Long.class, DriveQuotaRequest.class);
        assertThat(quota.getAnnotation(RequiresPermissions.class).value()).containsExactly(
                DriveConstants.PERMISSION_ACCESS,
                DriveConstants.PERMISSION_QUOTA_MANAGE);
    }

    @Test
    @DisplayName("功能关闭时先失败且不解析用户或访问空间服务")
    void shouldFailBeforeResolvingActorWhenDisabled()
    {
        DriveProperties properties = mock(DriveProperties.class);
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveSpaceService spaceService = mock(DriveSpaceService.class);
        DriveSpaceController controller = new DriveSpaceController(
                new DriveFeatureGuard(properties), resolver, spaceService);
        when(properties.isEnabled()).thenReturn(false);

        assertThatThrownBy(controller::spaces)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_DISABLED);
        verifyNoInteractions(resolver, spaceService);
    }

    @Test
    @DisplayName("开启时列表解析当前用户并包装空间数据")
    void shouldReturnVisibleSpacesWhenEnabled()
    {
        DriveProperties properties = mock(DriveProperties.class);
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveSpaceService spaceService = mock(DriveSpaceService.class);
        DriveSpaceController controller = new DriveSpaceController(
                new DriveFeatureGuard(properties), resolver, spaceService);
        DriveActor actor = new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
        List<DriveSpaceVo> spaces = List.of(new DriveSpaceVo(
                1L, DriveConstants.SPACE_PERSONAL, "我的文件",
                1000L, 0L, 0, true, false));
        when(properties.isEnabled()).thenReturn(true);
        when(resolver.resolve()).thenReturn(actor);
        when(spaceService.listVisibleSpaces(actor)).thenReturn(spaces);

        AjaxResult result = controller.spaces();

        assertThat(result.get("data")).isEqualTo(spaces);
        verify(spaceService).listVisibleSpaces(actor);
    }
}
