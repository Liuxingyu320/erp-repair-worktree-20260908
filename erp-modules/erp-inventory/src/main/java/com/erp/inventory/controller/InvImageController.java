package com.erp.inventory.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.file.FileTypeUtils;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.SysFile;

/**
 * 库存主数据图片上传。
 *
 * <p>业务服务先校验商品、OE 或礼盒维护权限，再以内网身份调用文件服务；
 * 这样不需要给业务角色开放通用文件上传权限。</p>
 */
@RestController
@RequestMapping("/image")
public class InvImageController extends BaseController
{
    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private final RemoteFileService remoteFileService;

    public InvImageController(RemoteFileService remoteFileService)
    {
        this.remoteFileService = remoteFileService;
    }

    @RequiresPermissions(value = {
            "inv:product:add", "inv:product:edit",
            "inv:oe:add", "inv:oe:edit",
            "inv:gift:add", "inv:gift:edit"
    }, logical = Logical.OR)
    @Log(title = "库存图片上传", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestPart("file") MultipartFile file)
    {
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
        String contentType = file.getContentType();
        if (!StringUtils.equalsAnyIgnoreCase(extension, "jpg", "jpeg", "png")
                || !StringUtils.equalsAnyIgnoreCase(contentType, "image/jpeg", "image/jpg", "image/png"))
        {
            return "仅支持 JPG、JPEG、PNG 格式图片";
        }
        return null;
    }
}
