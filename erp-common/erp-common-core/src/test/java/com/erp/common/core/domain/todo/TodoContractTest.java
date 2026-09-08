package com.erp.common.core.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TodoContractTest
{
    @Test
    void queryDefaultsScopeToActionableAndNormalizesPagination()
    {
        TodoQuery query = new TodoQuery();

        assertThat(query.getScopeMode()).isEqualTo(TodoConstants.SCOPE_ACTIONABLE);
        assertThat(query.getPriority()).isNull();
        assertThat(query.getPageNum()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(10);

        query.setPageNum(0);
        query.setPageSize(0);
        assertThat(query.getPageNum()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(1);

        query.setPageNum(null);
        query.setPageSize(101);
        assertThat(query.getPageNum()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(100);

        query.setPageSize(null);
        assertThat(query.getPageSize()).isEqualTo(10);
    }

    @Test
    void queryTrimsStringsAndConvertsBlanksToNull()
    {
        TodoQuery query = new TodoQuery();

        query.setCategory("  approval  ");
        query.setType("  INV_TRANSFER_APPROVAL  ");
        query.setSource("   ");
        query.setScopeMode(" current_org ");
        query.setKeyword("\t purchase order \n");
        query.setPriority(" important ");

        assertThat(query.getCategory()).isEqualTo("approval");
        assertThat(query.getType()).isEqualTo("INV_TRANSFER_APPROVAL");
        assertThat(query.getSource()).isNull();
        assertThat(query.getScopeMode()).isEqualTo("current_org");
        assertThat(query.getKeyword()).isEqualTo("purchase order");
        assertThat(query.getPriority()).isEqualTo("important");

        query.setCategory("future_category");
        query.setScopeMode("future_scope");
        assertThat(query.getCategory()).isEqualTo("future_category");
        assertThat(query.getScopeMode()).isEqualTo("future_scope");

        query.setType("   ");
        assertThat(query.getType()).isNull();
    }

    @Test
    void queryRejectsMalformedTodoTypes()
    {
        TodoQuery query = new TodoQuery();

        assertThatIllegalArgumentException().isThrownBy(() -> query.setType("inv_transfer_approval"));
        assertThatIllegalArgumentException().isThrownBy(() -> query.setType("1_INVALID"));
        assertThatIllegalArgumentException().isThrownBy(() -> query.setType("INV-TRANSFER"));
        assertThatIllegalArgumentException().isThrownBy(() -> query.setType("A".repeat(65)));
    }

    @Test
    void summaryRecalculatesTotalFromAllCategories()
    {
        TodoSummary summary = new TodoSummary();
        summary.setTotal(999L);
        summary.setApproval(1L);
        summary.setExecution(2L);
        summary.setReturned(3L);
        summary.setRisk(4L);
        summary.setPersonal(5L);

        summary.recalculateTotal();

        assertThat(summary.getTotal()).isEqualTo(15L);
    }

    @Test
    void collectionPropertiesAreNeverNullAfterNullSetters()
    {
        TodoItem item = new TodoItem();
        TodoSummary summary = new TodoSummary();

        assertThat(item.getRouteParams()).isInstanceOf(LinkedHashMap.class).isEmpty();
        assertThat(summary.getTypeCounts()).isInstanceOf(LinkedHashMap.class).isEmpty();
        assertThat(summary.getRecent()).isEmpty();

        item.setRouteParams(null);
        summary.setTypeCounts(null);
        summary.setRecent(null);

        assertThat(item.getRouteParams()).isInstanceOf(LinkedHashMap.class).isEmpty();
        assertThat(summary.getTypeCounts()).isInstanceOf(LinkedHashMap.class).isEmpty();
        assertThat(summary.getRecent()).isEmpty();
    }

    @Test
    void collectionSettersDefensivelyCopyMutableInputs()
    {
        TodoItem item = new TodoItem();
        TodoItem recentItem = new TodoItem();
        TodoSummary summary = new TodoSummary();
        Map<String, String> routeParams = new LinkedHashMap<>(Map.of("businessId", "42"));
        Map<String, Long> typeCounts = new LinkedHashMap<>(Map.of("shipment", 1L));
        List<TodoItem> recent = new ArrayList<>(List.of(recentItem));

        item.setRouteParams(routeParams);
        summary.setTypeCounts(typeCounts);
        summary.setRecent(recent);

        routeParams.clear();
        typeCounts.clear();
        recent.clear();

        assertThat(item.getRouteParams()).isEqualTo(Map.of("businessId", "42"));
        assertThat(summary.getTypeCounts()).isEqualTo(Map.of("shipment", 1L));
        assertThat(summary.getRecent()).containsExactly(recentItem);
    }

    @Test
    void collectionSettersCopyImmutableInputsIntoMutableCollections()
    {
        TodoItem item = new TodoItem();
        TodoSummary summary = new TodoSummary();
        item.setRouteParams(Map.of("businessId", "42"));
        summary.setTypeCounts(Map.of("shipment", 1L));
        summary.setRecent(List.of(item));

        assertThatCode(() -> {
            item.getRouteParams().put("action", "detail");
            summary.getTypeCounts().put("contract", 2L);
            summary.getRecent().add(new TodoItem());
        }).doesNotThrowAnyException();

        assertThat(item.getRouteParams()).containsEntry("action", "detail");
        assertThat(summary.getTypeCounts()).containsEntry("contract", 2L);
        assertThat(summary.getRecent()).hasSize(2);
    }

    @Test
    void transferContractBeansAreSerializable()
    {
        assertThat(new TodoItem()).isInstanceOf(Serializable.class);
        assertThat(new TodoSummary()).isInstanceOf(Serializable.class);
        assertThat(new TodoQuery()).isInstanceOf(Serializable.class);
    }

    @Test
    void constantsExposeExpectedValuesAndImmutableCategoryOrder()
    {
        assertThat(List.of(TodoConstants.SOURCE_INVENTORY, TodoConstants.SOURCE_OA, TodoConstants.SOURCE_SYSTEM))
                .containsExactly("inventory", "oa", "system");
        assertThat(TodoConstants.CATEGORIES).containsExactly("approval", "execution", "returned", "risk", "personal");
        assertThat(List.of(TodoConstants.PRIORITY_URGENT, TodoConstants.PRIORITY_IMPORTANT,
                TodoConstants.PRIORITY_NORMAL)).containsExactly("urgent", "important", "normal");
        assertThat(TodoConstants.PRIORITIES).containsExactly("urgent", "important", "normal");
        assertThat(List.of(TodoConstants.SCOPE_ACTIONABLE, TodoConstants.SCOPE_CURRENT_ORG,
                TodoConstants.SCOPE_ALL_AUTHORIZED))
                .containsExactly("actionable", "current_org", "all_authorized");
        assertThatThrownBy(() -> TodoConstants.CATEGORIES.add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> TodoConstants.PRIORITIES.add("high"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void keysBuildTrimmedNormalShipmentAndOrganizationAggregateKeys()
    {
        String normalKey = TodoKeys.build(" oa ", " leave ", 41L, " approve ");
        String shipmentKey = TodoKeys.build("inventory", "shipment", 42L, "receive");
        String firstOrganizationKey = TodoKeys.build("system", "organization", 43L, "aggregate");
        String secondOrganizationKey = TodoKeys.build("system", "organization", 44L, "aggregate");

        assertThat(normalKey).isEqualTo("oa:leave:41:approve");
        assertThat(shipmentKey).isEqualTo("inventory:shipment:42:receive");
        assertThat(Set.of(normalKey, shipmentKey, firstOrganizationKey, secondOrganizationKey)).hasSize(4);
    }

    @Test
    void keysRejectInvalidSegmentsAndBusinessIds()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> TodoKeys.build(null, "leave", 1L, "approve"));
        assertThatIllegalArgumentException().isThrownBy(() -> TodoKeys.build(" ", "leave", 1L, "approve"));
        assertThatIllegalArgumentException().isThrownBy(() -> TodoKeys.build("oa", "leave:request", 1L, "approve"));
        assertThatIllegalArgumentException().isThrownBy(() -> TodoKeys.build("oa", "leave", 1L, "approve:now"));
        assertThatIllegalArgumentException().isThrownBy(() -> TodoKeys.build("oa", "leave", null, "approve"));
        assertThatIllegalArgumentException().isThrownBy(() -> TodoKeys.build("oa", "leave", 0L, "approve"));
        assertThatIllegalArgumentException().isThrownBy(() -> TodoKeys.build("oa", "leave", -1L, "approve"));
    }

    @Test
    void beansExposeAllRequiredReadableAndWritableProperties() throws Exception
    {
        assertBeanProperties(TodoItem.class, "todoKey", "source", "type", "category", "businessId", "businessNo",
                "title", "summary", "status", "priority", "createdTime", "waitingSeconds", "deptId", "deptName",
                "deptType", "scopeMode", "routeType", "routeParams", "requiredPermission");
        assertBeanProperties(TodoSummary.class, "source", "total", "approval", "execution", "returned", "risk",
                "personal", "urgent", "important", "normal", "typeCounts", "recent");
        assertBeanProperties(TodoQuery.class, "type", "category", "source", "scopeMode", "keyword", "priority",
                "pageNum", "pageSize");
    }

    private static void assertBeanProperties(Class<?> beanType, String... expectedProperties) throws Exception
    {
        Set<String> expected = Set.copyOf(Arrays.asList(expectedProperties));
        List<PropertyDescriptor> descriptors = Arrays.stream(Introspector.getBeanInfo(beanType).getPropertyDescriptors())
                .filter(descriptor -> !"class".equals(descriptor.getName()))
                .collect(Collectors.toList());

        assertThat(descriptors).extracting(PropertyDescriptor::getName).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(descriptors).allSatisfy(descriptor -> {
            assertThat(descriptor.getReadMethod()).as(descriptor.getName() + " getter").isNotNull();
            assertThat(descriptor.getWriteMethod()).as(descriptor.getName() + " setter").isNotNull();
        });
    }
}
