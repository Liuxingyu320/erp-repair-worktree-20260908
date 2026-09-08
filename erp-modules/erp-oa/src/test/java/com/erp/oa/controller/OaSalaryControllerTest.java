package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.oa.domain.OaSalaryConfig;
import com.erp.oa.domain.OaSalaryRecord;
import com.erp.oa.domain.vo.OaSalaryAttendancePreflightVo;
import com.erp.oa.service.IOaSalaryService;

@DisplayName("OA工资Controller")
class OaSalaryControllerTest
{
    @Test
    @DisplayName("GET配置接口透传查询店铺和当前店铺上下文")
    void shouldReadConfigWithSelectedShopHeader()
    {
        FakeSalaryService service = new FakeSalaryService();
        OaSalaryController controller = controller(service);
        MockHttpServletRequest request = requestWithShopHeader(201L);

        AjaxResult result = controller.config(202L, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(service.configResult);
        assertThat(service.readShopDeptId).isEqualTo(202L);
        assertThat(service.readSelectedShopDeptId).isEqualTo(201L);
    }

    @Test
    @DisplayName("PUT配置接口透传请求体和当前店铺上下文")
    void shouldSaveConfigWithSelectedShopHeader()
    {
        FakeSalaryService service = new FakeSalaryService();
        OaSalaryController controller = controller(service);
        MockHttpServletRequest request = requestWithShopHeader(201L);
        OaSalaryConfig config = config(202L);

        AjaxResult result = controller.saveConfig(config, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get(AjaxResult.DATA_TAG)).isSameAs(service.configResult);
        assertThat(service.savedConfig).isSameAs(config);
        assertThat(service.saveSelectedShopDeptId).isEqualTo(201L);
    }

    @Test
    @DisplayName("配置接口无店铺Header时将空上下文交由Service校验")
    void shouldPassNullSelectedShopWhenHeaderMissing()
    {
        FakeSalaryService service = new FakeSalaryService();
        OaSalaryController controller = controller(service);

        controller.config(202L, new MockHttpServletRequest());

        assertThat(service.readShopDeptId).isEqualTo(202L);
        assertThat(service.readSelectedShopDeptId).isNull();
    }

    @Test
    @DisplayName("考勤预检透传工资店铺与当前店铺上下文")
    void shouldPreflightAttendanceWithSelectedShopContext()
    {
        FakeSalaryService service = new FakeSalaryService();
        OaSalaryController controller = controller(service);

        AjaxResult result = controller.attendancePreflight(
                202L, "2026-07", requestWithShopHeader(201L));

        assertThat(result.isSuccess()).isTrue();
        assertThat(service.preflightShopDeptId).isEqualTo(202L);
        assertThat(service.preflightSalaryMonth).isEqualTo("2026-07");
        assertThat(service.preflightSelectedShopDeptId).isEqualTo(201L);
    }

    private OaSalaryController controller(FakeSalaryService service)
    {
        OaSalaryController controller = new OaSalaryController();
        ReflectionTestUtils.setField(controller, "salaryService", service);
        return controller;
    }

    private MockHttpServletRequest requestWithShopHeader(Long shopDeptId)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", String.valueOf(shopDeptId));
        return request;
    }

    private OaSalaryConfig config(Long shopDeptId)
    {
        OaSalaryConfig config = new OaSalaryConfig();
        config.setShopDeptId(shopDeptId);
        config.setWorkStartTime("09:00");
        config.setWorkEndTime("18:00");
        config.setWorkHoursPerDay(new BigDecimal("8.00"));
        config.setLatePenaltyPerMin(BigDecimal.ONE);
        config.setEarlyPenaltyPerMin(BigDecimal.ONE);
        config.setAbsentPenaltyPerDay(new BigDecimal("200.00"));
        config.setOvertimePayPerHour(new BigDecimal("50.00"));
        return config;
    }

    private static class FakeSalaryService implements IOaSalaryService
    {
        private final OaSalaryConfig configResult = new OaSalaryConfig();
        private Long readShopDeptId;
        private Long readSelectedShopDeptId;
        private OaSalaryConfig savedConfig;
        private Long saveSelectedShopDeptId;
        private Long preflightShopDeptId;
        private String preflightSalaryMonth;
        private Long preflightSelectedShopDeptId;

        @Override
        public OaSalaryConfig getConfig(Long shopDeptId, Long selectedShopDeptId)
        {
            this.readShopDeptId = shopDeptId;
            this.readSelectedShopDeptId = selectedShopDeptId;
            return configResult;
        }

        @Override
        public OaSalaryConfig saveConfig(OaSalaryConfig config, Long selectedShopDeptId)
        {
            this.savedConfig = config;
            this.saveSelectedShopDeptId = selectedShopDeptId;
            return configResult;
        }

        @Override
        public List<OaSalaryRecord> selectMyRecords(OaSalaryRecord record, Long selectedShopDeptId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<OaSalaryRecord> selectAllRecords(OaSalaryRecord record, Long selectedShopDeptId)
        {
            return Collections.emptyList();
        }

        @Override
        public OaSalaryRecord getRecordById(Long salaryId, Long selectedShopDeptId)
        {
            return null;
        }

        @Override
        public OaSalaryAttendancePreflightVo preflightAttendance(
                Long shopDeptId, String salaryMonth, Long selectedShopDeptId)
        {
            this.preflightShopDeptId = shopDeptId;
            this.preflightSalaryMonth = salaryMonth;
            this.preflightSelectedShopDeptId = selectedShopDeptId;
            return new OaSalaryAttendancePreflightVo();
        }

        @Override
        public List<OaSalaryRecord> calculateSalary(Long shopDeptId, String salaryMonth, Long selectedShopDeptId)
        {
            return Collections.emptyList();
        }
    }
}
