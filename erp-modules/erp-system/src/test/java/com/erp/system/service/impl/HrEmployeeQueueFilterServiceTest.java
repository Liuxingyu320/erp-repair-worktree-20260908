package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.service.ISysConfigService;

class HrEmployeeQueueFilterServiceTest
{
    @Test
    void derivesTrustedContractWindowAndLeavesNormalQueriesUntouched()
    {
        ISysConfigService config=mock(ISysConfigService.class);
        when(config.selectConfigByKey("todo.contract.warning.days")).thenReturn("30");
        Clock clock=Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"),ZoneId.of("Asia/Shanghai"));
        HrEmployeeQueueFilterService service=new HrEmployeeQueueFilterService(config,clock);
        HrEmployeeQuery due=new HrEmployeeQuery();due.setContractDue(true);

        service.prepare(due);

        assertThat(due.getContractDueFrom()).isEqualTo(LocalDate.of(2026,7,13));
        assertThat(due.getContractDueTo()).isEqualTo(LocalDate.of(2026,8,12));
        HrEmployeeQuery normal=new HrEmployeeQuery();
        normal.setContractDueFrom(LocalDate.of(2020,1,1));
        normal.setContractDueTo(LocalDate.of(2030,1,1));
        service.prepare(normal);
        assertThat(normal.getContractDueFrom()).isNull();
        assertThat(normal.getContractDueTo()).isNull();
    }

    @Test
    void invalidWarningConfigurationUsesThirtyDayFallback()
    {
        ISysConfigService config=mock(ISysConfigService.class);
        when(config.selectConfigByKey("todo.contract.warning.days")).thenReturn("invalid");
        Clock clock=Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"),ZoneId.of("Asia/Shanghai"));
        HrEmployeeQueueFilterService service=new HrEmployeeQueueFilterService(config,clock);
        HrEmployeeQuery query=new HrEmployeeQuery();query.setContractDue(true);

        service.prepare(query);

        assertThat(query.getContractDueTo()).isEqualTo(LocalDate.of(2026,8,12));
    }
}
