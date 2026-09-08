package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveEffectiveQuota;
import com.erp.file.drive.domain.DrivePersonalQuotaPolicy;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.DriveUserQuotaContext;
import com.erp.file.drive.mapper.DrivePersonalQuotaPolicyMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘个人有效额度解析")
class DriveEffectiveQuotaServiceTest
{
    private DrivePersonalQuotaPolicyMapper policyMapper;
    private DriveSpaceMapper spaceMapper;
    private DriveProperties properties;
    private DriveEffectiveQuotaService service;

    @BeforeEach
    void setUp()
    {
        policyMapper = mock(DrivePersonalQuotaPolicyMapper.class);
        spaceMapper = mock(DriveSpaceMapper.class);
        properties = new DriveProperties();
        properties.setQuotaPolicyEnabled(true);
        service = new DriveEffectiveQuotaService(policyMapper, spaceMapper, properties);
    }

    @Test
    @DisplayName("个人例外优先于岗位和全员默认")
    void shouldPreferActiveUserOverride()
    {
        DrivePersonalQuotaPolicy user = policy(7L, DriveConstants.QUOTA_SUBJECT_USER,
                20L, 8_000L, 0, null);
        when(policyMapper.selectActiveUserPolicy(org.mockito.ArgumentMatchers.eq(20L), any()))
                .thenReturn(user);

        DriveEffectiveQuota result = service.resolve(20L);

        assertThat(result.quotaBytes()).isEqualTo(8_000L);
        assertThat(result.sourceType()).isEqualTo(DriveConstants.QUOTA_SUBJECT_USER);
        assertThat(result.sourceId()).isEqualTo(20L);
        verify(policyMapper, never()).selectActivePostPolicies(
                org.mockito.ArgumentMatchers.anyLong(), any());
        verify(policyMapper, never()).selectGlobalPolicy();
    }

    @Test
    @DisplayName("多岗位先按优先级再按较大额度确定性选择")
    void shouldResolveMultiplePostsDeterministically()
    {
        DrivePersonalQuotaPolicy expired = policy(8L, DriveConstants.QUOTA_SUBJECT_USER,
                20L, 9_000L, 0, new Date(System.currentTimeMillis() - 1_000L));
        DrivePersonalQuotaPolicy smaller = policy(10L, DriveConstants.QUOTA_SUBJECT_POST,
                100L, 3_000L, 30, null);
        smaller.setSubjectName("店长");
        DrivePersonalQuotaPolicy larger = policy(11L, DriveConstants.QUOTA_SUBJECT_POST,
                101L, 5_000L, 30, null);
        larger.setSubjectName("区域经理");
        when(policyMapper.selectActiveUserPolicy(org.mockito.ArgumentMatchers.eq(20L), any()))
                .thenReturn(expired);
        when(policyMapper.selectActivePostPolicies(org.mockito.ArgumentMatchers.eq(20L), any()))
                .thenReturn(List.of(smaller, larger));

        DriveEffectiveQuota result = service.resolve(20L);

        assertThat(result.quotaBytes()).isEqualTo(5_000L);
        assertThat(result.sourceType()).isEqualTo(DriveConstants.QUOTA_SUBJECT_POST);
        assertThat(result.sourceId()).isEqualTo(101L);
        assertThat(result.sourceLabel()).isEqualTo("岗位：区域经理");
    }

    @Test
    @DisplayName("策略关闭时保持旧个人盘默认额度")
    void shouldUseLegacyPropertyWhilePolicyFeatureIsDisabled()
    {
        properties.setQuotaPolicyEnabled(false);
        properties.setPersonalQuota(2_048L);

        DriveEffectiveQuota result = service.resolve(20L);

        assertThat(result.quotaBytes()).isEqualTo(2_048L);
        assertThat(result.sourceType()).isEqualTo(DriveConstants.QUOTA_SOURCE_GLOBAL);
        verify(policyMapper, never()).selectActiveUserPolicy(any(), any());
    }

    @Test
    @DisplayName("策略运行开关关闭时管理面仍按预配置额度核算容量")
    void shouldUseConfiguredPoliciesForAdministrationWhileRuntimeIsDisabled()
    {
        properties.setQuotaPolicyEnabled(false);
        properties.setPersonalQuota(2_048L);
        DrivePersonalQuotaPolicy global = policy(1L,
                DriveConstants.QUOTA_SUBJECT_GLOBAL, 0L, 4_096L, 0, null);
        DriveUserQuotaContext context = new DriveUserQuotaContext();
        context.setUserId(20L);
        context.setUserName("alice");
        context.setNickName("张三");
        context.setDeptId(8L);
        when(policyMapper.selectActiveUserContexts()).thenReturn(List.of(context));
        when(policyMapper.selectAllPolicies()).thenReturn(List.of(global));
        when(spaceMapper.selectByType(DriveConstants.SPACE_PERSONAL)).thenReturn(List.of());

        assertThat(service.listEffectiveUsers()).singleElement()
                .extracting("quotaBytes", "quotaSourceLabel")
                .containsExactly(4_096L, "全员默认");
        assertThat(service.resolve(20L).quotaBytes()).isEqualTo(2_048L);
    }

    @Test
    @DisplayName("物化额度允许降到当前用量以下且不删文件")
    void shouldMaterializeQuotaBelowCurrentUsage()
    {
        DrivePersonalQuotaPolicy global = policy(1L, DriveConstants.QUOTA_SUBJECT_GLOBAL,
                0L, 1_000L, 0, null);
        when(policyMapper.selectActivePostPolicies(org.mockito.ArgumentMatchers.eq(20L), any()))
                .thenReturn(List.of());
        when(policyMapper.selectGlobalPolicy()).thenReturn(global);
        DriveSpace original = personalSpace(9L, 20L, 8_000L, 6_000L, 3);
        DriveSpace updated = personalSpace(9L, 20L, 1_000L, 6_000L, 4);
        updated.setQuotaSourceType(DriveConstants.QUOTA_SOURCE_GLOBAL);
        when(spaceMapper.updateEffectiveQuota(9L, 1_000L,
                DriveConstants.QUOTA_SOURCE_GLOBAL, null, 3, "manager")).thenReturn(1);
        when(spaceMapper.selectById(9L)).thenReturn(updated);

        DriveSpace result = service.reconcilePersonalSpace(original, 20L, "manager");

        assertThat(result.getQuotaBytes()).isEqualTo(1_000L);
        assertThat(result.getUsedBytes()).isEqualTo(6_000L);
        verify(spaceMapper).updateEffectiveQuota(9L, 1_000L,
                DriveConstants.QUOTA_SOURCE_GLOBAL, null, 3, "manager");
    }

    private static DrivePersonalQuotaPolicy policy(Long policyId, String type,
            Long subjectId, long bytes, int priority, Date expireTime)
    {
        DrivePersonalQuotaPolicy value = new DrivePersonalQuotaPolicy();
        value.setPolicyId(policyId);
        value.setSubjectType(type);
        value.setSubjectId(subjectId);
        value.setQuotaBytes(bytes);
        value.setPriority(priority);
        value.setExpireTime(expireTime);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setVersion(0);
        return value;
    }

    private static DriveSpace personalSpace(Long spaceId, Long userId,
            long quota, long used, int version)
    {
        DriveSpace value = new DriveSpace();
        value.setSpaceId(spaceId);
        value.setSpaceType(DriveConstants.SPACE_PERSONAL);
        value.setOwnerUserId(userId);
        value.setQuotaBytes(quota);
        value.setUsedBytes(used);
        value.setQuotaSourceType(DriveConstants.QUOTA_SOURCE_LEGACY);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setVersion(version);
        return value;
    }
}
