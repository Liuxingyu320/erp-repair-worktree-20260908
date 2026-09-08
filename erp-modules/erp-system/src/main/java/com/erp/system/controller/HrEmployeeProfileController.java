package com.erp.system.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.utils.ip.IpUtils;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.dto.HrRegularizationRequest;
import com.erp.system.domain.dto.HrRenewalDecisionRequest;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
import com.erp.system.domain.dto.HrOffboardingConfirmRequest;
import com.erp.system.domain.vo.*;
import com.erp.system.service.IHrEmployeeProfileService;
import com.erp.system.service.IHrLifecycleService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.impl.HrEmployeeQueueFilterService;

/** HR employee-master endpoints. All returned defaults use explicit masked DTOs. */
@RestController
@RequestMapping("/hr/employee")
public class HrEmployeeProfileController extends BaseController
{
    @Autowired private IHrEmployeeProfileService employeeService;
    @Autowired private IHrLifecycleService hrLifecycleService;
    @Autowired private HrEmployeeQueueFilterService queueFilterService;
    /** Kept only for the pre-existing import/template compatibility endpoints. */
    @Autowired private ISysUserService userService;

    @RequiresPermissions("hr:employee:list")
    @GetMapping("/list")
    public TableDataInfo list(HrEmployeeQuery query)
    {
        query=queueFilterService.prepare(query);
        if(!query.hasCompletenessFilter())startPage();
        List<HrEmployeeListVo> rows=employeeService.list(query);
        return getDataTable(rows);
    }

    @RequiresPermissions("hr:employee:list")
    @GetMapping("/summary")
    public AjaxResult summary(HrEmployeeQuery query)
    {return success(employeeService.summary(queueFilterService.prepare(query)));}

    @RequiresPermissions("hr:employee:query")
    @GetMapping("/form-options")
    public AjaxResult formOptions(){return success(employeeService.formOptions());}

    @RequiresPermissions("hr:employee:edit")
    @PostMapping("/derived-preview")
    public AjaxResult derivedPreview(@RequestBody Map<String,Object> input)
    {return success(employeeService.derivedPreview(input));}

    @Log(title="建立员工档案",businessType=BusinessType.INSERT,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:employee:edit")
    @PostMapping("/{userId}/profile/initialize")
    public AjaxResult initializeProfile(@PathVariable Long userId)
    {return success(employeeService.initializeProfile(userId,SecurityUtils.getUsername()));}

    @RequiresPermissions("hr:employee:query")
    @GetMapping("/{userId}")
    public AjaxResult getInfo(@PathVariable Long userId){return success(employeeService.get(userId));}

    @RequiresPermissions("hr:employee:query")
    @GetMapping("/{userId}/completeness")
    public AjaxResult completeness(@PathVariable Long userId){return success(employeeService.completeness(userId));}

    @Log(title="人事员工档案",businessType=BusinessType.UPDATE,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:employee:edit")
    @PatchMapping("/{userId}")
    public AjaxResult edit(@PathVariable Long userId,@RequestBody Map<String,Object> patch)
    {return success(employeeService.update(userId,patch,SecurityUtils.getUsername()));}

    /** Compatibility alias; PATCH is the canonical contract. */
    @Log(title="人事员工档案",businessType=BusinessType.UPDATE,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:employee:edit")
    @PutMapping("/{userId}")
    public AjaxResult editLegacy(@PathVariable Long userId,@RequestBody Map<String,Object> patch)
    {return edit(userId,patch);}

    /** Current erp-ui compatibility contract: PUT /system/hr/employee with userId in the body. */
    @Log(title="人事员工档案",businessType=BusinessType.UPDATE,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:employee:edit")
    @PutMapping
    public AjaxResult editLegacyRoot(@RequestBody Map<String,Object> input)
    {return success(employeeService.updateLegacy(input,SecurityUtils.getUsername()));}

    @Log(title = "员工合同续签决定", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("hr:employee:renewal")
    @PostMapping("/{userId}/renewal/confirm")
    public AjaxResult confirmRenewal(@PathVariable Long userId,
            @Valid @RequestBody HrRenewalDecisionRequest request,
            HttpServletRequest servletRequest)
    {
        String userAgent = servletRequest == null ? null : servletRequest.getHeader("User-Agent");
        Long actionId = hrLifecycleService.confirmRenewal(userId, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin(),
                IpUtils.getIpAddr(servletRequest), userAgent);
        return success(Map.of("actionId", actionId));
    }

    @Log(title = "员工转正确认", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("hr:employee:regularize")
    @PostMapping("/{userId}/regularize")
    public AjaxResult confirmRegularization(@PathVariable Long userId,
            @Valid @RequestBody HrRegularizationRequest request,
            HttpServletRequest servletRequest)
    {
        String userAgent = servletRequest == null ? null : servletRequest.getHeader("User-Agent");
        Long actionId = hrLifecycleService.confirmRegularization(userId, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin(),
                IpUtils.getIpAddr(servletRequest), userAgent);
        return success(Map.of("actionId", actionId));
    }

    @RequiresPermissions("hr:employee:transfer")
    @GetMapping("/transfer/business-date")
    public AjaxResult transferBusinessDate()
    {
        return success(Map.of("businessDate", hrLifecycleService.transferBusinessDate()));
    }

    @Log(title = "员工调岗确认", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("hr:employee:transfer")
    @PostMapping("/{userId}/transfer")
    public AjaxResult confirmTransfer(@PathVariable Long userId,
            @Valid @RequestBody HrEmployeeTransferRequest request,
            HttpServletRequest servletRequest)
    {
        String userAgent = servletRequest == null ? null : servletRequest.getHeader("User-Agent");
        Long actionId = hrLifecycleService.confirmTransfer(userId, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin(),
                IpUtils.getIpAddr(servletRequest), userAgent);
        return success(Map.of("actionId", actionId));
    }

    @RequiresPermissions("hr:employee:offboard")
    @GetMapping("/offboard/business-date")
    public AjaxResult offboardingBusinessDate()
    {
        return success(Map.of("businessDate", hrLifecycleService.offboardingBusinessDate()));
    }

    @Log(title = "员工离职确认", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @RequiresPermissions("hr:employee:offboard")
    @PostMapping("/{userId}/offboard")
    public AjaxResult confirmOffboarding(@PathVariable Long userId,
            @Valid @RequestBody HrOffboardingConfirmRequest request,
            HttpServletRequest servletRequest)
    {
        Long actionId = hrLifecycleService.confirmOffboarding(userId, request,
                SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.isAdmin(),
                IpUtils.getIpAddr(servletRequest),
                servletRequest == null ? null : servletRequest.getHeader("User-Agent"));
        return success(Map.of("actionId", actionId));
    }

    @Log(title="人事员工档案",businessType=BusinessType.EXPORT,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:employee:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response,HrEmployeeQuery query)
    {
        new ExcelUtil<HrEmployeeListVo>(HrEmployeeListVo.class)
                .exportExcel(response,employeeService.list(queueFilterService.prepare(query)),"员工档案");
    }

    @Log(title="人事员工敏感查看",businessType=BusinessType.OTHER,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:employee:sensitive:view")
    @PostMapping("/{userId}/sensitive/reveal")
    public AjaxResult reveal(@PathVariable Long userId,@RequestBody HrSensitiveRevealRequest input,
            HttpServletRequest request)
    {
        return success(employeeService.reveal(userId,input==null?null:input.getFieldKey(),SecurityUtils.getUserId(),
                SecurityUtils.getUsername(),IpUtils.getIpAddr(request)));
    }

    @Log(title="人事员工敏感导出",businessType=BusinessType.EXPORT,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:employee:export:sensitive")
    @PostMapping("/export-sensitive")
    public void exportSensitive(@RequestBody HrSensitiveExportRequest input,HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        if(input==null)input=new HrSensitiveExportRequest();
        Long operatorId=SecurityUtils.getUserId();String operatorName=SecurityUtils.getUsername();
        String requestIp=IpUtils.getIpAddr(request);
        HrSensitiveExportArtifact artifact=employeeService.exportSensitive(input.getFilter(),input.getRequestedFields(),
                operatorId,operatorName,requestIp);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String name=URLEncoder.encode("员工档案敏感导出.xlsx",StandardCharsets.UTF_8).replace("+","%20");
        response.setHeader("Content-Disposition","attachment;filename*=UTF-8''"+name);
        try
        {
            response.getOutputStream().write(artifact.getContent());
            response.getOutputStream().flush();
        }
        catch(IOException deliveryFailure)
        {
            employeeService.recordSensitiveExportDeliveryFailure(artifact.getExportScope(),operatorId,operatorName,requestIp);
            throw deliveryFailure;
        }
    }

    @Log(title="人事员工档案导入",businessType=BusinessType.IMPORT,
            isSaveRequestData=false,isSaveResponseData=false)
    @RequiresPermissions("hr:import:confirm")
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file,boolean updateSupport,HttpServletResponse response)throws Exception
    {
        ExcelUtil<SysUser> util=new ExcelUtil<>(SysUser.class);
        AjaxResult result=success(userService.importUser(util.importExcel(file.getInputStream()),updateSupport,SecurityUtils.getUsername()));
        response.setHeader("Cache-Control","no-store, max-age=0");
        response.setHeader("Pragma","no-cache");
        return result;
    }

    @RequiresPermissions("hr:import:template")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {new ExcelUtil<SysUser>(SysUser.class).importTemplateExcel(response,"员工档案导入模板");}
}
