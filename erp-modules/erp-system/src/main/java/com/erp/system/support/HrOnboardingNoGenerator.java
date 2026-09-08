package com.erp.system.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import com.erp.common.core.utils.uuid.IdUtils;

@Component
public class HrOnboardingNoGenerator
{
    private final Clock clock;
    public HrOnboardingNoGenerator() { this(Clock.systemDefaultZone()); }
    HrOnboardingNoGenerator(Clock clock) { this.clock = clock; }
    public String next()
    {
        return "OB" + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)
                + IdUtils.simpleUUID().substring(0, 12).toUpperCase();
    }
}
