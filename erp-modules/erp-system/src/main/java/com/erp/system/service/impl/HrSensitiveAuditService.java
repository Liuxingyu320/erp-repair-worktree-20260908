package com.erp.system.service.impl;

import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.HrSensitiveAccessLog;
import com.erp.system.mapper.HrSensitiveAccessLogMapper;

/** Independent export audit boundary so failed outer workflows cannot erase failure evidence. */
@Service
public class HrSensitiveAuditService
{
    private final HrSensitiveAccessLogMapper mapper;
    public HrSensitiveAuditService(HrSensitiveAccessLogMapper mapper){this.mapper=mapper;}

    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void recordGenerated(String scope,Long operatorId,String operatorName,String ip)
    {insert(scope,operatorId,operatorName,ip,"SUCCESS","GENERATED");}

    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void recordFailure(String scope,Long operatorId,String operatorName,String ip,String reason)
    {insert(scope,operatorId,operatorName,ip,"FAILURE",reason);}

    private void insert(String scope,Long operatorId,String operatorName,String ip,String result,String message)
    {
        if(operatorId==null||operatorName==null||operatorName.isBlank())
            throw new ServiceException("敏感访问操作人不能为空");
        HrSensitiveAccessLog log=new HrSensitiveAccessLog();
        log.setActionType("EXPORT");log.setOperatorUserId(operatorId);log.setOperatorName(operatorName);
        log.setRequestIp(ip);log.setEventTime(new Date());log.setCreateBy(operatorName);
        log.setExportScope(scope);log.setResult(result);log.setResultMessage(message);
        if(mapper.insertSensitiveAccessLog(log)!=1)throw new ServiceException("敏感访问审计写入失败");
    }
}
