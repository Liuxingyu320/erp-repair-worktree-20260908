package com.erp.oa.controller;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.PageUtils;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaLaborContract;
import com.erp.oa.domain.OaLaborContractTemplate;
import com.erp.oa.domain.dto.OaLaborContractSignRequest;
import com.erp.oa.domain.dto.OaLaborContractVerifyRequest;
import com.erp.oa.service.IOaLaborContractService;

@RestController
@RequestMapping("/laborContract")
public class OaLaborContractController extends OaBaseController
{
    @Autowired
    private IOaLaborContractService laborContractService;

    @RequiresPermissions("oa:laborContract:template:list")
    @GetMapping("/template/list")
    public AjaxResult templateList(OaLaborContractTemplate template)
    {
        return success(laborContractService.selectTemplateList(template));
    }

    @RequiresPermissions("oa:laborContract:template:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "劳动合同模板", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/template")
    public AjaxResult saveTemplate(@Validated @RequestBody OaLaborContractTemplate template)
    {
        return success(laborContractService.saveTemplate(template));
    }

    @RequiresPermissions("oa:laborContract:template:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "企业合同印章", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/seal")
    public AjaxResult saveSeal(@Validated @RequestBody OaCompanySealConfig config)
    {
        return success(laborContractService.saveSealConfig(config));
    }

    @RequiresPermissions("oa:laborContract:template:list")
    @GetMapping("/seal")
    public AjaxResult seal()
    {
        PageUtils.clearPage();
        return success(laborContractService.getActiveSealConfig());
    }

    @RequiresPermissions("oa:laborContract:add")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "劳动合同", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/save")
    public AjaxResult save(@Validated @RequestBody OaLaborContract contract, HttpServletRequest request)
    {
        return success(laborContractService.saveContract(contract, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:laborContract:send")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "劳动合同发送签署", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/send")
    public AjaxResult send(@RequestBody OaLaborContract contract, HttpServletRequest request)
    {
        return success(laborContractService.sendContract(contract.getContractId(), resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:laborContract:list")
    @GetMapping("/list")
    public TableDataInfo list(OaLaborContract contract, HttpServletRequest request)
    {
        startPage();
        List<OaLaborContract> list = laborContractService.selectContractList(contract, resolveShopDeptId(request));
        return getDataTable(list);
    }

    @RequiresPermissions("oa:laborContract:query")
    @GetMapping("/{contractId}")
    public AjaxResult detail(@PathVariable("contractId") Long contractId, HttpServletRequest request)
    {
        return success(laborContractService.getContractDetail(contractId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:laborContract:void")
    @IdempotentSubmit(timeout = 30)
    @Log(title = "劳动合同作废", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{contractId}/void")
    public AjaxResult voidContract(@PathVariable("contractId") Long contractId, HttpServletRequest request)
    {
        return success(laborContractService.voidContract(contractId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:laborContract:query")
    @GetMapping("/{contractId}/preview")
    public AjaxResult preview(@PathVariable("contractId") Long contractId, HttpServletRequest request)
    {
        return success(laborContractService.getPreviewContract(contractId, resolveShopDeptId(request)));
    }

    @RequiresPermissions("oa:laborContract:query")
    @PostMapping("/verify")
    public AjaxResult verify(@Validated @RequestBody OaLaborContractVerifyRequest verifyRequest,
            HttpServletRequest request)
    {
        return success(laborContractService.verifyContractHash(verifyRequest, resolveShopDeptId(request)));
    }

    @RequiresLogin
    @GetMapping("/download/{contractId}/{kind}")
    public void download(@PathVariable("contractId") Long contractId, @PathVariable("kind") String kind,
            HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        laborContractService.downloadContractFile(contractId, kind, resolveShopDeptId(request), response);
    }

    @RequiresLogin
    @GetMapping("/mobile/my")
    public TableDataInfo mobileMy(OaLaborContract contract)
    {
        startPage();
        List<OaLaborContract> list = laborContractService.selectMyContracts(contract);
        return getDataTable(list);
    }

    @RequiresLogin
    @GetMapping("/mobile/{contractId}")
    public AjaxResult mobileDetail(@PathVariable("contractId") Long contractId)
    {
        return success(laborContractService.getMyContractDetail(contractId));
    }

    @RequiresLogin
    @IdempotentSubmit(timeout = 30)
    @Log(title = "劳动合同员工签署", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/mobile/{contractId}/sign")
    public AjaxResult mobileSign(@PathVariable("contractId") Long contractId,
            @Validated @RequestBody OaLaborContractSignRequest signRequest, HttpServletRequest request)
    {
        signRequest.setSignerIp(resolveClientIp(request));
        signRequest.setSignerUserAgent(request.getHeader("User-Agent"));
        return success(laborContractService.signContract(contractId, signRequest));
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
