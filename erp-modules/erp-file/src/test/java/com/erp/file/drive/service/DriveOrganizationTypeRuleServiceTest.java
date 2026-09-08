package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘组织类型自动建盘规则")
class DriveOrganizationTypeRuleServiceTest
{
    private DriveOrganizationMapper mapper;
    private DriveOrganizationBudgetService budgetService;
    private DriveCapacityService capacityService;
    private DriveOrganizationTypeRuleService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(DriveOrganizationMapper.class);
        budgetService = mock(DriveOrganizationBudgetService.class);
        capacityService = mock(DriveCapacityService.class);
        service = new DriveOrganizationTypeRuleService(mapper, budgetService,
                capacityService, new DriveAuthorizationService());
    }

    @Test
    @DisplayName("启用公司自动规则前模拟未配置组织并校验容量")
    void shouldProjectEligibleOrganizationsBeforeUpdatingRule()
    {
        DriveOrganizationTypeRule current = rule(false, 40L, 2);
        DriveOrganizationTypeRule saved = rule(true, 40L, 3);
        DriveOrganization company = organization(8L, 2L);
        when(mapper.selectTypeRule("COMPANY")).thenReturn(current, saved);
        when(mapper.selectAllOrganizations()).thenReturn(List.of(company));
        when(mapper.selectConfigs()).thenReturn(List.of());
        when(mapper.updateTypeRule(any())).thenReturn(1);

        DriveOrganizationTypeRule result = service.update("company", true,
                true, 40L, DriveConstants.STATUS_ACTIVE, 2,
                "开启公司组织盘", admin());

        assertThat(result).isSameAs(saved);
        verify(capacityService).lockForAllocationChange();
        verify(capacityService).requireOrganizationAllocationWithinPool(40L);
        verify(budgetService).validate(any(), any());
        verify(mapper).updateTypeRule(any());
    }

    @Test
    @DisplayName("规则版本过期时不覆盖其他管理员的修改")
    void shouldRejectStaleRuleVersion()
    {
        when(mapper.selectTypeRule("COMPANY")).thenReturn(rule(false, 40L, 4));

        assertThatThrownBy(() -> service.update("COMPANY", true, true, 40L,
                DriveConstants.STATUS_ACTIVE, 3, "并发修改", admin()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
    }

    private static DriveOrganizationTypeRule rule(boolean auto, long quota, int version)
    {
        DriveOrganizationTypeRule value = new DriveOrganizationTypeRule();
        value.setDeptType("COMPANY");
        value.setAutoEnable(auto);
        value.setRequireActiveMember(true);
        value.setDefaultQuotaBytes(quota);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setVersion(version);
        return value;
    }

    private static DriveOrganization organization(Long deptId, Long members)
    {
        DriveOrganization value = new DriveOrganization();
        value.setDeptId(deptId);
        value.setDeptType("COMPANY");
        value.setStatus("0");
        value.setDelFlag("0");
        value.setActiveMemberCount(members);
        return value;
    }

    private static DriveActor admin()
    {
        return new DriveActor(1L, null, null, "admin", Set.of(), true);
    }
}
