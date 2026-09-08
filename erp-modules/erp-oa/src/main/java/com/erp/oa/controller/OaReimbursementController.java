package com.erp.oa.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.dto.OaInvoiceRecognitionRequest;
import com.erp.oa.domain.dto.OaInvoiceRecognitionUpdateRequest;
import com.erp.oa.domain.dto.OaReimbursementExportRequest;
import com.erp.oa.domain.dto.OaReimbursementWithdrawRequest;
import com.erp.oa.service.IOaReimbursementService;
import com.erp.oa.service.OaReimbursementExportService;

@RestController
@RequestMapping("/reimbursement")
public class OaReimbursementController extends OaBaseController
{
    private static final String SELF = "oa:reimbursement:self";
    private static final String APPROVE = "oa:reimbursement:approve";
    private static final String FINANCE_APPROVE =
            "oa:reimbursement:finance:approve";
    private static final String FINANCE_LIST =
            "oa:reimbursement:finance:list";
    private static final String FINANCE_EXPORT =
            "oa:reimbursement:finance:export";

    private final IOaReimbursementService reimbursementService;
    private final OaReimbursementExportService exportService;

    public OaReimbursementController(
            IOaReimbursementService reimbursementService,
            OaReimbursementExportService exportService)
    {
        this.reimbursementService = reimbursementService;
        this.exportService = exportService;
    }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "报销申请草稿", businessType = BusinessType.INSERT)
    @PostMapping("/save")
    public AjaxResult save(@RequestBody OaReimbursement value,
            HttpServletRequest request)
    {
        return success(reimbursementService.saveDraft(value,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "提交报销申请", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public AjaxResult submit(@Validated @RequestBody OaReimbursement value,
            HttpServletRequest request)
    {
        return success(reimbursementService.submit(value,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @IdempotentSubmit(timeout = 30)
    @Log(title = "撤回报销申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{reimbursementId}/withdraw")
    public AjaxResult withdraw(
            @PathVariable("reimbursementId") Long reimbursementId,
            @Validated @RequestBody(required = false)
                    OaReimbursementWithdrawRequest body,
            HttpServletRequest request)
    {
        return success(reimbursementService.withdraw(reimbursementId,
                body == null ? null : body.getReason(),
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @GetMapping("/availability")
    public AjaxResult availability()
    {
        return success(Map.of(
                "enabled", reimbursementService.isSubmissionEnabled(),
                "recognition",
                        reimbursementService.recognitionAvailability()));
    }

    @RequiresPermissions(SELF)
    @GetMapping("/my")
    public TableDataInfo myList(OaReimbursement filter,
            HttpServletRequest request)
    {
        startPage();
        List<OaReimbursement> values =
                reimbursementService.selectMyList(filter,
                        resolveShopDeptId(request));
        return getDataTable(values);
    }

    @RequiresPermissions(FINANCE_LIST)
    @GetMapping("/finance")
    public TableDataInfo financeList(OaReimbursement filter,
            HttpServletRequest request)
    {
        startPage();
        List<OaReimbursement> values =
                reimbursementService.selectFinanceList(filter,
                        resolveShopDeptId(request));
        return getDataTable(values);
    }

    @RequiresPermissions(value = {
            SELF, APPROVE, FINANCE_APPROVE, FINANCE_LIST, FINANCE_EXPORT
    }, logical = Logical.OR)
    @GetMapping("/{reimbursementId}")
    public AjaxResult detail(
            @PathVariable("reimbursementId") Long reimbursementId,
            HttpServletRequest request)
    {
        return success(reimbursementService.detail(reimbursementId,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @Log(title = "上传报销发票", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/{reimbursementId}/invoices",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult uploadInvoice(
            @PathVariable("reimbursementId") Long reimbursementId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request)
    {
        Long shopDeptId = resolveShopDeptId(request);
        OaReimbursementInvoice invoice =
                reimbursementService.uploadInvoice(reimbursementId,
                        file, shopDeptId);
        if (!Boolean.TRUE.equals(invoice.getIdempotentReplay())
                && Boolean.TRUE.equals(reimbursementService
                        .recognitionAvailability().get("autoRecognize")))
        {
            invoice = reimbursementService.recognizeInvoice(
                    reimbursementId, invoice.getInvoiceId(), "auto",
                    shopDeptId);
        }
        return success(invoice);
    }

    @RequiresPermissions(SELF)
    @Log(title = "识别报销发票", businessType = BusinessType.UPDATE,
            isSaveRequestData = false)
    @PostMapping("/{reimbursementId}/invoices/{invoiceId}/recognize")
    public AjaxResult recognizeInvoice(
            @PathVariable("reimbursementId") Long reimbursementId,
            @PathVariable("invoiceId") Long invoiceId,
            @Validated @RequestBody(required = false)
                    OaInvoiceRecognitionRequest body,
            HttpServletRequest request)
    {
        String engine = body == null ? "auto" : body.getEngine();
        return success(reimbursementService.recognizeInvoice(
                reimbursementId, invoiceId, engine,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @Log(title = "核对报销发票识别结果",
            businessType = BusinessType.UPDATE)
    @PutMapping("/{reimbursementId}/invoices/{invoiceId}/recognition")
    public AjaxResult updateInvoiceRecognition(
            @PathVariable("reimbursementId") Long reimbursementId,
            @PathVariable("invoiceId") Long invoiceId,
            @Validated @RequestBody
                    OaInvoiceRecognitionUpdateRequest body,
            HttpServletRequest request)
    {
        return success(reimbursementService.updateInvoiceRecognition(
                reimbursementId, invoiceId, body,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(SELF)
    @Log(title = "删除报销发票", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reimbursementId}/invoices/{invoiceId}")
    public AjaxResult deleteInvoice(
            @PathVariable("reimbursementId") Long reimbursementId,
            @PathVariable("invoiceId") Long invoiceId,
            @RequestParam(name = "expectedRowVersion", required = false)
                    Long expectedRowVersion,
            HttpServletRequest request)
    {
        return success(reimbursementService.deleteInvoice(reimbursementId,
                invoiceId, resolveShopDeptId(request), expectedRowVersion));
    }

    @RequiresPermissions(value = {
            SELF, APPROVE, FINANCE_APPROVE, FINANCE_LIST, FINANCE_EXPORT
    }, logical = Logical.OR)
    @GetMapping("/{reimbursementId}/invoices/{invoiceId}/content")
    public ResponseEntity<Resource> invoiceContent(
            @PathVariable("reimbursementId") Long reimbursementId,
            @PathVariable("invoiceId") Long invoiceId,
            @RequestParam(defaultValue = "preview") String mode,
            HttpServletRequest request)
    {
        IOaReimbursementService.InvoiceContent file =
                reimbursementService.invoiceContent(reimbursementId,
                        invoiceId, mode, resolveShopDeptId(request));
        return stream(file.path(), file.fileName(), file.contentType(),
                file.size(), file.inline());
    }

    @RequiresPermissions(FINANCE_EXPORT)
    @IdempotentSubmit(timeout = 120)
    @Log(title = "导出报销会计资料", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/finance/exports")
    public AjaxResult createExport(
            @Validated @RequestBody OaReimbursementExportRequest body,
            HttpServletRequest request)
    {
        return success(exportService.createExport(body,
                resolveShopDeptId(request)));
    }

    @RequiresPermissions(FINANCE_EXPORT)
    @GetMapping("/finance/exports/{batchId}/download")
    public ResponseEntity<Resource> downloadExport(
            @PathVariable("batchId") Long batchId)
    {
        OaReimbursementExportService.ExportContent file =
                exportService.exportContent(batchId);
        return stream(file.path(), file.fileName(), file.contentType(),
                file.size(), false);
    }

    private ResponseEntity<Resource> stream(java.nio.file.Path path,
            String fileName, String contentType, long size, boolean inline)
    {
        HttpHeaders headers = new HttpHeaders();
        ContentDisposition.Builder disposition = inline
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        headers.setContentDisposition(disposition
                .filename(fileName, StandardCharsets.UTF_8).build());
        headers.setCacheControl("no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(size)
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(path));
    }
}
