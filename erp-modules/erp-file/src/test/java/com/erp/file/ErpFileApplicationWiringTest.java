package com.erp.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.common.security.annotation.EnableRyFeignClients;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;

@DisplayName("文件服务启动接线")
class ErpFileApplicationWiringTest
{
    @Test
    @DisplayName("文件服务启用系统远程日志所需的 Feign 客户端扫描")
    void shouldEnableFeignClientsRequiredByCommonLog()
    {
        assertThat(AnnotationUtils.findAnnotation(
                ErpFileApplication.class, EnableRyFeignClients.class)).isNotNull();
    }
}
