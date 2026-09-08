package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.vo.DriveOrganizationBudgetViolationVo;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘组织树预算漂移")
class DriveOrganizationBudgetServiceTest
{
    @Test
    @DisplayName("有额度的子组织换到预算不足的上级后实时识别受影响树")
    void shouldDetectViolationAfterOrganizationReparent()
    {
        DriveOrganizationMapper mapper = mock(DriveOrganizationMapper.class);
        DriveOrganizationBudgetService service = new DriveOrganizationBudgetService(mapper);
        when(mapper.selectAllOrganizations()).thenReturn(List.of(
                org(1L, "甲公司", "0", "0", "0"),
                org(2L, "乙公司", "0", "0", "0"),
                org(3L, "门店", "0,2", "0", "0")));
        when(mapper.selectConfigs()).thenReturn(List.of(
                config(1L, 50L, 200L, true, DriveConstants.STATUS_ACTIVE),
                config(2L, 40L, 100L, true, DriveConstants.STATUS_ACTIVE),
                config(3L, 80L, null, true, DriveConstants.STATUS_ACTIVE)));

        List<DriveOrganizationBudgetViolationVo> result = service.detectViolations();

        assertThat(result).singleElement().satisfies(violation -> {
            assertThat(violation.budgetDeptId()).isEqualTo(2L);
            assertThat(violation.allocatedBytes()).isEqualTo(120L);
            assertThat(violation.exceededBytes()).isEqualTo(20L);
            assertThat(violation.affectedDeptIds()).containsExactly(2L, 3L);
        });
        assertThat(service.violationForDept(3L).budgetDeptName()).isEqualTo("乙公司");
        assertThat(service.violationForDept(1L)).isNull();
        assertThatThrownBy(() -> service.requireWriteAllowed(3L))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED);
    }

    @Test
    @DisplayName("多层预算同时违规时保留全部根并为组织选择最近上级")
    void shouldChooseNearestOfNestedViolations()
    {
        DriveOrganizationMapper mapper = mock(DriveOrganizationMapper.class);
        DriveOrganizationBudgetService service = new DriveOrganizationBudgetService(mapper);
        when(mapper.selectAllOrganizations()).thenReturn(List.of(
                org(1L, "集团", "0", "0", "0"),
                org(2L, "区域", "0,1", "0", "0"),
                org(3L, "门店", "0,1,2", "0", "0")));
        when(mapper.selectConfigs()).thenReturn(List.of(
                config(1L, 30L, 100L, true, DriveConstants.STATUS_ACTIVE),
                config(2L, 40L, 60L, true, DriveConstants.STATUS_ACTIVE),
                config(3L, 50L, null, true, DriveConstants.STATUS_ACTIVE)));

        List<DriveOrganizationBudgetViolationVo> violations = service.detectViolations();

        assertThat(violations).extracting(
                DriveOrganizationBudgetViolationVo::budgetDeptId)
                .containsExactly(2L, 1L);
        assertThat(service.violationForDept(3L).budgetDeptId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("停用、删除、归档或禁用的组织配置不计入逻辑分配")
    void shouldExcludeInactiveAndDisabledAllocations()
    {
        DriveOrganizationBudgetService service = new DriveOrganizationBudgetService(
                mock(DriveOrganizationMapper.class));
        List<DriveOrganization> organizations = List.of(
                org(1L, "公司", "0", "0", "0"),
                org(2L, "禁用配置", "0,1", "0", "0"),
                org(3L, "已归档", "0,1", "0", "0"),
                org(4L, "已停用", "0,1", "1", "0"),
                org(5L, "已删除", "0,1", "0", "2"));
        List<DriveOrganizationSpaceConfig> configs = List.of(
                config(1L, 10L, 20L, true, DriveConstants.STATUS_ACTIVE),
                config(2L, 100L, null, false, DriveConstants.STATUS_ACTIVE),
                config(3L, 100L, null, true, DriveConstants.STATUS_ARCHIVED),
                config(4L, 100L, null, true, DriveConstants.STATUS_ACTIVE),
                config(5L, 100L, null, true, DriveConstants.STATUS_ACTIVE));

        assertThat(service.detectViolations(configs, organizations)).isEmpty();
    }

    @Test
    @DisplayName("组织 ancestors 缺失、引用不存在节点或形成循环时保守阻断且不递归")
    void shouldFailClosedForMalformedMissingAndCyclicHierarchy()
    {
        DriveOrganizationBudgetService service = new DriveOrganizationBudgetService(
                mock(DriveOrganizationMapper.class));
        List<DriveOrganization> organizations = List.of(
                org(1L, "正常公司", "0", "0", "0"),
                org(2L, "缺失祖先", null, "0", "0"),
                org(3L, "引用不存在节点", "0,999", "0", "0"),
                org(4L, "循环甲", "0,5", "0", "0"),
                org(5L, "循环乙", "0,4", "0", "0"));
        List<DriveOrganizationSpaceConfig> configs = List.of(
                config(1L, 10L, null, true, DriveConstants.STATUS_ACTIVE),
                config(3L, 10L, null, true, DriveConstants.STATUS_ACTIVE));

        List<DriveOrganizationBudgetViolationVo> result =
                service.detectViolations(configs, organizations);

        assertThat(result).singleElement().satisfies(violation -> {
            assertThat(violation.hierarchyInvalid()).isTrue();
            assertThat(violation.violationMessage()).contains("组织目录层级数据异常");
            assertThat(violation.affectedDeptIds()).containsExactly(1L, 3L);
        });
        assertThat(service.indexByAffectedDept(result)).containsKeys(1L, 3L);
    }

    private static DriveOrganization org(Long id, String name, String ancestors,
            String status, String delFlag)
    {
        DriveOrganization value = new DriveOrganization();
        value.setDeptId(id);
        value.setDeptName(name);
        value.setAncestors(ancestors);
        value.setStatus(status);
        value.setDelFlag(delFlag);
        return value;
    }

    private static DriveOrganizationSpaceConfig config(Long deptId, long quota,
            Long budget, boolean enabled, String lifecycle)
    {
        DriveOrganizationSpaceConfig value = new DriveOrganizationSpaceConfig();
        value.setDeptId(deptId);
        value.setQuotaBytes(quota);
        value.setTreeBudgetBytes(budget);
        value.setEnabled(enabled);
        value.setLifecycleStatus(lifecycle);
        return value;
    }
}
