package com.erp.common.log.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.service.AsyncLogService;
import com.erp.common.log.support.AuditPayloadSanitizer;
import com.erp.system.api.domain.SysOperLog;

@DisplayName("操作日志切面安全行为")
class LogAspectTest
{
    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("异常路径不保存任意响应、异常消息或未允许参数")
    void failureShouldPersistOnlyAllowedRequestFieldsAndStableError() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/demo");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.setUserName("auditor");

        Method method = DemoController.class.getDeclaredMethod("save", DemoRequest.class);
        Log annotation = method.getAnnotation(Log.class);
        JoinPoint joinPoint = mock(JoinPoint.class);
        CodeSignature signature = mock(CodeSignature.class);
        DemoRequest argument = new DemoRequest(42L, "do-not-log");
        when(joinPoint.getTarget()).thenReturn(new DemoController());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { argument });
        when(signature.getName()).thenReturn("save");
        when(signature.getParameterNames()).thenReturn(new String[] { "request" });

        AtomicReference<SysOperLog> saved = new AtomicReference<>();
        AsyncLogService asyncLogService = mock(AsyncLogService.class);
        doAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return null;
        }).when(asyncLogService).saveSysLog(any(SysOperLog.class));

        TestableLogAspect aspect = new TestableLogAspect();
        ReflectionTestUtils.setField(aspect, "asyncLogService", asyncLogService);
        ReflectionTestUtils.setField(aspect, "auditPayloadSanitizer", new AuditPayloadSanitizer());

        aspect.doBefore(joinPoint, annotation);
        aspect.invoke(joinPoint, annotation,
                new ServiceException("password=do-not-log", 409), Map.of("token", "do-not-log"));

        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().getOperParam()).isEqualTo("{\"recordId\":42}");
        assertThat(saved.get().getJsonResult()).isNull();
        assertThat(saved.get().getErrorMsg()).isEqualTo("ServiceException(code=409)");
        assertThat(saved.get().toString()).doesNotContain("do-not-log");
    }

    private static final class TestableLogAspect extends LogAspect
    {
        private void invoke(JoinPoint joinPoint, Log annotation, Exception error, Object response)
        {
            handleLog(joinPoint, annotation, error, response);
        }
    }

    private static final class DemoController
    {
        @Log(title = "demo", includeParamNames = { "recordId" })
        private void save(DemoRequest request)
        {
        }
    }

    private static final class DemoRequest
    {
        private final Long recordId;
        private final String password;

        private DemoRequest(Long recordId, String password)
        {
            this.recordId = recordId;
            this.password = password;
        }

        public Long getRecordId()
        {
            return recordId;
        }

        public String getPassword()
        {
            return password;
        }
    }
}
