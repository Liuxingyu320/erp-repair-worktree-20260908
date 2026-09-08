package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.annotation.Excel;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.domain.vo.SysOperLogExportVo;

@DisplayName("操作日志安全接口")
class SysOperlogControllerTest
{
    @Test
    @DisplayName("日志详情必须使用独立查询权限")
    void detailShouldRequireQueryPermission() throws Exception
    {
        Method method = SysOperlogController.class.getMethod("getInfo", Long.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("system:operlog:detail");
    }

    @Test
    @DisplayName("默认导出模型不包含日志正文和异常全文")
    void exportContractShouldNotContainPayloadFields()
    {
        Set<String> fieldNames = Arrays.stream(SysOperLogExportVo.class.getDeclaredFields())
                .filter(field -> field.getAnnotation(Excel.class) != null)
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .doesNotContain("operParam", "jsonResult", "errorMsg")
                .contains("operId", "title", "requestMethod", "operName", "status", "operTime", "costTime");
    }
}
