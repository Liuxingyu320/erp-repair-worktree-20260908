package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.SysFile;

@DisplayName("库存主数据图片上传")
class InvImageControllerTest
{
    @Test
    @DisplayName("商品、OE、礼盒任一维护权限均可上传")
    void uploadShouldUseInventoryMaintenancePermissions() throws Exception
    {
        Method method = InvImageController.class.getMethod("upload",
                org.springframework.web.multipart.MultipartFile.class);
        RequiresPermissions permission = method.getAnnotation(RequiresPermissions.class);

        assertThat(permission).isNotNull();
        assertThat(permission.logical()).isEqualTo(Logical.OR);
        assertThat(permission.value()).containsExactlyInAnyOrder(
                "inv:product:add", "inv:product:edit",
                "inv:oe:add", "inv:oe:edit",
                "inv:gift:add", "inv:gift:edit");
    }

    @Test
    @DisplayName("合法图片通过内部文件服务上传并返回标准数据")
    void uploadShouldReturnRemoteFileData()
    {
        RemoteFileService fileService = mock(RemoteFileService.class);
        InvImageController controller = new InvImageController(fileService);
        MockMultipartFile image = new MockMultipartFile("file", "tea.jpg",
                "image/jpeg", "jpeg-content".getBytes(StandardCharsets.UTF_8));
        SysFile saved = new SysFile();
        saved.setName("tea.jpg");
        saved.setUrl("https://files.example.test/tea.jpg");
        when(fileService.uploadInner(image, SecurityConstants.INNER)).thenReturn(R.ok(saved));

        AjaxResult result = controller.upload(image);

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.SUCCESS);
        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(saved);
        verify(fileService).uploadInner(image, SecurityConstants.INNER);
    }

    @Test
    @DisplayName("伪装成图片的非图片 MIME 类型会在调用文件服务前拒绝")
    void uploadShouldRejectNonImageMimeType()
    {
        RemoteFileService fileService = mock(RemoteFileService.class);
        InvImageController controller = new InvImageController(fileService);
        MockMultipartFile invalid = new MockMultipartFile("file", "script.jpg",
                "application/javascript", "alert(1)".getBytes(StandardCharsets.UTF_8));

        AjaxResult result = controller.upload(invalid);

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.ERROR);
        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("仅支持 JPG、JPEG、PNG 格式图片");
        verify(fileService, never()).uploadInner(invalid, SecurityConstants.INNER);
    }

    @Test
    @DisplayName("超过 5MB 的图片会在调用文件服务前拒绝")
    void uploadShouldRejectOversizedImage()
    {
        RemoteFileService fileService = mock(RemoteFileService.class);
        InvImageController controller = new InvImageController(fileService);
        MockMultipartFile oversized = new MockMultipartFile("file", "large.png",
                "image/png", new byte[5 * 1024 * 1024 + 1]);

        AjaxResult result = controller.upload(oversized);

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.ERROR);
        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("图片大小不能超过 5MB");
        verify(fileService, never()).uploadInner(oversized, SecurityConstants.INNER);
    }
}
