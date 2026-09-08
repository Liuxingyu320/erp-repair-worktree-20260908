package com.erp.system.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.service.ISysConfigService;

@Service
public class HrEmployeeQueueFilterService
{
    static final String CONTRACT_WARNING_DAYS_KEY="todo.contract.warning.days";
    static final int DEFAULT_WARNING_DAYS=30;
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private final ISysConfigService configService;
    private final Clock clock;

    @Autowired
    public HrEmployeeQueueFilterService(ISysConfigService configService)
    { this(configService,Clock.system(BUSINESS_ZONE)); }

    HrEmployeeQueueFilterService(ISysConfigService configService,Clock clock)
    { this.configService=configService;this.clock=clock; }

    public HrEmployeeQuery prepare(HrEmployeeQuery value)
    {
        HrEmployeeQuery query=value==null?new HrEmployeeQuery():value;
        if(Boolean.TRUE.equals(query.getContractDue()))
        {
            LocalDate today=LocalDate.now(clock);
            query.setContractDueFrom(today);
            query.setContractDueTo(today.plusDays(resolveWarningDays()));
        }
        else
        {
            query.setContractDueFrom(null);
            query.setContractDueTo(null);
        }
        return query;
    }

    private int resolveWarningDays()
    {
        try
        {
            int value=Integer.parseInt(configService.selectConfigByKey(CONTRACT_WARNING_DAYS_KEY));
            return value>=1&&value<=365?value:DEFAULT_WARNING_DAYS;
        }
        catch(Exception ignored)
        {
            return DEFAULT_WARNING_DAYS;
        }
    }
}
