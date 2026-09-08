package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.common.log.sanitize.SensitiveLogSanitizer;
import com.erp.system.api.domain.SysOperLog;
import com.erp.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SysOperLogServiceImplSecurityTest
{
    @Test
    void shouldSanitizeRemoteLogBeforePersistingIt()
    {
        SysOperLogMapper mapper = mock(SysOperLogMapper.class);
        SysOperLogServiceImpl service = service(mapper);
        SysOperLog log = logWithSecrets();
        when(mapper.insertOperlog(log)).thenReturn(1);

        assertThat(service.insertOperlog(log)).isEqualTo(1);

        verify(mapper).insertOperlog(log);
        assertThat(log.getOperParam()).doesNotContain("request-secret");
        assertThat(log.getJsonResult()).doesNotContain("response-secret");
        assertThat(log.getErrorMsg()).doesNotContain("error-secret");
    }

    private SysOperLogServiceImpl service(SysOperLogMapper mapper)
    {
        SysOperLogServiceImpl service = new SysOperLogServiceImpl();
        ReflectionTestUtils.setField(service, "operLogMapper", mapper);
        ReflectionTestUtils.setField(service, "sensitiveLogSanitizer", new SensitiveLogSanitizer());
        return service;
    }

    private SysOperLog logWithSecrets()
    {
        SysOperLog log = new SysOperLog();
        log.setOperParam("{\"password\":\"request-secret\"}");
        log.setJsonResult("{\"accessToken\":\"response-secret\"}");
        log.setErrorMsg("password=error-secret");
        return log;
    }
}
