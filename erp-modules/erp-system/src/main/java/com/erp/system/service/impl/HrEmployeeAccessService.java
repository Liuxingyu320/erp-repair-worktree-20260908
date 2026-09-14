package com.erp.system.service.impl;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.support.HrEmployeePopulationPolicy;

/** Employee-master authorization boundary; callers must use the injected Spring proxy. */
@Service
public class HrEmployeeAccessService
{
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final HrOnboardingMapper onboardingMapper;
    public HrEmployeeAccessService(SysUserMapper userMapper) { this(userMapper,null,null); }
    public HrEmployeeAccessService(SysUserMapper userMapper,SysDeptMapper deptMapper)
    { this(userMapper,deptMapper,null); }
    @Autowired
    public HrEmployeeAccessService(SysUserMapper userMapper,SysDeptMapper deptMapper,HrOnboardingMapper onboardingMapper)
    { this.userMapper=userMapper; this.deptMapper=deptMapper; this.onboardingMapper=onboardingMapper; }

    @DataScope(deptAlias = "d")
    public List<SysUser> listScoped(HrEmployeeQuery query)
    {
        query=HrEmployeePopulationPolicy.applyArchiveAccess(query);
        healthDate(query);
        List<SysUser> rows = userMapper.selectHrEmployeeList(query);
        return rows == null ? Collections.emptyList() : rows;
    }

    /** Active employees used by readiness, completeness and other current-workforce governance. */
    @DataScope(deptAlias = "d")
    public List<SysUser> listActiveScoped(HrEmployeeQuery query)
    {
        query=HrEmployeePopulationPolicy.applyActiveGovernance(query);
        healthDate(query);
        List<SysUser> rows=userMapper.selectHrEmployeeList(query);
        return rows==null?Collections.emptyList():rows;
    }

    @DataScope(deptAlias = "d")
    public SysUser findScoped(HrEmployeeQuery query)
    {
        if (query == null || query.getUserId() == null) throw denied();
        HrEmployeePopulationPolicy.applyArchiveAccess(query);
        healthDate(query);
        List<SysUser> rows = userMapper.selectHrEmployeeList(query);
        if (rows == null || rows.isEmpty()) throw denied();
        return rows.get(0);
    }

    @DataScope(deptAlias = "d")
    public SysUser findActiveScoped(HrEmployeeQuery query)
    {
        if(query==null||query.getUserId()==null)throw denied();
        HrEmployeePopulationPolicy.applyActiveGovernance(query);
        healthDate(query);
        List<SysUser> rows=userMapper.selectHrEmployeeList(query);
        if(rows==null||rows.isEmpty())throw denied();
        return rows.get(0);
    }

    /** Lock and authorize the same authoritative row inside the caller's transaction. */
    @DataScope(deptAlias = "d")
    public SysUser lockScoped(HrEmployeeQuery query)
    {
        if(query==null||query.getUserId()==null)throw denied();
        HrEmployeePopulationPolicy.applyArchiveAccess(query);
        healthDate(query);
        SysUser row=userMapper.selectHrEmployeeForUpdate(query);
        if(row==null)throw denied();
        return row;
    }

    /** Lock an active-governance employee row inside the caller's transaction. */
    @DataScope(deptAlias = "d")
    public SysUser lockActiveScoped(HrEmployeeQuery query)
    {
        if(query==null||query.getUserId()==null)throw denied();
        HrEmployeePopulationPolicy.applyActiveGovernance(query);
        healthDate(query);
        SysUser row=userMapper.selectHrEmployeeForUpdate(query);
        if(row==null)throw denied();
        return row;
    }

    @DataScope(deptAlias = "d")
    public List<SysDept> listScopedDepartments(SysDept query)
    {
        if(deptMapper==null)return Collections.emptyList();
        if(query==null)query=new SysDept(); query.setStatus("0");
        List<SysDept> rows=deptMapper.selectDeptList(query);
        return rows==null?Collections.emptyList():rows;
    }

    @DataScope(deptAlias = "d")
    public List<SysUser> listScopedUserOptions(SysUser query)
    {
        if(onboardingMapper==null)return Collections.emptyList();
        if(query==null)query=new SysUser(); query.setStatus("0");
        List<SysUser> rows=onboardingMapper.selectScopedUserOptions(query);
        return rows==null?Collections.emptyList():rows;
    }

    @DataScope(deptAlias = "d")
    public SysDept requireScopedDepartment(Long deptId)
    {
        if(deptId==null)throw new ServiceException("目标组织不能为空");
        SysDept query=new SysDept(); query.setDeptId(deptId); query.setStatus("0");
        if(deptMapper==null)throw new ServiceException("目标组织校验不可用");
        List<SysDept> rows=deptMapper.selectDeptList(query);
        if(rows==null||rows.isEmpty())throw new ServiceException(
                "目标组织不存在、已停用或无权访问", HttpStatus.FORBIDDEN);
        return rows.get(0);
    }

    private void healthDate(HrEmployeeQuery query)
    {
        query.getParams().put("healthAsOfDate", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
    }

    private ServiceException denied() { return new ServiceException("无权访问该员工或记录不存在"); }
}
