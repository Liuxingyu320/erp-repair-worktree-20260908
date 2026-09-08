package com.erp.system.service.support;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import com.erp.system.domain.vo.SysBuildInfoVo;

@Component
public class SystemBuildInfoProvider
{
    private final BuildProperties buildProperties;

    @Autowired
    public SystemBuildInfoProvider(ObjectProvider<BuildProperties> buildProperties)
    {
        this.buildProperties = buildProperties.getIfAvailable();
    }

    SystemBuildInfoProvider(BuildProperties buildProperties)
    {
        this.buildProperties = buildProperties;
    }

    public SysBuildInfoVo current()
    {
        if (buildProperties == null)
        {
            return SysBuildInfoVo.unavailable();
        }
        String commit = normalized(buildProperties.get("commit"));
        Instant time = buildProperties.getTime();
        return new SysBuildInfoVo(commit,
                time == null ? "UNSET" : time.toString(), normalized(buildProperties.getVersion()));
    }

    private String normalized(String value)
    {
        return value == null || value.isBlank() ? "UNSET" : value;
    }
}
