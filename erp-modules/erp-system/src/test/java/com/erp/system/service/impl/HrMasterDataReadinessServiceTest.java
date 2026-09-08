package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysDictData;
import com.erp.system.api.domain.SysDictType;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.constant.HrMasterDataIssueCodes;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.domain.vo.HrMasterDataIssueQuery;
import com.erp.system.mapper.HrOnboardingPositionConfigMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDictTypeService;
import com.erp.system.service.ISysRoleService;

@ExtendWith(MockitoExtension.class)
class HrMasterDataReadinessServiceTest
{
    @Mock private HrEmployeeAccessService employeeAccess;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysPostMapper postMapper;
    @Mock private SysUserPostMapper userPostMapper;
    @Mock private HrOnboardingPositionConfigMapper positionConfigMapper;
    @Mock private ISysConfigService configService;
    @Mock private ISysDictTypeService dictTypeService;
    @Mock private ISysRoleService roleService;
    private HrMasterDataReadinessService service;

    @BeforeEach
    void setUp()
    {
        service=new HrMasterDataReadinessService(employeeAccess,deptMapper,postMapper,userPostMapper,
                positionConfigMapper,configService,dictTypeService,roleService);
    }

    @Test
    void emitsStableReadOnlyFindingsAndReportsObservationMode()
    {
        SysDept root=dept(1L,0L,"0","集团","GROUP","负责人");
        SysDept company=dept(2L,1L,"0,1","公司","COMPANY","负责人");
        SysDept department=dept(3L,2L,"0,1,2","运营部","DEPARTMENT",null);
        when(employeeAccess.listScopedDepartments(any())).thenReturn(List.of(department));
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(root,company,department));

        SysUser employee=new SysUser();employee.setUserId(10L);employee.setDeptId(3L);employee.setNickName("测试员工");
        SysUserProfile profile=new SysUserProfile();profile.setEmployeeStatus("正式");profile.setEmployeeCategory("FULL_TIME");
        employee.setProfile(profile);
        when(employeeAccess.listActiveScoped(any())).thenReturn(List.of(employee));
        SysUserPost relation=new SysUserPost();relation.setUserId(10L);relation.setPostId(100L);
        when(userPostMapper.selectByUserIds(List.of(10L))).thenReturn(List.of(relation));
        SysPost post=new SysPost();post.setPostId(100L);post.setPostName("店员");post.setStatus("0");
        when(postMapper.selectPostAll()).thenReturn(List.of(post));

        HrOnboardingPositionConfig config=new HrOnboardingPositionConfig();config.setConfigId(1000L);
        config.setPostId(100L);config.setEmployeeCategory("FULL_TIME");config.setStatus("0");config.setAccountEnabled(true);
        when(positionConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(positionConfigMapper.selectRoleIds(1000L)).thenReturn(List.of());
        SysRole role=new SysRole();role.setRoleId(9L);role.setStatus("0");role.setDelFlag("0");
        when(roleService.selectRoleList(any())).thenReturn(List.of(role));

        when(configService.selectConfigByKey(anyString())).thenAnswer(invocation ->
                "hr.onboarding.dict_type.employeeCategory".equals(invocation.getArgument(0))
                        ?"hr_employee_category":null);
        SysDictType dictType=new SysDictType();dictType.setDictType("hr_employee_category");dictType.setStatus("0");
        when(dictTypeService.selectDictTypeByType("hr_employee_category")).thenReturn(dictType);
        SysDictData category=new SysDictData();category.setDictType("hr_employee_category");
        category.setDictValue("FULL_TIME");category.setStatus("0");
        when(dictTypeService.selectDictDataByType("hr_employee_category")).thenReturn(List.of(category));

        var issues=service.issues(new HrMasterDataIssueQuery());
        var summary=service.summary(new HrMasterDataIssueQuery());

        assertThat(issues).extracting("issueCode").contains(
                HrMasterDataIssueCodes.DEPT_LEADER_MISSING,
                HrMasterDataIssueCodes.POSITION_CONFIG_ROLE_MISSING,
                HrMasterDataIssueCodes.DICTIONARY_ROUTE_MISSING);
        assertThat(issues).filteredOn(value->HrMasterDataIssueCodes.DEPT_LEADER_MISSING.equals(value.getIssueCode()))
                .singleElement().satisfies(value->assertThat(value.getAffectedEmployeeCount()).isEqualTo(1));
        assertThat(summary.isReadinessEnabled()).isTrue();
        assertThat(summary.isObservationMode()).isTrue();
        assertThat(summary.isEnforcementEnabled()).isFalse();
        assertThat(summary.getBlockingIssueCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getAffectedEmployeeCount()).isEqualTo(1);
        assertThat(summary.getAffectedOccurrenceCount()).isEqualTo(2);
    }

    @Test
    void missingEmployeeProfileIsARepairableP0Finding()
    {
        SysDept root=dept(1L,0L,"0","集团","GROUP","负责人");
        SysDept company=dept(2L,1L,"0,1","公司","COMPANY","负责人");
        SysDept department=dept(3L,2L,"0,1,2","运营部","DEPARTMENT","负责人");
        when(employeeAccess.listScopedDepartments(any())).thenReturn(List.of(department));
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(root,company,department));
        SysUser employee=new SysUser();employee.setUserId(10L);employee.setDeptId(3L);employee.setNickName("缺档员工");
        when(employeeAccess.listActiveScoped(any())).thenReturn(List.of(employee));
        when(postMapper.selectPostAll()).thenReturn(List.of());
        when(positionConfigMapper.selectList(any())).thenReturn(List.of());
        when(roleService.selectRoleList(any())).thenReturn(List.of());

        var finding=service.issues(new HrMasterDataIssueQuery()).stream()
                .filter(value->HrMasterDataIssueCodes.EMPLOYEE_PROFILE_MISSING.equals(value.getIssueCode()))
                .findFirst().orElseThrow();

        assertThat(finding.getSeverity()).isEqualTo("P0");
        assertThat(finding.getAffectedEmployeeCount()).isEqualTo(1);
        assertThat(finding.getResourceId()).isEqualTo("10");
        assertThat(finding.getActionUrl()).isEqualTo("/hr/employee?userId=10&action=initializeProfile");
    }

    @Test
    void legacyLeaderTextWithoutStableIdentityIsAnUnresolvedP0Finding()
    {
        SysDept root=dept(1L,0L,"0","集团","GROUP","集团负责人");
        SysDept company=dept(2L,1L,"0,1","公司","COMPANY","历史负责人");
        company.setLeaderUserId(null);
        when(employeeAccess.listScopedDepartments(any())).thenReturn(List.of(company));
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(root,company));
        when(employeeAccess.listActiveScoped(any())).thenReturn(List.of());
        when(postMapper.selectPostAll()).thenReturn(List.of());
        when(positionConfigMapper.selectList(any())).thenReturn(List.of());
        when(roleService.selectRoleList(any())).thenReturn(List.of());

        var finding=service.issues(new HrMasterDataIssueQuery()).stream()
                .filter(value->HrMasterDataIssueCodes.DEPT_LEADER_UNRESOLVED.equals(value.getIssueCode()))
                .findFirst().orElseThrow();

        assertThat(finding.getSeverity()).isEqualTo("P0");
        assertThat(finding.getResourceId()).isEqualTo("2");
        assertThat(finding.getDetail()).contains("历史负责人");
        assertThat(finding.getActionUrl()).isEqualTo("/system/dept?deptId=2");
    }

    @Test
    void validStableLeaderIdentityDoesNotEmitLeaderFinding()
    {
        SysDept root=dept(1L,0L,"0","集团","GROUP","集团负责人");
        SysDept company=dept(2L,1L,"0,1","公司","COMPANY","负责人甲");
        company.setLeaderUserId(20L);
        when(employeeAccess.listScopedDepartments(any())).thenReturn(List.of(company));
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(root,company));
        SysUser leader=new SysUser();leader.setUserId(20L);leader.setNickName("负责人甲");leader.setStatus("0");
        when(employeeAccess.listActiveScoped(any())).thenReturn(List.of(leader));
        when(postMapper.selectPostAll()).thenReturn(List.of());
        when(userPostMapper.selectByUserIds(List.of(20L))).thenReturn(List.of());
        when(positionConfigMapper.selectList(any())).thenReturn(List.of());
        when(roleService.selectRoleList(any())).thenReturn(List.of());

        var issues=service.issues(new HrMasterDataIssueQuery());

        assertThat(issues).extracting("issueCode")
                .doesNotContain(HrMasterDataIssueCodes.DEPT_LEADER_MISSING,
                        HrMasterDataIssueCodes.DEPT_LEADER_UNRESOLVED);
    }

    @Test
    void rootGroupAnchorDoesNotCreateAPermanentCompanyFinding()
    {
        SysDept root=dept(1L,0L,"0","集团","GROUP",null);
        when(employeeAccess.listScopedDepartments(any())).thenReturn(List.of(root));
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(root));
        when(employeeAccess.listActiveScoped(any())).thenReturn(List.of());
        when(postMapper.selectPostAll()).thenReturn(List.of());
        when(positionConfigMapper.selectList(any())).thenReturn(List.of());
        when(roleService.selectRoleList(any())).thenReturn(List.of());

        assertThat(service.issues(new HrMasterDataIssueQuery()))
                .extracting("issueCode")
                .doesNotContain(HrMasterDataIssueCodes.COMPANY_NODE_MISSING,
                        HrMasterDataIssueCodes.DEPT_LEADER_MISSING,
                        HrMasterDataIssueCodes.DEPT_LEADER_UNRESOLVED);
    }

    @Test
    void disabledReadinessDoesNotScanAnyBusinessTable()
    {
        when(configService.selectConfigByKey(HrMasterDataReadinessService.READINESS_ENABLED_KEY)).thenReturn("false");

        assertThat(service.issues(new HrMasterDataIssueQuery())).isEmpty();

        verify(employeeAccess,never()).listScopedDepartments(any());
        verify(deptMapper,never()).selectDeptList(any());
    }

    @Test
    void invalidIssueCodeFilterIsRejected()
    {
        when(employeeAccess.listScopedDepartments(any())).thenReturn(List.of());
        when(deptMapper.selectDeptList(any())).thenReturn(List.of());
        when(employeeAccess.listActiveScoped(any())).thenReturn(List.of());
        when(postMapper.selectPostAll()).thenReturn(List.of());
        when(positionConfigMapper.selectList(any())).thenReturn(List.of());
        when(roleService.selectRoleList(any())).thenReturn(List.of());
        HrMasterDataIssueQuery query=new HrMasterDataIssueQuery();query.setIssueCode("UNKNOWN");

        assertThatThrownBy(()->service.issues(query)).isInstanceOf(ServiceException.class)
                .hasMessage("主数据问题代码无效");
    }

    private SysDept dept(Long id,Long parent,String ancestors,String name,String type,String leader)
    {
        SysDept value=new SysDept();value.setDeptId(id);value.setParentId(parent);value.setAncestors(ancestors);
        value.setDeptName(name);value.setDeptType(type);value.setLeader(leader);
        if(leader!=null&&!leader.isBlank())value.setLeaderUserId(id+1000L);
        value.setStatus("0");return value;
    }
}
