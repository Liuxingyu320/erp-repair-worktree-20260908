package com.erp.system.controller;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.SysUserOptionVo;
import com.erp.system.domain.vo.SysUserShopScopeVo;
import com.erp.system.domain.dto.SysUserShopScopeChangeRequest;
import com.erp.system.domain.vo.SysUserShopScopePreviewVo;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.service.impl.SysUserShopScopeConflictException;

/**
 * 用户店铺授权
 *
 * @author erp
 */
@RestController
@RequestMapping("/user/shop")
public class SysUserShopController extends BaseController
{
    @Autowired
    private ISysUserShopService userShopService;

    @Autowired
    private ISysUserService userService;

    /**
     * 查询可配置店铺树
     */
    @RequiresPermissions("system:userShop:list")
    @GetMapping("/tree")
    public AjaxResult tree()
    {
        return success(userShopService.selectAuthorizedShopTree(SecurityUtils.getUserId(), SecurityUtils.isAdmin()));
    }

    /**
     * 查询当前操作人数据范围内的最小用户候选列表。
     */
    @RequiresPermissions("system:userShop:list")
    @GetMapping("/users")
    public TableDataInfo users(SysUser user)
    {
        user.getParams().put("includeDisabled", Boolean.TRUE);
        startPage();
        List<SysUser> users = userService.selectUserOptionList(user);
        TableDataInfo table = getDataTable(users);
        table.setRows(users.stream().map(SysUserOptionVo::from).collect(Collectors.toList()));
        return table;
    }

    /**
     * 查询用户已配置店铺
     */
    @RequiresPermissions("system:userShop:query")
    @GetMapping("/{userId}")
    public AjaxResult getInfo(@PathVariable Long userId)
    {
        userService.checkUserDataScope(userId);
        SysUserShopScopeVo scope = userShopService.selectUserShopScope(userId, SecurityUtils.getUserId(),
                SecurityUtils.isAdmin());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("shopIds", scope.getShopIds());
        ajax.put("editableShopIds", scope.getEditableShopIds());
        ajax.put("preservedShopIds", scope.getPreservedShopIds());
        ajax.put("outOfScopeCount", scope.getOutOfScopeCount());
        return ajax;
    }

    /**
     * 批量查询用户已配置店铺
     */
    @RequiresPermissions("system:userShop:query")
    @GetMapping("/batch")
    public AjaxResult batch(Long[] userIds)
    {
        Long[] safeUserIds = normalizeIds(userIds);
        for (Long userId : safeUserIds)
        {
            userService.checkUserDataScope(userId);
        }
        return success(userShopService.selectShopDeptIdsByUserIds(safeUserIds));
    }

    /**
     * 预览用户组织授权差异。保存必须携带此处返回的 scopeVersion。
     */
    @RequiresPermissions(value = { "system:userShop:query", "system:userShop:edit" })
    @PostMapping("/{userId}/preview")
    public AjaxResult preview(@PathVariable Long userId,
            @Validated @RequestBody SysUserShopScopeChangeRequest request)
    {
        userService.checkUserDataScope(userId);
        SysUserShopScopePreviewVo preview = userShopService.previewUserShops(userId,
                request.getShopDeptIds(), SecurityUtils.getUserId(), SecurityUtils.isAdmin());
        return success(preview);
    }

    /**
     * 保存已经预览并且版本未变化的用户组织授权。
     */
    @RequiresPermissions(value = { "system:userShop:query", "system:userShop:edit" })
    @Log(title = "店铺配置", businessType = BusinessType.GRANT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{userId}")
    public AjaxResult save(@PathVariable Long userId,
            @Validated @RequestBody SysUserShopScopeChangeRequest request)
    {
        userService.checkUserDataScope(userId);
        try
        {
            return success(userShopService.savePreviewedUserShops(userId,
                    request.getShopDeptIds(), request.getScopeVersion(), SecurityUtils.getUsername(),
                    SecurityUtils.getUserId(), SecurityUtils.isAdmin()));
        }
        catch (SysUserShopScopeConflictException conflict)
        {
            return AjaxResult.error(HttpStatus.CONFLICT, conflict.getMessage())
                    .put("businessCode", "USER_SHOP_SCOPE_CONFLICT");
        }
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
}
