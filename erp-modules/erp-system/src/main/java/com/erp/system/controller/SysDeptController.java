package com.erp.system.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.dto.SysSortBatchRequest;
import com.erp.system.domain.dto.SysSortChangeRequest;
import com.erp.system.domain.vo.SysProfileCompletionVo;
import com.erp.system.domain.vo.SysDeptLeaderOptionVo;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysUserProfileCompletionService;
import com.erp.system.service.impl.SysSortConflictException;

/**
 * 部门信息
 * 
 * @author erp
 */
@RestController
@RequestMapping("/dept")
public class SysDeptController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(SysDeptController.class);

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysUserProfileCompletionService profileCompletionService;

    /**
     * 获取部门列表
     */
    @RequiresPermissions("system:dept:list")
    @GetMapping("/list")
    public AjaxResult list(SysDept dept)
    {
        List<SysDept> depts = deptService.selectDeptList(dept);
        return success(depts);
    }

    /** 当前数据范围内可设置为部门负责人的启用在职员工。 */
    @RequiresPermissions("system:dept:list")
    @GetMapping("/leader-options")
    public AjaxResult leaderOptions(@RequestParam(value = "keyword", required = false) String keyword)
    {
        List<SysDeptLeaderOptionVo> options = deptService.selectLeaderOptions(keyword);
        return success(options);
    }

    /**
     * 获取店铺/仓库上下文树（选店页面专用，不限制 system:dept:list 权限）
     */
    @RequiresLogin
    @GetMapping("/shop-tree")
    public AjaxResult shopTree()
    {
        preventSessionContextCaching();
        SysProfileCompletionVo completion = profileCompletionService.evaluate(SecurityUtils.getUserId());
        if (completion.isCompletionRequired())
        {
            return AjaxResult.error(HttpStatus.CONFLICT, "请先补全入职资料")
                    .put("businessCode", "PROFILE_COMPLETION_REQUIRED")
                    .put("profileMissingFields", completion.getMissingFields());
        }
        List<SysDept> depts = deptService.selectShopTree(new SysDept());
        return success(depts);
    }

    private void preventSessionContextCaching()
    {
        jakarta.servlet.http.HttpServletResponse response = ServletUtils.getResponse();
        if (response != null)
        {
            response.setHeader("Cache-Control", "no-store, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }
    }

    /**
     * 获取启用仓库列表（业务选择仓库目标，不要求 system:dept:list 权限）
     */
    @RequiresLogin
    @GetMapping("/warehouse-list")
    public AjaxResult warehouseList(@RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "scopeDeptId", required = false) Long scopeDeptId)
    {
        return success(deptService.selectWarehouseList(purpose, scopeDeptId));
    }

    /**
     * 获取当前业务组织范围内的可见门店列表（仓库库存筛选使用）。
     */
    @RequiresLogin
    @GetMapping("/visible-store-list")
    public AjaxResult visibleStoreList(@RequestParam(value = "scopeDeptId", required = false) Long scopeDeptId,
            HttpServletRequest request)
    {
        Long effectiveScopeDeptId = scopeDeptId != null && scopeDeptId > 0 ? scopeDeptId : ShopHeaderUtils.resolveShopDeptId(request);
        return success(deptService.selectVisibleStoreList(effectiveScopeDeptId));
    }

    /**
     * 查询部门列表（排除节点）
     */
    @RequiresPermissions("system:dept:list")
    @GetMapping("/list/exclude/{deptId}")
    public AjaxResult excludeChild(@PathVariable(value = "deptId", required = false) Long deptId)
    {
        List<SysDept> depts = deptService.selectDeptList(new SysDept());
        depts.removeIf(d -> d.getDeptId().intValue() == deptId || ArrayUtils.contains(StringUtils.split(d.getAncestors(), ","), deptId + ""));
        return success(depts);
    }

    /**
     * 根据部门编号获取详细信息
     */
    @RequiresPermissions("system:dept:query")
    @GetMapping(value = "/{deptId}")
    public AjaxResult getInfo(@PathVariable Long deptId)
    {
        deptService.checkDeptDataScope(deptId);
        return success(deptService.selectDeptById(deptId));
    }

    /**
     * 新增部门
     */
    @RequiresPermissions("system:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDept dept)
    {
        if (!deptService.checkDeptNameUnique(dept))
        {
            return error("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        dept.setCreateBy(SecurityUtils.getUsername());
        return toAjax(deptService.insertDept(dept));
    }

    /**
     * 修改部门
     */
    @RequiresPermissions("system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDept dept)
    {
        Long deptId = dept.getDeptId();
        deptService.checkDeptDataScope(deptId);
        if (!deptService.checkDeptNameUnique(dept))
        {
            return error("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        else if (dept.getParentId().equals(deptId))
        {
            return error("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        }
        else if (StringUtils.equals(UserConstants.DEPT_DISABLE, dept.getStatus()) && deptService.selectNormalChildrenDeptById(deptId) > 0)
        {
            return error("该部门包含未停用的子部门！");
        }
        dept.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(deptService.updateDept(dept));
    }

    /**
     * 保存部门排序
     */
    @RequiresPermissions("system:dept:edit")
    @Log(title = "保存部门排序", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/updateSort")
    public AjaxResult updateSort(@Validated @RequestBody SysSortChangeRequest request)
    {
        try
        {
            deptService.updateDeptSort(request);
            return success();
        }
        catch (SysSortConflictException conflict)
        {
            return AjaxResult.error(HttpStatus.CONFLICT, conflict.getMessage())
                    .put("businessCode", "SORT_CONFLICT")
                    .put("conflictIds", conflict.getConflictIds());
        }
    }

    /**
     * 使用标准 JSON 请求保存差量部门排序。
     */
    @RequiresPermissions("system:dept:edit")
    @Log(title = "保存部门排序", businessType = BusinessType.UPDATE)
    @PutMapping("/sort/batch")
    public AjaxResult updateSortBatch(@Validated @RequestBody SysSortBatchRequest request)
    {
        deptService.updateDeptSort(request);
        return success();
    }

    /**
     * 删除部门
     */
    @RequiresPermissions("system:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public AjaxResult remove(@PathVariable Long deptId)
    {
        if (deptService.hasChildByDeptId(deptId))
        {
            return warn("存在下级部门,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId))
        {
            return warn("部门存在用户,不允许删除");
        }
        deptService.checkDeptDataScope(deptId);
        return toAjax(deptService.deleteDeptById(deptId));
    }
}
