package com.erp.system.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingImportRow;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.mapper.HrOnboardingImportMapper;
import com.erp.system.service.IHrOnboardingService;
import com.erp.system.support.HrOnboardingImportPayloadCodec;

/** One-row transaction boundary. It must be invoked through this separately injected bean. */
@Service
public class HrOnboardingImportRowProcessor
{
    private final HrOnboardingImportMapper mapper;
    private final HrOnboardingAccessService accessService;
    private final HrOnboardingConflictService conflictService;
    private final IHrOnboardingService onboardingService;
    private final LongSupplier currentUserId;
    private final HrOnboardingImportPayloadCodec payloadCodec;

    @Autowired
    public HrOnboardingImportRowProcessor(HrOnboardingImportMapper mapper, HrOnboardingAccessService accessService,
            HrOnboardingConflictService conflictService, IHrOnboardingService onboardingService,
            HrOnboardingImportPayloadCodec payloadCodec)
    {
        this(mapper,accessService,conflictService,onboardingService,payloadCodec,SecurityUtils::getUserId);
    }

    HrOnboardingImportRowProcessor(HrOnboardingImportMapper mapper,HrOnboardingAccessService accessService,
            HrOnboardingConflictService conflictService,IHrOnboardingService onboardingService,LongSupplier currentUserId)
    {
        this(mapper,accessService,conflictService,onboardingService,new HrOnboardingImportPayloadCodec(),currentUserId);
    }

    HrOnboardingImportRowProcessor(HrOnboardingImportMapper mapper,HrOnboardingAccessService accessService,
            HrOnboardingConflictService conflictService,IHrOnboardingService onboardingService,
            HrOnboardingImportPayloadCodec payloadCodec,LongSupplier currentUserId)
    {
        this.mapper=mapper;this.accessService=accessService;this.conflictService=conflictService;
        this.onboardingService=onboardingService;this.payloadCodec=payloadCodec;this.currentUserId=currentUserId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long process(Long rowId,HrOnboardingImportConfirmRequest.RowDecision decision,String operator)
    {
        return processInternal(rowId,decision,operator,null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long process(Long rowId,HrOnboardingImportConfirmRequest.RowDecision decision,String operator,
            Integer leaseVersion)
    {
        return processInternal(rowId,decision,operator,leaseVersion);
    }

    private Long processInternal(Long rowId,HrOnboardingImportConfirmRequest.RowDecision decision,String operator,
            Integer leaseVersion)
    {
        HrOnboardingImportRow row=mapper.selectRowByIdForUpdate(rowId);
        if(row==null || !"PREVIEWED".equals(row.getRowStatus())) throw new ServiceException("ROW_NOT_CONFIRMABLE");
        if(leaseVersion!=null)requireOne(mapper.touchProcessingBatchLease(row.getBatchId(),leaseVersion,operator),
                "IMPORT_PROCESSING_LEASE_LOST");
        if(row.getPayload()==null && row.getPayloadJson()!=null) payloadCodec.hydrate(row);
        if(row.getPayload()==null) throw new ServiceException("ROW_PAYLOAD_INVALID");
        validateDecision(row,decision);
        try{accessService.validateTargets(row.getPayload());}
        catch(ServiceException denied){throw new ServiceException("OUT_OF_SCOPE");}
        List<HrOnboarding> global=accessService.lockGlobalOpenIdentitySetForUpdate(row.getPayload().getPhoneNumber(),
                row.getPayload().getIdNumber(), row.getPayload().getEmployeeNo());
        if(global!=null&&!global.isEmpty())throw new ServiceException("STALE_CONFLICT");
        List<HrOnboardingConflictVo> latest=conflictService.findImportConflicts(row.getPayload());
        validateLatest(row,decision,latest);
        if("BIND_EXISTING".equals(decision.getDecision()))
        {
            row.getPayload().setPreferredConflictAction("BIND_EXISTING");
            row.getPayload().setPreferredBindUserId(decision.getBindUserId());
            row.getPayload().setLinkedUserId(null);
        }
        requireOne(mapper.recordRowDecision(rowId,row.getBatchId(),decision.getDecision(),decision.getBindUserId(),operator),
                "IMPORT_ROW_DECISION_UPDATE_FAILED");
        HrOnboarding created=onboardingService.createImported(row,operator);
        if(created==null || created.getOnboardingId()==null) throw new ServiceException("IMPORT_CREATE_FAILED");
        requireOne(mapper.markRowSuccess(rowId,row.getBatchId(),created.getOnboardingId(),currentUserId.getAsLong(),operator),
                "IMPORT_ROW_SUCCESS_UPDATE_FAILED");
        if(leaseVersion!=null)requireOne(mapper.touchProcessingBatchLease(row.getBatchId(),leaseVersion,operator),
                "IMPORT_PROCESSING_LEASE_LOST");
        return created.getOnboardingId();
    }

    private void requireOne(int count,String code)
    {
        if(count!=1)throw new ServiceException(code);
    }

    private void validateDecision(HrOnboardingImportRow row,HrOnboardingImportConfirmRequest.RowDecision decision)
    {
        if(decision==null || !Objects.equals(row.getRowId(),decision.getRowId()))throw new ServiceException("ROW_DECISION_INVALID");
        String category=row.getCategory(), value=decision.getDecision();
        if("INVALID".equals(category))throw new ServiceException("ROW_INVALID");
        if("IMPORTABLE".equals(category) && !"IMPORT".equals(value))throw new ServiceException("IMPORT_DECISION_REQUIRED");
        if(("WARNING".equals(category)||"POSSIBLE_DUPLICATE".equals(category)) && !"CONTINUE".equals(value))
            throw new ServiceException("CONTINUE_DECISION_REQUIRED");
        if("BINDABLE_ACCOUNT".equals(category))
        {
            if(!"BIND_EXISTING".equals(value))throw new ServiceException("BIND_DECISION_REQUIRED");
            if(decision.getBindUserId()==null)throw new ServiceException("BIND_USER_REQUIRED");
        }
    }

    private void validateLatest(HrOnboardingImportRow row,HrOnboardingImportConfirmRequest.RowDecision decision,
            List<HrOnboardingConflictVo> conflicts)
    {
        boolean selected=false;
        if(conflicts!=null)for(HrOnboardingConflictVo conflict:conflicts)
        {
            if("BIND_EXISTING".equals(decision.getDecision()) && Boolean.TRUE.equals(conflict.getEligibleForBind())
                    && Objects.equals(decision.getBindUserId(),conflict.getCandidateUserId())) selected=true;
            else if(Boolean.TRUE.equals(conflict.getBlocking())) throw new ServiceException("STALE_CONFLICT");
        }
        if("BIND_EXISTING".equals(decision.getDecision()) && !selected)throw new ServiceException("STALE_BIND_CANDIDATE");
    }
}
