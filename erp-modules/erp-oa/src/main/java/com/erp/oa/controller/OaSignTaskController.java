package com.erp.oa.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.core.utils.file.FileUtils;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignTaskBatchFinalizePreviewRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchFinalizeRequest;
import com.erp.oa.domain.dto.OaSignTaskNotificationRetryRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchSendRequest;
import com.erp.oa.domain.dto.OaSignTaskRetryRequest;
import com.erp.oa.domain.dto.OaSignExceptionResolutionRequest;
import com.erp.oa.service.IOaSignTaskService;
import com.erp.oa.service.impl.OaSignTaskBatchFinalizeService;
import com.erp.oa.service.impl.OaSignTaskBatchSendService;
import com.erp.oa.service.impl.OaSignTaskExportService;
import com.erp.oa.service.impl.OaSignNotificationFailureQueryService;

@RestController
@RequestMapping("/signTask")
public class OaSignTaskController extends OaBaseController
{
    @Autowired
    private IOaSignTaskService signTaskService;

    @Autowired
    private OaSignTaskBatchSendService batchSendService;

    @Autowired
    private OaSignTaskBatchFinalizeService batchFinalizeService;

    @Autowired
    private OaSignNotificationFailureQueryService notificationFailureQueryService;

    @Autowired
    private OaSignTaskExportService signTaskExportService;

    @Value("${oa.sign.excel-import.enabled:true}")
    private boolean excelImportEnabled;

    @RequiresLogin
    @GetMapping("/capabilities")
    public AjaxResult capabilities()
    {
        return success(Map.of("coreEnabled", true, "excelImportEnabled", excelImportEnabled));
    }

    @RequiresPermissions("oa:signTask:batchFinalize")
    @PostMapping("/batch/finalize/preview")
    public AjaxResult batchFinalizePreview(
            @Validated @RequestBody OaSignTaskBatchFinalizePreviewRequest action,
            HttpServletRequest request)
    {
        return success(batchFinalizeService.preview(action, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:batchFinalize")
    @Log(title = "批量选择合同公司并盖章", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/batch/finalize")
    public AjaxResult batchFinalize(
            @Validated @RequestBody OaSignTaskBatchFinalizeRequest action,
            HttpServletRequest request)
    {
        return success(batchFinalizeService.execute(action, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:send")
    @Log(title = "签约任务批量发送", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/batch/send")
    public AjaxResult batchSend(@Validated @RequestBody OaSignTaskBatchSendRequest action,
            HttpServletRequest request)
    {
        return success(batchSendService.batchSend(action, resolveSignScopeDeptId(request),
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    @RequiresPermissions("oa:signTask:delete")
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @Log(title = "管理员批量删除未完成签约任务", businessType = BusinessType.DELETE,
            includeParamNames = { "requestId", "items" })
    @PostMapping("/batch/delete")
    public AjaxResult batchDelete(@Validated @RequestBody OaSignTaskBatchDeleteRequest action,
            HttpServletRequest request)
    {
        return success(signTaskService.hardDeleteUnfinishedTasks(
                action, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions(value = { "oa:signTask:list", "oa:signTask:technicalEvidence" }, logical = Logical.OR)
    @GetMapping("/list")
    public TableDataInfo list(OaSignTask task, HttpServletRequest request)
    {
        startPage();
        List<OaSignTask> tasks = signTaskService.selectTaskList(task, resolveSignScopeDeptId(request));
        return getDataTable(tasks);
    }

    @RequiresPermissions(value = { "oa:signTask:list", "oa:signTask:technicalEvidence" },
            logical = Logical.OR)
    @Log(title = "签约数据导出", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/export")
    public void export(HttpServletResponse response, OaSignTask task,
            HttpServletRequest request) throws IOException
    {
        byte[] workbook = signTaskExportService.export(task, resolveSignScopeDeptId(request));
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setContentLengthLong(workbook.length);
        FileUtils.setAttachmentResponseHeader(response, "签约数据.xlsx");
        response.getOutputStream().write(workbook);
    }

    @RequiresPermissions("oa:signTask:list")
    @GetMapping("/metrics")
    public AjaxResult metrics(HttpServletRequest request)
    {
        return success(signTaskService.getTaskMetrics(resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions(value = { "oa:signTask:list", "oa:signTask:retry" })
    @GetMapping("/notification/failures")
    public AjaxResult notificationFailures(HttpServletRequest request)
    {
        return success(notificationFailureQueryService.list(resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions(value = { "oa:signTask:query", "oa:signTask:technicalEvidence" }, logical = Logical.OR)
    @GetMapping("/{taskId}")
    public AjaxResult detail(@PathVariable("taskId") Long taskId, HttpServletRequest request)
    {
        return success(signTaskService.getTaskDetail(taskId, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:revalidate")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "签约任务重新校验", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/revalidate")
    public AjaxResult revalidate(@PathVariable("taskId") Long taskId,
            @Validated @RequestBody OaSignTaskRetryRequest action,
            HttpServletRequest request)
    {
        return success(signTaskService.revalidate(taskId, action, resolveSignScopeDeptId(request),
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    @RequiresPermissions("oa:signTask:send")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "签约任务发送", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/send")
    public AjaxResult send(@PathVariable("taskId") Long taskId,
            @Validated @RequestBody OaSignTaskRetryRequest action,
            HttpServletRequest request)
    {
        return success(signTaskService.send(taskId, action, resolveSignScopeDeptId(request),
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    @RequiresPermissions("oa:signTask:retry")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "签约任务重试", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/retry")
    public AjaxResult retry(@PathVariable("taskId") Long taskId,
            @Validated @RequestBody OaSignTaskRetryRequest action,
            HttpServletRequest request)
    {
        return success(signTaskService.retry(taskId, action, resolveSignScopeDeptId(request),
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    @RequiresPermissions("oa:signTask:retry")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "签约通知重新入队", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/notification/retry")
    public AjaxResult retryNotification(@PathVariable("taskId") Long taskId,
            @Validated @RequestBody OaSignTaskNotificationRetryRequest action,
            HttpServletRequest request)
    {
        return success(signTaskService.retryNotification(taskId, action, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signTask:cancel")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "签约任务取消", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/cancel")
    public AjaxResult cancel(@PathVariable("taskId") Long taskId,
            @Validated @RequestBody OaSignTaskRetryRequest action,
            HttpServletRequest request)
    {
        return success(signTaskService.cancel(taskId, action, resolveSignScopeDeptId(request),
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    @RequiresPermissions(value = { "oa:signTask:resolveRefusal", "oa:signTask:resolveExpiry" },
            logical = Logical.OR)
    @Log(title = "签约异常处置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{taskId}/resolve")
    public AjaxResult resolveException(@PathVariable("taskId") Long taskId,
            @Validated @RequestBody OaSignExceptionResolutionRequest action,
            HttpServletRequest request)
    {
        return success(signTaskService.resolveException(taskId, action, resolveSignScopeDeptId(request),
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    private String resolveClientIp(HttpServletRequest request)
    {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank())
        {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex > -1 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }
}
