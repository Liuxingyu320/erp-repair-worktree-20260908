package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.IHrLifecycleService;
import com.erp.system.service.ISysConfigService;

@DisplayName("合同续签决策扫描")
class HrContractRenewalScannerTest
{
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private ISysConfigService configService;
    private SysUserProfileMapper profileMapper;
    private IHrLifecycleService lifecycleService;
    private Clock clock;
    private HrContractRenewalScanner scanner;

    @BeforeEach
    void setUp()
    {
        configService = mock(ISysConfigService.class);
        profileMapper = mock(SysUserProfileMapper.class);
        lifecycleService = mock(IHrLifecycleService.class);
        clock = Clock.fixed(Instant.parse("2026-07-11T16:30:00Z"), SHANGHAI);
        scanner = new HrContractRenewalScanner(
                configService, profileMapper, lifecycleService, clock);
    }

    @Test
    @DisplayName("每天上海时区02:15触发扫描")
    void shouldScheduleDailyAt0215InShanghai() throws Exception
    {
        Method scheduledMethod = HrContractRenewalScanner.class
                .getDeclaredMethod("scanRenewalDecisions");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 15 2 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }

    @Test
    @DisplayName("生产构造器在测试时钟构造器并存时仍可由Spring明确注入")
    void shouldMarkProductionConstructorForInjection() throws Exception
    {
        Constructor<HrContractRenewalScanner> constructor = HrContractRenewalScanner.class
                .getConstructor(ISysConfigService.class, SysUserProfileMapper.class,
                        IHrLifecycleService.class);

        assertThat(constructor.getAnnotation(Autowired.class)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "-1", "999999999999999999999"})
    @DisplayName("缺失空白非法或负数阈值都安全回退30天并包含两端边界")
    void shouldFallbackToThirtyDaysForMissingOrInvalidConfiguration(String configured)
    {
        when(configService.selectConfigByKey("sign.renewal.decision-days"))
                .thenReturn(configured);
        when(profileMapper.selectRenewalCandidateUserIds(
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11), 0L, 200))
                .thenReturn(List.of(11L, 12L));

        scanner.scanRenewalDecisions();

        verify(lifecycleService).createRenewalDecision(
                11L, LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));
        verify(lifecycleService).createRenewalDecision(
                12L, LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));
    }

    @Test
    @DisplayName("有效配置控制含边界的查询窗口")
    void shouldUseConfiguredDecisionWindow()
    {
        when(configService.selectConfigByKey("sign.renewal.decision-days"))
                .thenReturn("14");
        when(profileMapper.selectRenewalCandidateUserIds(
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 26), 0L, 200))
                .thenReturn(List.of(21L));

        scanner.scanRenewalDecisions();

        verify(lifecycleService).createRenewalDecision(
                21L, LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 26));
    }

    @Test
    @DisplayName("配置读取异常也使用30天安全窗口而不漏扫")
    void shouldFallbackWhenConfigurationLookupFails()
    {
        when(configService.selectConfigByKey("sign.renewal.decision-days"))
                .thenThrow(new IllegalStateException("config unavailable"));
        when(profileMapper.selectRenewalCandidateUserIds(
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11), 0L, 200))
                .thenReturn(List.of(31L));

        scanner.scanRenewalDecisions();

        verify(lifecycleService).createRenewalDecision(
                31L, LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));
    }

    @Test
    @DisplayName("单员工失败不会中断同批或下一批并以游标继续")
    void shouldIsolateEmployeeFailuresAndContinueBatches()
    {
        when(configService.selectConfigByKey("sign.renewal.decision-days"))
                .thenReturn("30");
        List<Long> fullBatch = java.util.stream.LongStream.rangeClosed(1, 200)
                .boxed().toList();
        when(profileMapper.selectRenewalCandidateUserIds(any(), any(), anyLong(), anyInt()))
                .thenReturn(fullBatch, List.of(201L));
        doThrow(new IllegalStateException("employee failed"))
                .when(lifecycleService).createRenewalDecision(
                        org.mockito.ArgumentMatchers.eq(2L), any(), any());

        scanner.scanRenewalDecisions();

        verify(lifecycleService).createRenewalDecision(1L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));
        verify(lifecycleService).createRenewalDecision(2L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));
        verify(lifecycleService).createRenewalDecision(3L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));
        verify(lifecycleService).createRenewalDecision(201L,
                LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 11));
        verify(profileMapper).selectRenewalCandidateUserIds(any(), any(),
                org.mockito.ArgumentMatchers.eq(200L), org.mockito.ArgumentMatchers.eq(200));
        verify(profileMapper, times(2)).selectRenewalCandidateUserIds(
                any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("候选SQL排除离职并使用合同到期范围和可用复合索引")
    void shouldUseIndexedInclusiveCandidateQuery() throws Exception
    {
        String xml = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml"),
                StandardCharsets.UTF_8);
        String migration = Files.readString(repoFile(
                "sql/erp_user_profile_contract_renewal_20260712.sql"),
                StandardCharsets.UTF_8);

        assertThat(xml).contains(
                "selectRenewalCandidateUserIds",
                "p.contract_end_date &gt;= #{windowStart}",
                "p.contract_end_date &lt;= #{windowEnd}",
                "p.employee_status &lt;&gt; '离职'",
                "p.user_id &gt; #{afterUserId}",
                "order by p.user_id asc",
                "limit #{limit}");
        assertThat(migration).contains(
                "idx_sys_user_profile_renewal_scan",
                "employee_status", "contract_end_date", "user_id");
        assertThat("erp_user_profile_contract_renewal_20260712.sql")
                .isGreaterThan("erp_user_employee_profile_20260706.sql");
    }

    private static Path repoFile(String relativePath)
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return path;
    }
}
