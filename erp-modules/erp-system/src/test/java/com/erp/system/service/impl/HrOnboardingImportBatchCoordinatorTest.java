package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.mapper.HrOnboardingImportMapper;

@ExtendWith(MockitoExtension.class)
class HrOnboardingImportBatchCoordinatorTest
{
    @Mock HrOnboardingImportMapper mapper;

    @Test
    void claimAndDecisionPersistenceShareASeparateAtomicTransaction() throws Exception
    {
        Method method=HrOnboardingImportBatchCoordinator.class.getMethod("claimAndRecord",Long.class,
                Integer.class,Long.class,java.util.List.class,String.class);
        Transactional tx=method.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        when(mapper.claimPreviewedBatch(1L,3,7L,"operator")).thenReturn(1);
        when(mapper.recordRowDecision(11L,1L,"IMPORT",null,"operator")).thenReturn(1);
        HrOnboardingImportConfirmRequest.RowDecision decision=new HrOnboardingImportConfirmRequest.RowDecision();
        decision.setRowId(11L);decision.setDecision("IMPORT");

        new HrOnboardingImportBatchCoordinator(mapper).claimAndRecord(1L,3,7L,
                Collections.singletonList(decision),"operator");

        verify(mapper).recordRowDecision(11L,1L,"IMPORT",null,"operator");
    }

    @Test
    void claimDecisionHeartbeatAndFinishRequireExactlyOneMutation()
    {
        HrOnboardingImportBatchCoordinator coordinator=new HrOnboardingImportBatchCoordinator(mapper);
        when(mapper.claimPreviewedBatch(any(),any(),any(),any())).thenReturn(0);
        assertThatThrownBy(()->coordinator.claimAndRecord(1L,3,7L,Collections.emptyList(),"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("IMPORT_VERSION_CONFLICT");
        when(mapper.heartbeatProcessingBatch(1L,4,7L,"operator")).thenReturn(0);
        assertThatThrownBy(()->coordinator.heartbeat(1L,4,7L,"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("IMPORT_PROCESSING_LEASE_LOST");
        when(mapper.finishBatch(1L,4,1,0,"COMPLETED",7L,"operator")).thenReturn(0);
        assertThatThrownBy(()->coordinator.finish(1L,4,1,0,"COMPLETED",7L,"operator"))
                .isInstanceOf(ServiceException.class).hasMessage("IMPORT_FINISH_FAILED");
    }
}
