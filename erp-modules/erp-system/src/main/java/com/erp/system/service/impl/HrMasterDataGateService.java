package com.erp.system.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDept;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysConfigService;

/** Resource-local write gate for changes that could create new HR master-data P0 findings. */
@Service
public class HrMasterDataGateService
{
    private static final Set<String> TRUE_VALUES=Set.of("true","1","yes","on");
    private static final Set<String> FALSE_VALUES=Set.of("false","0","no","off");
    private static final Set<String> NON_DEPARTMENT_TYPES=Set.of("GROUP","STORE","WAREHOUSE");

    private final ISysConfigService configService;
    private final SysDeptMapper deptMapper;
    private final SysUserPostMapper userPostMapper;

    public HrMasterDataGateService(ISysConfigService configService,SysDeptMapper deptMapper,
            SysUserPostMapper userPostMapper)
    {
        this.configService=configService;
        this.deptMapper=deptMapper;
        this.userPostMapper=userPostMapper;
    }

    public boolean enforcementEnabled()
    {
        String raw=configService.selectConfigByKey(HrMasterDataReadinessService.ENFORCEMENT_ENABLED_KEY);
        String value=raw==null?null:raw.trim().toLowerCase(Locale.ROOT);
        if(value==null||value.isEmpty())return false;
        if(TRUE_VALUES.contains(value))return true;
        if(FALSE_VALUES.contains(value))return false;
        return false;
    }

    public void validateDepartmentChange(SysDept proposed,SysDept previous)
    {
        if(!enforcementEnabled()||proposed==null||!"0".equals(proposed.getStatus())
                ||!affectsDepartmentReadiness(proposed,previous))return;
        List<SysDept> rows=deptMapper.selectDeptList(new SysDept());
        Map<Long,SysDept> byId=new HashMap<>();
        for(SysDept row:rows==null?Collections.<SysDept>emptyList():rows)
            if(row!=null&&row.getDeptId()!=null)byId.put(row.getDeptId(),row);
        if(proposed.getDeptId()!=null)byId.put(proposed.getDeptId(),proposed);

        List<SysDept> path=resolveProposedPath(proposed,byId);
        if(path.isEmpty())throw departmentFailure("ORG_PATH_INVALID",proposed,"组织路径无效、父组织不存在或已停用");
        String proposedType=type(proposed);
        if("GROUP".equals(proposedType))
        {
            if(path.size()!=1||!Long.valueOf(0L).equals(parentId(proposed)))
                throw departmentFailure("ORG_PATH_INVALID",proposed,"集团节点必须是根组织");
            return;
        }
        int groupIndex=nearestTypeIndex(path,"GROUP");
        int companyIndex=groupIndex+1;
        if(groupIndex<0||companyIndex>=path.size()||!isDepartmentLayer(path.get(companyIndex)))
            throw departmentFailure("COMPANY_NODE_MISSING",proposed,"组织路径必须符合“集团 → 公司”规则");
        long storeCount=path.stream().filter(value->"STORE".equals(type(value))).count();
        int storeIndex=nearestTypeIndex(path,"STORE");
        if(storeCount>1||(storeIndex>=0&&storeIndex<path.size()-1))
            throw departmentFailure("STORE_MAPPING_MISSING",proposed,"门店节点必须是组织路径叶子且只能出现一次");
        int levelEnd=storeIndex>companyIndex?storeIndex:path.size();
        long layers=path.subList(companyIndex+1,levelEnd).stream().filter(this::isDepartmentLayer).count();
        if(layers>3)throw departmentFailure("ORG_PATH_INVALID",proposed,"公司与门店之间最多允许三级部门");
        if(isDepartmentLayer(proposed)&&(proposed.getLeaderUserId()==null||blank(proposed.getLeader())))
            throw departmentFailure("DEPT_LEADER_MISSING",proposed,"启用的公司或部门必须选择有效负责人");
    }

    public void validatePostDisableOrDelete(Long postId)
    {
        if(!enforcementEnabled()||postId==null)return;
        if(userPostMapper.countActiveEmployeePostById(postId)>0)
            throw new ServiceException("POST_MISSING_OR_DISABLED: 在岗员工仍在使用该岗位；处理入口 /system/post?postId="+postId);
    }

    private boolean affectsDepartmentReadiness(SysDept proposed,SysDept previous)
    {
        if(previous==null)return true;
        if(!"0".equals(previous.getStatus()))return true;
        return !Objects.equals(proposed.getParentId(),previous.getParentId())
                ||!Objects.equals(type(proposed),type(previous))
                ||!Objects.equals(proposed.getLeaderUserId(),previous.getLeaderUserId())
                ||!Objects.equals(trim(proposed.getLeader()),trim(previous.getLeader()));
    }

    private List<SysDept> resolveProposedPath(SysDept proposed,Map<Long,SysDept> byId)
    {
        java.util.ArrayList<SysDept> reverse=new java.util.ArrayList<>();
        Set<Long> seen=new HashSet<>();
        SysDept current=proposed;
        while(current!=null)
        {
            if(!"0".equals(current.getStatus()))return Collections.emptyList();
            if(current.getDeptId()!=null&&!seen.add(current.getDeptId()))return Collections.emptyList();
            reverse.add(current);
            Long parentId=parentId(current);
            if(parentId==0)break;
            current=byId.get(parentId);
            if(current==null)return Collections.emptyList();
        }
        Collections.reverse(reverse);
        return reverse;
    }

    private ServiceException departmentFailure(String code,SysDept dept,String message)
    {
        String id=dept.getDeptId()==null?"new":String.valueOf(dept.getDeptId());
        return new ServiceException(code+": "+message+"；处理入口 /system/dept?deptId="+id);
    }

    private int nearestTypeIndex(List<SysDept> path,String expected)
    {for(int index=path.size()-1;index>=0;index--)if(expected.equals(type(path.get(index))))return index;return -1;}
    private boolean isDepartmentLayer(SysDept dept){return dept!=null&&!NON_DEPARTMENT_TYPES.contains(type(dept));}
    private String type(SysDept dept){return dept==null||dept.getDeptType()==null?"":dept.getDeptType().trim().toUpperCase(Locale.ROOT);}
    private Long parentId(SysDept dept){return dept.getParentId()==null?0L:dept.getParentId();}
    private boolean blank(String value){return value==null||value.isBlank();}
    private String trim(String value){return value==null?null:value.trim();}
}
