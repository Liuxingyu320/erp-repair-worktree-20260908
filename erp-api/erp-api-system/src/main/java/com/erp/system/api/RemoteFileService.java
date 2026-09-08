package com.erp.system.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.system.api.domain.DriveBusinessFile;
import com.erp.system.api.domain.SysFile;
import com.erp.system.api.factory.RemoteFileFallbackFactory;
import feign.Response;

/**
 * 文件服务
 * 
 * @author erp
 */
@FeignClient(contextId = "remoteFileService", value = ServiceNameConstants.FILE_SERVICE, fallbackFactory = RemoteFileFallbackFactory.class)
public interface RemoteFileService
{
    /**
     * 上传文件
     *
     * @param file 文件信息
     * @return 结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SysFile> upload(@RequestPart(value = "file") MultipartFile file);

    /**
     * 业务服务完成自身权限校验后上传文件。
     */
    @PostMapping(value = "/inner/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<SysFile> uploadInner(@RequestPart(value = "file") MultipartFile file,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 删除文件
     *
     * @param fileUrl 文件地址
     * @return 结果
     */
    @DeleteMapping(value = "/delete", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public R<Boolean> delete(@RequestParam("fileUrl") String fileUrl);

    /**
     * 验证当前用户可以将云盘文件绑定到指定业务用途。
     */
    @GetMapping("/drive/inner/nodes/{nodeId}/binding")
    R<DriveBusinessFile> validateDriveBusinessFile(
            @PathVariable("nodeId") Long nodeId,
            @RequestParam("usage") String usage,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 在业务服务完成二次授权后，流式读取受控文件。
     */
    @GetMapping("/drive/inner/nodes/{nodeId}/content")
    Response readDriveBusinessContent(
            @PathVariable("nodeId") Long nodeId,
            @RequestParam("mode") String mode,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
