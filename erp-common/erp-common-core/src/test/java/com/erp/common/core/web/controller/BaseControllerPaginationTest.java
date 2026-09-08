package com.erp.common.core.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.web.page.TableDataInfo;

@DisplayName("基础控制器分页响应")
class BaseControllerPaginationTest
{
    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("列表未被 PageHelper 拦截时按请求分页参数兜底切片")
    void shouldSliceRowsWhenPageHelperDidNotApply()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pageNum", "2");
        request.addParameter("pageSize", "3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        TableDataInfo table = new TestController().table(rows(8));

        assertThat(table.getTotal()).isEqualTo(8);
        assertThat(table.getRows()).isEqualTo(Arrays.asList("row-4", "row-5", "row-6"));
    }

    private static List<String> rows(int count)
    {
        List<String> rows = new ArrayList<>();
        for (int index = 1; index <= count; index++)
        {
            rows.add("row-" + index);
        }
        return rows;
    }

    private static class TestController extends BaseController
    {
        private TableDataInfo table(List<?> list)
        {
            return getDataTable(list);
        }
    }
}
