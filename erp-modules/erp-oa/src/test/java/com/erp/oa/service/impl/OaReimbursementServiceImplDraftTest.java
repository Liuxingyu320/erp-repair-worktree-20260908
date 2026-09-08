package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.OaReimbursementItem;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaReimbursementMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.oa.service.invoice.OaInvoiceRecognitionCoordinator;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("报销草稿与提交边界")
class OaReimbursementServiceImplDraftTest
{
    private OaReimbursementMapper mapper;
    private OaDeptScopeMapper deptScopeMapper;
    private ShopScopeService shopScopeService;
    private BusinessFeatureGate featureGate;
    private OaReimbursementFileStorageService fileStorage;
    private OaInvoiceRecognitionCoordinator recognitionCoordinator;
    private OaReimbursementServiceImpl service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(OaReimbursementMapper.class);
        deptScopeMapper = mock(OaDeptScopeMapper.class);
        shopScopeService = mock(ShopScopeService.class);
        featureGate = mock(BusinessFeatureGate.class);
        when(shopScopeService.resolveRequiredShopDept(any())).thenReturn(201L);
        when(deptScopeMapper.countDeptInScope(201L, 201L)).thenReturn(1);

        OaReimbursementProperties properties =
                new OaReimbursementProperties();
        fileStorage = mock(OaReimbursementFileStorageService.class);
        recognitionCoordinator = mock(OaInvoiceRecognitionCoordinator.class);
        service = new OaReimbursementServiceImpl(
                mapper,
                deptScopeMapper,
                shopScopeService,
                mock(RemoteApprovalService.class),
                mock(OaReimbursementApprovalStartOutboxService.class),
                mock(OaReimbursementApprovalStartAfterCommitTrigger.class),
                new ObjectMapper(),
                featureGate,
                fileStorage,
                recognitionCoordinator,
                properties);
        login();
    }

    @AfterEach
    void tearDown()
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("未填完整的手机表单也能原样保存为草稿")
    void shouldPersistIncompleteDraftWithoutPlaceholderBusinessValues()
    {
        AtomicReference<OaReimbursement> stored =
                new AtomicReference<>();
        List<OaReimbursementItem> storedItems = new ArrayList<>();
        doAnswer(invocation -> {
            OaReimbursement value = invocation.getArgument(0);
            value.setReimbursementId(77L);
            value.setRowVersion(0L);
            stored.set(value);
            return 1;
        }).when(mapper).insertReimbursement(any());
        doAnswer(invocation -> {
            storedItems.add(invocation.getArgument(0));
            return 1;
        }).when(mapper).insertItem(any());
        when(mapper.selectById(77L)).thenAnswer(
                invocation -> stored.get());
        when(mapper.selectItemsByReimbursementId(77L)).thenAnswer(
                invocation -> new ArrayList<>(storedItems));
        when(mapper.selectInvoicesByReimbursementId(77L))
                .thenReturn(List.of());

        OaReimbursement request = new OaReimbursement();
        request.setTitle("");
        request.setPurpose("");
        OaReimbursementItem partial = new OaReimbursementItem();
        partial.setExpenseDate(new Date());
        request.setItems(List.of(partial));

        OaReimbursement saved = service.saveDraft(request, 201L);

        assertThat(saved.getReimbursementId()).isEqualTo(77L);
        assertThat(saved.getTitle()).isEmpty();
        assertThat(saved.getPurpose()).isEmpty();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getExpenseType()).isNull();
            assertThat(item.getDescription()).isNull();
            assertThat(item.getClaimedAmount()).isNull();
            assertThat(item.getExpenseDate()).isNotNull();
        });
    }

    @Test
    @DisplayName("缺少发票时提交先失败且不会悄悄改写已有草稿")
    void shouldRejectSubmissionBeforeSavingWhenInvoiceIsMissing()
    {
        OaReimbursement current = claim(77L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.countInvoices(77L)).thenReturn(0);

        OaReimbursement request = claim(77L);
        request.setItems(List.of(completeItem()));

        assertThatThrownBy(() -> service.submit(request, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请至少上传一个发票文件");

        verify(featureGate).requireEnabled(
                BusinessFeatureGate.REIMBURSEMENT);
        verify(mapper, never()).updateReimbursement(any());
        verify(mapper, never()).deleteItemsByReimbursementId(anyLong());
    }

    @Test
    @DisplayName("同单同 SHA 重试只返回原发票并清理本次文件")
    void shouldReplaySameClaimInvoiceWithoutRecognitionOrVersionChange()
    {
        OaReimbursement current = claim(77L);
        OaReimbursementInvoice existing = new OaReimbursementInvoice();
        existing.setInvoiceId(901L);
        existing.setReimbursementId(77L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceByReimbursementAndSha(77L, "sha-1"))
                .thenReturn(existing);
        when(fileStorage.storeInvoice(anyLong(), any()))
                .thenReturn(new OaReimbursementFileStorageService.StoredInvoice(
                        "invoice.pdf", "stored.pdf", "invoices/77/stored.pdf",
                        "application/pdf", "pdf", 12L, "sha-1"));

        OaReimbursementInvoice replay = service.uploadInvoice(77L,
                mock(org.springframework.web.multipart.MultipartFile.class),
                201L);

        assertThat(replay).isSameAs(existing);
        assertThat(replay.getIdempotentReplay()).isTrue();
        verify(fileStorage).delete("invoices/77/stored.pdf");
        verify(mapper, never()).countInvoices(anyLong());
        verify(mapper, never()).insertInvoice(any());
        verify(mapper, never()).upsertInvoiceRecognition(any());
        verify(recognitionCoordinator, never()).apply(any(), any());
        verify(mapper, never()).updateReimbursement(any());
    }

    @Test
    @DisplayName("唯一键并发冲突后仍返回原发票且不重复识别")
    void shouldReplayAfterUniqueKeyRejectsConcurrentInsert()
    {
        OaReimbursement current = claim(77L);
        OaReimbursementInvoice existing = new OaReimbursementInvoice();
        existing.setInvoiceId(902L);
        existing.setReimbursementId(77L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceByReimbursementAndSha(77L, "sha-2"))
                .thenReturn(null, existing);
        when(fileStorage.storeInvoice(anyLong(), any()))
                .thenReturn(new OaReimbursementFileStorageService.StoredInvoice(
                        "invoice.pdf", "stored-2.pdf",
                        "invoices/77/stored-2.pdf", "application/pdf", "pdf",
                        12L, "sha-2"));
        when(mapper.insertInvoice(any()))
                .thenThrow(new DuplicateKeyException("duplicate claim hash"));

        OaReimbursementInvoice replay = service.uploadInvoice(77L,
                mock(org.springframework.web.multipart.MultipartFile.class),
                201L);

        assertThat(replay).isSameAs(existing);
        assertThat(replay.getInvoiceId()).isEqualTo(902L);
        assertThat(replay.getIdempotentReplay()).isTrue();
        verify(fileStorage).delete("invoices/77/stored-2.pdf");
        verify(mapper, never()).upsertInvoiceRecognition(any());
        verify(recognitionCoordinator, never()).apply(any(), any());
        verify(mapper, never()).updateReimbursement(any());
    }

    @Test
    @DisplayName("删除关联发票会原子删除明细并重算总额和版本")
    void shouldDeleteLinkedItemAndRecalculateClaim()
    {
        OaReimbursement current = claim(77L);
        current.setRowVersion(3L);
        OaReimbursementInvoice invoice = new OaReimbursementInvoice();
        invoice.setInvoiceId(901L);
        invoice.setReimbursementId(77L);
        invoice.setItemId(12L);
        invoice.setStoragePath("invoices/77/stored.pdf");
        OaReimbursement detail = claim(77L);
        detail.setRowVersion(4L);
        OaReimbursementItem manual = completeItem();
        manual.setClaimedAmount(new BigDecimal("10.00"));
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceById(901L)).thenReturn(invoice);
        when(mapper.deleteItem(77L, 12L)).thenReturn(1);
        when(mapper.deleteInvoice(901L, 77L)).thenReturn(1);
        when(mapper.selectItemsByReimbursementId(77L))
                .thenReturn(List.of(manual));
        when(mapper.updateReimbursement(argThat(value ->
                value.getReimbursementId().equals(77L)
                        && value.getRowVersion().equals(3L)
                        && value.getTotalAmount().compareTo(
                                new BigDecimal("10.00")) == 0)))
                .thenReturn(1);
        when(mapper.selectById(77L)).thenReturn(detail);
        when(mapper.selectInvoicesByReimbursementId(77L))
                .thenReturn(List.of());

        OaReimbursement result = service.deleteInvoice(77L, 901L, 201L, 3L);

        assertThat(result).isSameAs(detail);
        verify(mapper).deleteInvoiceRecognition(901L, 77L);
        verify(mapper).deleteItem(77L, 12L);
        verify(fileStorage).delete("invoices/77/stored.pdf");
    }

    @Test
    @DisplayName("删除未关联发票不删除人工明细且只推进一次版本")
    void shouldDeleteUnlinkedInvoiceWithoutDeletingManualItem()
    {
        OaReimbursement current = claim(77L);
        current.setRowVersion(3L);
        OaReimbursementInvoice invoice = new OaReimbursementInvoice();
        invoice.setInvoiceId(903L);
        invoice.setReimbursementId(77L);
        invoice.setStoragePath("invoices/77/manual.pdf");
        OaReimbursement detail = claim(77L);
        detail.setRowVersion(4L);
        OaReimbursementItem manual = completeItem();
        manual.setClaimedAmount(new BigDecimal("10.00"));
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceById(903L)).thenReturn(invoice);
        when(mapper.deleteInvoice(903L, 77L)).thenReturn(1);
        when(mapper.selectItemsByReimbursementId(77L))
                .thenReturn(List.of(manual));
        when(mapper.updateReimbursement(argThat(value ->
                value.getReimbursementId().equals(77L)
                        && value.getRowVersion().equals(3L)
                        && value.getTotalAmount().compareTo(
                                new BigDecimal("10.00")) == 0)))
                .thenReturn(1);
        when(mapper.selectById(77L)).thenReturn(detail);
        when(mapper.selectInvoicesByReimbursementId(77L))
                .thenReturn(List.of());

        OaReimbursement result = service.deleteInvoice(77L, 903L, 201L, 3L);

        assertThat(result).isSameAs(detail);
        verify(mapper, never()).deleteItem(anyLong(), anyLong());
        verify(mapper, times(1)).updateReimbursement(any());
        verify(fileStorage).delete("invoices/77/manual.pdf");
    }

    @Test
    @DisplayName("删除文件只在提交后执行")
    void shouldDeleteInvoiceFileOnlyAfterCommit()
    {
        stubUnlinkedDelete(904L, "invoices/77/after-commit.pdf");
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.deleteInvoice(77L, 904L, 201L, 3L);
            List<TransactionSynchronization> synchronizations =
                    new ArrayList<>(TransactionSynchronizationManager
                            .getSynchronizations());
            verify(fileStorage, never()).delete(any());
            TransactionSynchronizationManager.clearSynchronization();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            verify(fileStorage).deleteQuietly(
                    "invoices/77/after-commit.pdf");
        }
        finally
        {
            if (TransactionSynchronizationManager.isSynchronizationActive())
            {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("删除事务回滚不删除原发票文件")
    void shouldKeepInvoiceFileWhenDeleteRollsBack()
    {
        stubUnlinkedDelete(905L, "invoices/77/rollback.pdf");
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.deleteInvoice(77L, 905L, 201L, 3L);
            List<TransactionSynchronization> synchronizations =
                    new ArrayList<>(TransactionSynchronizationManager
                            .getSynchronizations());
            TransactionSynchronizationManager.clearSynchronization();
            synchronizations.forEach(sync -> sync.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(fileStorage, never()).delete(any());
            verify(fileStorage, never()).deleteQuietly(
                    "invoices/77/rollback.pdf");
        }
        finally
        {
            if (TransactionSynchronizationManager.isSynchronizationActive())
            {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("新上传文件在事务回滚后清理")
    void shouldCleanNewUploadFileAfterRollback()
    {
        OaReimbursement current = claim(77L);
        OaReimbursementInvoice invoice = new OaReimbursementInvoice();
        invoice.setInvoiceId(906L);
        invoice.setReimbursementId(77L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(fileStorage.storeInvoice(anyLong(), any()))
                .thenReturn(new OaReimbursementFileStorageService.StoredInvoice(
                        "invoice.pdf", "stored-3.pdf",
                        "invoices/77/stored-3.pdf", "application/pdf", "pdf",
                        12L, "sha-3"));
        when(mapper.insertInvoice(any())).thenAnswer(invocation -> {
            invocation.<OaReimbursementInvoice>getArgument(0)
                    .setInvoiceId(906L);
            return 1;
        });
        when(mapper.updateReimbursement(any())).thenReturn(1);
        when(mapper.selectInvoiceById(906L)).thenReturn(invoice);

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.uploadInvoice(77L,
                    mock(org.springframework.web.multipart.MultipartFile.class),
                    201L);
            List<TransactionSynchronization> synchronizations =
                    new ArrayList<>(TransactionSynchronizationManager
                            .getSynchronizations());
            TransactionSynchronizationManager.clearSynchronization();
            verify(fileStorage, never()).deleteQuietly(any());
            synchronizations.forEach(sync -> sync.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(fileStorage).deleteQuietly("invoices/77/stored-3.pdf");
        }
        finally
        {
            if (TransactionSynchronizationManager.isSynchronizationActive())
            {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("重放文件提交后不遗留暂存文件")
    void shouldKeepReplayFileCleanupQuietOnCommit()
    {
        stubReplayUpload(907L, "sha-4", "invoices/77/stored-4.pdf");

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.uploadInvoice(77L,
                    mock(org.springframework.web.multipart.MultipartFile.class),
                    201L);
            List<TransactionSynchronization> synchronizations =
                    new ArrayList<>(TransactionSynchronizationManager
                            .getSynchronizations());
            TransactionSynchronizationManager.clearSynchronization();
            verify(fileStorage).delete("invoices/77/stored-4.pdf");
            synchronizations.forEach(sync -> sync.afterCompletion(
                    TransactionSynchronization.STATUS_COMMITTED));
            verify(fileStorage, never()).deleteQuietly(
                    "invoices/77/stored-4.pdf");
        }
        finally
        {
            if (TransactionSynchronizationManager.isSynchronizationActive())
            {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("重放文件回滚后清理暂存文件")
    void shouldCleanReplayFileAfterRollback()
    {
        stubReplayUpload(908L, "sha-5", "invoices/77/stored-5.pdf");

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            service.uploadInvoice(77L,
                    mock(org.springframework.web.multipart.MultipartFile.class),
                    201L);
            List<TransactionSynchronization> synchronizations =
                    new ArrayList<>(TransactionSynchronizationManager
                            .getSynchronizations());
            TransactionSynchronizationManager.clearSynchronization();
            verify(fileStorage).delete("invoices/77/stored-5.pdf");
            synchronizations.forEach(sync -> sync.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(fileStorage).deleteQuietly("invoices/77/stored-5.pdf");
        }
        finally
        {
            if (TransactionSynchronizationManager.isSynchronizationActive())
            {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("删除发票拒绝过期版本且不产生部分删除")
    void shouldRejectDeleteWhenExpectedVersionIsStale()
    {
        OaReimbursement current = claim(77L);
        current.setRowVersion(4L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);

        assertThatThrownBy(() -> service.deleteInvoice(77L, 901L, 201L, 3L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("报销申请已变化，请刷新后重试");

        verify(mapper, never()).selectInvoiceById(anyLong());
        verify(mapper, never()).deleteInvoice(anyLong(), anyLong());
        verify(mapper, never()).deleteItem(anyLong(), anyLong());
    }

    @Test
    @DisplayName("保存草稿会校验来源发票并重建新的明细关联")
    void shouldRebindSourceInvoiceAfterReplacingItems()
    {
        OaReimbursement current = claim(77L);
        OaReimbursementInvoice invoice = new OaReimbursementInvoice();
        invoice.setInvoiceId(901L);
        invoice.setReimbursementId(77L);
        OaReimbursementItem sourceItem = completeItem();
        sourceItem.setSourceInvoiceId(901L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceById(901L)).thenReturn(invoice);
        when(mapper.updateReimbursement(any())).thenReturn(1);
        when(mapper.bindInvoiceItem(901L, 77L, 501L)).thenReturn(1);
        doAnswer(invocation -> {
            OaReimbursementItem value = invocation.getArgument(0);
            value.setItemId(501L);
            return 1;
        }).when(mapper).insertItem(any());
        when(mapper.selectById(77L)).thenReturn(current);
        when(mapper.selectItemsByReimbursementId(77L))
                .thenReturn(List.of(sourceItem));
        when(mapper.selectInvoicesByReimbursementId(77L))
                .thenReturn(List.of(invoice));

        OaReimbursement request = claim(77L);
        request.setRowVersion(0L);
        request.setItems(List.of(sourceItem));

        service.saveDraft(request, 201L);

        verify(mapper).clearInvoiceItemLinks(77L);
        verify(mapper).deleteItemsByReimbursementId(77L);
        verify(mapper).bindInvoiceItem(901L, 77L, 501L);
    }

    @Test
    @DisplayName("重复来源发票在任何明细写入前被拒绝")
    void shouldRejectDuplicateSourceInvoiceIdsBeforeReplacingItems()
    {
        OaReimbursement current = claim(77L);
        OaReimbursementInvoice invoice = new OaReimbursementInvoice();
        invoice.setInvoiceId(901L);
        invoice.setReimbursementId(77L);
        OaReimbursementItem first = completeItem();
        first.setSourceInvoiceId(901L);
        OaReimbursementItem second = completeItem();
        second.setSourceInvoiceId(901L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceById(901L)).thenReturn(invoice);

        OaReimbursement request = claim(77L);
        request.setRowVersion(0L);
        request.setItems(List.of(first, second));

        assertThatThrownBy(() -> service.saveDraft(request, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("同一发票不能关联多条费用明细");

        verify(mapper, never()).updateReimbursement(any());
        verify(mapper, never()).clearInvoiceItemLinks(anyLong());
        verify(mapper, never()).deleteItemsByReimbursementId(anyLong());
        verify(mapper, never()).insertItem(any());
    }

    @Test
    @DisplayName("跨报销单来源发票关联在任何写入前失败")
    void shouldRejectSourceInvoiceFromAnotherClaimBeforeReplacingItems()
    {
        OaReimbursement current = claim(77L);
        OaReimbursementInvoice foreignInvoice = new OaReimbursementInvoice();
        foreignInvoice.setInvoiceId(901L);
        foreignInvoice.setReimbursementId(78L);
        OaReimbursementItem sourceItem = completeItem();
        sourceItem.setSourceInvoiceId(901L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceById(901L)).thenReturn(foreignInvoice);
        when(mapper.updateReimbursement(any())).thenReturn(1);

        OaReimbursement request = claim(77L);
        request.setRowVersion(0L);
        request.setItems(List.of(sourceItem));

        assertThatThrownBy(() -> service.saveDraft(request, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("发票费用明细关联无效");

        verify(mapper, never()).clearInvoiceItemLinks(anyLong());
        verify(mapper, never()).deleteItemsByReimbursementId(anyLong());
        verify(mapper, never()).insertItem(any());
        verify(mapper, never()).updateReimbursement(any());
    }

    private void stubUnlinkedDelete(Long invoiceId, String storagePath)
    {
        OaReimbursement current = claim(77L);
        current.setRowVersion(3L);
        OaReimbursementInvoice invoice = new OaReimbursementInvoice();
        invoice.setInvoiceId(invoiceId);
        invoice.setReimbursementId(77L);
        invoice.setStoragePath(storagePath);
        OaReimbursement detail = claim(77L);
        detail.setRowVersion(4L);
        OaReimbursementItem manual = completeItem();
        manual.setClaimedAmount(new BigDecimal("10.00"));
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceById(invoiceId)).thenReturn(invoice);
        when(mapper.deleteInvoice(invoiceId, 77L)).thenReturn(1);
        when(mapper.selectItemsByReimbursementId(77L))
                .thenReturn(List.of(manual));
        when(mapper.updateReimbursement(any())).thenReturn(1);
        when(mapper.selectById(77L)).thenReturn(detail);
        when(mapper.selectInvoicesByReimbursementId(77L))
                .thenReturn(List.of());
    }

    private void stubReplayUpload(Long invoiceId, String sha256,
            String storagePath)
    {
        OaReimbursement current = claim(77L);
        OaReimbursementInvoice existing = new OaReimbursementInvoice();
        existing.setInvoiceId(invoiceId);
        existing.setReimbursementId(77L);
        when(mapper.selectByIdForUpdate(77L)).thenReturn(current);
        when(mapper.selectInvoiceByReimbursementAndSha(77L, sha256))
                .thenReturn(existing);
        when(fileStorage.storeInvoice(anyLong(), any()))
                .thenReturn(new OaReimbursementFileStorageService.StoredInvoice(
                        "invoice.pdf", "replay.pdf", storagePath,
                        "application/pdf", "pdf", 12L, sha256));
    }

    private OaReimbursement claim(Long id)
    {
        OaReimbursement value = new OaReimbursement();
        value.setReimbursementId(id);
        value.setTitle("客户拜访交通费");
        value.setPurpose("客户拜访");
        value.setApplicantId(9L);
        value.setShopDeptId(201L);
        value.setStatus("draft");
        value.setExportStatus("not_exported");
        value.setRowVersion(0L);
        return value;
    }

    private OaReimbursementItem completeItem()
    {
        OaReimbursementItem item = new OaReimbursementItem();
        item.setExpenseType("交通费");
        item.setExpenseDate(new Date());
        item.setDescription("市内交通");
        item.setClaimedAmount(new BigDecimal("18.60"));
        return item;
    }

    private void login()
    {
        SysUser user = new SysUser();
        user.setUserId(9L);
        user.setUserName("mobile-user");
        user.setDeptId(301L);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(9L);
        loginUser.setUsername("mobile-user");
        loginUser.setSysUser(user);
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("mobile-user");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }
}
