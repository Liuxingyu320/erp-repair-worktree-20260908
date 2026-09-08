package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingImportRow;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.mapper.HrOnboardingImportMapper;
import com.erp.system.service.IHrOnboardingService;

@ExtendWith(MockitoExtension.class)
class HrOnboardingImportRowProcessorTest
{
    @Mock private HrOnboardingImportMapper mapper;
    @Mock private HrOnboardingAccessService accessService;
    @Mock private HrOnboardingConflictService conflictService;
    @Mock private IHrOnboardingService onboardingService;
    private HrOnboardingImportRowProcessor processor;

    @BeforeEach
    void setUp()
    {
        processor = new HrOnboardingImportRowProcessor(mapper, accessService, conflictService,
                onboardingService, () -> 7L);
        org.mockito.Mockito.lenient().when(mapper.recordRowDecision(any(), any(), any(), any(), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.markRowSuccess(any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void processIsAPublicRequiresNewTransactionBoundary() throws Exception
    {
        Method method = HrOnboardingImportRowProcessor.class.getMethod("process", Long.class,
                HrOnboardingImportConfirmRequest.RowDecision.class, String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor()).contains(Exception.class);
        Method fenced=HrOnboardingImportRowProcessor.class.getMethod("process",Long.class,
                HrOnboardingImportConfirmRequest.RowDecision.class,String.class,Integer.class);
        Transactional fencedTx=fenced.getAnnotation(Transactional.class);
        assertThat(fencedTx).isNotNull();assertThat(fencedTx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @SuppressWarnings("deprecation")
    void springProxyActuallyOpensRequiresNewForThePublicRowProcessor()
    {
        HrOnboardingImportRow row = row("IMPORTABLE");
        when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        when(conflictService.findImportConflicts(any())).thenReturn(Collections.emptyList());
        HrOnboarding created = new HrOnboarding(); created.setOnboardingId(101L);
        when(onboardingService.createImported(row, "operator")).thenReturn(created);
        java.util.List<Integer> propagations = new java.util.ArrayList<>();
        PlatformTransactionManager transactions = new PlatformTransactionManager()
        {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition)
            { propagations.add(definition.getPropagationBehavior()); return new SimpleTransactionStatus(); }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        };
        ProxyFactory factory = new ProxyFactory(processor);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        HrOnboardingImportRowProcessor proxy = (HrOnboardingImportRowProcessor) factory.getProxy();

        proxy.process(11L, decision("IMPORT", null), "operator");

        assertThat(propagations).containsExactly(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void revalidatesScopeAndLatestConflictsBeforeUsingImportedCreationPath()
    {
        HrOnboardingImportRow row = row("IMPORTABLE");
        when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        when(conflictService.findImportConflicts(any(HrOnboarding.class))).thenReturn(Collections.emptyList());
        HrOnboarding created = new HrOnboarding(); created.setOnboardingId(101L);
        when(onboardingService.createImported(row, "operator")).thenReturn(created);
        when(mapper.recordRowDecision(any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.markRowSuccess(any(), any(), any(), any(), any())).thenReturn(1);

        Long result = processor.process(11L, decision("IMPORT", null), "operator");

        verify(accessService).validateTargets(row.getPayload());
        verify(conflictService).findImportConflicts(row.getPayload());
        verify(onboardingService).createImported(row, "operator");
        verify(mapper).markRowSuccess(11L, 1L, 101L, 7L, "operator");
        assertThat(result).isEqualTo(101L);
    }

    @Test
    void locksGlobalIdentityBeforeRequeryAndRequiresExactMutationCounts()
    {
        HrOnboardingImportRow row = row("IMPORTABLE");
        row.getPayload().setPhoneNumber("13800138000");
        row.getPayload().setIdNumber("350203199001011234");
        row.getPayload().setEmployeeNo("E001");
        when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        when(conflictService.findImportConflicts(any())).thenReturn(Collections.emptyList());
        when(mapper.recordRowDecision(any(), any(), any(), any(), any())).thenReturn(1);
        HrOnboarding created = new HrOnboarding(); created.setOnboardingId(101L);
        when(onboardingService.createImported(row, "operator")).thenReturn(created);
        when(mapper.markRowSuccess(any(), any(), any(), any(), any())).thenReturn(1);

        processor.process(11L, decision("IMPORT", null), "operator");

        org.mockito.InOrder order = inOrder(accessService, conflictService, onboardingService);
        order.verify(accessService).validateTargets(row.getPayload());
        order.verify(accessService).lockGlobalOpenIdentitySetForUpdate("13800138000", "350203199001011234", "E001");
        order.verify(conflictService).findImportConflicts(row.getPayload());
        order.verify(onboardingService).createImported(row, "operator");
    }

    @Test
    void zeroSuccessMutationRollsBackImportedCreationPath()
    {
        HrOnboardingImportRow row = row("IMPORTABLE");
        when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        when(conflictService.findImportConflicts(any())).thenReturn(Collections.emptyList());
        when(mapper.recordRowDecision(any(), any(), any(), any(), any())).thenReturn(1);
        HrOnboarding created = new HrOnboarding(); created.setOnboardingId(101L);
        when(onboardingService.createImported(row, "operator")).thenReturn(created);
        when(mapper.markRowSuccess(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> processor.process(11L, decision("IMPORT", null), "operator"))
                .isInstanceOf(ServiceException.class).hasMessage("IMPORT_ROW_SUCCESS_UPDATE_FAILED");
    }

    @Test
    void globalOpenIdentityResultBlocksBeforeScopedRequeryOrCreation()
    {
        HrOnboardingImportRow row=row("IMPORTABLE");when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        HrOnboarding other=new HrOnboarding();other.setOnboardingId(999L);other.setStatus(HrOnboarding.STATUS_DRAFT);
        when(accessService.lockGlobalOpenIdentitySetForUpdate(any(),any(),any()))
                .thenReturn(Collections.singletonList(other));

        assertThatThrownBy(()->processor.process(11L,decision("IMPORT",null),"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("STALE_CONFLICT");

        verify(conflictService,never()).findImportConflicts(any());
        verify(onboardingService,never()).createImported(any(),any());
    }

    @Test
    void fencedProcessTouchesLeaseBeforeWorkAndImmediatelyBeforeCommit()
    {
        HrOnboardingImportRow row=row("IMPORTABLE");when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        when(mapper.touchProcessingBatchLease(1L,4,"operator")).thenReturn(1);
        when(accessService.lockGlobalOpenIdentitySetForUpdate(any(),any(),any())).thenReturn(Collections.emptyList());
        when(conflictService.findImportConflicts(any())).thenReturn(Collections.emptyList());
        HrOnboarding created=new HrOnboarding();created.setOnboardingId(101L);
        when(onboardingService.createImported(row,"operator")).thenReturn(created);

        processor.process(11L,decision("IMPORT",null),"operator",4);

        verify(mapper,org.mockito.Mockito.times(2)).touchProcessingBatchLease(1L,4,"operator");
        org.mockito.InOrder order=org.mockito.Mockito.inOrder(mapper,onboardingService);
        order.verify(mapper).touchProcessingBatchLease(1L,4,"operator");
        order.verify(onboardingService).createImported(row,"operator");
        order.verify(mapper).markRowSuccess(11L,1L,101L,7L,"operator");
        order.verify(mapper).touchProcessingBatchLease(1L,4,"operator");
    }

    @Test
    void lostLeaseFailsBeforeScopeConflictOrCreation()
    {
        HrOnboardingImportRow row=row("IMPORTABLE");when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        when(mapper.touchProcessingBatchLease(1L,4,"operator")).thenReturn(0);

        assertThatThrownBy(()->processor.process(11L,decision("IMPORT",null),"operator",4))
                .isInstanceOf(ServiceException.class).hasMessage("IMPORT_PROCESSING_LEASE_LOST");

        verify(accessService,never()).validateTargets(any());verify(conflictService,never()).findImportConflicts(any());
        verify(onboardingService,never()).createImported(any(),any());
    }

    @Test
    void staleBlockingConflictAndOutOfScopeTargetFailBeforeCreation()
    {
        HrOnboardingImportRow row = row("WARNING");
        when(mapper.selectRowByIdForUpdate(11L)).thenReturn(row);
        HrOnboardingConflictVo conflict = new HrOnboardingConflictVo();
        conflict.setBlocking(true); conflict.setSourceType("ONBOARDING");
        when(conflictService.findImportConflicts(any())).thenReturn(Collections.singletonList(conflict));

        assertThatThrownBy(() -> processor.process(11L, decision("CONTINUE", null), "operator"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("STALE_CONFLICT");
        verify(onboardingService, never()).createImported(any(), any());

        org.mockito.Mockito.doThrow(new ServiceException("OUT_OF_SCOPE"))
                .when(accessService).validateTargets(any());
        assertThatThrownBy(() -> processor.process(11L, decision("CONTINUE", null), "operator"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("OUT_OF_SCOPE");
    }

    @Test
    void requiresExactDecisionsAndStoresBindablePreferenceOnlyForLaterPrefill()
    {
        HrOnboardingImportRow bindable = row("BINDABLE_ACCOUNT");
        bindable.setCandidateUserId(88L);
        when(mapper.selectRowByIdForUpdate(11L)).thenReturn(bindable);
        HrOnboardingConflictVo candidate = new HrOnboardingConflictVo();
        candidate.setCandidateUserId(88L); candidate.setEligibleForBind(true); candidate.setBlocking(true);
        when(conflictService.findImportConflicts(any())).thenReturn(Collections.singletonList(candidate));
        HrOnboarding created = new HrOnboarding(); created.setOnboardingId(101L);
        when(onboardingService.createImported(any(), any())).thenReturn(created);

        assertThatThrownBy(() -> processor.process(11L, decision("BIND_EXISTING", null), "operator"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("BIND_USER_REQUIRED");

        processor.process(11L, decision("BIND_EXISTING", 88L), "operator");

        assertThat(bindable.getPayload().getPreferredConflictAction()).isEqualTo("BIND_EXISTING");
        assertThat(bindable.getPayload().getPreferredBindUserId()).isEqualTo(88L);
        assertThat(bindable.getPayload().getLinkedUserId()).isNull();
    }

    @Test
    void continueIsRejectedWhenLatestEligibleAccountIsStillBlocking()
    {
        HrOnboardingImportRow warning=row("WARNING");
        when(mapper.selectRowByIdForUpdate(11L)).thenReturn(warning);
        HrOnboardingConflictVo account=new HrOnboardingConflictVo();account.setSourceType("EMPLOYEE_ACCOUNT");
        account.setEligibleForBind(true);account.setBlocking(true);account.setCandidateUserId(88L);
        when(conflictService.findImportConflicts(any())).thenReturn(Collections.singletonList(account));

        assertThatThrownBy(() -> processor.process(11L,decision("CONTINUE",null),"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("STALE_CONFLICT");
        verify(onboardingService,never()).createImported(any(),any());
    }

    private static HrOnboardingImportConfirmRequest.RowDecision decision(String value, Long bind)
    {
        HrOnboardingImportConfirmRequest.RowDecision decision = new HrOnboardingImportConfirmRequest.RowDecision();
        decision.setRowId(11L); decision.setDecision(value); decision.setBindUserId(bind); return decision;
    }

    private static HrOnboardingImportRow row(String category)
    {
        HrOnboardingImportRow row = new HrOnboardingImportRow();
        row.setRowId(11L); row.setBatchId(1L); row.setCategory(category); row.setRowStatus("PREVIEWED");
        row.setPayload(new HrOnboarding()); return row;
    }
}
