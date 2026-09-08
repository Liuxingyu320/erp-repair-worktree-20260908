package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.mapper.OaDeptScopeMapper;

@DisplayName("OA 控制器门店上下文解析")
class OaBaseControllerTest
{
    @Test
    @DisplayName("统一解析 Dept-NumId 请求头")
    void shouldResolveSelectedDeptHeader()
    {
        TestController controller = new TestController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "88");

        assertThat(controller.resolve(request)).isEqualTo(88L);
    }

    @Test
    @DisplayName("缺少请求或空请求头返回空门店上下文")
    void shouldReturnNullWhenHeaderMissing()
    {
        TestController controller = new TestController();
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(controller.resolve(null)).isNull();
        assertThat(controller.resolve(request)).isNull();
    }

    @Test
    @DisplayName("考勤门店上下文只接受有效门店")
    void shouldAcceptActiveStoreForAttendance()
    {
        OaDeptScopeMapper deptScopeMapper = mock(OaDeptScopeMapper.class);
        TestController controller = attendanceController(deptScopeMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "88");
        when(deptScopeMapper.countActiveStoreDept(88L)).thenReturn(1);

        assertThat(controller.resolveAttendance(request)).isEqualTo(88L);
    }

    @Test
    @DisplayName("考勤门店上下文拒绝仓库或无效部门")
    void shouldRejectNonStoreForAttendance()
    {
        OaDeptScopeMapper deptScopeMapper = mock(OaDeptScopeMapper.class);
        TestController controller = attendanceController(deptScopeMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "99");
        when(deptScopeMapper.countActiveStoreDept(99L)).thenReturn(0);

        assertThatThrownBy(() -> controller.resolveAttendance(request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("考勤只能选择有效门店");
    }

    @Test
    @DisplayName("管理员未指定考勤门店时保留全门店查询语义")
    void shouldKeepNullAttendanceScopeWhenHeaderMissing()
    {
        OaDeptScopeMapper deptScopeMapper = mock(OaDeptScopeMapper.class);
        TestController controller = attendanceController(deptScopeMapper);

        assertThat(controller.resolveAttendance(new MockHttpServletRequest())).isNull();
        verifyNoInteractions(deptScopeMapper);
    }

    private static TestController attendanceController(OaDeptScopeMapper deptScopeMapper)
    {
        TestController controller = new TestController();
        ReflectionTestUtils.setField(controller, "deptScopeMapper", deptScopeMapper);
        return controller;
    }

    private static class TestController extends OaBaseController
    {
        private Long resolve(MockHttpServletRequest request)
        {
            return resolveShopDeptId(request);
        }

        private Long resolveAttendance(MockHttpServletRequest request)
        {
            return resolveAttendanceShopDeptId(request);
        }
    }
}
