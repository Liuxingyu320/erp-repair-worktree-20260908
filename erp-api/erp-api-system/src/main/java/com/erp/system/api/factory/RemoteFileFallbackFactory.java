package com.erp.system.api.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.domain.R;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import com.erp.system.api.domain.SysFile;
import feign.Response;

/**
 * 文件服务降级处理
 * 
 * @author erp
 */
@Component
public class RemoteFileFallbackFactory implements FallbackFactory<RemoteFileService>
{
    private static final Logger log = LoggerFactory.getLogger(RemoteFileFallbackFactory.class);

    @Override
    public RemoteFileService create(Throwable throwable)
    {
        log.error("文件服务调用失败:{}", throwable.getMessage());
        return new RemoteFileService()
        {
            @Override
            public R<SysFile> upload(MultipartFile file)
            {
                return R.fail("上传文件失败:" + throwable.getMessage());
            }

            @Override
            public R<SysFile> uploadInner(MultipartFile file, String source)
            {
                return R.fail("上传文件失败:" + throwable.getMessage());
            }

            @Override
            public R<Boolean> delete(String fileUrl)
            {
                return R.fail("删除文件失败:" + throwable.getMessage());
            }

            @Override
            public R<DriveBusinessFile> validateDriveBusinessFile(Long nodeId,
                    String usage, String source)
            {
                return R.fail("受控文件校验失败");
            }

            @Override
            public Response readDriveBusinessContent(Long nodeId, String mode,
                    String source)
            {
                return null;
            }
        };
    }
}
