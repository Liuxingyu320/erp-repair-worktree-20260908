package com.erp.oa.controller;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.PageUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.dto.OaSignBatchCreateDraftsRequest;
import com.erp.oa.domain.dto.OaSignPlanPublishRequest;
import com.erp.oa.domain.dto.OaSignBatchPreviewRequest;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;
import com.erp.oa.domain.dto.OaSignPackageRefuseRequest;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentReadRequest;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.domain.vo.OaSignTemplateFile;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.domain.vo.OaSignPlanVersionPublishResult;
import com.erp.oa.service.IOaSignBatchService;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignPlanService;
import com.erp.oa.service.IOaSignPlanVersionService;
import com.erp.oa.service.IOaSignTemplateService;
import com.erp.oa.service.impl.OaSignHrAccessService;
import com.erp.oa.service.impl.OaSignPackageLifecycleService;
import com.erp.oa.service.impl.OaSignPdfPreviewService;
import com.erp.oa.service.impl.OaSignResponseSanitizer;
import com.erp.oa.service.impl.OaSignVerificationService;
import com.erp.oa.service.impl.OaSignCompanyService;
import com.erp.oa.service.impl.OaSignScopeService;
import com.erp.oa.service.impl.OaSignTemplateFileService;

@RestController
@RequestMapping("/signPackage")
public class OaSignPackageController extends OaBaseController
{
    private static final String EMERGENCY_REASON_PREFIX = "非生命周期任务来源：";

    @Value("${oa.sign.emergency-create.enabled:false}")
    private boolean emergencyCreateEnabled;

    @Autowired
    private IOaSignPackageService signPackageService;

    @Autowired
    private IOaSignBatchService signBatchService;

    @Autowired
    private IOaSignPlanService signPlanService;

    @Autowired
    private IOaSignPlanVersionService signPlanVersionService;

    @Autowired
    private IOaSignTemplateService signTemplateService;

    @Autowired
    private OaSignTemplateFileService signTemplateFileService;

    @Autowired
    private OaSignVerificationService signVerificationService;

    @Autowired
    private OaSignHrAccessService signHrAccessService;

    @Autowired
    private OaSignCompanyService signCompanyService;

    @Autowired
    private OaSignPackageLifecycleService signPackageLifecycleService;

    @Autowired
    private OaSignPdfPreviewService signPdfPreviewService;

    @Autowired
    private OaSignScopeService signScopeService;

    @RequiresPermissions(value = { "oa:signPackage:list", "oa:signTask:list", "oa:signTask:send" },
            logical = Logical.OR)
    @GetMapping("/scope/options")
    public AjaxResult signScopeOptions()
    {
        signHrAccessService.requireCurrentHr();
        return success(signScopeService.listAvailableScopes());
    }

    @RequiresPermissions("oa:signPackage:list")
    @GetMapping("/template/types")
    public AjaxResult templateTypes()
    {
        signHrAccessService.requireCurrentHr();
        PageUtils.clearPage();
        return success(signTemplateService.listTemplateTypes());
    }

    @RequiresPermissions("oa:signPackage:template")
    @GetMapping("/template/list")
    public AjaxResult templateList(OaSignTemplate template)
    {
        signHrAccessService.requireCurrentHr();
        return success(OaSignResponseSanitizer.businessTemplates(
                signTemplateService.selectTemplateList(template)));
    }

    @RequiresPermissions("oa:signPackage:template")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约模板", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/template")
    public AjaxResult saveTemplate(@Validated @RequestBody OaSignTemplate template)
    {
        signHrAccessService.requireCurrentHr();
        return success(OaSignResponseSanitizer.businessTemplate(
                signTemplateService.saveTemplate(template)));
    }

    @RequiresPermissions("oa:signPackage:template")
    @GetMapping("/template/{templateId}/preview")
    public ResponseEntity<byte[]> templatePreview(@PathVariable("templateId") Long templateId)
    {
        signHrAccessService.requireCurrentHr();
        return streamTemplateFile(signTemplateFileService.preview(templateId), true);
    }

    @RequiresPermissions("oa:signPackage:template")
    @GetMapping("/template/{templateId}/file")
    public ResponseEntity<byte[]> templateFile(@PathVariable("templateId") Long templateId)
    {
        signHrAccessService.requireCurrentHr();
        return streamTemplateFile(signTemplateFileService.download(templateId), false);
    }

    @RequiresPermissions("oa:signPackage:list")
    @GetMapping("/plan/list")
    public AjaxResult planList(OaSignPlan plan, HttpServletRequest request)
    {
        signHrAccessService.requireCurrentHr();
        PageUtils.clearPage();
        return success(OaSignResponseSanitizer.businessPlans(
                signPlanService.selectPlanList(plan, null)));
    }

    @RequiresPermissions("oa:signPackage:list")
    @GetMapping("/plan/{planId}")
    public AjaxResult planDetail(@PathVariable("planId") Long planId, HttpServletRequest request)
    {
        signHrAccessService.requireCurrentHr();
        return success(OaSignResponseSanitizer.businessPlan(
                signPlanService.getPlanDetail(planId, null)));
    }

    @RequiresPermissions("oa:signPackage:template")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约方案", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/plan")
    public AjaxResult savePlan(@Validated @RequestBody OaSignPlan plan, HttpServletRequest request)
    {
        signHrAccessService.requireCurrentHr();
        return success(OaSignResponseSanitizer.businessPlan(
                signPlanService.savePlan(plan, null)));
    }

    @RequiresPermissions("oa:signPackage:template")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约方案", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/plan")
    public AjaxResult updatePlan(@Validated @RequestBody OaSignPlan plan, HttpServletRequest request)
    {
        signHrAccessService.requireCurrentHr();
        return success(OaSignResponseSanitizer.businessPlan(
                signPlanService.savePlan(plan, null)));
    }

    @RequiresPermissions("oa:signPackage:template")
    @GetMapping("/plan/{planId}/publish-preview")
    public AjaxResult previewPublishPlan(@PathVariable("planId") Long planId)
    {
        signHrAccessService.requireCurrentHr();
        return success(signPlanVersionService.previewPublish(planId, null));
    }

    @RequiresPermissions("oa:signPackage:template")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约方案发布", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/plan/{planId}/publish")
    public AjaxResult publishPlan(@PathVariable("planId") Long planId,
            @Validated @RequestBody(required = false) OaSignPlanPublishRequest confirmation,
            HttpServletRequest request)
    {
        signHrAccessService.requireCurrentHr();
        return success(confirmation == null
                ? OaSignPlanVersionPublishResult.from(signPlanVersionService.publish(planId, null))
                : signPlanVersionService.confirmPublish(planId, confirmation, null));
    }

    @RequiresPermissions("oa:signPackage:list")
    @GetMapping("/list")
    public TableDataInfo list(OaSignPackage signPackage, HttpServletRequest request)
    {
        startPage();
        List<OaSignPackage> list = signPackageService.selectPackageList(signPackage, resolveSignScopeDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("oa:signPackage:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约包", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult create(@Validated @RequestBody OaSignPackage signPackage, HttpServletRequest request)
    {
        if (signPackage == null)
        {
            throw new ServiceException("签约包信息不能为空");
        }
        if (signPackage.getTaskId() == null)
        {
            requireEmergencyCreateEnabled();
            signPackage.setRemark(normalizeEmergencyReason(signPackage.getRemark()));
        }
        return success(signPackageService.createPackage(signPackage, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signPackage:add")
    @PostMapping("/batch/preview")
    public AjaxResult batchPreview(@RequestBody OaSignBatchPreviewRequest request, HttpServletRequest servletRequest)
    {
        requireEmergencyCreateEnabled();
        signHrAccessService.requireCurrentHr();
        return success(signBatchService.preview(request, resolveSignScopeDeptId(servletRequest)));
    }

    @RequiresPermissions("oa:signPackage:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约包批量生成", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/batch/createDrafts")
    public AjaxResult batchCreateDrafts(@RequestBody OaSignBatchCreateDraftsRequest request,
            HttpServletRequest servletRequest)
    {
        requireEmergencyCreateEnabled();
        if (request == null)
        {
            throw new ServiceException("批量应急建包请求不能为空");
        }
        request.setEmergencyReason(normalizeEmergencyReasonText(request.getEmergencyReason()));
        signHrAccessService.requireCurrentHr();
        return success(signBatchService.createDrafts(request, resolveSignScopeDeptId(servletRequest)));
    }

    private void requireEmergencyCreateEnabled()
    {
        if (!emergencyCreateEnabled)
        {
            throw new ServiceException("应急建包入口未启用，请从人事生命周期任务发起签约");
        }
    }

    private String normalizeEmergencyReason(String reason)
    {
        String normalized = normalizeEmergencyReasonText(reason);
        return EMERGENCY_REASON_PREFIX + normalized;
    }

    private String normalizeEmergencyReasonText(String reason)
    {
        String normalized = StringUtils.trim(reason);
        if (StringUtils.isBlank(normalized))
        {
            throw new ServiceException("应急建包原因不能为空");
        }
        if (normalized.startsWith(EMERGENCY_REASON_PREFIX))
        {
            normalized = normalized.substring(EMERGENCY_REASON_PREFIX.length()).trim();
        }
        if (StringUtils.isBlank(normalized) || normalized.length() > 450)
        {
            throw new ServiceException("应急建包原因长度必须在1至450个字符之间");
        }
        return normalized;
    }

    @RequiresPermissions("oa:signPackage:query")
    @GetMapping("/{packageId}")
    public AjaxResult detail(@PathVariable("packageId") Long packageId, HttpServletRequest request)
    {
        return success(signPackageService.getPackageDetail(packageId, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions(value = { "oa:signPackage:query", "oa:signTask:technicalEvidence" }, logical = Logical.OR)
    @GetMapping("/{packageId}/verify")
    public AjaxResult verify(@PathVariable("packageId") Long packageId,
            @RequestParam(defaultValue = "false") boolean includeTechnical, HttpServletRequest request)
    {
        OaSignPackage signPackage = signPackageService.getPackageForVerification(packageId, resolveSignScopeDeptId(request));
        boolean technicalMode = includeTechnical && !signHrAccessService.isCurrentHr();
        if (technicalMode)
        {
            AuthUtil.checkPermi("oa:signTask:technicalEvidence");
        }
        return success(signVerificationService.verify(signPackage, technicalMode));
    }

    @RequiresPermissions("oa:signPackage:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约包编辑", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{packageId}")
    public AjaxResult update(@PathVariable("packageId") Long packageId,
            @Validated @RequestBody OaSignPackage signPackage, HttpServletRequest request)
    {
        return success(signPackageService.updateDraftPackage(packageId, signPackage, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signPackage:send")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约包发送", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{packageId}/send")
    public AjaxResult send(@PathVariable("packageId") Long packageId, HttpServletRequest request)
    {
        return success(signPackageService.sendPackage(packageId, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signPackage:send")
    @GetMapping("/{packageId}/company-options")
    public AjaxResult companyOptions(@PathVariable("packageId") Long packageId,
            @RequestParam(required = false) Long legalEntityId, HttpServletRequest request)
    {
        OaSignCompanyOptions options = signPackageService.getCompanyOptions(
                packageId, resolveSignScopeDeptId(request));
        if (legalEntityId != null)
        {
            OaSignCompanyService.SealRecommendation recommendation =
                    signCompanyService.recommendContractSeal(legalEntityId);
            options.setSeals(recommendation.getCandidates());
            options.setRecommendedSealId(recommendation.getSelectedSeal() == null ? null
                    : recommendation.getSelectedSeal().getSealId());
        }
        return success(options);
    }

    @RequiresPermissions("oa:signPackage:send")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "补充合同公司与印章", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{packageId}/finalize")
    public AjaxResult finalizePackage(@PathVariable("packageId") Long packageId,
            @Validated @RequestBody OaSignPackageFinalizeRequest finalizeRequest,
            HttpServletRequest request)
    {
        return success(signPackageService.finalizePackage(
                packageId, finalizeRequest, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signSeal:list")
    @GetMapping("/company/seals")
    public AjaxResult companySeals(@RequestParam Long legalEntityId,
            @RequestParam(defaultValue = "false") boolean activeOnly)
    {
        signHrAccessService.requireCurrentHr();
        return success(signCompanyService.listSeals(legalEntityId, activeOnly));
    }

    @RequiresPermissions("oa:signSeal:edit")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "公司合同印章", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/company/seals")
    public AjaxResult saveCompanySeal(@Validated @RequestBody OaCompanySealConfig seal)
    {
        signHrAccessService.requireCurrentHr();
        return success(signCompanyService.saveSeal(seal));
    }

    @RequiresPermissions("oa:signPackage:void")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "员工签约包撤回", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{packageId}/void")
    public AjaxResult voidPackage(@PathVariable("packageId") Long packageId, @RequestBody OaSignPackage signPackage,
            HttpServletRequest request)
    {
        return success(signPackageService.voidPackage(packageId, resolveSignScopeDeptId(request), signPackage.getVoidReason()));
    }

    @RequiresPermissions("oa:signPackage:add")
    @PostMapping("/template/match")
    public AjaxResult matchTemplates(@RequestBody OaSignPackage signPackage)
    {
        signHrAccessService.requireCurrentHr();
        return success(OaSignResponseSanitizer.businessTemplates(
                signTemplateService.matchTemplates(signPackage)));
    }

    @RequiresLogin
    @GetMapping("/mobile/list")
    public TableDataInfo mobileList(OaSignPackage signPackage)
    {
        startPage();
        List<OaSignPackage> list = signPackageService.selectMyPackages(signPackage);
        return getDataTable(list);
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}")
    public AjaxResult mobileDetail(@PathVariable("packageId") Long packageId)
    {
        return success(signPackageService.getMyPackageDetail(packageId));
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}/documents/{documentId}/file")
    public ResponseEntity<Resource> mobileDocumentFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId)
    {
        return streamFile(signPackageService.resolveMyDocumentFile(packageId, documentId));
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}/documents/{documentId}/signed-file")
    public ResponseEntity<Resource> mobileSignedDocumentFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId)
    {
        return streamFile(signPackageService.resolveMySignedDocumentFile(packageId, documentId));
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}/documents/{documentId}/final-file")
    public ResponseEntity<Resource> mobileFinalDocumentFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId)
    {
        return streamFile(signPackageService.resolveMyFinalDocumentFile(packageId, documentId));
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}/documents/{documentId}/final-file/export")
    public ResponseEntity<Resource> mobileFinalDocumentExport(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId)
    {
        return streamFile(signPackageService.resolveMyFinalDocumentExportFile(packageId, documentId),
                true);
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}/documents/{documentId}/certificate")
    public ResponseEntity<Resource> mobileCertificateFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId)
    {
        return streamFile(signPackageService.resolveMyCertificateFile(packageId, documentId));
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}/documents/{documentId}/preview")
    public ResponseEntity<AjaxResult> mobileDocumentPreview(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId, @RequestParam("kind") String kind)
    {
        OaSignPackageFile file = resolveMyPreviewFile(packageId, documentId, kind);
        HttpHeaders headers = previewHeaders();
        return ResponseEntity.ok()
                .headers(headers)
                .body(success(Map.of("pageCount", signPdfPreviewService.pageCount(file))));
    }

    @RequiresLogin
    @GetMapping("/mobile/{packageId}/documents/{documentId}/preview/{pageNumber}")
    public ResponseEntity<byte[]> mobileDocumentPreviewPage(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId, @PathVariable("pageNumber") int pageNumber,
            @RequestParam("kind") String kind)
    {
        OaSignPackageFile file = resolveMyPreviewFile(packageId, documentId, kind);
        byte[] content = signPdfPreviewService.renderPage(file, pageNumber);
        HttpHeaders headers = previewHeaders();
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("签约文件-第" + pageNumber + "页.png", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(content.length)
                .contentType(MediaType.IMAGE_PNG)
                .body(content);
    }

    @RequiresPermissions("oa:signPackage:query")
    @GetMapping("/{packageId}/documents/{documentId}/file")
    public ResponseEntity<Resource> documentFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId, HttpServletRequest request)
    {
        return streamFile(signPackageService.resolveScopedDocumentFile(packageId, documentId, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signPackage:query")
    @GetMapping("/{packageId}/documents/{documentId}/signed-file")
    public ResponseEntity<Resource> signedDocumentFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId, HttpServletRequest request)
    {
        return streamFile(signPackageService.resolveScopedSignedDocumentFile(
                packageId, documentId, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signPackage:query")
    @GetMapping("/{packageId}/documents/{documentId}/final-file")
    public ResponseEntity<Resource> finalDocumentFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId, HttpServletRequest request)
    {
        return streamFile(signPackageService.resolveScopedFinalDocumentFile(
                packageId, documentId, resolveSignScopeDeptId(request)));
    }

    @RequiresPermissions("oa:signPackage:query")
    @GetMapping("/{packageId}/documents/{documentId}/final-file/export")
    public ResponseEntity<Resource> finalDocumentExport(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId, HttpServletRequest request)
    {
        return streamFile(signPackageService.resolveScopedFinalDocumentExportFile(
                packageId, documentId, resolveSignScopeDeptId(request)), true);
    }

    @RequiresPermissions("oa:signPackage:query")
    @GetMapping("/{packageId}/documents/{documentId}/certificate")
    public ResponseEntity<Resource> certificateFile(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId, HttpServletRequest request)
    {
        return streamFile(signPackageService.resolveScopedCertificateFile(packageId, documentId, resolveSignScopeDeptId(request)));
    }

    @RequiresLogin
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @PostMapping("/mobile/{packageId}/read/{documentId}")
    public AjaxResult mobileRead(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId,
            @Validated @RequestBody OaSignDocumentReadRequest readRequest)
    {
        return success(signPackageService.confirmDocumentRead(packageId, documentId, readRequest));
    }

    @RequiresLogin
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @Log(title = "员工签约包签署", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/mobile/{packageId}/sign")
    public AjaxResult mobileSign(@PathVariable("packageId") Long packageId,
            @Validated @RequestBody OaSignPackageSignRequest signRequest, HttpServletRequest request)
    {
        return success(signPackageService.signPackage(packageId, signRequest,
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    @RequiresLogin
    @Log(title = "员工拒签签约包", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/mobile/{packageId}/refuse")
    public AjaxResult mobileRefuse(@PathVariable("packageId") Long packageId,
            @Validated @RequestBody OaSignPackageRefuseRequest refuseRequest,
            HttpServletRequest request)
    {
        signPackageLifecycleService.refuse(packageId, refuseRequest, SecurityUtils.getUserId(),
                resolveClientIp(request), request.getHeader("User-Agent"));
        return success(signPackageService.getMyPackageDetail(packageId));
    }

    @RequiresLogin
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @Log(title = "员工确认最终合同", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/mobile/{packageId}/final-confirm")
    public AjaxResult mobileFinalConfirm(@PathVariable("packageId") Long packageId,
            @Validated @RequestBody OaSignFinalConfirmRequest confirmRequest,
            HttpServletRequest request)
    {
        return success(signPackageService.confirmFinalPackage(packageId, confirmRequest,
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

    @RequiresLogin
    @IdempotentSubmit(timeout = 30, releaseOnSuccess = true)
    @PostMapping("/mobile/{packageId}/final-read/{documentId}")
    public AjaxResult mobileFinalRead(@PathVariable("packageId") Long packageId,
            @PathVariable("documentId") Long documentId,
            @Validated @RequestBody OaSignFinalDocumentReadRequest readRequest,
            HttpServletRequest request)
    {
        return success(signPackageService.confirmFinalDocumentRead(packageId, documentId,
                readRequest, resolveClientIp(request), request.getHeader("User-Agent")));
    }

    private ResponseEntity<Resource> streamFile(OaSignPackageFile file)
    {
        return streamFile(file, false);
    }

    private ResponseEntity<Resource> streamFile(OaSignPackageFile file, boolean attachment)
    {
        HttpHeaders headers = new HttpHeaders();
        ContentDisposition.Builder disposition = attachment
                ? ContentDisposition.attachment() : ContentDisposition.inline();
        headers.setContentDisposition(disposition
                .filename(file.getFileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl("no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        Resource resource;
        try
        {
            if (Files.isRegularFile(file.getPath()))
            {
                headers.setContentLength(Files.size(file.getPath()));
            }
            if (file.isDeleteAfterStreaming())
            {
                InputStream input = Files.newInputStream(file.getPath());
                resource = new InputStreamResource(new FilterInputStream(input)
                {
                    @Override
                    public void close() throws IOException
                    {
                        try
                        {
                            super.close();
                        }
                        finally
                        {
                            Files.deleteIfExists(file.getPath());
                        }
                    }
                });
            }
            else
            {
                resource = new FileSystemResource(file.getPath());
            }
        }
        catch (IOException e)
        {
            if (file.isDeleteAfterStreaming())
            {
                try
                {
                    Files.deleteIfExists(file.getPath());
                }
                catch (IOException ignored)
                {
                    // Keep the original stream failure as the response error.
                }
            }
            throw new ServiceException("读取签约文件失败").setDetailMessage(e.getMessage());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .body(resource);
    }

    private OaSignPackageFile resolveMyPreviewFile(Long packageId, Long documentId, String kind)
    {
        if ("review".equals(kind))
        {
            return signPackageService.resolveMyDocumentPreviewFile(packageId, documentId);
        }
        if ("final".equals(kind))
        {
            return signPackageService.resolveMyFinalDocumentPreviewFile(packageId, documentId);
        }
        throw new ServiceException("预览文件类型仅支持 review 或 final");
    }

    private HttpHeaders previewHeaders()
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }

    private ResponseEntity<byte[]> streamTemplateFile(OaSignTemplateFile file, boolean inline)
    {
        byte[] content = file.getContent();
        HttpHeaders headers = new HttpHeaders();
        ContentDisposition.Builder disposition = inline
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        headers.setContentDisposition(disposition
                .filename(file.getFileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl("no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(content.length)
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .body(content);
    }

    private String resolveClientIp(HttpServletRequest request)
    {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && forwardedFor.length() > 0)
        {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex > -1 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }
}
