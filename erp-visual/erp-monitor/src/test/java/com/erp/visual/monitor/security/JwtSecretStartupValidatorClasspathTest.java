package com.erp.visual.monitor.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtSecretStartupValidatorClasspathTest
{
    private static final String VALIDATOR_CLASS = "com.erp.common.core.config.JwtSecretStartupValidator";

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void monitorClasspathImportsJwtSecretStartupValidator()
    {
        assertThat(autoConfigurationImports()).contains(VALIDATOR_CLASS);
    }

    private static List<String> autoConfigurationImports()
    {
        try
        {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
                    .getResources(AUTO_CONFIGURATION_IMPORTS);
            List<String> imports = new ArrayList<>();
            while (resources.hasMoreElements())
            {
                imports.addAll(readImports(resources.nextElement()));
            }
            return imports;
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to read auto-configuration imports", e);
        }
    }

    private static List<String> readImports(URL resource) throws IOException
    {
        List<String> imports = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(),
                StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#"))
                {
                    imports.add(trimmedLine);
                }
            }
        }
        return imports;
    }
}
