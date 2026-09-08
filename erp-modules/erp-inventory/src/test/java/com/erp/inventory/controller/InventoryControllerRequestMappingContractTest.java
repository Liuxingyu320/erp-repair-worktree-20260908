package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("库存控制器请求映射唯一性")
class InventoryControllerRequestMappingContractTest
{
    @Test
    @DisplayName("相同请求条件不能映射到多个控制器方法")
    void shouldNotDeclareAmbiguousRequestMappings()
            throws ClassNotFoundException
    {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(
                RestController.class));
        Map<String, String> owners = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (var candidate : scanner.findCandidateComponents(
                "com.erp.inventory.controller"))
        {
            String className = candidate.getBeanClassName();
            if (className == null)
            {
                continue;
            }
            Class<?> type = Class.forName(className, false,
                    Thread.currentThread().getContextClassLoader());
            RequestMapping typeMapping = AnnotatedElementUtils
                    .findMergedAnnotation(type, RequestMapping.class);
            List<String> roots = paths(typeMapping);
            for (Method method : type.getDeclaredMethods())
            {
                RequestMapping mapping = AnnotatedElementUtils
                        .findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null)
                {
                    continue;
                }
                List<String> methods = httpMethods(mapping);
                for (String root : roots)
                {
                    for (String path : paths(mapping))
                    {
                        for (String httpMethod : methods)
                        {
                            String key = httpMethod + " "
                                    + normalize(root, path) + " params="
                                    + sorted(mapping.params()) + " headers="
                                    + sorted(mapping.headers()) + " consumes="
                                    + sorted(mapping.consumes()) + " produces="
                                    + sorted(mapping.produces());
                            String owner = type.getName() + "#"
                                    + method.toGenericString();
                            String previous = owners.putIfAbsent(key, owner);
                            if (previous != null && !previous.equals(owner))
                            {
                                duplicates.add(key + " -> " + previous
                                        + " | " + owner);
                            }
                        }
                    }
                }
            }
        }

        assertThat(duplicates)
                .as("Spring MVC请求映射冲突")
                .isEmpty();
    }

    private static List<String> paths(RequestMapping mapping)
    {
        if (mapping == null)
        {
            return List.of("");
        }
        String[] values = mapping.path().length == 0
                ? mapping.value() : mapping.path();
        return values.length == 0 ? List.of("")
                : Arrays.asList(values);
    }

    private static List<String> httpMethods(RequestMapping mapping)
    {
        RequestMethod[] methods = mapping.method();
        return methods.length == 0 ? List.of("ANY")
                : Arrays.stream(methods).map(Enum::name).toList();
    }

    private static String normalize(String root, String path)
    {
        String value = ("/" + root + "/" + path)
                .replaceAll("/{2,}", "/");
        return value.length() > 1 && value.endsWith("/")
                ? value.substring(0, value.length() - 1) : value;
    }

    private static String sorted(String[] values)
    {
        return new TreeSet<>(Arrays.asList(values)).toString();
    }
}
