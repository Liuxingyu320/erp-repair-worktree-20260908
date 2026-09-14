package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.common.core.exception.auth.PasswordChangeRequiredException;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.aspect.PreAuthorizeAspect;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.SysFile;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@DisplayName("固定资产报修照片专用上传")
class OaFixedAssetRepairImageControllerTest
{
    private static final Long USER_ID = 42L;
    private static final Long SHOP_ID = 88L;
    private static final Long OTHER_SHOP_ID = 99L;

    private RemoteFileService fileService;
    private ShopScopeService shopScopeService;
    private OaDeptScopeMapper deptScopeMapper;
    private OaFixedAssetRepairImageController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        fileService = mock(RemoteFileService.class);
        shopScopeService = mock(ShopScopeService.class);
        deptScopeMapper = mock(OaDeptScopeMapper.class);
        OaFixedAssetRepairImageController target = new OaFixedAssetRepairImageController(
                fileService, shopScopeService, deptScopeMapper);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new PreAuthorizeAspect());
        controller = factory.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("合法 JPEG 通过内部文件接口上传并返回 name/url")
    void jpegUploadUsesInnerFileService() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        stubAuthorizedShop(SHOP_ID);
        MockMultipartFile image = jpegFile("repair.jpg");
        SysFile saved = savedFile("repair.jpg", "https://files.example.test/repair.jpg");
        when(fileService.uploadInner(image, SecurityConstants.INNER)).thenReturn(R.ok(saved));

        AjaxResult result = controller.upload(image, String.valueOf(SHOP_ID), request());

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.SUCCESS);
        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(saved);
        verify(fileService).uploadInner(image, SecurityConstants.INNER);
        verify(fileService, never()).upload(any());
        verify(shopScopeService).resolveRequiredShopDept(SHOP_ID);
        verify(shopScopeService).hasUserShopScope(USER_ID, SHOP_ID);
        verify(deptScopeMapper).countActiveStoreDept(SHOP_ID);
    }

    @Test
    @DisplayName("合法 PNG 通过内部文件接口上传")
    void pngUploadUsesInnerFileService() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        stubAuthorizedShop(SHOP_ID);
        MockMultipartFile image = pngFile("repair.png");
        SysFile saved = savedFile("repair.png", "https://files.example.test/repair.png");
        when(fileService.uploadInner(image, SecurityConstants.INNER)).thenReturn(R.ok(saved));

        AjaxResult result = controller.upload(image, String.valueOf(SHOP_ID), request());

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.SUCCESS);
        assertThat(((SysFile) result.get(AjaxResult.DATA_TAG)).getUrl())
                .isEqualTo("https://files.example.test/repair.png");
        verify(fileService).uploadInner(image, SecurityConstants.INNER);
        verify(fileService, never()).upload(any());
    }

    @Test
    @DisplayName("24MP 合法图片只读头信息即可通过，不设分辨率上限")
    void legal24MpImagePassesHeaderCheck() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        stubAuthorizedShop(SHOP_ID);
        byte[] encodedImage = pngBytes(6000, 4000);
        assertThat(encodedImage.length).isLessThan(5 * 1024 * 1024);
        MockMultipartFile image = new MockMultipartFile("file", "phone.png",
                MediaType.IMAGE_PNG_VALUE, encodedImage);
        SysFile saved = savedFile("phone.png", "https://files.example.test/phone.png");
        when(fileService.uploadInner(image, SecurityConstants.INNER)).thenReturn(R.ok(saved));

        AjaxResult result = controller.upload(image, String.valueOf(SHOP_ID), request());

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.SUCCESS);
        verify(fileService).uploadInner(image, SecurityConstants.INNER);
        verify(fileService, never()).upload(any());
    }

    @Test
    @DisplayName("仅报修权限即可经真实 around 链进入专用入口")
    void repairPermissionCanEnterDedicatedEndpoint() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        stubAuthorizedShop(SHOP_ID);
        MockMultipartFile image = jpegFile("repair.jpg");
        SysFile saved = savedFile("repair.jpg", "https://files.example.test/repair.jpg");
        when(fileService.uploadInner(any(MultipartFile.class), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(saved));

        mvc.perform(multipart("/fixedAsset/repair/image/upload")
                        .file(image)
                        .param("shopDeptId", String.valueOf(SHOP_ID))
                        .header(SecurityConstants.AUTHORIZATION_HEADER, "Bearer test-token"))
                .andExpect(status().isOk());

        verify(fileService).uploadInner(any(MultipartFile.class), eq(SecurityConstants.INNER));
        verify(fileService, never()).upload(any());
    }

    @Test
    @DisplayName("仅有 file:upload 没有报修权限时不能进入")
    void fileUploadPermissionCannotEnter() throws Exception
    {
        loginAs("file:upload");
        stubAuthorizedShop(SHOP_ID);
        MockMultipartFile image = jpegFile("repair.jpg");

        assertThatThrownBy(() -> mvc.perform(multipart("/fixedAsset/repair/image/upload")
                        .file(image)
                        .param("shopDeptId", String.valueOf(SHOP_ID))
                        .header(SecurityConstants.AUTHORIZATION_HEADER, "Bearer test-token"))
                .andReturn())
                .hasRootCauseInstanceOf(NotPermissionException.class);

        verify(fileService, never()).uploadInner(any(), any());
        verify(fileService, never()).upload(any());
    }

    @Test
    @DisplayName("必须改密用户走真实 around 密码约束不能进入")
    void passwordChangeRequiredCannotEnter() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        SecurityContextHolder.get(SecurityConstants.LOGIN_USER, LoginUser.class)
                .getSysUser().setMustChangePassword("1");
        stubAuthorizedShop(SHOP_ID);
        MockMultipartFile image = jpegFile("repair.jpg");

        assertThatThrownBy(() -> mvc.perform(multipart("/fixedAsset/repair/image/upload")
                        .file(image)
                        .param("shopDeptId", String.valueOf(SHOP_ID))
                        .header(SecurityConstants.AUTHORIZATION_HEADER, "Bearer test-token"))
                .andReturn())
                .hasRootCauseInstanceOf(PasswordChangeRequiredException.class);

        verify(fileService, never()).uploadInner(any(), any());
    }

    @Test
    @DisplayName("缺少门店时拒绝且不调用上传")
    void missingShopDeptRejectsWithoutUpload() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        MockHttpServletRequest request = request();
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, String.valueOf(SHOP_ID));

        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"), null, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请先选择当前有效门店");
        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"), "", request()))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"), "abc", request()))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"), "1.5", request()))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"), "0", request()))
                .isInstanceOf(ServiceException.class);

        verify(fileService, never()).uploadInner(any(), any());
        verify(shopScopeService, never()).resolveRequiredShopDept(any());
    }

    @Test
    @DisplayName("跨门店或无范围时拒绝且不调用上传")
    void outOfScopeShopRejectsWithoutUpload() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        when(shopScopeService.resolveRequiredShopDept(OTHER_SHOP_ID))
                .thenThrow(new ServiceException("当前用户无权选择该店铺", HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"),
                String.valueOf(OTHER_SHOP_ID), request()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前用户无权选择该店铺");

        when(shopScopeService.resolveRequiredShopDept(SHOP_ID)).thenReturn(SHOP_ID);
        when(deptScopeMapper.countActiveStoreDept(SHOP_ID)).thenReturn(1);
        when(shopScopeService.hasUserShopScope(USER_ID, SHOP_ID)).thenReturn(false);

        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"),
                String.valueOf(SHOP_ID), request()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("无权在当前门店上传报修照片");

        verify(fileService, never()).uploadInner(any(), any());
    }

    @Test
    @DisplayName("非有效门店时拒绝且不调用上传")
    void nonStoreDeptRejectsWithoutUpload() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        when(shopScopeService.resolveRequiredShopDept(SHOP_ID)).thenReturn(SHOP_ID);
        when(shopScopeService.hasUserShopScope(USER_ID, SHOP_ID)).thenReturn(true);
        when(deptScopeMapper.countActiveStoreDept(SHOP_ID)).thenReturn(0);

        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"),
                String.valueOf(SHOP_ID), request()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("只能在当前有效门店上传报修照片");

        verify(fileService, never()).uploadInner(any(), any());
    }

    @Test
    @DisplayName("Dept-NumId 与传入门店不一致时拒绝且不调用上传")
    void headerBodyMismatchRejectsWithoutUpload() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        MockHttpServletRequest request = request();
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, String.valueOf(OTHER_SHOP_ID));

        assertThatThrownBy(() -> controller.upload(jpegFile("repair.jpg"),
                String.valueOf(SHOP_ID), request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("门店上下文不一致");

        verify(fileService, never()).uploadInner(any(), any());
        verify(shopScopeService, never()).resolveRequiredShopDept(any());
    }

    @Test
    @DisplayName("空文件、超限、MIME/扩展名/真实字节不匹配被拒绝")
    void invalidFilesRejectedWithoutUpload() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        stubAuthorizedShop(SHOP_ID);
        String shop = String.valueOf(SHOP_ID);
        MockHttpServletRequest request = request();

        AjaxResult empty = controller.upload(new MockMultipartFile("file", "empty.jpg",
                "image/jpeg", new byte[0]), shop, request);
        assertThat(empty.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.ERROR);
        assertThat(empty.get(AjaxResult.MSG_TAG)).isEqualTo("请选择需要上传的图片");

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(5L * 1024L * 1024L + 1);
        AjaxResult tooLarge = controller.upload(oversized, shop, request);
        assertThat(tooLarge.get(AjaxResult.MSG_TAG)).isEqualTo("图片大小不能超过 5MB");

        AjaxResult mime = controller.upload(new MockMultipartFile("file", "script.jpg",
                "application/javascript", jpegBytes()), shop, request);
        assertThat(mime.get(AjaxResult.MSG_TAG)).isEqualTo("仅支持 JPG、JPEG、PNG 格式图片");

        AjaxResult extension = controller.upload(new MockMultipartFile("file", "script.txt",
                "image/jpeg", jpegBytes()), shop, request);
        assertThat(extension.get(AjaxResult.MSG_TAG)).isEqualTo("仅支持 JPG、JPEG、PNG 格式图片");

        AjaxResult mixed = controller.upload(new MockMultipartFile("file", "repair.jpg",
                "image/png", jpegBytes()), shop, request);
        assertThat(mixed.get(AjaxResult.MSG_TAG)).isEqualTo("仅支持 JPG、JPEG、PNG 格式图片");

        AjaxResult script = controller.upload(new MockMultipartFile("file", "script.jpg",
                "image/jpeg", "alert(1)".getBytes(StandardCharsets.UTF_8)), shop, request);
        assertThat(script.get(AjaxResult.MSG_TAG)).isEqualTo("图片内容不合法");

        AjaxResult svg = controller.upload(new MockMultipartFile("file", "photo.png",
                "image/png", "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8)),
                shop, request);
        assertThat(svg.get(AjaxResult.MSG_TAG)).isEqualTo("图片内容不合法");

        AjaxResult pdf = controller.upload(new MockMultipartFile("file", "photo.jpg",
                "image/jpeg", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)), shop, request);
        assertThat(pdf.get(AjaxResult.MSG_TAG)).isEqualTo("图片内容不合法");

        AjaxResult zeroSize = controller.upload(new MockMultipartFile("file", "empty-size.png",
                "image/png", pngHeader(0, 8)), shop, request);
        assertThat(zeroSize.get(AjaxResult.MSG_TAG)).isEqualTo("图片内容不合法");

        verify(fileService, never()).uploadInner(any(), any());
    }

    @Test
    @DisplayName("远程失败、null 或空 URL 不能伪装成功")
    void remoteFailuresAreNotSuccessful() throws Exception
    {
        loginAs("oa:fixedAsset:repair:add");
        stubAuthorizedShop(SHOP_ID);
        MockMultipartFile image = jpegFile("repair.jpg");
        when(fileService.uploadInner(image, SecurityConstants.INNER))
                .thenReturn(R.fail("offline"), null, R.ok(null), R.ok(savedFile("repair.jpg", "")));

        AjaxResult failed = controller.upload(image, String.valueOf(SHOP_ID), request());
        assertThat(failed.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.ERROR);
        assertThat(failed.get(AjaxResult.MSG_TAG)).isEqualTo("offline");

        AjaxResult missing = controller.upload(image, String.valueOf(SHOP_ID), request());
        assertThat(missing.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.ERROR);
        assertThat(missing.get(AjaxResult.MSG_TAG)).isEqualTo("图片上传失败，请稍后重试");

        AjaxResult noData = controller.upload(image, String.valueOf(SHOP_ID), request());
        assertThat(noData.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.ERROR);

        AjaxResult emptyUrl = controller.upload(image, String.valueOf(SHOP_ID), request());
        assertThat(emptyUrl.get(AjaxResult.CODE_TAG)).isEqualTo(HttpStatus.ERROR);
        assertThat(emptyUrl.get(AjaxResult.DATA_TAG)).isNull();
    }

    private void loginAs(String... permissions)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, "Bearer test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.setUserId(String.valueOf(USER_ID));
        SysUser sysUser = new SysUser(USER_ID);
        sysUser.setMustChangePassword("0");
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(USER_ID);
        loginUser.setSysUser(sysUser);
        loginUser.setPermissions(new HashSet<>(java.util.Arrays.asList(permissions)));
        loginUser.setRoles(Collections.emptySet());
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private void stubAuthorizedShop(Long shopDeptId)
    {
        when(shopScopeService.resolveRequiredShopDept(shopDeptId)).thenReturn(shopDeptId);
        when(shopScopeService.hasUserShopScope(USER_ID, shopDeptId)).thenReturn(true);
        when(deptScopeMapper.countActiveStoreDept(shopDeptId)).thenReturn(1);
    }

    private MockHttpServletRequest request()
    {
        return new MockHttpServletRequest();
    }

    private static SysFile savedFile(String name, String url)
    {
        SysFile saved = new SysFile();
        saved.setName(name);
        saved.setUrl(url);
        return saved;
    }

    private static MockMultipartFile jpegFile(String filename) throws IOException
    {
        return new MockMultipartFile("file", filename, MediaType.IMAGE_JPEG_VALUE, jpegBytes());
    }

    private static MockMultipartFile pngFile(String filename) throws IOException
    {
        return new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, pngBytes());
    }

    private static byte[] jpegBytes() throws IOException
    {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", output))
        {
            throw new IOException("JPEG encoder unavailable");
        }
        return output.toByteArray();
    }

    private static byte[] pngBytes() throws IOException
    {
        return pngBytes(8, 8);
    }

    private static byte[] pngBytes(int width, int height) throws IOException
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output))
        {
            throw new IOException("PNG encoder unavailable");
        }
        return output.toByteArray();
    }

    private static byte[] pngHeader(int width, int height)
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = new byte[13];
        ihdr[0] = (byte) (width >>> 24);
        ihdr[1] = (byte) (width >>> 16);
        ihdr[2] = (byte) (width >>> 8);
        ihdr[3] = (byte) width;
        ihdr[4] = (byte) (height >>> 24);
        ihdr[5] = (byte) (height >>> 16);
        ihdr[6] = (byte) (height >>> 8);
        ihdr[7] = (byte) height;
        ihdr[8] = 8;
        ihdr[9] = 2;
        writePngChunk(output, "IHDR", ihdr);
        writePngChunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream output, String type, byte[] data)
    {
        int length = data.length;
        output.writeBytes(new byte[] {
                (byte) (length >>> 24), (byte) (length >>> 16),
                (byte) (length >>> 8), (byte) length
        });
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        output.write(typeBytes, 0, typeBytes.length);
        output.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        int value = (int) crc.getValue();
        output.writeBytes(new byte[] {
                (byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value
        });
    }
}
