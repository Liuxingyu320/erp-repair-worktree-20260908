package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDept;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysConfigService;

class HrMasterDataGateServiceTest
{
    @Test
    void disabledGateDoesNotScanOrBlockWrites()
    {
        ISysConfigService config=mock(ISysConfigService.class);
        SysDeptMapper deptMapper=mock(SysDeptMapper.class);
        SysUserPostMapper userPostMapper=mock(SysUserPostMapper.class);
        when(config.selectConfigByKey(HrMasterDataReadinessService.ENFORCEMENT_ENABLED_KEY)).thenReturn("false");
        HrMasterDataGateService gate=new HrMasterDataGateService(config,deptMapper,userPostMapper);

        assertThatCode(()->gate.validateDepartmentChange(dept(2L,0L,"COMPANY",null,null),null))
                .doesNotThrowAnyException();
        assertThatCode(()->gate.validatePostDisableOrDelete(9L)).doesNotThrowAnyException();

        verifyNoInteractions(deptMapper,userPostMapper);
    }

    @Test
    void enabledGateRejectsOnlyTheInvalidProposedDepartmentPath()
    {
        ISysConfigService config=enabledConfig();
        SysDeptMapper deptMapper=mock(SysDeptMapper.class);
        SysUserPostMapper userPostMapper=mock(SysUserPostMapper.class);
        SysDept unrelated=dept(99L,0L,"COMPANY",null,null);
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(unrelated));
        HrMasterDataGateService gate=new HrMasterDataGateService(config,deptMapper,userPostMapper);

        assertThatThrownBy(()->gate.validateDepartmentChange(dept(2L,0L,"COMPANY",20L,"负责人"),null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("COMPANY_NODE_MISSING")
                .hasMessageContaining("/system/dept?deptId=2");
    }

    @Test
    void enabledGateAllowsValidRepairEvenWhenAnotherOrganizationIsInvalid()
    {
        ISysConfigService config=enabledConfig();
        SysDeptMapper deptMapper=mock(SysDeptMapper.class);
        SysUserPostMapper userPostMapper=mock(SysUserPostMapper.class);
        SysDept group=dept(1L,0L,"GROUP",null,null);
        SysDept unrelated=dept(99L,0L,"COMPANY",null,null);
        when(deptMapper.selectDeptList(any())).thenReturn(List.of(group,unrelated));
        HrMasterDataGateService gate=new HrMasterDataGateService(config,deptMapper,userPostMapper);

        assertThatCode(()->gate.validateDepartmentChange(dept(2L,1L,"COMPANY",20L,"负责人"),null))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledGateBlocksDisablingAnActivelyUsedPost()
    {
        ISysConfigService config=enabledConfig();
        SysDeptMapper deptMapper=mock(SysDeptMapper.class);
        SysUserPostMapper userPostMapper=mock(SysUserPostMapper.class);
        when(userPostMapper.countActiveEmployeePostById(9L)).thenReturn(2);
        HrMasterDataGateService gate=new HrMasterDataGateService(config,deptMapper,userPostMapper);

        assertThatThrownBy(()->gate.validatePostDisableOrDelete(9L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("POST_MISSING_OR_DISABLED")
                .hasMessageContaining("/system/post?postId=9");
        verify(userPostMapper).countActiveEmployeePostById(9L);
        verify(deptMapper,never()).selectDeptList(any());
    }

    private static ISysConfigService enabledConfig()
    {
        ISysConfigService config=mock(ISysConfigService.class);
        when(config.selectConfigByKey(HrMasterDataReadinessService.ENFORCEMENT_ENABLED_KEY)).thenReturn("true");
        return config;
    }

    private static SysDept dept(Long id,Long parentId,String type,Long leaderUserId,String leader)
    {
        SysDept dept=new SysDept();dept.setDeptId(id);dept.setParentId(parentId);dept.setDeptType(type);
        dept.setDeptName("组织"+id);dept.setStatus("0");dept.setLeaderUserId(leaderUserId);dept.setLeader(leader);
        return dept;
    }
}
