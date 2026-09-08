package com.erp.system.service.impl;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.domain.vo.HrOnboardingOwnerOptionVo;
import com.erp.system.domain.vo.HrOnboardingOwnerQuery;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserMapper;

/** Authorization boundary. Call these public methods through the injected Spring proxy. */
@Service
public class HrOnboardingAccessService
{
    private final HrOnboardingMapper onboardingMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final SysUserMapper userMapper;
    private final SysUserProfileDerivationService profileDerivationService;

    public HrOnboardingAccessService(HrOnboardingMapper onboardingMapper, SysDeptMapper deptMapper,
            SysPostMapper postMapper, SysUserMapper userMapper,
            SysUserProfileDerivationService profileDerivationService)
    {
        this.onboardingMapper = onboardingMapper;
        this.deptMapper = deptMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.profileDerivationService = profileDerivationService;
    }

    @DataScope(deptAlias = "d")
    public HrOnboarding findScoped(HrOnboardingQuery query)
    {
        List<HrOnboarding> rows = onboardingMapper.selectOnboardingList(query);
        if (rows == null || rows.isEmpty()) throw denied();
        return rows.get(0);
    }

    @DataScope(deptAlias = "d")
    public HrOnboarding lockScopedForUpdate(HrOnboardingQuery query)
    {
        HrOnboarding row = onboardingMapper.selectScopedOnboardingByIdForUpdate(query);
        if (row == null) throw denied();
        return row;
    }

    @DataScope(deptAlias = "d")
    public List<SysUser> listScopedUserConflicts(HrOnboardingQuery query, String phone,
            String idNumber, String employeeNo)
    {
        if (query == null) throw new ServiceException("数据范围查询条件不能为空");
        List<SysUser> rows = userMapper.selectScopedOnboardingConflictUsers(query, phone, idNumber, employeeNo);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public SysUser lockScopedBindCandidateForUpdate(HrOnboardingQuery query, Long userId,
            String phone, String idNumber, String employeeNo)
    {
        if (query == null || userId == null) throw denied();
        SysUser row = userMapper.selectScopedOnboardingConflictUserForUpdate(
                query, userId, phone, idNumber, employeeNo);
        if (row == null) throw denied();
        return row;
    }

    @DataScope(deptAlias = "d")
    public List<HrOnboarding> listScopedOnboardingConflicts(HrOnboardingQuery query, String phone,
            String idNumber, String employeeNo)
    {
        if (query == null) throw new ServiceException("数据范围查询条件不能为空");
        List<HrOnboarding> rows = onboardingMapper.selectScopedConflictCandidates(
                query, phone, idNumber, employeeNo);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias="d")
    public List<SysUser> listScopedUserConflictsBatch(HrOnboardingQuery query,List<HrOnboarding> identities)
    {
        requireBatch(query,identities);
        List<SysUser> rows=userMapper.selectScopedOnboardingConflictUsersBatch(query,identities);
        return rows==null?Collections.emptyList():rows;
    }

    @DataScope(deptAlias="d")
    public List<HrOnboarding> listScopedOnboardingConflictsBatch(HrOnboardingQuery query,List<HrOnboarding> identities)
    {
        requireBatch(query,identities);
        List<HrOnboarding> rows=onboardingMapper.selectScopedConflictCandidatesBatch(query,identities);
        return rows==null?Collections.emptyList():rows;
    }

    @DataScope(deptAlias="d")
    public List<HrOnboarding> listScopedByLinkedUserIds(HrOnboardingQuery query,List<Long> userIds)
    {
        if(query==null||userIds==null||userIds.isEmpty()||userIds.size()>200)
            throw new ServiceException("ONBOARDING_LINKED_USER_BATCH_INVALID");
        List<HrOnboarding> rows=onboardingMapper.selectScopedByLinkedUserIds(query,userIds);
        return rows==null?Collections.emptyList():rows;
    }

    private void requireBatch(HrOnboardingQuery query,List<HrOnboarding> identities)
    {
        if(query==null||identities==null||identities.isEmpty()||identities.size()>200)
            throw new ServiceException("IMPORT_CONFLICT_BATCH_INVALID");
    }

    /**
     * Internal global identity invariant. Authorization must be established before calling this method.
     * Returned rows contain only onboarding ID and state; callers must not expose cross-scope existence details.
     */
    public List<HrOnboarding> lockGlobalOpenIdentitySetForUpdate(
            String phone, String idNumber, String employeeNo)
    {
        List<HrOnboarding> rows = onboardingMapper.selectGlobalOpenIdentitySetForUpdate(
                phone, idNumber, employeeNo);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public List<SysDept> listScopedDepartments(SysDept query)
    {
        if (query == null) throw new ServiceException("数据范围查询条件不能为空");
        query.setStatus("0");
        List<SysDept> rows = deptMapper.selectDeptList(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public List<SysUser> listScopedUsers(SysUser query)
    {
        if (query == null) throw new ServiceException("数据范围查询条件不能为空");
        query.setStatus("0");
        List<SysUser> rows = onboardingMapper.selectScopedUserOptions(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public void validateScopedOwnerDepartment(HrOnboardingOwnerQuery query)
    {
        if (query == null) throw new ServiceException("负责人查询条件不能为空");
        if (query.getDeptId() != null)
        {
            SysDept department = new SysDept();
            department.setDeptId(query.getDeptId());
            department.setStatus("0");
            department.getParams().put("dataScope",
                    String.valueOf(query.getParams().getOrDefault("dataScope", "")));
            List<SysDept> rows = deptMapper.selectDeptList(department);
            if (rows == null || rows.isEmpty())
                throw new ServiceException("筛选组织不存在、已停用或无权访问");
        }
    }

    @DataScope(deptAlias = "d")
    public List<HrOnboardingOwnerOptionVo> listScopedOwnerOptions(HrOnboardingOwnerQuery query)
    {
        if (query == null) throw new ServiceException("负责人查询条件不能为空");
        List<HrOnboardingOwnerOptionVo> rows = onboardingMapper.selectScopedOwnerOptions(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public void validateTargets(HrOnboarding targets)
    {
        String dataScope = String.valueOf(targets.getParams().getOrDefault("dataScope", ""));
        SysDept targetDepartment = scopedDept(targets.getTargetDeptId(), dataScope, "目标组织");
        SysDept derivationRoot = targetDepartment;

        if (targets.getTargetStoreId() != null)
        {
            SysDept store = scopedDept(targets.getTargetStoreId(), dataScope, "目标门店");
            if (!"STORE".equals(store.getDeptType())) throw new ServiceException(
                    "目标门店不存在、已停用或无权访问", HttpStatus.FORBIDDEN);
            if (!sameOrganizationChain(targetDepartment, store))
                throw new ServiceException("目标组织与目标门店不在同一组织链");
            derivationRoot = depth(store) > depth(targetDepartment) ? store : targetDepartment;
        }

        // 岗位是全局主数据、没有部门关系；存在/启用校验必须在已通过 scope 的组织之后执行，
        // 因而岗位只能作为该已授权组织上的配置被使用，不能被 selectById 冒充数据范围查询。
        if (targets.getTargetPostId() != null)
        {
            SysPost post = postMapper.selectPostById(targets.getTargetPostId());
            if (post == null || !"0".equals(post.getStatus())) throw new ServiceException("目标岗位不存在或已停用");
        }
        if (targets.getDirectSupervisorUserId() != null)
        {
            scopedUser(targets.getDirectSupervisorUserId(), dataScope, "直属主管");
        }
        if (targets.getOwnerUserId() != null) scopedUser(targets.getOwnerUserId(), dataScope, "入职负责人");

        SysUser projection = new SysUser();
        projection.setDeptId(derivationRoot.getDeptId());
        if (targets.getTargetPostId() != null) projection.setPostIds(new Long[] { targets.getTargetPostId() });
        SysUserProfile derived = profileDerivationService.preview(projection);
        copyDerivedFields(targets, derived);
    }

    private boolean sameOrganizationChain(SysDept left, SysDept right)
    {
        return left.getDeptId().equals(right.getDeptId())
                || hasAncestor(left, right.getDeptId()) || hasAncestor(right, left.getDeptId());
    }

    private boolean hasAncestor(SysDept node, Long ancestorId)
    {
        if (node.getAncestors() == null) return false;
        for (String token : node.getAncestors().split(","))
            if (String.valueOf(ancestorId).equals(token.trim())) return true;
        return false;
    }

    private int depth(SysDept node)
    {
        int depth = 1;
        if (node.getAncestors() == null) return depth;
        for (String token : node.getAncestors().split(","))
            if (!token.trim().isEmpty() && !"0".equals(token.trim())) depth++;
        return depth;
    }

    private void copyDerivedFields(HrOnboarding targets, SysUserProfile derived)
    {
        targets.setCompanyName(derived == null ? null : derived.getCompanyName());
        targets.setDeptLevel1Name(derived == null ? null : derived.getDeptLevel1Name());
        targets.setDeptLevel2Name(derived == null ? null : derived.getDeptLevel2Name());
        targets.setDeptLevel3Name(derived == null ? null : derived.getDeptLevel3Name());
        targets.setStoreName(derived == null ? null : derived.getStoreName());
        targets.setPositionName(derived == null ? null : derived.getPositionNames());
        targets.setDepartmentSupervisor(derived == null ? null : derived.getDepartmentSupervisor());
    }

    private SysDept scopedDept(Long id, String dataScope, String label)
    {
        if (id == null) throw new ServiceException(label + "不能为空");
        SysDept query = new SysDept();
        query.setDeptId(id);
        query.setStatus("0");
        query.getParams().put("dataScope", dataScope);
        List<SysDept> rows = deptMapper.selectDeptList(query);
        if (rows == null || rows.isEmpty()) throw new ServiceException(label + "不存在、已停用或无权访问");
        return rows.get(0);
    }

    private SysUser scopedUser(Long id, String dataScope, String label)
    {
        SysUser query = new SysUser();
        query.setUserId(id);
        query.setStatus("0");
        query.getParams().put("dataScope", dataScope);
        List<SysUser> rows = userMapper.selectUserList(query);
        if (rows == null || rows.isEmpty()) throw new ServiceException(label + "不存在、已停用或无权访问");
        return rows.get(0);
    }

    private ServiceException denied() { return new ServiceException("无权访问该入职单或记录不存在"); }
}
