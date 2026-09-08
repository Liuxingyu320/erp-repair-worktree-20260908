package com.erp.file.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.domain.R;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.file.FileUtils;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.file.service.ISysFileService;
import com.erp.system.api.domain.SysFile;

/**
 * 文件请求处理
 * 
 * @author erp
 */
@RestController
public class SysFileController
{
    private static final Logger log = LoggerFactory.getLogger(SysFileController.class);

    @Autowired
    private ISysFileService sysFileService;

    /**
     * 文件上传请求
     */
    @RequiresPermissions("file:upload")
    @PostMapping("upload")
    public R<SysFile> upload(MultipartFile file, HttpServletResponse response)
    {
        return doUpload(file);
    }

    /**
     * 仅供已完成业务权限校验的内部服务调用。
     */
    @InnerAuth(isUser = true)
    @PostMapping("inner/upload")
    public R<SysFile> uploadInner(MultipartFile file)
    {
        return doUpload(file);
    }

    private R<SysFile> doUpload(MultipartFile file)
    {
        try
        {
            // 上传并返回访问地址
            String url = sysFileService.uploadFile(file);
            SysFile sysFile = new SysFile();
            sysFile.setName(FileUtils.getName(url));
            sysFile.setUrl(url);
            return R.ok(sysFile);
        }
        catch (Exception e)
        {
            log.error("上传文件失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 文件删除请求
     */
    @RequiresPermissions("file:delete")
    @DeleteMapping("delete")
    public R<Boolean> delete(String fileUrl, HttpServletResponse response)
    {
        disableCaching(response);
        try
        {
            if (!FileUtils.validateFilePath(fileUrl))
            {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许删除。 ", fileUrl));
            }
            sysFileService.deleteFile(fileUrl);
            return R.ok();
        }
        catch (Exception e)
        {
            log.error("删除文件失败", e);
            return R.fail(e.getMessage());
        }
    }

    private void disableCaching(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
