package com.erp.system.mapper;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;

/** Supplies the shared health selection fragment to isolated mapper tests. */
public final class HealthCertificateMapperFragments
{
    private HealthCertificateMapperFragments() { }
    public static void register(Configuration configuration)
    {
        String path = "mapper/system/HrHealthCertificateMapper.xml";
        if (configuration.isResourceLoaded(path)) return;
        try (InputStream input = HealthCertificateMapperFragments.class.getClassLoader().getResourceAsStream(path))
        {
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        catch (Exception error) { throw new AssertionError(error); }
    }
}
