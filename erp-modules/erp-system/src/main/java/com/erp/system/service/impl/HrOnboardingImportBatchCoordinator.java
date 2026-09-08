package com.erp.system.service.impl;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.mapper.HrOnboardingImportMapper;

/** Durable transaction boundaries for claiming/resuming an import processing lease. */
@Service
public class HrOnboardingImportBatchCoordinator
{
    private final HrOnboardingImportMapper mapper;

    public HrOnboardingImportBatchCoordinator(HrOnboardingImportMapper mapper){this.mapper=mapper;}

    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void claimAndRecord(Long batchId,Integer version,Long creatorUserId,
            List<HrOnboardingImportConfirmRequest.RowDecision> decisions,String operator)
    {
        requireOne(mapper.claimPreviewedBatch(batchId,version,creatorUserId,operator),"IMPORT_VERSION_CONFLICT");
        for(HrOnboardingImportConfirmRequest.RowDecision decision:
                decisions==null?Collections.<HrOnboardingImportConfirmRequest.RowDecision>emptyList():decisions)
            requireOne(mapper.recordRowDecision(decision.getRowId(),batchId,decision.getDecision(),
                    decision.getBindUserId(),operator),"IMPORT_ROW_DECISION_UPDATE_FAILED");
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void resume(Long batchId,Integer version,Long creatorUserId,Date leaseCutoff,String operator)
    {
        requireOne(mapper.claimStaleProcessingBatch(batchId,version,creatorUserId,leaseCutoff,operator),
                "IMPORT_PROCESSING_LEASE_ACTIVE");
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void heartbeat(Long batchId,Integer version,Long creatorUserId,String operator)
    {
        requireOne(mapper.heartbeatProcessingBatch(batchId,version,creatorUserId,operator),"IMPORT_PROCESSING_LEASE_LOST");
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void finish(Long batchId,Integer version,int success,int failure,String status,Long userId,String operator)
    {
        requireOne(mapper.finishBatch(batchId,version,success,failure,status,userId,operator),"IMPORT_FINISH_FAILED");
    }

    private void requireOne(int count,String code){if(count!=1)throw new ServiceException(code);}
}
