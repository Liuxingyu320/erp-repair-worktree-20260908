package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveCapacityConfig;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.domain.DrivePersonalQuotaPolicy;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.DriveUserQuotaContext;
import com.erp.file.drive.domain.dto.DriveQuotaImpactRequest;
import com.erp.file.drive.domain.dto.DriveOrganizationBatchTarget;
import com.erp.file.drive.domain.vo.DriveQuotaImpactVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveCapacityMapper;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DrivePersonalQuotaPolicyMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘额度变更影响预览")
class DriveQuotaImpactServiceTest
{
    private DrivePersonalQuotaPolicyMapper policyMapper;
    private DriveCapacityMapper capacityMapper;
    private DriveOrganizationMapper organizationMapper;
    private DriveSpaceMapper spaceMapper;
    private DriveOrganizationBudgetService budgetService;
    private DriveOrganizationTypeRuleService typeRuleService;
    private DriveQuotaImpactService service;

    @BeforeEach
    void setUp()
    {
        policyMapper = mock(DrivePersonalQuotaPolicyMapper.class);
        capacityMapper = mock(DriveCapacityMapper.class);
        organizationMapper = mock(DriveOrganizationMapper.class);
        spaceMapper = mock(DriveSpaceMapper.class);
        budgetService = mock(DriveOrganizationBudgetService.class);
        typeRuleService = mock(DriveOrganizationTypeRuleService.class);
        service = new DriveQuotaImpactService(policyMapper,
                organizationMapper, capacityMapper, spaceMapper,
                mock(DriveOrganizationConfigService.class),
                budgetService, typeRuleService,
                mock(DriveOrganizationScopeService.class),
                mock(DriveCapacityService.class), new DriveAuthorizationService(),
                new DriveProperties());
    }

    @Test
    @DisplayName("个人例外预览计算受影响人数、逻辑分配和降额后超额量")
    void shouldPreviewPersonalOverrideWithoutWriting()
    {
        when(policyMapper.selectAllPolicies()).thenReturn(List.of(global(100L)));
        when(policyMapper.selectActiveUserContexts()).thenReturn(List.of(user(20L)));
        when(spaceMapper.selectByType(DriveConstants.SPACE_PERSONAL))
                .thenReturn(List.of(personalSpace(20L, 80L, 1)));
        when(capacityMapper.selectConfig(1L)).thenReturn(capacity(1_000L));
        DriveQuotaImpactRequest request = userOverride(20L, 50L);

        DriveQuotaImpactVo result = service.preview(request, admin());

        assertThat(result.affectedCount()).isEqualTo(1);
        assertThat(result.beforeAllocatedBytes()).isEqualTo(100L);
        assertThat(result.afterAllocatedBytes()).isEqualTo(50L);
        assertThat(result.deltaBytes()).isEqualTo(-50L);
        assertThat(result.overQuotaCount()).isEqualTo(1);
        assertThat(result.overQuotaBytes()).isEqualTo(30L);
        assertThat(result.impactHash()).hasSize(64);
    }

    @Test
    @DisplayName("预览后用量变化使旧影响哈希失效")
    void shouldRejectImpactHashAfterUsageChanges()
    {
        when(policyMapper.selectAllPolicies()).thenReturn(List.of(global(100L)));
        when(policyMapper.selectActiveUserContexts()).thenReturn(List.of(user(20L)));
        when(spaceMapper.selectByType(DriveConstants.SPACE_PERSONAL)).thenReturn(
                List.of(personalSpace(20L, 40L, 1)),
                List.of(personalSpace(20L, 60L, 2)));
        when(capacityMapper.selectConfig(1L)).thenReturn(capacity(1_000L));
        DriveQuotaImpactRequest request = userOverride(20L, 50L);
        String hash = service.preview(request, admin()).impactHash();

        assertThatThrownBy(() -> service.verify(request, hash, admin()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_IMPACT_STALE);
    }

    @Test
    @DisplayName("自动建盘规则预览计入尚未配置的符合组织")
    void shouldPreviewAutomaticOrganizationTypeRule()
    {
        DriveOrganization company = organization(8L, "COMPANY");
        DriveOrganizationSpaceConfig generated = organizationConfig(8L, 40L);
        DriveOrganizationTypeRule current = typeRule("COMPANY", false, 40L, 0);
        when(organizationMapper.selectConfigs()).thenReturn(List.of());
        when(organizationMapper.selectTypeRules()).thenReturn(List.of(current));
        when(typeRuleService.project(any())).thenReturn(
                new DriveOrganizationTypeRuleService.Projection(
                        List.of(generated), List.of(company), 1));
        when(spaceMapper.selectByType(DriveConstants.SPACE_DEPARTMENT))
                .thenReturn(List.of());
        when(capacityMapper.selectConfig(1L)).thenReturn(organizationCapacity(100L));

        DriveQuotaImpactVo result = service.preview(typeRuleImpact(), admin());

        assertThat(result.changeType()).isEqualTo(
                DriveConstants.IMPACT_ORGANIZATION_TYPE_RULE);
        assertThat(result.affectedCount()).isEqualTo(1);
        assertThat(result.beforeAllocatedBytes()).isZero();
        assertThat(result.afterAllocatedBytes()).isEqualTo(40L);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("组织批量预览同时统计容量变化和降额超额量")
    void shouldPreviewSelectedOrganizationBatch()
    {
        DriveOrganization first = organization(8L, "STORE");
        DriveOrganization second = organization(9L, "STORE");
        when(organizationMapper.selectAllOrganizations()).thenReturn(List.of(first, second));
        when(organizationMapper.selectConfigs()).thenReturn(List.of());
        when(spaceMapper.selectByType(DriveConstants.SPACE_DEPARTMENT))
                .thenReturn(List.of(organizationSpace(8L, 150L)));
        when(capacityMapper.selectConfig(1L)).thenReturn(organizationCapacity(500L));

        DriveQuotaImpactVo result = service.preview(batchImpact(), admin());

        assertThat(result.changeType()).isEqualTo(DriveConstants.IMPACT_ORGANIZATION_BATCH);
        assertThat(result.affectedCount()).isEqualTo(2);
        assertThat(result.afterAllocatedBytes()).isEqualTo(200L);
        assertThat(result.overQuotaCount()).isEqualTo(1);
        assertThat(result.overQuotaBytes()).isEqualTo(50L);
    }

    private static DriveQuotaImpactRequest userOverride(Long userId, Long quota)
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_PERSONAL_POLICY);
        value.setSubjectType(DriveConstants.QUOTA_SUBJECT_USER);
        value.setSubjectId(userId);
        value.setQuotaBytes(quota);
        value.setPriority(0);
        value.setVersion(0);
        return value;
    }

    private static DrivePersonalQuotaPolicy global(long quota)
    {
        DrivePersonalQuotaPolicy value = new DrivePersonalQuotaPolicy();
        value.setPolicyId(1L);
        value.setSubjectType(DriveConstants.QUOTA_SUBJECT_GLOBAL);
        value.setSubjectId(0L);
        value.setQuotaBytes(quota);
        value.setPriority(0);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setVersion(0);
        return value;
    }

    private static DriveUserQuotaContext user(Long userId)
    {
        DriveUserQuotaContext value = new DriveUserQuotaContext();
        value.setUserId(userId);
        value.setUserName("u" + userId);
        return value;
    }

    private static DriveSpace personalSpace(Long userId, long used, int version)
    {
        DriveSpace value = new DriveSpace();
        value.setOwnerUserId(userId);
        value.setUsedBytes(used);
        value.setVersion(version);
        return value;
    }

    private static DriveCapacityConfig capacity(long personalPool)
    {
        DriveCapacityConfig value = new DriveCapacityConfig();
        value.setConfigId(1L);
        value.setPersonalPoolBytes(personalPool);
        return value;
    }

    private static DriveQuotaImpactRequest typeRuleImpact()
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_ORGANIZATION_TYPE_RULE);
        value.setSubjectType("COMPANY");
        value.setEnabled(true);
        value.setRequireActiveMember(true);
        value.setQuotaBytes(40L);
        value.setRuleStatus(DriveConstants.STATUS_ACTIVE);
        value.setVersion(0);
        return value;
    }

    private static DriveQuotaImpactRequest batchImpact()
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_ORGANIZATION_BATCH);
        value.setTargets(List.of(target(8L), target(9L)));
        value.setEnabled(true);
        value.setQuotaBytes(100L);
        value.setMemberWriteMode(DriveConstants.ORG_WRITE_PERMISSION_ONLY);
        value.setLifecycleStatus(DriveConstants.STATUS_ACTIVE);
        return value;
    }

    private static DriveOrganizationBatchTarget target(Long deptId)
    {
        DriveOrganizationBatchTarget value = new DriveOrganizationBatchTarget();
        value.setDeptId(deptId);
        value.setVersion(0);
        return value;
    }

    private static DriveOrganization organization(Long deptId, String type)
    {
        DriveOrganization value = new DriveOrganization();
        value.setDeptId(deptId);
        value.setDeptType(type);
        value.setStatus("0");
        value.setDelFlag("0");
        value.setActiveMemberCount(1L);
        return value;
    }

    private static DriveOrganizationSpaceConfig organizationConfig(Long deptId, long quota)
    {
        DriveOrganizationSpaceConfig value = new DriveOrganizationSpaceConfig();
        value.setDeptId(deptId);
        value.setEnabled(true);
        value.setQuotaBytes(quota);
        value.setLifecycleStatus(DriveConstants.STATUS_ACTIVE);
        return value;
    }

    private static DriveOrganizationTypeRule typeRule(String type, boolean auto,
            long quota, int version)
    {
        DriveOrganizationTypeRule value = new DriveOrganizationTypeRule();
        value.setDeptType(type);
        value.setAutoEnable(auto);
        value.setRequireActiveMember(true);
        value.setDefaultQuotaBytes(quota);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setVersion(version);
        return value;
    }

    private static DriveSpace organizationSpace(Long deptId, long used)
    {
        DriveSpace value = new DriveSpace();
        value.setDeptId(deptId);
        value.setUsedBytes(used);
        value.setVersion(0);
        return value;
    }

    private static DriveCapacityConfig organizationCapacity(long pool)
    {
        DriveCapacityConfig value = new DriveCapacityConfig();
        value.setConfigId(1L);
        value.setOrganizationPoolBytes(pool);
        return value;
    }

    private static DriveActor admin()
    {
        return new DriveActor(1L, null, null, "admin", Set.of(), true);
    }
}
