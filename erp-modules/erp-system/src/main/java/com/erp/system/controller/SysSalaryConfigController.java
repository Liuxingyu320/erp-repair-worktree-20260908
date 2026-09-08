package com.erp.system.controller;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.utils.poi.ExcelUtil;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysRoleSalaryScheme;
import com.erp.system.domain.SysSalaryScheme;
import com.erp.system.domain.SysSalarySchemeExport;
import com.erp.system.domain.SysSalarySchemeItem;
import com.erp.system.domain.SysUserSalaryScheme;
import com.erp.system.domain.dto.SysSalaryImpactPreviewRequest;
import com.erp.system.domain.dto.SysSalaryRevisionRollbackRequest;
import com.erp.system.domain.vo.SysSalaryUserOptionVo;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysSalaryConfigService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.ISysUserShopService;

/**
 * 薪资配置操作处理
 *
 * @author erp
 */
@RestController
@RequestMapping("/salaryConfig")
public class SysSalaryConfigController extends BaseController
{
    @Autowired
    private ISysSalaryConfigService salaryConfigService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysUserShopService userShopService;

    @RequiresPermissions("system:salary:list")
    @GetMapping("/list")
    public TableDataInfo list(SysSalaryScheme scheme)
    {
        startPage();
        List<SysSalaryScheme> list = salaryConfigService.selectSalarySchemeList(scheme);
        return getDataTable(list);
    }

    @RequiresPermissions("system:salary:export")
    @Log(title = "薪资配置", businessType = BusinessType.EXPORT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysSalaryScheme scheme)
    {
        List<SysSalarySchemeExport> list = salaryConfigService.selectSalarySchemeExportList(scheme);
        ExcelUtil<SysSalarySchemeExport> util = new ExcelUtil<>(SysSalarySchemeExport.class);
        util.exportExcel(response, list, "薪资方案");
    }

    @RequiresPermissions("system:salary:query")
    @GetMapping("/{schemeId}")
    public AjaxResult getInfo(@PathVariable Long schemeId)
    {
        return success(salaryConfigService.selectSalarySchemeById(schemeId));
    }

    @RequiresPermissions("system:salary:query")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return success(salaryConfigService.selectSalarySchemeOptions());
    }

    @RequiresPermissions("system:salary:query")
    @PostMapping("/impact-preview")
    public AjaxResult impactPreview(@Validated @RequestBody SysSalaryImpactPreviewRequest request)
    {
        return success(salaryConfigService.previewSalaryImpact(request));
    }

    @RequiresPermissions("system:salary:query")
    @GetMapping("/{schemeId}/revisions")
    public AjaxResult revisions(@PathVariable Long schemeId)
    {
        return success(salaryConfigService.selectSalarySchemeRevisions(schemeId));
    }

    @RequiresPermissions("system:salary:edit")
    @Log(title = "薪资方案版本回滚", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/revision/{revisionId}/rollback")
    public AjaxResult rollbackRevision(@PathVariable Long revisionId,
            @Validated @RequestBody SysSalaryRevisionRollbackRequest request)
    {
        assertEmergencyPermission(Boolean.TRUE.equals(request.getEmergencyCorrection()));
        return toAjax(salaryConfigService.rollbackSalarySchemeRevision(revisionId, request));
    }

    @RequiresPermissions("system:salary:role")
    @GetMapping("/users")
    public AjaxResult users(SysUser user, Long shopDeptId)
    {
        user.setStatus("0");
        if (shopDeptId != null && shopDeptId > 0)
        {
            user.getParams().put("salaryShopDeptId", shopDeptId);
        }
        List<SysSalaryUserOptionVo> options = userService.selectSalaryUserOptions(user);
        return success(options);
    }

    @RequiresPermissions("system:salary:role")
    @GetMapping("/shopTree")
    public AjaxResult shopTree()
    {
        return success(userShopService.selectAuthorizedShopTree(SecurityUtils.getUserId(), SecurityUtils.isAdmin()));
    }

    @RequiresPermissions("system:salary:add")
    @Log(title = "薪资配置", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysSalaryScheme scheme)
    {
        scheme.setCreateBy(SecurityUtils.getUsername());
        return toAjax(salaryConfigService.insertSalaryScheme(scheme));
    }

    @RequiresPermissions("system:salary:edit")
    @Log(title = "薪资配置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysSalaryScheme scheme)
    {
        assertEmergencyPermission(Boolean.TRUE.equals(scheme.getEmergencyCorrection()));
        scheme.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(salaryConfigService.updateSalaryScheme(scheme));
    }

    @RequiresPermissions("system:salary:remove")
    @Log(title = "薪资配置", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{schemeIds}")
    public AjaxResult remove(@PathVariable Long[] schemeIds,
            @RequestParam Integer expectedVersion,
            @RequestParam String changeReason,
            @RequestParam(value = "emergencyCorrection", defaultValue = "false") boolean emergencyCorrection)
    {
        assertEmergencyPermission(emergencyCorrection);
        return toAjax(salaryConfigService.deleteSalarySchemeByIds(
                schemeIds, expectedVersion, changeReason, emergencyCorrection));
    }

    @RequiresPermissions("system:salary:query")
    @GetMapping("/{schemeId}/items")
    public AjaxResult items(@PathVariable Long schemeId)
    {
        return success(salaryConfigService.selectSalarySchemeItemsBySchemeId(schemeId));
    }

    @RequiresPermissions("system:salary:add")
    @Log(title = "薪资档位", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/item")
    public AjaxResult addItem(@Validated @RequestBody SysSalarySchemeItem item)
    {
        assertEmergencyPermission(Boolean.TRUE.equals(item.getEmergencyCorrection()));
        item.setCreateBy(SecurityUtils.getUsername());
        int rows = salaryConfigService.insertSalarySchemeItem(item);
        return rows > 0 ? success(item.getItemId()) : error();
    }

    @RequiresPermissions("system:salary:edit")
    @Log(title = "薪资档位", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/item")
    public AjaxResult editItem(@Validated @RequestBody SysSalarySchemeItem item)
    {
        assertEmergencyPermission(Boolean.TRUE.equals(item.getEmergencyCorrection()));
        item.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(salaryConfigService.updateSalarySchemeItem(item));
    }

    @RequiresPermissions("system:salary:remove")
    @Log(title = "薪资档位", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/item/{itemId}")
    public AjaxResult removeItem(@PathVariable Long itemId,
            @RequestParam Integer expectedVersion,
            @RequestParam(value = "changeReason", required = false) String changeReason,
            @RequestParam(value = "emergencyCorrection", defaultValue = "false") boolean emergencyCorrection)
    {
        assertEmergencyPermission(emergencyCorrection);
        return toAjax(salaryConfigService.deleteSalarySchemeItemById(itemId, expectedVersion, changeReason, emergencyCorrection));
    }

    @RequiresPermissions("system:salary:role")
    @GetMapping("/role/{roleId}")
    public AjaxResult roleBindings(@PathVariable Long roleId)
    {
        roleService.checkRoleDataScope(roleId);
        return success(salaryConfigService.selectRoleSalarySchemesByRoleId(roleId));
    }

    @RequiresPermissions("system:salary:role")
    @GetMapping("/role/batch")
    public AjaxResult roleBindingsBatch(Long[] roleIds)
    {
        Long[] safeRoleIds = normalizeIds(roleIds);
        roleService.checkRoleDataScope(safeRoleIds);
        return success(salaryConfigService.selectRoleSalarySchemesByRoleIds(safeRoleIds));
    }

    @RequiresPermissions("system:salary:role")
    @Log(title = "角色薪资配置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/role/{roleId}")
    public AjaxResult saveRoleBindings(@PathVariable Long roleId, @RequestBody List<SysRoleSalaryScheme> bindings)
    {
        roleService.checkRoleDataScope(roleId);
        return toAjax(salaryConfigService.saveRoleSalarySchemes(roleId, bindings));
    }

    @RequiresPermissions("system:salary:role")
    @GetMapping("/user/{userId}")
    public AjaxResult userBindings(@PathVariable Long userId)
    {
        userService.checkUserDataScope(userId);
        return success(salaryConfigService.selectUserSalarySchemesByUserId(userId));
    }

    @RequiresPermissions("system:salary:role")
    @GetMapping("/user/batch")
    public AjaxResult userBindingsBatch(Long[] userIds)
    {
        Long[] safeUserIds = normalizeIds(userIds);
        for (Long userId : safeUserIds)
        {
            userService.checkUserDataScope(userId);
        }
        return success(salaryConfigService.selectUserSalarySchemesByUserIds(safeUserIds));
    }

    @RequiresPermissions("system:salary:role")
    @Log(title = "员工薪资配置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/user/{userId}")
    public AjaxResult saveUserBindings(@PathVariable Long userId, @RequestBody List<SysUserSalaryScheme> bindings)
    {
        userService.checkUserDataScope(userId);
        return toAjax(salaryConfigService.saveUserSalarySchemes(userId, bindings));
    }

    private Long[] normalizeIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return new Long[0];
        }
        return Arrays.stream(ids)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .toArray(new Long[0]);
    }

    private void assertEmergencyPermission(boolean emergencyCorrection)
    {
        if (emergencyCorrection && !AuthUtil.hasPermi("system:salary:emergency"))
        {
            throw new ServiceException("没有薪资紧急修正权限", HttpStatus.FORBIDDEN);
        }
    }
}
