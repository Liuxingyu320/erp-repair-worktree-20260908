package com.erp.system.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.type.TypeAliasRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.AssignableTypeFilter;

class TemporaryPasswordGeneratorBeanRegistrationTest
{
    @Test
    void systemComponentScanHasNoDuplicateDefaultBeanNames()
    {
        try (GenericApplicationContext context = new GenericApplicationContext())
        {
            new ClassPathBeanDefinitionScanner(context).scan("com.erp.system");
        }
    }

    @Test
    void systemMyBatisTypeAliasScanHasNoDuplicateSimpleClassNames()
    {
        TypeAliasRegistry registry = new TypeAliasRegistry();

        registry.registerAliases("com.erp.system");

        assertThat(registry.resolveAlias("TemporaryPasswordGenerator"))
                .isEqualTo(com.erp.system.service.support.TemporaryPasswordGenerator.class);
        assertThat(registry.resolveAlias("CredentialTemporaryPasswordGenerator"))
                .isEqualTo(com.erp.system.service.credential.CredentialTemporaryPasswordGenerator.class);
    }

    @Test
    void bothTemporaryPasswordGeneratorsHaveUniqueComponentNames()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new AssignableTypeFilter(
                    com.erp.system.service.support.TemporaryPasswordGenerator.class));
            scanner.addIncludeFilter(new AssignableTypeFilter(
                    com.erp.system.service.credential.CredentialTemporaryPasswordGenerator.class));

            scanner.scan("com.erp.system.service.support", "com.erp.system.service.credential");
            context.refresh();

            assertThat(context.getBean(
                    "temporaryPasswordGenerator",
                    com.erp.system.service.support.TemporaryPasswordGenerator.class)).isNotNull();
            assertThat(context.getBean(
                    "credentialTemporaryPasswordGenerator",
                    com.erp.system.service.credential.CredentialTemporaryPasswordGenerator.class)).isNotNull();
            assertThat(context.getBeansOfType(
                    com.erp.system.service.support.TemporaryPasswordGenerator.class)).hasSize(1);
            assertThat(context.getBeansOfType(
                    com.erp.system.service.credential.CredentialTemporaryPasswordGenerator.class)).hasSize(1);
        }
    }
}
