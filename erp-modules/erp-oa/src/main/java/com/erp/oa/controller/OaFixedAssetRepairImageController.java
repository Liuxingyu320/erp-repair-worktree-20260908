package com.erp.oa.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.file.FileTypeUtils;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.SysFile;

/**
 * 固定资产报修照片上传。业务侧校验报修权限与当前门店范围后，以内网身份调用文件服务，
 * 避免给专岗开放通用 {@code file:upload}。
 */
@RestController
@RequestMapping("/fixedAsset/repair/image")
public class OaFixedAssetRepairImageController extends OaBaseController
{
    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private final RemoteFileService remoteFileService;
    private final ShopScopeService shopScopeService;
    private final OaDeptScopeMapper repairDeptScopeMapper;

    public OaFixedAssetRepairImageController(RemoteFileService remoteFileService,
            ShopScopeService shopScopeService, OaDeptScopeMapper repairDeptScopeMapper)
    {
        this.remoteFileService = remoteFileService;
        this.shopScopeService = shopScopeService;
        this.repairDeptScopeMapper = repairDeptScopeMapper;
    }

    @RequiresPermissions("oa:fixedAsset:repair:add")
    @Log(title = "固定资产报修图片上传", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestPart("file") MultipartFile file,
            @RequestParam(value = "shopDeptId", required = false) String shopDeptId,
            HttpServletRequest request)
    {
        resolveRepairUploadShop(shopDeptId, request);
        String validationError = validateImage(file);
        if (validationError != null)
        {
            return error(validationError);
        }
        R<SysFile> result = remoteFileService.uploadInner(file, SecurityConstants.INNER);
        if (result == null || R.isError(result) || result.getData() == null
                || StringUtils.isEmpty(result.getData().getUrl()))
        {
            String message = result == null || StringUtils.isEmpty(result.getMsg())
                    ? "图片上传失败，请稍后重试" : result.getMsg();
            return error(message);
        }
        return success(result.getData());
    }

    private Long resolveRepairUploadShop(String shopDeptIdValue, HttpServletRequest request)
    {
        Long shopDeptId = parsePositiveShopDeptId(shopDeptIdValue);
        if (shopDeptId == null)
        {
            throw new ServiceException("请先选择当前有效门店", HttpStatus.FORBIDDEN);
        }
        if (request != null && StringUtils.isNotEmpty(request.getHeader(ShopHeaderUtils.SHOP_HEADER)))
        {
            Long headerShopDeptId = ShopHeaderUtils.resolveShopDeptId(request);
            if (!shopDeptId.equals(headerShopDeptId))
            {
                throw new ServiceException("门店上下文不一致", HttpStatus.FORBIDDEN);
            }
        }
        Long currentShopDeptId = shopScopeService.resolveRequiredShopDept(shopDeptId);
        if (currentShopDeptId == null || !shopDeptId.equals(currentShopDeptId)
                || repairDeptScopeMapper.countActiveStoreDept(currentShopDeptId) != 1)
        {
            throw new ServiceException("只能在当前有效门店上传报修照片", HttpStatus.FORBIDDEN);
        }
        if (!SecurityUtils.isAdmin()
                && !shopScopeService.hasUserShopScope(SecurityUtils.getUserId(), currentShopDeptId))
        {
            throw new ServiceException("无权在当前门店上传报修照片", HttpStatus.FORBIDDEN);
        }
        return currentShopDeptId;
    }

    private Long parsePositiveShopDeptId(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[1-9]\\d{0,18}$"))
        {
            return null;
        }
        try
        {
            return Long.valueOf(trimmed);
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private String validateImage(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            return "请选择需要上传的图片";
        }
        if (file.getSize() > MAX_IMAGE_SIZE)
        {
            return "图片大小不能超过 5MB";
        }
        String originalFilename = file.getOriginalFilename();
        String extension = FileTypeUtils.getFileType(originalFilename == null ? "" : originalFilename);
        String contentType = normalizeContentType(file.getContentType());
        boolean jpegName = StringUtils.equalsAnyIgnoreCase(extension, "jpg", "jpeg");
        boolean pngName = StringUtils.equalsAnyIgnoreCase(extension, "png");
        boolean jpegMime = StringUtils.equalsAnyIgnoreCase(contentType, "image/jpeg", "image/jpg");
        boolean pngMime = StringUtils.equalsAnyIgnoreCase(contentType, "image/png");
        if (!(jpegName && jpegMime) && !(pngName && pngMime))
        {
            return "仅支持 JPG、JPEG、PNG 格式图片";
        }
        byte[] bytes;
        try
        {
            bytes = file.getBytes();
        }
        catch (IOException ex)
        {
            return "图片内容不合法";
        }
        if (bytes == null || bytes.length == 0)
        {
            return "请选择需要上传的图片";
        }
        boolean jpegContent = isJpeg(bytes);
        boolean pngContent = isPng(bytes);
        if (jpegName && !jpegContent || pngName && !pngContent || !jpegContent && !pngContent)
        {
            return "图片内容不合法";
        }
        return inspectImageHeader(bytes, jpegContent);
    }

    private String inspectImageHeader(byte[] bytes, boolean jpeg)
    {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(bytes)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
            {
                return "图片内容不合法";
            }
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (jpeg && !StringUtils.equalsAnyIgnoreCase(format, "JPEG", "jpg")
                        || !jpeg && !StringUtils.equalsAnyIgnoreCase(format, "png"))
                {
                    return "图片内容不合法";
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0)
                {
                    return "图片内容不合法";
                }
                return null;
            }
            finally
            {
                reader.dispose();
            }
        }
        catch (IOException | IllegalArgumentException ex)
        {
            return "图片内容不合法";
        }
    }

    private static String normalizeContentType(String contentType)
    {
        if (contentType == null)
        {
            return "";
        }
        int separator = contentType.indexOf(';');
        return separator < 0 ? contentType.trim() : contentType.substring(0, separator).trim();
    }

    private static boolean isJpeg(byte[] bytes)
    {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] bytes)
    {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }
}
