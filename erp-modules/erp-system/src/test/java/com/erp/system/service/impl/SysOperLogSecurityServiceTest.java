package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.system.api.domain.SysOperLog;
import com.erp.system.domain.vo.SysOperLogDetailVo;
import com.erp.system.mapper.SysOperLogMapper;

@DisplayName("操作日志详情脱敏")
class SysOperLogSecurityServiceTest
{
    @Test
    @DisplayName("历史正文只转换为固定保护摘要")
    void detailShouldNeverReturnStoredPayload()
    {
        SysOperLog stored = new SysOperLog();
        stored.setOperId(9L);
        stored.setStatus(1);
        stored.setOperParam("{\"password\":\"secret-value\"}");
        stored.setJsonResult("{\"token\":\"secret-value\"}");
        stored.setErrorMsg("bankAccount=6222020202020202");
        SysOperLogMapper mapper = mock(SysOperLogMapper.class);
        when(mapper.selectOperLogById(9L)).thenReturn(stored);
        SysOperLogServiceImpl service = new SysOperLogServiceImpl();
        ReflectionTestUtils.setField(service, "operLogMapper", mapper);

        SysOperLogDetailVo detail = service.selectOperLogDetailById(9L);

        assertThat(detail.getRequestSummary()).isEqualTo("历史请求正文已保护");
        assertThat(detail.getResultSummary()).isEqualTo("历史响应正文已保护");
        assertThat(detail.getErrorSummary()).isEqualTo("错误详情已保护，请依据业务记录和稳定错误码排查");
        assertThat(detail.toString()).doesNotContain("secret-value", "6222020202020202");
    }
}
