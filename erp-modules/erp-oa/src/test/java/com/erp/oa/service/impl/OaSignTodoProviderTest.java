package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.vo.OaTodoItem;
import com.erp.oa.domain.vo.OaTodoSummary;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignNotificationOutboxMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("签约业务待办Provider")
class OaSignTodoProviderTest
{
    private OaSignTaskMapper taskMapper;
    private OaSignPackageMapper packageMapper;
    private OaSignNotificationOutboxMapper outboxMapper;
    private ShopScopeService shopScopeService;
    private OaSignHrAccessService signHrAccessService;
    private OaSignTodoProvider provider;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(OaSignTaskMapper.class);
        packageMapper = mock(OaSignPackageMapper.class);
        outboxMapper = mock(OaSignNotificationOutboxMapper.class);
        shopScopeService = mock(ShopScopeService.class);
        signHrAccessService = mock(OaSignHrAccessService.class);
        provider = new OaSignTodoProvider(taskMapper, packageMapper, shopScopeService, outboxMapper,
                signHrAccessService);
        when(signHrAccessService.isCurrentHr()).thenReturn(true);
        when(signHrAccessService.isTechnicalEvidenceReader()).thenReturn(false);
        when(signHrAccessService.currentTaskOwnerFilter()).thenReturn(101L);
        SecurityContextHolder.setUserId("101");
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(101L);
        loginUser.setPermissions(Set.of("oa:signTask:list", "oa:signTask:revalidate",
                "oa:signPackage:send", "oa:signTask:retry",
                "oa:signTask:resolveRefusal", "oa:signTask:resolveExpiry"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        when(taskMapper.selectOaSignTaskList(any())).thenAnswer(invocation -> {
            OaSignTask query = invocation.getArgument(0);
            return List.of(task(query.getStatus()));
        });
        when(packageMapper.selectMyOaSignPackageList(any())).thenAnswer(invocation -> {
            OaSignPackage query = invocation.getArgument(0);
            return List.of(signPackage(query.getStatus()));
        });
    }

    @Test
    @DisplayName("通知重试耗尽后单独生成HR异常待办且不改合同业务状态")
    void shouldExposeDeadOutboxAsHrTodoWithoutCorruptingTaskStatus()
    {
        OaSignTask pendingSign = task("PENDING_SIGN");
        pendingSign.setTaskId(9L);
        OaSignNotificationOutbox dead = new OaSignNotificationOutbox();
        dead.setOutboxId(71L);
        dead.setChannel("MOBILE_PUSH");
        dead.setBusinessKey("SIGN_SENT:90:SP-90-V1");
        dead.setStatus("DEAD");
        dead.setPayloadJson("{\"hrUserId\":88,\"taskId\":9,\"shopDeptId\":9999}");
        dead.setUpdatedTime(new Date());
        when(outboxMapper.selectDeadNotificationsForHr(101L, List.of(1171L), 100)).thenReturn(List.of(dead));
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(pendingSign);
        when(shopScopeService.resolveScopeDeptIds(1171L)).thenReturn(List.of(1171L));

        List<OaTodoItem> items = provider.list("OA_SIGN_SEND_FAILED", 1171L);

        assertThat(items).anySatisfy(item -> {
            assertThat(item.getTodoKey()).isEqualTo("OA_SIGN_OUTBOX:SIGN_SENT:90:SP-90-V1:SEND_FAILED");
            assertThat(item.getBusinessId()).isEqualTo(9L);
            assertThat(item.getTitle()).isEqualTo("合同通知发送异常");
            assertThat(item.getRouteType()).isEqualTo("OA_SIGN_HR_TASK");
            assertThat(item.getRouteParams()).containsEntry("notificationBusinessKey",
                    "SIGN_SENT:90:SP-90-V1");
        });
        assertThat(pendingSign.getStatus()).isEqualTo("PENDING_SIGN");
        verify(taskMapper, never()).updateStatusWithVersion(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("经办人五类状态和员工本人待签包映射为稳定待办类型")
    void shouldMapHrAndEmployeeSigningTodos()
    {
        List<OaTodoItem> items = provider.list(null, 1171L);

        assertThat(items).extracting(OaTodoItem::getType)
                .containsExactlyInAnyOrder("OA_SIGN_NEEDS_DATA", "OA_SIGN_COMPANY_FINALIZE",
                        "OA_SIGN_SEND_FAILED", "OA_SIGN_REFUSED", "OA_SIGN_EXPIRED",
                        "OA_SIGN_PACKAGE_SIGN", "OA_SIGN_PACKAGE_SIGN");
        assertThat(items).filteredOn(item -> item.getType().equals("OA_SIGN_PACKAGE_SIGN"))
                .allSatisfy(item -> {
                    assertThat(item.getCategory()).isEqualTo("personal");
                    assertThat(item.getRouteType()).isEqualTo("OA_SIGN_PACKAGE_SIGN");
                });
        assertThat(items).filteredOn(item -> item.getType().equals("OA_SIGN_PACKAGE_SIGN"))
                .extracting(OaTodoItem::getBusinessId)
                .containsExactlyInAnyOrder(500L, 501L);
        assertThat(items).filteredOn(item -> !item.getType().equals("OA_SIGN_PACKAGE_SIGN"))
                .allSatisfy(item -> {
                    assertThat(item.getRouteType()).isEqualTo("OA_SIGN_HR_TASK");
                    if ("OA_SIGN_COMPANY_FINALIZE".equals(item.getType()))
                    {
                        assertThat(item.getRequiredPermission()).isEqualTo("oa:signPackage:send");
                    }
                    else
                    {
                        assertThat(item.getRequiredPermission()).startsWith("oa:signTask:");
                    }
                    assertThat(item.getSummary()).isEqualTo("张三（账号 201） · 入职");
                });
    }

    @Test
    @DisplayName("列表只读且固定为本人和组织范围")
    void shouldRemainReadOnlyAndScopedToCurrentHandler()
    {
        provider.list("OA_SIGN_COMPANY_FINALIZE", 1171L);

        ArgumentCaptor<OaSignTask> captor = ArgumentCaptor.forClass(OaSignTask.class);
        verify(shopScopeService).appendShopScope(captor.capture(),
                org.mockito.ArgumentMatchers.eq(1171L));
        assertThat(captor.getValue().getAssignedHrUserId()).isEqualTo(101L);
        verify(taskMapper, never()).updateStatusWithVersion(any(), any(), any(), any(), any(), any(), any(), any());
        verify(packageMapper, never()).updateOaSignPackage(any());
    }

    @Test
    @DisplayName("汇总不混入公告未读数并按类型分类")
    void shouldSummarizeOnlyBusinessTodos()
    {
        OaTodoSummary summary = provider.summary(1171L);

        assertThat(summary.getTotal()).isEqualTo(7);
        assertThat(summary.getTypeCounts()).containsEntry("OA_SIGN_COMPANY_FINALIZE", 1L)
                .containsEntry("OA_SIGN_PACKAGE_SIGN", 2L);
        assertThat(summary.getCategoryCounts()).containsEntry("personal", 2L);
        assertThat(summary.getRecentItems()).isNotEmpty();
    }

    @Test
    @DisplayName("缺少处理权限时不暴露对应HR待办但本人签署仍可见")
    void shouldRequireActionPermissionForHrTodos()
    {
        LoginUser loginUser = SecurityContextHolder.get(SecurityConstants.LOGIN_USER, LoginUser.class);
        loginUser.setPermissions(Set.of("oa:signTask:list"));

        List<OaTodoItem> items = provider.list(null, 1171L);

        assertThat(items).extracting(OaTodoItem::getType)
                .containsOnly("OA_SIGN_PACKAGE_SIGN");
    }

    @Test
    @DisplayName("被配置为当前HR的管理员同时保留HR和员工本人待办")
    void shouldPreferCurrentHrAndKeepPersonalTodosForConfiguredAdmin()
    {
        SecurityContextHolder.setUserId("1");
        LoginUser loginUser = SecurityContextHolder.get(SecurityConstants.LOGIN_USER, LoginUser.class);
        loginUser.setUserid(1L);
        loginUser.setPermissions(Set.of("*:*:*"));
        when(signHrAccessService.isTechnicalEvidenceReader()).thenReturn(true);

        assertThat(provider.list(null, 1171L)).extracting(OaTodoItem::getType)
                .contains("OA_SIGN_COMPANY_FINALIZE", "OA_SIGN_PACKAGE_SIGN");
    }

    @Test
    @DisplayName("非当前技术证据用户只保留员工本人的签署待办")
    void shouldKeepOnlyPersonalTodosForNonCurrentTechnicalEvidenceReader()
    {
        SecurityContextHolder.setUserId("102");
        LoginUser loginUser = SecurityContextHolder.get(SecurityConstants.LOGIN_USER, LoginUser.class);
        loginUser.setUserid(102L);
        loginUser.setPermissions(Set.of("oa:signTask:list", "oa:signTask:revalidate",
                "oa:signPackage:send", "oa:signTask:retry", "oa:signTask:technicalEvidence"));
        when(signHrAccessService.isTechnicalEvidenceReader()).thenReturn(true);
        when(signHrAccessService.isCurrentHr()).thenReturn(false);

        assertThat(provider.list(null, 1171L)).extracting(OaTodoItem::getType)
                .containsOnly("OA_SIGN_PACKAGE_SIGN");
    }

    @Test
    @DisplayName("当前HR持有技术证据权限时仍同时拥有HR和员工本人待办")
    void shouldPreferCurrentHrTodosForTechnicalEvidenceReader()
    {
        LoginUser loginUser = SecurityContextHolder.get(SecurityConstants.LOGIN_USER, LoginUser.class);
        loginUser.setPermissions(Set.of("oa:signTask:list", "oa:signTask:revalidate",
                "oa:signPackage:send", "oa:signTask:retry", "oa:signTask:technicalEvidence"));
        when(signHrAccessService.isTechnicalEvidenceReader()).thenReturn(true);

        assertThat(provider.list(null, 1171L)).extracting(OaTodoItem::getType)
                .contains("OA_SIGN_COMPANY_FINALIZE", "OA_SIGN_PACKAGE_SIGN");
    }

    private OaSignTask task(String status)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId((long) status.hashCode() & 0x7fffffffL);
        task.setTaskNo("ST-" + status);
        task.setStatus(status);
        task.setScenario("ONBOARD");
        task.setEmployeeId(201L);
        task.setEmployeeName("张三");
        task.setShopDeptId(1171L);
        task.setAssignedHrUserId(101L);
        if ("REFUSED".equals(status) || "EXPIRED".equals(status))
        {
            task.setResolutionStatus("OPEN");
        }
        task.setCreatedTime(new Date(System.currentTimeMillis() - 60_000));
        return task;
    }

    private OaSignPackage signPackage(String status)
    {
        OaSignPackage signPackage = new OaSignPackage();
        long packageId = "pending_sign".equals(status) ? 500L : 501L;
        signPackage.setPackageId(packageId);
        signPackage.setPackageNo("SP-" + packageId);
        signPackage.setEmployeeId(SecurityContextHolder.getUserId());
        signPackage.setEmployeeNameSnapshot("本人");
        signPackage.setShopDeptId(1171L);
        signPackage.setShopDeptName("测试门店");
        signPackage.setStatus(status);
        signPackage.setCreateTime(new Date(System.currentTimeMillis() - 120_000));
        return signPackage;
    }
}
