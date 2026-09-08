package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

@DisplayName("库存服务Spring构造器注入契约")
class SpringServiceConstructorInjectionContractTest
{
    @Test
    @DisplayName("多个非默认构造器必须明确标记注入入口")
    void shouldMarkInjectionConstructorForAmbiguousServices()
            throws ClassNotFoundException
    {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        List<String> violations = new ArrayList<>();

        for (var candidate : scanner.findCandidateComponents(
                "com.erp.inventory"))
        {
            String className = candidate.getBeanClassName();
            if (className == null)
            {
                continue;
            }
            Class<?> type = Class.forName(className);
            Constructor<?>[] constructors = Arrays.stream(
                            type.getDeclaredConstructors())
                    .filter(constructor -> !constructor.isSynthetic())
                    .toArray(Constructor<?>[]::new);
            boolean hasDefault = Arrays.stream(constructors)
                    .anyMatch(constructor ->
                            constructor.getParameterCount() == 0);
            boolean hasInjectionMarker = Arrays.stream(constructors)
                    .anyMatch(constructor -> constructor.isAnnotationPresent(
                            Autowired.class));
            if (constructors.length > 1 && !hasDefault
                    && !hasInjectionMarker)
            {
                violations.add(type.getName());
            }
        }

        assertThat(violations)
                .as("存在多个非默认构造器的@Service必须明确标记@Autowired")
                .isEmpty();
    }
}
