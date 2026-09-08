package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.Logical;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignTaskBatchFinalizePreviewRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchFinalizeRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteAction;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteRequest;
import com.erp.oa.domain.dto.OaSignTaskNotificationRetryRequest;
import com.erp.oa.domain.dto.OaSignTaskRetryRequest;
import com.erp.oa.domain.dto.OaSignExceptionResolutionRequest;

@DisplayName("单HR签约任务Controller")
class OaSignTaskControllerTest
{
    @Test
    @DisplayName("任务接口使用细粒度权限且页面访问权限同时允许导出")
    void shouldUseDedicatedTaskPermissions() throws Exception
    {
        assertReadPermission("list", "oa:signTask:list", OaSignTask.class, HttpServletRequest.class);
        assertReadPermission("export", "oa:signTask:list", HttpServletResponse.class,
                OaSignTask.class, HttpServletRequest.class);
        assertReadPermission("detail", "oa:signTask:query", Long.class, HttpServletRequest.class);
        assertPermission("revalidate", "oa:signTask:revalidate", Long.class,
                OaSignTaskRetryRequest.class, HttpServletRequest.class);
        assertPermission("send", "oa:signTask:send", Long.class,
                OaSignTaskRetryRequest.class, HttpServletRequest.class);
        assertPermission("retry", "oa:signTask:retry", Long.class,
                OaSignTaskRetryRequest.class, HttpServletRequest.class);
        assertPermission("retryNotification", "oa:signTask:retry", Long.class,
                OaSignTaskNotificationRetryRequest.class, HttpServletRequest.class);
        assertPermission("cancel", "oa:signTask:cancel", Long.class,
                OaSignTaskRetryRequest.class, HttpServletRequest.class);
        Method resolve = OaSignTaskController.class.getMethod("resolveException", Long.class,
                OaSignExceptionResolutionRequest.class, HttpServletRequest.class);
        assertThat(resolve.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signTask:resolveRefusal", "oa:signTask:resolveExpiry");
        assertThat(resolve.getAnnotation(RequiresPermissions.class).logical()).isEqualTo(Logical.OR);
        assertThat(resolve.getAnnotation(com.erp.common.security.annotation.IdempotentSubmit.class))
                .as("处置服务自身按 requestId 返回耐久幂等结果")
                .isNull();

        assertThat(OaSignTaskController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/signTask");
    }

    @Test
    @DisplayName("任务接口不再公开审批确认入口")
    void shouldNotExposeApprovalEndpoint()
    {
        assertThat(Arrays.stream(OaSignTaskController.class.getMethods()).map(Method::getName))
                .doesNotContain("confirm");
    }

    @Test
    @DisplayName("通知重新入队必须指定请求ID和业务事件")
    void shouldValidateNotificationRetryRequest()
    {
        try (var factory = Validation.buildDefaultValidatorFactory())
        {
            Set<ConstraintViolation<OaSignTaskNotificationRetryRequest>> violations =
                    factory.getValidator().validate(new OaSignTaskNotificationRetryRequest());
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("requestId", "businessKey");
        }
    }

    @Test
    @DisplayName("任务中心公开后端七指标聚合接口")
    void shouldExposeMetricsEndpointWithListPermission()
    {
        assertThat(Arrays.stream(OaSignTaskController.class.getMethods()).map(Method::getName))
                .contains("metrics");
    }

    @Test
    @DisplayName("签约数据导出复用页面权限并使用POST文件接口")
    void shouldProtectSigningDataExport() throws Exception
    {
        Method method = OaSignTaskController.class.getMethod("export",
                HttpServletResponse.class, OaSignTask.class, HttpServletRequest.class);
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signTask:list", "oa:signTask:technicalEvidence");
        assertThat(method.getAnnotation(RequiresPermissions.class).logical()).isEqualTo(Logical.OR);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/export");
        var log = method.getAnnotation(com.erp.common.log.annotation.Log.class);
        assertThat(log).isNotNull();
        assertThat(log.businessType()).isEqualTo(com.erp.common.log.enums.BusinessType.EXPORT);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    @Test
    @DisplayName("签约能力接口始终可用且不要求组织范围")
    void shouldExposeUnscopedSignCapabilities() throws Exception
    {
        Method method = OaSignTaskController.class.getMethod("capabilities");
        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/capabilities");
        assertThat(method.getAnnotation(RequiresPermissions.class)).isNull();
        assertThat(method.getParameterCount()).isZero();

        assertThat(OaSignTaskController.class.getDeclaredField("excelImportEnabled")
                .getAnnotation(Value.class).value())
                .isEqualTo("${oa.sign.excel-import.enabled:true}");
    }

    @Test
    @DisplayName("通知异常列表是签约任务专用只读接口且同时要求列表和重试权限")
    void shouldExposeScopedNotificationFailuresEndpoint() throws Exception
    {
        Method method = OaSignTaskController.class.getMethod(
                "notificationFailures", HttpServletRequest.class);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("oa:signTask:list", "oa:signTask:retry");
        assertThat(annotation.logical()).isEqualTo(Logical.AND);
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class))
                .isNotNull();
    }

    @Test
    @DisplayName("批量硬删除只开放独立管理员权限并要求不可恢复确认")
    void shouldProtectAndValidateBatchHardDelete() throws Exception
    {
        Method method = OaSignTaskController.class.getMethod("batchDelete",
                OaSignTaskBatchDeleteRequest.class, HttpServletRequest.class);
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signTask:delete");
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/batch/delete");
        assertThat(method.getAnnotation(
                com.erp.common.security.annotation.IdempotentSubmit.class).releaseOnSuccess())
                .as("快速并发保护成功后必须释放，同一requestId由持久台账重放")
                .isTrue();

        try (var factory = Validation.buildDefaultValidatorFactory())
        {
            Set<ConstraintViolation<OaSignTaskBatchDeleteRequest>> violations =
                    factory.getValidator().validate(new OaSignTaskBatchDeleteRequest());
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .contains("requestId", "items", "irreversibleConfirmed");

            OaSignTaskBatchDeleteRequest versionBound = new OaSignTaskBatchDeleteRequest();
            versionBound.setRequestId("hard-delete-validation");
            versionBound.setIrreversibleConfirmed(true);
            versionBound.setItems(java.util.List.of(new OaSignTaskBatchDeleteAction()));
            assertThat(factory.getValidator().validate(versionBound))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("items[0].taskId", "items[0].expectedVersion");
        }
    }

    @Test
    @DisplayName("批量选公司盖章的预览与执行共用独立权限")
    void shouldProtectBatchFinalizeEndpointsWithDedicatedPermission() throws Exception
    {
        Method preview = OaSignTaskController.class.getMethod("batchFinalizePreview",
                OaSignTaskBatchFinalizePreviewRequest.class, HttpServletRequest.class);
        Method execute = OaSignTaskController.class.getMethod("batchFinalize",
                OaSignTaskBatchFinalizeRequest.class, HttpServletRequest.class);

        assertThat(preview.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signTask:batchFinalize");
        assertThat(execute.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("oa:signTask:batchFinalize");
        assertThat(preview.getAnnotation(PostMapping.class).value())
                .containsExactly("/batch/finalize/preview");
        assertThat(execute.getAnnotation(PostMapping.class).value())
                .containsExactly("/batch/finalize");

        try (var factory = Validation.buildDefaultValidatorFactory())
        {
            assertThat(factory.getValidator().validate(
                    new OaSignTaskBatchFinalizePreviewRequest()))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("taskIds");
            assertThat(factory.getValidator().validate(new OaSignTaskBatchFinalizeRequest()))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("requestId", "items");
        }
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes) throws Exception
    {
        Method method = OaSignTaskController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(RequiresPermissions.class)).isNotNull();
        assertThat(method.getAnnotation(RequiresPermissions.class).value()).containsExactly(permission);
    }

    private void assertReadPermission(String methodName, String businessPermission,
            Class<?>... parameterTypes) throws Exception
    {
        Method method = OaSignTaskController.class.getMethod(methodName, parameterTypes);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(businessPermission, "oa:signTask:technicalEvidence");
        assertThat(annotation.logical()).isEqualTo(Logical.OR);
    }
}
