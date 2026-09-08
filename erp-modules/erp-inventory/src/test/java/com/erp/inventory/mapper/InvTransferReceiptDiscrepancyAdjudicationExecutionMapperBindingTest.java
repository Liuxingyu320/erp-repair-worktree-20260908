package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Introspector;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2调拨差异裁决动作惰性持久化契约")
class
        InvTransferReceiptDiscrepancyAdjudicationExecutionMapperBindingTest
{
    private static final String SIMPLE_NAME =
            "InvTransferReceiptDiscrepancyAdjudicationExecutionMapper";
    private static final String NAMESPACE =
            "com.erp.inventory.mapper." + SIMPLE_NAME;
    private static final String XML =
            "mapper/inventory/" + SIMPLE_NAME + ".xml";

    @Test
    @DisplayName("九个窄方法全部绑定到可解析XML")
    void shouldBindEveryNarrowMethod() throws Exception
    {
        Configuration configuration = configuration();

        assertThat(Arrays.stream(
                InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
                        .class.getDeclaredMethods())
                .map(method -> NAMESPACE + "." + method.getName()))
                .allMatch(configuration::hasStatement)
                .containsExactlyInAnyOrder(
                        NAMESPACE + ".selectCaseIdForUpdate",
                        NAMESPACE + ".countCaseInScope",
                        NAMESPACE + ".selectBoundaryForUpdate",
                        NAMESPACE + ".selectActionsForUpdate",
                        NAMESPACE + ".selectEventByRequestIdForUpdate",
                        NAMESPACE + ".insertExecutionEvent",
                        NAMESPACE + ".transitionAction",
                        NAMESPACE + ".transitionAdjudication",
                        NAMESPACE + ".transitionCase");
    }

    @Test
    @DisplayName("全部结果映射属性都必须存在于目标JavaBean")
    void shouldMapOnlyWritableBeanProperties() throws Exception
    {
        Configuration configuration = configuration();

        for (String id : Set.of("ExecutionBoundaryResult",
                "LockedActionResult", "StoredExecutionEventResult"))
        {
            var resultMap = configuration.getResultMap(NAMESPACE + "." + id);
            Set<String> writableProperties = Arrays.stream(Introspector
                    .getBeanInfo(resultMap.getType()).getPropertyDescriptors())
                    .filter(property -> property.getWriteMethod() != null)
                    .map(property -> property.getName())
                    .collect(Collectors.toSet());

            assertThat(resultMap.getResultMappings())
                    .extracting(mapping -> mapping.getProperty())
                    .allMatch(writableProperties::contains);
        }
    }

    @Test
    @DisplayName("锁序稳定且DML仅能追加事件并条件推进三层状态")
    void shouldFreezeLocksAndConditionalDml() throws Exception
    {
        String xml = normalized();
        String caseLock = between(xml,
                "<select id=\"selectcaseidforupdate\"", "</select>");
        String boundaryLock = between(xml,
                "<select id=\"selectboundaryforupdate\"", "</select>");
        String scopeCheck = between(xml,
                "<select id=\"countcaseinscope\"", "</select>");
        String actionLock = between(xml,
                "<select id=\"selectactionsforupdate\"", "</select>");
        String requestLock = between(xml,
                "<select id=\"selecteventbyrequestidforupdate\"",
                "</select>");
        String eventInsert = between(xml,
                "<insert id=\"insertexecutionevent\"", "</insert>");
        String actionUpdate = between(xml,
                "<update id=\"transitionaction\"", "</update>");
        String planUpdate = between(xml,
                "<update id=\"transitionadjudication\"", "</update>");
        String caseUpdate = between(xml,
                "<update id=\"transitioncase\"", "</update>");

        assertThat(caseLock).contains(
                "from inv_transfer_receipt_discrepancy_case",
                "where discrepancy_case_id = #{caseid}", "for update");
        assertThat(scopeCheck).contains(
                "select count(1)", "inner join inv_transfer_order o",
                "o.transfer_id = c.transfer_id",
                "where c.discrepancy_case_id = #{caseid}",
                "collection=\"scopedeptids\"")
                .doesNotContain("for update");
        assertThat(boundaryLock).contains(
                "from inv_transfer_receipt_discrepancy_case c",
                "inner join inv_transfer_receipt_discrepancy_adjudication a",
                "a.adjudication_id = #{adjudicationid}",
                "a.fact_fingerprint = c.fact_fingerprint",
                "a.source_cost_price",
                "for update");
        assertThat(actionLock).contains(
                "coverage_kind", "execution_version",
                "execution_effect_reference",
                "where discrepancy_case_id = #{caseid}",
                "and adjudication_id = #{adjudicationid}",
                "order by action_sequence, adjudication_action_id",
                "for update");
        String lockedActionMap = between(xml,
                "<resultmap id=\"lockedactionresult\"", "</resultmap>");
        String storedEventMap = between(xml,
                "<resultmap id=\"storedexecutioneventresult\"",
                "</resultmap>");
        assertThat(lockedActionMap).doesNotContain("sourcecostprice");
        assertThat(storedEventMap).contains(
                "property=\"coveragekind\" column=\"coverage_kind\"",
                "property=\"sourcecostprice\" column=\"source_cost_price\"");
        assertThat(requestLock).contains(
                "from inv_transfer_receipt_discrepancy_adjudication_execution_event",
                "where request_id = #{requestid}", "for update");

        assertThat(eventInsert).contains(
                "insert into inv_transfer_receipt_discrepancy_adjudication_execution_event",
                "c.case_version = #{execution.caseversionbefore}",
                "a.plan_status = #{execution.planstatusbefore}",
                "x.execution_status = #{execution.actionstatusbefore}",
                "x.execution_version = #{execution.executionversionbefore}",
                "x.coverage_kind = #{execution.coveragekind}",
                "a.source_cost_price = #{execution.sourcecostprice}",
                "#{execution.caseversionbefore} + 1",
                "#{execution.executionversionbefore} + 1",
                "#{execution.requiredpermission} = 'inv:transfer:discrepancy:execute'",
                "x.execution_effect_reference is null",
                "x.execution_effect_reference = #{execution.effectreference}",
                "prior.request_id = #{execution.requestid}",
                "prior.action_version_before = #{execution.executionversionbefore}");
        assertThat(actionUpdate).contains(
                "set execution_status = #{execution.actionstatusafter}",
                "execution_version = #{execution.executionversionafter}",
                "execution_effect_reference = #{execution.effectreference}",
                "event.event_fingerprint = #{execution.eventfingerprint}");
        assertThat(planUpdate).contains(
                "set plan_status = #{execution.planstatusafter}",
                "plan_status = #{execution.planstatusbefore}",
                "event.event_fingerprint = #{execution.eventfingerprint}");
        assertThat(caseUpdate).contains(
                "set status = #{execution.casestatusafter}",
                "case_version = #{execution.caseversionafter}",
                "case_version = #{execution.caseversionbefore}",
                "event.event_fingerprint = #{execution.eventfingerprint}");

        assertThat(count(xml, "<select ")).isEqualTo(5);
        assertThat(count(xml, "<insert ")).isEqualTo(1);
        assertThat(count(xml, "<update ")).isEqualTo(3);
        assertThat(eventInsert + actionUpdate + planUpdate + caseUpdate)
                .doesNotContain(
                "<delete ", " inv_stock ",
                " inv_stock_balance_detail ", " inv_inventory_lot ",
                " inv_inventory_location ", " inv_inventory_serial ",
                " inv_transfer_order ", " inv_transfer_reservation ",
                " inv_stock_ledger_detail ", " finance");
    }

    @Test
    @DisplayName("持久化契约只服务同步事务和未接线异步完成事务")
    void shouldOnlyServeExecutionTransactions() throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            assertThat(paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, SIMPLE_NAME))
                    .map(path -> root.relativize(path))
                    .toList())
                    .containsExactlyInAnyOrder(
                            Path.of("mapper", SIMPLE_NAME + ".java"),
                            Path.of("service", "impl",
                                    "InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction.java"),
                            Path.of("service", "impl",
                                    "InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction.java"));
        }
    }

    private static Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.addMapper(
                InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
                        .class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String normalized() throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase().replaceAll("\\s+", " ");
        }
    }

    private static String between(String source, String start, String end)
    {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to + end.length());
    }

    private static int count(String value, String token)
    {
        return value.split(java.util.regex.Pattern.quote(token), -1).length
                - 1;
    }

    private static boolean contains(Path path, String value)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains(value);
        }
        catch (java.io.IOException error)
        {
            throw new IllegalStateException(error);
        }
    }
}
