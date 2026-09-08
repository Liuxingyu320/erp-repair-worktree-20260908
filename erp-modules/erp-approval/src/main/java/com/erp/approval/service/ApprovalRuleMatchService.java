package com.erp.approval.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleCondition;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;

/** Deterministic store > area > all, exact subtype > all matching. */
@Service
public class ApprovalRuleMatchService
{
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "amount", "category", "departmentId", "applicantDeptId",
            "anchorDeptId", "businessSubtype", "transferType",
            "sourceDeptId", "targetDeptId", "shopDeptId", "warehouseId",
            "detailCount", "profitItemCount", "lossItemCount",
            "totalDiffQuantity", "totalQuantity");
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "EQ", "IN", "GTE", "GT", "LTE", "LT");

    private final ApprovalDefinitionMapper definitionMapper;
    private final ApprovalJsonSupport jsonSupport;

    public ApprovalRuleMatchService(ApprovalDefinitionMapper definitionMapper,
            ApprovalJsonSupport jsonSupport)
    {
        this.definitionMapper = definitionMapper;
        this.jsonSupport = jsonSupport;
    }

    public MatchedApprovalRule match(ApprovalTemplate template,
            Long anchorDeptId, ApprovalStartRequest request)
    {
        String subtype = normalizeSubtype(request.getBusinessSubtype());
        Map<String, Object> values = standardValues(anchorDeptId, subtype,
                request.getApplicantDeptId(), request.getVariables());
        List<MatchedApprovalRule> matching = new ArrayList<>();
        for (ApprovalRule rule : definitionMapper.selectActiveRulesForMatch(
                template.getTemplateId(), anchorDeptId, subtype))
        {
            ApprovalRuleVersion version = definitionMapper
                    .selectRuleVersionById(rule.getCurrentVersionId());
            if (version == null || !ApprovalDefinitionConstants.VERSION_PUBLISHED
                    .equals(version.getVersionStatus()))
            {
                continue;
            }
            List<ApprovalRuleCondition> conditions = definitionMapper
                    .selectConditionsByVersionId(version.getVersionId());
            if (conditions.stream().allMatch(item -> matches(item, values)))
            {
                version.setConditions(conditions);
                version.setNodes(definitionMapper.selectNodesByVersionId(
                        version.getVersionId()));
                matching.add(new MatchedApprovalRule(rule, version,
                        precision(rule, subtype)));
            }
        }
        if (matching.isEmpty())
        {
            throw new ServiceException("未匹配到已发布的审批规则");
        }
        int max = matching.stream().mapToInt(MatchedApprovalRule::precision)
                .max().orElseThrow();
        List<MatchedApprovalRule> best = matching.stream()
                .filter(item -> item.precision() == max).toList();
        if (best.size() != 1)
        {
            String codes = String.join(", ", best.stream()
                    .map(item -> item.rule().getRuleCode()).toList());
            throw new ServiceException("审批规则同精度冲突: " + codes);
        }
        return best.get(0);
    }

    public List<ApprovalRule> samePrecisionConflicts(ApprovalRule candidate,
            List<ApprovalRuleCondition> candidateConditions)
    {
        return definitionMapper.selectActiveRulesByTemplateId(
                candidate.getTemplateId()).stream()
                .filter(item -> !Objects.equals(item.getRuleId(),
                        candidate.getRuleId()))
                .filter(item -> Objects.equals(item.getScopeType(),
                        candidate.getScopeType()))
                .filter(item -> Objects.equals(item.getScopeId(),
                        candidate.getScopeId()))
                .filter(item -> Objects.equals(normalizeSubtype(
                        item.getBusinessSubtype()), normalizeSubtype(
                                candidate.getBusinessSubtype())))
                .filter(item -> conditionsCanOverlap(candidateConditions,
                        definitionMapper.selectConditionsByVersionId(
                                item.getCurrentVersionId())))
                .toList();
    }

    /** Two conjunctions overlap unless at least one field is provably disjoint. */
    public boolean conditionsCanOverlap(List<ApprovalRuleCondition> left,
            List<ApprovalRuleCondition> right)
    {
        List<ApprovalRuleCondition> combined = new ArrayList<>();
        if (left != null) combined.addAll(left);
        if (right != null) combined.addAll(right);
        return conditionsSatisfiable(combined);
    }

    public boolean conditionsSatisfiable(
            List<ApprovalRuleCondition> conditions)
    {
        Map<String, List<ApprovalRuleCondition>> byField =
                new LinkedHashMap<>();
        if (conditions != null)
        {
            for (ApprovalRuleCondition condition : conditions)
            {
                if (condition != null && condition.getFieldCode() != null)
                {
                    byField.computeIfAbsent(condition.getFieldCode(),
                            ignored -> new ArrayList<>()).add(condition);
                }
            }
        }
        return byField.values().stream().allMatch(this::fieldSatisfiable);
    }

    public boolean matches(ApprovalRuleCondition condition,
            Map<String, Object> values)
    {
        validateCondition(condition);
        Object actual = values.get(condition.getFieldCode());
        if (actual == null)
        {
            return false;
        }
        String operator = condition.getOperatorCode().toUpperCase();
        if ("IN".equals(operator))
        {
            return inValues(condition.getValueText()).stream()
                    .anyMatch(item -> compare(actual, item,
                            condition.getValueType()) == 0);
        }
        int compared = compare(actual, condition.getValueText(),
                condition.getValueType());
        return switch (operator)
        {
            case "EQ" -> compared == 0;
            case "GTE" -> compared >= 0;
            case "GT" -> compared > 0;
            case "LTE" -> compared <= 0;
            case "LT" -> compared < 0;
            default -> false;
        };
    }

    public void validateCondition(ApprovalRuleCondition condition)
    {
        if (condition == null || !ALLOWED_FIELDS.contains(
                condition.getFieldCode()))
        {
            throw new ServiceException("审批条件字段不在白名单: "
                    + (condition == null ? null : condition.getFieldCode()));
        }
        String operator = condition.getOperatorCode() == null ? ""
                : condition.getOperatorCode().toUpperCase();
        if (!ALLOWED_OPERATORS.contains(operator))
        {
            throw new ServiceException("审批条件操作符不受支持: "
                    + condition.getOperatorCode());
        }
        if (condition.getValueText() == null)
        {
            throw new ServiceException("审批条件值不能为空");
        }
        String valueType = condition.getValueType() == null ? "STRING"
                : condition.getValueType().toUpperCase();
        if (!Set.of("STRING", "NUMBER", "BOOLEAN").contains(valueType))
        {
            throw new ServiceException("审批条件值类型不受支持: "
                    + condition.getValueType());
        }
        if ("BOOLEAN".equals(valueType)
                && !Set.of("EQ", "IN").contains(operator))
        {
            throw new ServiceException("布尔条件只支持EQ或IN");
        }
        List<String> configuredValues = "IN".equals(operator)
                ? inValues(condition.getValueText())
                : List.of(condition.getValueText());
        if (configuredValues.isEmpty())
        {
            throw new ServiceException("审批条件值不能为空集合");
        }
        for (String configuredValue : configuredValues)
        {
            if ("NUMBER".equals(valueType))
            {
                try
                {
                    new BigDecimal(configuredValue);
                }
                catch (NumberFormatException exception)
                {
                    throw new ServiceException("审批数值条件格式无效: "
                            + configuredValue);
                }
            }
            else if ("BOOLEAN".equals(valueType))
            {
                parseBoolean(configuredValue);
            }
        }
    }

    private int precision(ApprovalRule rule, String subtype)
    {
        int scope = switch (rule.getScopeType())
        {
            case ApprovalDefinitionConstants.SCOPE_STORE -> 3;
            case ApprovalDefinitionConstants.SCOPE_AREA -> 2;
            default -> 1;
        };
        int child = !"ALL".equals(normalizeSubtype(rule.getBusinessSubtype()))
                && Objects.equals(normalizeSubtype(rule.getBusinessSubtype()),
                        subtype) ? 2 : 1;
        return scope * 10 + child;
    }

    private Map<String, Object> standardValues(Long anchorDeptId,
            String subtype, Long applicantDeptId,
            Map<String, Object> variables)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (variables != null)
        {
            result.putAll(variables);
        }
        // Standard request fields are authoritative: callers cannot spoof a
        // routing department by putting a different value in variables.
        result.put("applicantDeptId", applicantDeptId);
        result.put("departmentId", applicantDeptId);
        result.put("anchorDeptId", anchorDeptId);
        result.put("businessSubtype", subtype);
        return result;
    }

    private List<String> inValues(String text)
    {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("[") && value.endsWith("]"))
        {
            Object raw = jsonSupport.readMap("{\"values\":" + value + "}")
                    .get("values");
            if (raw instanceof Collection<?> collection)
            {
                return collection.stream().map(String::valueOf).toList();
            }
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private int compare(Object actual, String expected, String valueType)
    {
        if (actual == null)
        {
            return -1;
        }
        String type = valueType == null ? "STRING" : valueType.toUpperCase();
        try
        {
            return switch (type)
            {
                case "NUMBER" -> new BigDecimal(actual.toString())
                        .compareTo(new BigDecimal(expected));
                case "BOOLEAN" -> parseBoolean(actual.toString())
                        .compareTo(parseBoolean(expected));
                default -> actual.toString().compareTo(expected);
            };
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("审批条件值类型无效")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private boolean fieldSatisfiable(List<ApprovalRuleCondition> conditions)
    {
        try
        {
            String valueType = null;
            List<Object> finite = null;
            Bound lower = null;
            Bound upper = null;
            for (ApprovalRuleCondition condition : conditions)
            {
                validateCondition(condition);
                String currentType = condition.getValueType() == null
                        ? "STRING" : condition.getValueType().toUpperCase();
                if (valueType != null && !valueType.equals(currentType))
                {
                    return true;
                }
                valueType = currentType;
                String operator = condition.getOperatorCode().toUpperCase();
                if ("EQ".equals(operator) || "IN".equals(operator))
                {
                    List<String> texts = "IN".equals(operator)
                            ? inValues(condition.getValueText())
                            : List.of(condition.getValueText());
                    List<Object> values = texts.stream()
                            .map(item -> typedValue(item, currentType)).toList();
                    finite = finite == null ? distinctValues(values)
                            : intersection(finite, values);
                    if (finite.isEmpty()) return false;
                }
                else
                {
                    Object value = typedValue(condition.getValueText(),
                            currentType);
                    if ("GT".equals(operator) || "GTE".equals(operator))
                    {
                        lower = strongerLower(lower, new Bound(value,
                                "GTE".equals(operator)));
                    }
                    else if ("LT".equals(operator)
                            || "LTE".equals(operator))
                    {
                        upper = strongerUpper(upper, new Bound(value,
                                "LTE".equals(operator)));
                    }
                }
            }
            if (finite != null)
            {
                Bound finalLower = lower;
                Bound finalUpper = upper;
                return finite.stream().anyMatch(value -> within(value,
                        finalLower, finalUpper));
            }
            if (lower == null || upper == null) return true;
            int compared = compareValues(lower.value(), upper.value());
            return compared < 0 || (compared == 0
                    && lower.inclusive() && upper.inclusive());
        }
        catch (RuntimeException ignored)
        {
            // Existing invalid published definitions must conflict fail-closed.
            return true;
        }
    }

    private Object typedValue(String value, String valueType)
    {
        return switch (valueType)
        {
            case "NUMBER" -> new BigDecimal(value);
            case "BOOLEAN" -> parseBoolean(value);
            default -> value;
        };
    }

    private static List<Object> intersection(List<Object> left,
            List<Object> right)
    {
        return left.stream().filter(item -> containsValue(right, item))
                .toList();
    }

    private static List<Object> distinctValues(List<Object> source)
    {
        List<Object> result = new ArrayList<>();
        for (Object value : source)
        {
            if (!containsValue(result, value)) result.add(value);
        }
        return result;
    }

    private static boolean containsValue(List<Object> source, Object expected)
    {
        return source.stream().anyMatch(item -> compareValues(item,
                expected) == 0);
    }

    private static Bound strongerLower(Bound current, Bound candidate)
    {
        if (current == null) return candidate;
        int compared = compareValues(candidate.value(), current.value());
        if (compared > 0) return candidate;
        if (compared < 0) return current;
        return new Bound(current.value(), current.inclusive()
                && candidate.inclusive());
    }

    private static Bound strongerUpper(Bound current, Bound candidate)
    {
        if (current == null) return candidate;
        int compared = compareValues(candidate.value(), current.value());
        if (compared < 0) return candidate;
        if (compared > 0) return current;
        return new Bound(current.value(), current.inclusive()
                && candidate.inclusive());
    }

    private static boolean within(Object value, Bound lower, Bound upper)
    {
        if (lower != null)
        {
            int compared = compareValues(value, lower.value());
            if (compared < 0 || (compared == 0 && !lower.inclusive()))
                return false;
        }
        if (upper != null)
        {
            int compared = compareValues(value, upper.value());
            if (compared > 0 || (compared == 0 && !upper.inclusive()))
                return false;
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Object left, Object right)
    {
        if (left == null || right == null
                || !left.getClass().equals(right.getClass()))
        {
            throw new IllegalArgumentException("incomparable approval values");
        }
        return ((Comparable) left).compareTo(right);
    }

    private record Bound(Object value, boolean inclusive) { }

    private static Boolean parseBoolean(String value)
    {
        if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        throw new ServiceException("审批布尔条件格式无效: " + value);
    }

    public static String normalizeSubtype(String value)
    {
        return value == null || value.isBlank() ? "ALL" : value.trim();
    }
}
