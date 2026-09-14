package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferModels.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Actual controllers and default MVC converters; no custom mapper or global coercion. */
class AttendanceLeaveBalanceMvcSerializationTest
{
    private static final long MAX = Long.MAX_VALUE;
    private static final String EXACT = Long.toString(MAX);
    private static final String BASE = "/attendance-v2/leave/balance";
    private final JsonMapper reader = JsonMapper.builder().build();
    private AttendanceLeaveBalanceService balances;
    private AttendanceOvertimeTransferService overtime;
    private MockMvc mvc;

    @BeforeEach void setup()
    {
        balances = mock(AttendanceLeaveBalanceService.class);
        overtime = mock(AttendanceOvertimeTransferService.class);
        var controller = new AttendanceOvertimeTransferController(overtime);
        var depts = mock(OaDeptScopeMapper.class);
        when(depts.countActiveStoreDept(anyLong())).thenReturn(1);
        ReflectionTestUtils.setField(controller, "deptScopeMapper", depts);
        mvc = MockMvcBuilders.standaloneSetup(new AttendanceLeaveBalanceController(balances), controller).build();
    }

    @Test void defaultMvcActuallyUsesJackson3()
    {
        var adapter = mvc.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerAdapter.class);
        assertThat(adapter.getMessageConverters()).anyMatch(c -> c.getClass().getName().equals("org.springframework.http.converter.json.JacksonJsonHttpMessageConverter"));
    }

    @Test void myEmployeeAndRecalculateExposeExactBalancesAndHistoricalBuckets() throws Exception
    {
        Balance result = balance();
        when(balances.my(MAX)).thenReturn(result);
        when(balances.employee(MAX, MAX)).thenReturn(result);
        when(balances.recalculateMy(MAX)).thenReturn(result);
        when(balances.recalculateEmployee(MAX, MAX)).thenReturn(result);
        for (RequestBuilder request : List.of(
                get(BASE + "/my").param("leaveTypeId", EXACT).accept(MediaType.APPLICATION_JSON),
                get(BASE + "/employees/" + EXACT).param("leaveTypeId", EXACT).accept(MediaType.APPLICATION_JSON),
                post(BASE + "/my/recalculate").contentType(MediaType.APPLICATION_JSON).content("{\"leaveTypeId\":\"" + EXACT + "\"}").accept(MediaType.APPLICATION_JSON),
                post(BASE + "/employees/" + EXACT + "/recalculate").contentType(MediaType.APPLICATION_JSON).content("{\"leaveTypeId\":\"" + EXACT + "\"}").accept(MediaType.APPLICATION_JSON)))
        {
            JsonNode data = data(request);
            annotated(data, Balance.class, EXACT);
            annotated(data.get("buckets").get(0), Bucket.class, EXACT);
            assertThat(data.get("buckets").get(0).get("minutesPerDay").decimalValue()).isEqualByComparingTo("420.5");
        }
    }

    @Test void ledgerIdsAndUnitsRemainExact() throws Exception
    {
        when(balances.ledger(MAX, MAX, null)).thenReturn(List.of(max(Ledger.class)));
        annotated(data(get(BASE + "/employees/" + EXACT + "/ledger").param("leaveTypeId", EXACT).accept(MediaType.APPLICATION_JSON)).get(0), Ledger.class, EXACT);
    }

    @Test void ruleListDetailCreateUpdatePublishKeepFamilyAndMappingIdentity() throws Exception
    {
        Rule result = max(Rule.class); result.config = policy(); result.tiers = List.of(max(Tier.class));
        when(balances.rules()).thenReturn(List.of(result)); when(balances.rule(MAX)).thenReturn(result);
        when(balances.saveRule(isNull(), any())).thenReturn(result); when(balances.saveRule(eq(MAX), any())).thenReturn(result);
        when(balances.publishRule(MAX, MAX)).thenReturn(result);
        annotated(data(get(BASE + "/rules").accept(MediaType.APPLICATION_JSON)).get(0), Rule.class, EXACT);
        for (RequestBuilder request : List.of(
                get(BASE + "/rules/" + EXACT).accept(MediaType.APPLICATION_JSON),
                post(BASE + "/rules").contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON),
                put(BASE + "/rules/" + EXACT + "/draft").contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON),
                post(BASE + "/rules/" + EXACT + "/publish").contentType(MediaType.APPLICATION_JSON).content("{\"rowVersion\":\"" + EXACT + "\"}").accept(MediaType.APPLICATION_JSON)))
        {
            JsonNode json = data(request); annotated(json, Rule.class, EXACT); annotated(json.get("tiers").get(0), Tier.class, EXACT);
            assertThat(json.get("config").get("amount").decimalValue()).isEqualByComparingTo("7.125");
            assertThat(json.get("config").get("minutesPerDay").decimalValue()).isEqualByComparingTo("420.5");
        }
    }

    @Test void locationListCreateUpdateExposeExactIdsAndRowVersion() throws Exception
    {
        LocationMapping result = max(LocationMapping.class);
        when(balances.locations()).thenReturn(List.of(result));
        when(balances.saveLocation(isNull(), any())).thenReturn(result); when(balances.saveLocation(eq(MAX), any())).thenReturn(result);
        annotated(data(get(BASE + "/locations").accept(MediaType.APPLICATION_JSON)).get(0), LocationMapping.class, EXACT);
        annotated(data(post(BASE + "/locations").contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON)), LocationMapping.class, EXACT);
        annotated(data(put(BASE + "/locations/" + EXACT).contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON)), LocationMapping.class, EXACT);
    }

    @Test void adjustmentCommandIdsAndResultUnitsRemainExact() throws Exception
    {
        when(balances.adjust(eq(MAX), any())).thenReturn(max(Command.class));
        annotated(data(post(BASE + "/employees/" + EXACT + "/adjustments").contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON)), Command.class, EXACT);
    }

    @Test void overtimeContextIncludesExactSourceHistoryAndBalance() throws Exception
    {
        Context context = new Context(); context.source = max(Source.class); context.history = List.of(max(Transfer.class)); context.balance = balance();
        when(overtime.context(MAX, MAX, MAX)).thenReturn(context);
        JsonNode json = data(get(BASE + "/overtime-transfers/context/" + EXACT).param("leaveTypeId", EXACT).header(com.erp.common.core.utils.ShopHeaderUtils.SHOP_HEADER, EXACT).accept(MediaType.APPLICATION_JSON));
        annotated(json.get("source"), Source.class, EXACT); annotated(json.get("history").get(0), Transfer.class, EXACT);
        annotated(json.get("balance"), Balance.class, EXACT); annotated(json.get("balance").get("buckets").get(0), Bucket.class, EXACT);
    }

    @Test void overtimeApplyAndReverseKeepAllTransferIdsAndVersions() throws Exception
    {
        when(overtime.apply(any(), eq(MAX))).thenReturn(max(Transfer.class));
        when(overtime.reverse(eq(MAX), any(), eq(MAX))).thenReturn(max(Transfer.class));
        annotated(data(post(BASE + "/overtime-transfers").header(com.erp.common.core.utils.ShopHeaderUtils.SHOP_HEADER, EXACT).contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON)), Transfer.class, EXACT);
        annotated(data(post(BASE + "/overtime-transfers/" + EXACT + "/reverse").header(com.erp.common.core.utils.ShopHeaderUtils.SHOP_HEADER, EXACT).contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON)), Transfer.class, EXACT);
    }

    @Test void zeroNullIntegerAndDecimalPoliciesRetainTheirMeaning() throws Exception
    {
        Rule result = max(Rule.class); result.rowVersion = 0L; result.publishedBy = null; result.version = 0; result.priority = 0; result.config = policy(); result.config.carryLimit = BigDecimal.ZERO;
        when(balances.rule(MAX)).thenReturn(result);
        JsonNode rule = data(get(BASE + "/rules/" + EXACT).accept(MediaType.APPLICATION_JSON));
        assertThat(rule.get("rowVersion").isString()).isTrue(); assertThat(rule.get("rowVersion").asString()).isEqualTo("0");
        assertThat(rule.get("publishedBy").isNull()).isTrue(); assertThat(rule.get("version").isNumber()).isTrue(); assertThat(rule.get("version").intValue()).isZero();
        assertThat(rule.get("priority").isNumber()).isTrue(); assertThat(rule.get("config").get("carryLimit").decimalValue()).isEqualByComparingTo("0");
        assertThat(rule.get("config").get("minutesPerDay").decimalValue()).isEqualByComparingTo("420.5");
        Command command = new Command(); command.resultUnits = 0; when(balances.adjust(eq(MAX), any())).thenReturn(command);
        JsonNode adjusted = data(post(BASE + "/employees/" + EXACT + "/adjustments").contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON));
        assertThat(adjusted.get("commandId").isNull()).isTrue(); assertThat(adjusted.get("resultUnits").isString()).isTrue(); assertThat(adjusted.get("resultUnits").asString()).isEqualTo("0");
    }

    @Test void allExistingExactLongFieldsWorkWithBothJacksonGenerations() throws Exception
    {
        var legacy = new com.fasterxml.jackson.databind.ObjectMapper();
        for (Class<?> outer : List.of(AttendanceLeaveBalanceModels.class, AttendanceOvertimeTransferModels.class))
            for (Class<?> type : outer.getDeclaredClasses())
            {
                Object value = max(type);
                annotated(reader.valueToTree(value), type, EXACT);
                com.fasterxml.jackson.databind.JsonNode old = legacy.valueToTree(value);
                for (Field field : type.getFields()) if (exact(field))
                {
                    assertThat(old.get(field.getName()).isTextual()).as(type.getSimpleName() + "." + field.getName()).isTrue();
                    assertThat(old.get(field.getName()).textValue()).isEqualTo(EXACT);
                }
            }
    }

    private Balance balance() throws Exception
    { Balance value = max(Balance.class); Bucket bucket = max(Bucket.class); bucket.minutesPerDay = new BigDecimal("420.5"); value.buckets = List.of(bucket); return value; }
    private RuleConfig policy()
    { RuleConfig p = new RuleConfig(); p.minutesPerDay = new BigDecimal("420.5"); p.amount = new BigDecimal("7.125"); return p; }
    private <T> T max(Class<T> type) throws Exception
    { T value = type.getDeclaredConstructor().newInstance(); for (Field field : type.getFields()) if (exact(field)) field.set(value, MAX); return value; }
    private static boolean exact(Field field)
    { return field.isAnnotationPresent(com.fasterxml.jackson.databind.annotation.JsonSerialize.class); }
    private void annotated(JsonNode value, Class<?> type, String expected)
    {
        assertThat(value).isNotNull();
        for (Field field : type.getFields()) if (exact(field))
        {
            assertThat(value.get(field.getName()).isString()).as(type.getSimpleName() + "." + field.getName()).isTrue();
            assertThat(value.get(field.getName()).asString()).isEqualTo(expected);
        }
    }
    private JsonNode data(RequestBuilder request) throws Exception
    { return reader.readTree(mvc.perform(request).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString()).get("data"); }
}
