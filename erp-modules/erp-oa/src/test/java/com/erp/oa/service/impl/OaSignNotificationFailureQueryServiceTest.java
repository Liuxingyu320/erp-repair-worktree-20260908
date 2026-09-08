package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.vo.OaSignNotificationFailure;
import com.erp.oa.mapper.OaSignNotificationOutboxMapper;

@DisplayName("签约通知异常只读查询")
class OaSignNotificationFailureQueryServiceTest
{
    private OaSignNotificationOutboxMapper outboxMapper;
    private ShopScopeService signScopeService;
    private OaSignHrAccessService signHrAccessService;
    private OaSignNotificationFailureQueryService service;

    @BeforeEach
    void setUp()
    {
        outboxMapper = mock(OaSignNotificationOutboxMapper.class);
        signScopeService = mock(ShopScopeService.class);
        signHrAccessService = mock(OaSignHrAccessService.class);
        service = new OaSignNotificationFailureQueryService(
                outboxMapper, signScopeService, signHrAccessService);
        SecurityContextHolder.setUserId("940");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("公司组织查询包含后端解析的公司及子店范围且只返回最小字段")
    void shouldUseSigningCompanyScopeAndReturnSanitizedProjection()
    {
        List<Long> companyAndStores = List.of(1157L, 1185L, 1186L);
        when(signScopeService.resolveScopeDeptIds(1157L)).thenReturn(companyAndStores);
        OaSignNotificationFailure mapperRow = failure(9L, "SIGN_SENT:90:SP-90-V1");
        when(outboxMapper.selectNotificationFailuresForHr(940L, companyAndStores, 100))
                .thenReturn(List.of(mapperRow));

        List<OaSignNotificationFailure> result = service.list(1157L);

        verify(signHrAccessService).requireCurrentHr();
        verify(signScopeService).resolveScopeDeptIds(1157L);
        verify(outboxMapper).selectNotificationFailuresForHr(940L, companyAndStores, 100);
        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.getTaskId()).isEqualTo(9L);
            assertThat(row.getNotificationBusinessKey()).isEqualTo("SIGN_SENT:90:SP-90-V1");
            assertThat(row).isNotSameAs(mapperRow);
        });
    }

    @Test
    @DisplayName("损坏业务键和重复任务在响应前按失败关闭方式移除")
    void shouldFailClosedForMalformedOrDuplicateRows()
    {
        when(signScopeService.resolveScopeDeptIds(1157L)).thenReturn(List.of(1157L));
        when(outboxMapper.selectNotificationFailuresForHr(940L, List.of(1157L), 100))
                .thenReturn(List.of(
                        failure(9L, "SIGN_SENT:90:SP-90-V1"),
                        failure(9L, "SIGN_SENT:90:SP-90-V2"),
                        failure(10L, "bad key with spaces")));

        assertThat(service.list(1157L))
                .extracting(OaSignNotificationFailure::getTaskId)
                .containsExactly(9L);
    }

    @Test
    @DisplayName("无法识别当前用户时不执行通知查询")
    void shouldRejectMissingCurrentUser()
    {
        SecurityContextHolder.remove();

        assertThatThrownBy(() -> service.list(1157L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无法识别当前合同经办人");
    }

    private OaSignNotificationFailure failure(Long taskId, String businessKey)
    {
        OaSignNotificationFailure row = new OaSignNotificationFailure();
        row.setTaskId(taskId);
        row.setNotificationBusinessKey(businessKey);
        return row;
    }
}
