package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvCustomer;
import com.erp.inventory.domain.dto.InvCustomerServiceCardArchiveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceCardSaveRequest;
import com.erp.inventory.domain.dto.InvCustomerServiceRecordRequest;
import com.erp.inventory.domain.vo.InvCustomerServiceCardQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceCardVo;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditQuery;
import com.erp.inventory.domain.vo.InvCustomerServiceAuditVo;
import com.erp.inventory.mapper.InvCustomerMapper;
import com.erp.inventory.mapper.InvCustomerServiceCardMapper;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.service.BusinessFeatureGate;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

class InvCustomerServiceCardServiceImplTest
{
    private InvCustomerServiceCardMapper mapper;
    private InvCustomerMapper customerMapper;
    private RemoteFileService remoteFileService;
    private InvDeptScopeMapper deptScopeMapper;
    private InvCustomerServiceCardServiceImpl service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("store-user");
        mapper = mock(InvCustomerServiceCardMapper.class);
        customerMapper = mock(InvCustomerMapper.class);
        remoteFileService = mock(RemoteFileService.class);
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        service = new InvCustomerServiceCardServiceImpl(mapper,
                customerMapper, remoteFileService, gate);
        deptScopeMapper = mock(InvDeptScopeMapper.class);
        when(deptScopeMapper.countUserShopScope(7L, 10L)).thenReturn(1);
        when(deptScopeMapper.selectDeptTypeById(10L)).thenReturn("STORE");
        ReflectionTestUtils.setField(service, "deptScopeMapper",
                deptScopeMapper);
    }

    @AfterEach
    void tearDown()
    {
        PageHelper.clearPage();
        SecurityContextHolder.remove();
    }

    @Test
    void cardListStartsBoundedPaginationAfterStoreScopeChecks()
    {
        when(deptScopeMapper.countUserShopScope(7L, 10L)).thenAnswer(invocation -> {
            assertThat(PageHelper.getLocalPage()).isNull();
            return 1;
        });
        when(deptScopeMapper.selectDeptTypeById(10L)).thenAnswer(invocation -> {
            assertThat(PageHelper.getLocalPage()).isNull();
            return "STORE";
        });
        when(mapper.selectCardList(any())).thenAnswer(invocation -> {
            Page<?> page = PageHelper.getLocalPage();
            assertThat(page).isNotNull();
            assertThat(page.getPageNum()).isEqualTo(1);
            assertThat(page.getPageSize()).isEqualTo(100);
            return List.of();
        });

        service.selectList(new InvCustomerServiceCardQuery(), 10L, 0, 1000);

        verify(mapper).selectCardList(any());
    }

    @Test
    void createValidatesPhotoBindingAndReturnsSalesCompatibleCustomerId()
    {
        DriveBusinessFile file = new DriveBusinessFile();
        file.setNodeId(99L);
        file.setContentType("image/jpeg");
        when(remoteFileService.validateDriveBusinessFile(99L,
                "CUSTOMER_PHOTO", SecurityConstants.INNER))
                .thenReturn(R.ok(file));
        when(mapper.selectCreatedCustomerIdByRequestKey(10L, "new-1"))
                .thenReturn(null);
        when(customerMapper.insertInvCustomer(any())).thenAnswer(invocation -> {
            InvCustomer customer = invocation.getArgument(0);
            customer.setCustomerId(50L);
            return 1;
        });
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 10L, 99L));
        when(mapper.selectRecords(50L)).thenReturn(List.of());

        InvCustomerServiceCardVo created = service.create(request("new-1"),
                10L);

        assertThat(created.getCustomerId()).isEqualTo(50L);
        verify(remoteFileService).validateDriveBusinessFile(99L,
                "CUSTOMER_PHOTO", SecurityConstants.INNER);
        verify(customerMapper).insertInvCustomer(any());
        verify(mapper).insertChangeLog(any());
    }

    @Test
    void duplicateCreateRequestReturnsOriginalWithoutRevalidatingOrWriting()
    {
        when(mapper.selectCreatedCustomerIdByRequestKey(10L, "new-1"))
                .thenReturn(50L);
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 10L, 99L));
        when(mapper.selectRecords(50L)).thenReturn(List.of());

        InvCustomerServiceCardVo replay = service.create(request("new-1"),
                10L);

        assertThat(replay.getCustomerId()).isEqualTo(50L);
        verifyNoInteractions(remoteFileService);
        verify(customerMapper, never()).insertInvCustomer(any());
    }

    @Test
    void disabledCustomerCardRejectsCreateBeforeAnyMapperOrFileCall()
    {
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        doThrow(new ServiceException("FEATURE_DISABLED: "
                + BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
                .when(gate).requireEnabledForShop(
                        BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                        BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                        10L);
        InvCustomerServiceCardServiceImpl disabledService =
                new InvCustomerServiceCardServiceImpl(mapper,
                        customerMapper, remoteFileService, gate);
        ReflectionTestUtils.setField(disabledService, "deptScopeMapper",
                deptScopeMapper);

        assertThatThrownBy(() -> disabledService.create(request("new-1"),
                10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("FEATURE_DISABLED: "
                        + BusinessFeatureGate.CUSTOMER_SERVICE_CARD);

        verifyNoInteractions(mapper, customerMapper, remoteFileService);
    }

    @Test
    void directIdAndPhotoReadsFailClosedAcrossStores()
    {
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 11L, 99L));

        assertThatThrownBy(() -> service.selectById(50L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问");
        assertThatThrownBy(() -> service.resolvePhotoNode(50L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void auditListOverwritesCallerScopeAndReturnsOnlySafeProjection()
    {
        InvCustomerServiceAuditQuery query = new InvCustomerServiceAuditQuery();
        query.setShopDeptId(999L);
        query.setCustomerId(50L);
        InvCustomerServiceAuditVo row = new InvCustomerServiceAuditVo();
        row.setLogId(1L);
        when(mapper.selectCardById(50L)).thenAnswer(invocation -> {
            assertThat(PageHelper.getLocalPage()).isNull();
            return card(50L, 10L, 99L);
        });
        when(mapper.selectAuditList(any())).thenAnswer(invocation -> {
            Page<?> page = PageHelper.getLocalPage();
            assertThat(page).isNotNull();
            assertThat(page.getPageNum()).isEqualTo(2);
            assertThat(page.getPageSize()).isEqualTo(20);
            return List.of(row);
        });

        List<InvCustomerServiceAuditVo> result = service.selectAuditList(query,
                10L, 2, 20);

        assertThat(result).containsExactly(row);
        ArgumentCaptor<InvCustomerServiceAuditQuery> captor =
                ArgumentCaptor.forClass(InvCustomerServiceAuditQuery.class);
        verify(mapper).selectAuditList(captor.capture());
        assertThat(captor.getValue().getShopDeptId()).isEqualTo(10L);
        Set<String> fields = Arrays.stream(
                InvCustomerServiceAuditVo.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertThat(fields).doesNotContain("requestKey", "shopDeptId",
                "serviceNote", "contactPhone");
    }

    @Test
    void auditListWithCrossStoreCustomerIdFailsBeforeAuditQuery()
    {
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 11L, 99L));
        InvCustomerServiceAuditQuery query = new InvCustomerServiceAuditQuery();
        query.setCustomerId(50L);

        assertThatThrownBy(() -> service.selectAuditList(query, 10L, 1, 20))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问");

        verify(mapper, never()).selectAuditList(any());
    }

    @Test
    void safeCardDtoDoesNotDeclareLegacyFinancialFields()
    {
        Set<String> fields = Arrays.stream(
                InvCustomerServiceCardVo.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());

        assertThat(fields).doesNotContain("creditLimit", "usedCredit",
                "paymentDays", "accountPeriod");
        assertThat(fields).contains("createBy", "createTime", "updateBy",
                "updateTime");
    }

    @Test
    void archivedCardRejectsUpdateBeforeAnyWrite()
    {
        InvCustomerServiceCardVo archived = card(50L, 10L, 99L);
        archived.setStatus("1");
        when(mapper.selectCardById(50L)).thenReturn(archived);
        when(mapper.selectChangeLogIdByRequestKey(50L, "update-1"))
                .thenReturn(null);
        InvCustomerServiceCardSaveRequest request = request("update-1");
        request.setVersion(0L);

        assertThatThrownBy(() -> service.update(50L, request, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已归档");

        verify(mapper, never()).updateCustomerCore(any(), any(), any(),
                any(), any(), any());
        verify(mapper, never()).insertProfile(any());
        verify(mapper, never()).updateProfile(any());
        verify(mapper, never()).insertChangeLog(any());
    }

    @Test
    void archivedCardRejectsServiceRecordBeforeAnyWrite()
    {
        InvCustomerServiceCardVo archived = card(50L, 10L, 99L);
        archived.setStatus("1");
        when(mapper.selectCardById(50L)).thenReturn(archived);
        when(mapper.selectRecordIdByRequestKey(50L, "record-1"))
                .thenReturn(null);
        InvCustomerServiceRecordRequest request = new InvCustomerServiceRecordRequest();
        request.setRequestKey("record-1");

        assertThatThrownBy(() -> service.addRecord(50L, request, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已归档");

        verify(mapper, never()).insertRecord(any());
        verify(mapper, never()).touchLastVisit(any(), any(), any());
        verify(mapper, never()).insertChangeLog(any());
    }

    @Test
    void archivedCardCannotBeArchivedAgain()
    {
        InvCustomerServiceCardVo archived = card(50L, 10L, 99L);
        archived.setStatus("1");
        when(mapper.selectCardById(50L)).thenReturn(archived);
        when(mapper.selectChangeLogIdByRequestKey(50L, "archive-new"))
                .thenReturn(null);
        InvCustomerServiceCardArchiveRequest request = archiveRequest(
                "archive-new");

        assertThatThrownBy(() -> service.archive(50L, request, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已归档");

        verify(mapper, never()).bumpProfileVersion(any(), any(), any());
        verify(mapper, never()).archiveCustomer(any(), any());
        verify(mapper, never()).insertChangeLog(any());
    }

    @Test
    void updateReplayLooksUpTrimmedRequestKey()
    {
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 10L, 99L));
        when(mapper.selectChangeLogIdByRequestKey(50L, "update-1"))
                .thenReturn(1L);
        when(mapper.selectRecords(50L)).thenReturn(List.of());
        InvCustomerServiceCardSaveRequest request = request("  update-1  ");
        request.setVersion(0L);

        service.update(50L, request, 10L);

        verify(mapper).selectChangeLogIdByRequestKey(50L, "update-1");
        verify(mapper, never()).updateCustomerCore(any(), any(), any(),
                any(), any(), any());
        verify(mapper, never()).insertChangeLog(any());
    }

    @Test
    void recordReplayLooksUpTrimmedRequestKey()
    {
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 10L, 99L));
        when(mapper.selectRecordIdByRequestKey(50L, "record-1"))
                .thenReturn(1L);
        when(mapper.selectRecords(50L)).thenReturn(List.of());
        InvCustomerServiceRecordRequest request = new InvCustomerServiceRecordRequest();
        request.setRequestKey("  record-1  ");

        service.addRecord(50L, request, 10L);

        verify(mapper).selectRecordIdByRequestKey(50L, "record-1");
        verify(mapper, never()).insertRecord(any());
        verify(mapper, never()).touchLastVisit(any(), any(), any());
    }

    @Test
    void archiveReplayLooksUpTrimmedRequestKey()
    {
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 10L, 99L));
        when(mapper.selectChangeLogIdByRequestKey(50L, "archive-1"))
                .thenReturn(1L);
        when(mapper.selectRecords(50L)).thenReturn(List.of());
        InvCustomerServiceCardArchiveRequest request = archiveRequest(
                "  archive-1  ");

        service.archive(50L, request, 10L);

        verify(mapper).selectChangeLogIdByRequestKey(50L, "archive-1");
        verify(mapper, never()).bumpProfileVersion(any(), any(), any());
        verify(mapper, never()).archiveCustomer(any(), any());
    }

    @Test
    void writesRequireOneBoundedRequestKeyForDurableReplay()
    {
        when(mapper.selectCardById(50L)).thenReturn(card(50L, 10L, 99L));

        assertThatThrownBy(() -> service.create(request(" "), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请求标识不能为空");

        InvCustomerServiceCardSaveRequest update = request(" ");
        update.setVersion(0L);
        assertThatThrownBy(() -> service.update(50L, update, 10L))
                .hasMessage("请求标识不能为空");

        InvCustomerServiceRecordRequest record =
                new InvCustomerServiceRecordRequest();
        record.setRequestKey(" ");
        assertThatThrownBy(() -> service.addRecord(50L, record, 10L))
                .hasMessage("请求标识不能为空");

        assertThatThrownBy(() -> service.archive(50L,
                archiveRequest(" "), 10L))
                .hasMessage("请求标识不能为空");

        assertThatThrownBy(() -> service.create(request("x".repeat(129)),
                10L)).hasMessage("请求标识长度不能超过128");

        verifyNoInteractions(remoteFileService);
        verify(customerMapper, never()).insertInvCustomer(any());
        verify(mapper, never()).insertRecord(any());
        verify(mapper, never()).insertChangeLog(any());
    }

    private InvCustomerServiceCardSaveRequest request(String requestKey)
    {
        InvCustomerServiceCardSaveRequest request =
                new InvCustomerServiceCardSaveRequest();
        request.setCustomerName("王女士");
        request.setContactPhone("13800000000");
        request.setPhotoNodeId(99L);
        request.setRequestKey(requestKey);
        request.setSourceClient("DESKTOP");
        return request;
    }

    private InvCustomerServiceCardArchiveRequest archiveRequest(
            String requestKey)
    {
        InvCustomerServiceCardArchiveRequest request =
                new InvCustomerServiceCardArchiveRequest();
        request.setVersion(0L);
        request.setReason("重复客户");
        request.setRequestKey(requestKey);
        request.setSourceClient("DESKTOP");
        return request;
    }

    private InvCustomerServiceCardVo card(Long customerId, Long shopDeptId,
            Long photoNodeId)
    {
        InvCustomerServiceCardVo card = new InvCustomerServiceCardVo();
        card.setCustomerId(customerId);
        card.setCustomerName("王女士");
        card.setShopDeptId(shopDeptId);
        card.setPhotoNodeId(photoNodeId);
        card.setVersion(0L);
        card.setStatus("0");
        return card;
    }
}
