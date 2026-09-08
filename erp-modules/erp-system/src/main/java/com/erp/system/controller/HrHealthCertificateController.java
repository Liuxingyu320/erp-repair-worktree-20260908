package com.erp.system.controller;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.HrHealthCertificate;
import com.erp.system.domain.dto.HrHealthCertificateReviewRequest;
import com.erp.system.domain.dto.HrHealthCertificateSubmitRequest;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.api.RemoteFileService;
import com.erp.system.service.IHrHealthCertificateService;
import com.erp.system.service.impl.HrHealthCertificateFeatureService;
import feign.Response;

@RestController
@RequestMapping("/hr/health-certificate")
public class HrHealthCertificateController extends BaseController
{
    private final IHrHealthCertificateService service;
    private final RemoteFileService remoteFileService;
    private final HrHealthCertificateFeatureService featureService;

    public HrHealthCertificateController(IHrHealthCertificateService service,
            RemoteFileService remoteFileService,
            HrHealthCertificateFeatureService featureService)
    {
        this.service = service;
        this.remoteFileService = remoteFileService;
        this.featureService = featureService;
    }

    @RequiresLogin
    @GetMapping("/capability")
    public AjaxResult capability()
    {
        return success(featureService.capability());
    }

    @RequiresLogin
    @GetMapping("/me")
    public AjaxResult me()
    {
        return success(service.selectMine(SecurityUtils.getUserId()));
    }

    @RequiresPermissions("hr:healthCertificate:self:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "保存本人健康证草稿", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/me/draft")
    public AjaxResult saveMyDraft(@RequestBody HrHealthCertificate certificate)
    {
        return success(service.saveMyDraft(SecurityUtils.getUserId(), certificate,
                SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:healthCertificate:self:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "提交本人健康证审核", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/me/submit")
    public AjaxResult submitMine(
            @RequestBody HrHealthCertificateSubmitRequest request)
    {
        return success(service.submitMine(SecurityUtils.getUserId(), request,
                SecurityUtils.getUsername()));
    }

    @RequiresPermissions("hr:healthCertificate:self:submit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "撤回本人健康证审批", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/me/{certificateId}/withdraw")
    public AjaxResult withdrawMine(@PathVariable Long certificateId)
    {
        return success(service.withdrawMine(SecurityUtils.getUserId(),
                certificateId, SecurityUtils.getUsername()));
    }

    @RequiresPermissions(value = { "hr:healthCertificate:list",
            "hr:healthCertificate:review" }, logical = Logical.OR)
    @GetMapping("/list")
    public TableDataInfo list(HrHealthCertificateVo query)
    {
        startPage();
        List<HrHealthCertificateVo> rows = service.selectList(query);
        return getDataTable(rows);
    }

    @RequiresPermissions("hr:healthCertificate:list")
    @GetMapping("/ops-summary")
    public AjaxResult opsSummary(HrHealthCertificateVo query)
    {
        return success(service.selectOpsSummary(query));
    }

    @RequiresPermissions("hr:healthCertificate:query")
    @GetMapping("/employee/{userId}")
    public AjaxResult employee(@PathVariable Long userId)
    {
        return success(service.selectEmployee(userId));
    }

    @RequiresLogin
    @GetMapping("/{certificateId}/attachment")
    public ResponseEntity<StreamingResponseBody> attachment(
            @PathVariable Long certificateId,
            @RequestParam(defaultValue = "preview") String mode)
    {
        Long nodeId = service.resolveAttachmentNode(certificateId,
                SecurityUtils.getUserId());
        return stream(remoteFileService.readDriveBusinessContent(nodeId, mode,
                SecurityConstants.INNER));
    }

    @RequiresPermissions("hr:healthCertificate:review")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "审核员工健康证", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{certificateId}/review")
    public AjaxResult review(@PathVariable Long certificateId,
            @RequestBody HrHealthCertificateReviewRequest request)
    {
        return success(service.review(certificateId, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    private ResponseEntity<StreamingResponseBody> stream(Response remote)
    {
        if (remote == null || remote.status() != 200 || remote.body() == null)
        {
            closeQuietly(remote);
            throw new ServiceException("健康证附件暂时不可用");
        }
        HttpHeaders headers = safeHeaders(remote.headers());
        StreamingResponseBody body = output -> {
            try (InputStream input = remote.body().asInputStream())
            {
                input.transferTo(output);
            }
            finally
            {
                closeQuietly(remote);
            }
        };
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private HttpHeaders safeHeaders(Map<String, Collection<String>> source)
    {
        HttpHeaders headers = new HttpHeaders();
        String contentType = firstHeader(source, HttpHeaders.CONTENT_TYPE);
        try
        {
            headers.setContentType(contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType));
        }
        catch (IllegalArgumentException ex)
        {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        String disposition = firstHeader(source,
                HttpHeaders.CONTENT_DISPOSITION);
        if (disposition != null && !disposition.contains("\r")
                && !disposition.contains("\n"))
        {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
        }
        String length = firstHeader(source, HttpHeaders.CONTENT_LENGTH);
        try
        {
            if (length != null && Long.parseLong(length) >= 0)
            {
                headers.setContentLength(Long.parseLong(length));
            }
        }
        catch (NumberFormatException ignored)
        {
            // Unknown length is safe for chunked streaming.
        }
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }

    private String firstHeader(Map<String, Collection<String>> headers,
            String name)
    {
        if (headers == null)
        {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).filter(value -> value != null)
                .flatMap(Collection::stream).findFirst().orElse(null);
    }

    private void closeQuietly(Response response)
    {
        if (response == null)
        {
            return;
        }
        try
        {
            response.close();
        }
        catch (RuntimeException ignored)
        {
            // The client-safe attachment error remains primary.
        }
    }
}
