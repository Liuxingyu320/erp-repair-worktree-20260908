package com.erp.approval.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleCondition;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("审批条件与业务变量契约")
class ApprovalRuleMatchServiceFieldContractTest
{
    private final ApprovalRuleMatchService service =
            new ApprovalRuleMatchService(mock(ApprovalDefinitionMapper.class),
                    mock(ApprovalJsonSupport.class));

    @Test
    @DisplayName("库存与调拨实际发送的可路由业务量均可配置")
    void shouldAllowProducedRoutingVariables()
    {
        List<String> fields = List.of("amount", "transferType",
                "sourceDeptId", "targetDeptId", "shopDeptId",
                "warehouseId", "detailCount", "profitItemCount",
                "lossItemCount", "totalDiffQuantity", "totalQuantity");

        for (String field : fields)
        {
            assertThatCode(() -> service.validateCondition(condition(field)))
                    .as(field).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("真实启动请求的申请部门可匹配标准规则字段")
    void shouldMatchApplicantDepartmentFromStartRequest()
    {
        ApprovalDefinitionMapper mapper = mock(ApprovalDefinitionMapper.class);
        ApprovalRuleMatchService matcher = new ApprovalRuleMatchService(mapper,
                mock(ApprovalJsonSupport.class));
        ApprovalTemplate template = new ApprovalTemplate();
        template.setTemplateId(11L);
        ApprovalRule rule = new ApprovalRule();
        rule.setRuleId(21L);
        rule.setRuleCode("ALL-DEPT-42");
        rule.setScopeType(ApprovalDefinitionConstants.SCOPE_ALL);
        rule.setBusinessSubtype("ALL");
        rule.setCurrentVersionId(31L);
        ApprovalRuleVersion version = new ApprovalRuleVersion();
        version.setVersionId(31L);
        version.setVersionStatus(
                ApprovalDefinitionConstants.VERSION_PUBLISHED);
        ApprovalRuleCondition canonical = condition("applicantDeptId");
        canonical.setValueType("NUMBER");
        canonical.setValueText("42");
        ApprovalRuleCondition compatibleAlias = condition("departmentId");
        compatibleAlias.setValueType("NUMBER");
        compatibleAlias.setValueText("42");
        when(mapper.selectActiveRulesForMatch(11L, 7L, "ALL"))
                .thenReturn(List.of(rule));
        when(mapper.selectRuleVersionById(31L)).thenReturn(version);
        when(mapper.selectConditionsByVersionId(31L))
                .thenReturn(List.of(canonical, compatibleAlias));
        when(mapper.selectNodesByVersionId(31L)).thenReturn(List.of());
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setApplicantDeptId(42L);
        request.setVariables(Map.of("applicantDeptId", 999L,
                "departmentId", 999L));

        MatchedApprovalRule matched = matcher.match(template, 7L, request);

        assertThat(matched.rule()).isSameAs(rule);
    }

    @Test
    @DisplayName("业务标识与展示文本不得用于审批路由")
    void shouldKeepIdentifiersAndFreeTextOutsideTheWhitelist()
    {
        for (String field : List.of("purchaseId", "transferId",
                "certificateNo", "title", "reason"))
        {
            assertThatThrownBy(() -> service.validateCondition(condition(field)))
                    .as(field).isInstanceOf(ServiceException.class)
                    .hasMessageContaining("字段不在白名单");
        }
    }

    private static ApprovalRuleCondition condition(String field)
    {
        ApprovalRuleCondition condition = new ApprovalRuleCondition();
        condition.setFieldCode(field);
        condition.setOperatorCode("EQ");
        condition.setValueType("STRING");
        condition.setValueText("1");
        return condition;
    }
}
