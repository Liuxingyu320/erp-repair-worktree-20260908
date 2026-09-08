package com.erp.system.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.vo.MobileOption;
import com.erp.system.domain.vo.MobileProfile;
import com.erp.system.service.ISysDeptService;

@RestController
@RequestMapping("/mobile")
public class SysMobileController extends BaseController
{
    private static final String WILDCARD_PERMISSION = "*:*:*";
    private static final String DEPT_TYPE_WAREHOUSE = "WAREHOUSE";

    @Autowired(required = false)
    private ISysDeptService deptService;

    @RequiresLogin
    @GetMapping("/profile")
    public AjaxResult profile()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Set<String> permissions = loginUser == null || loginUser.getPermissions() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(loginUser.getPermissions());
        Set<String> roles = loginUser == null || loginUser.getRoles() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(loginUser.getRoles());
        boolean admin = SecurityUtils.isAdmin() || permissions.contains(WILDCARD_PERMISSION);

        MobileProfile profile = new MobileProfile();
        profile.setUserId(SecurityUtils.getUserId());
        profile.setUsername(loginUser == null ? SecurityUtils.getUsername() : loginUser.getUsername());
        profile.setAdmin(admin);
        profile.setRoles(roles);
        profile.setPermissions(permissions);
        profile.setAdminEntries(new ArrayList<>());
        return success(profile);
    }

    @RequiresLogin
    @GetMapping("/options/warehouses")
    public AjaxResult warehouseOptions(String keyword, Integer limit)
    {
        List<SysDept> depts = deptService == null ? new ArrayList<>() : deptService.selectShopTree(new SysDept());
        List<MobileOption> options = new ArrayList<>();
        collectWarehouseOptions(depts, normalizeKeyword(keyword), normalizeLimit(limit), new ArrayList<>(), options);
        return success(options);
    }

    private void collectWarehouseOptions(List<SysDept> depts, String keyword, int limit, List<String> parentPath,
            List<MobileOption> options)
    {
        if (depts == null || options.size() >= limit)
        {
            return;
        }
        for (SysDept dept : depts)
        {
            if (dept == null || options.size() >= limit)
            {
                continue;
            }
            List<String> currentPath = new ArrayList<>(parentPath);
            if (dept.getDeptName() != null && !dept.getDeptName().trim().isEmpty())
            {
                currentPath.add(dept.getDeptName());
            }
            if (DEPT_TYPE_WAREHOUSE.equals(dept.getDeptType()) && matchesKeyword(dept, keyword))
            {
                options.add(toWarehouseOption(dept, currentPath));
            }
            collectWarehouseOptions(dept.getChildren(), keyword, limit, currentPath, options);
        }
    }

    private MobileOption toWarehouseOption(SysDept dept, List<String> currentPath)
    {
        MobileOption option = new MobileOption();
        option.setValue(dept.getDeptId());
        option.setLabel(dept.getDeptName());
        option.setCode(String.valueOf(dept.getDeptId()));
        option.setMeta(joinMeta(currentPath, dept));
        option.setStatus(dept.getStatus());
        option.setType(DEPT_TYPE_WAREHOUSE);
        return option;
    }

    private String joinMeta(List<String> currentPath, SysDept dept)
    {
        List<String> parts = new ArrayList<>();
        String path = String.join(" / ", currentPath);
        if (!path.isEmpty())
        {
            parts.add(path);
        }
        if (dept.getLeader() != null && !dept.getLeader().trim().isEmpty())
        {
            parts.add(dept.getLeader());
        }
        if (dept.getPhone() != null && !dept.getPhone().trim().isEmpty())
        {
            parts.add(dept.getPhone());
        }
        return String.join(" · ", parts);
    }

    private boolean matchesKeyword(SysDept dept, String keyword)
    {
        if (keyword == null)
        {
            return true;
        }
        return contains(dept.getDeptName(), keyword)
                || contains(String.valueOf(dept.getDeptId()), keyword)
                || contains(dept.getLeader(), keyword)
                || contains(dept.getPhone(), keyword);
    }

    private boolean contains(String value, String keyword)
    {
        return value != null && value.contains(keyword);
    }

    private String normalizeKeyword(String keyword)
    {
        if (keyword == null)
        {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizeLimit(Integer limit)
    {
        if (limit == null || limit <= 0)
        {
            return 20;
        }
        return Math.min(limit, 50);
    }

}
