package com.erp.approval.candidate;

import static com.erp.approval.constant.ApprovalDefinitionConstants.GENERAL_MANAGER_POST;
import static com.erp.approval.constant.ApprovalDefinitionConstants.OPERATIONS_DIRECTOR_POST;
import static com.erp.approval.constant.ApprovalDefinitionConstants.STORE_ASSISTANT_POST;
import static com.erp.approval.constant.ApprovalDefinitionConstants.STORE_MANAGER_POST;
import static com.erp.approval.constant.ApprovalDefinitionConstants.TRANSFER_GENERAL_MANAGER;
import static com.erp.approval.constant.ApprovalDefinitionConstants.TRANSFER_LEVEL3;
import static com.erp.approval.constant.ApprovalDefinitionConstants.TRANSFER_LEVEL4;
import static com.erp.approval.constant.ApprovalDefinitionConstants.TRANSFER_OPERATIONS_DIRECTOR;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.service.ApprovalPermissionPolicy;
import com.erp.common.core.exception.ServiceException;

/** Exact port of the confirmed inventory four-level responsibility algorithm. */
@Component
public class TransferApprovalCandidateResolver
        extends AbstractDirectoryCandidateResolver
        implements ApprovalCandidateResolver
{
    private static final List<String> EXCLUDED_LEVEL3_POST_CODES = List.of(
            STORE_MANAGER_POST, STORE_ASSISTANT_POST,
            OPERATIONS_DIRECTOR_POST, GENERAL_MANAGER_POST);

    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalPermissionPolicy permissionPolicy;

    public TransferApprovalCandidateResolver(
            ApprovalCandidateDirectoryMapper directoryMapper,
            ApprovalPermissionPolicy permissionPolicy)
    {
        this.directoryMapper = directoryMapper;
        this.permissionPolicy = permissionPolicy;
    }

    @Override
    public boolean supports(ApprovalVersionNode node)
    {
        return ApprovalDefinitionConstants.STRATEGY_BUSINESS
                .equals(node.getStrategyType())
                && List.of(TRANSFER_LEVEL4, TRANSFER_LEVEL3,
                        TRANSFER_OPERATIONS_DIRECTOR,
                        TRANSFER_GENERAL_MANAGER)
                        .contains(node.getStrategyCode());
    }

    @Override
    public ApprovalCandidateResolution resolve(ApprovalCandidateContext context)
    {
        ApprovalDirectoryDept anchor = directoryMapper.selectActiveDeptById(
                context.anchorDeptId());
        if (anchor == null || !"STORE".equals(anchor.getDeptType()))
        {
            throw new ServiceException("调拨审批锚点门店无效");
        }
        String permission = permissionPolicy.requiredPermission(
                context.template().getBusinessCode());
        String code = context.node().getStrategyCode();
        if (TRANSFER_LEVEL4.equals(code))
        {
            List<ApprovalDirectoryUser> users = directoryMapper
                    .selectDirectStoreUsersByPostCode(context.anchorDeptId(),
                            STORE_MANAGER_POST, permission);
            String source = STORE_MANAGER_POST;
            if (users.isEmpty())
            {
                users = directoryMapper.selectDirectStoreUsersByPostCode(
                        context.anchorDeptId(), STORE_ASSISTANT_POST, permission);
                source = STORE_ASSISTANT_POST;
            }
            return ApprovalCandidateResolution.of(candidates(users,
                    context.node().getStrategyType(), source,
                    STORE_MANAGER_POST.equals(source)
                            ? "店长优先" : "无店长，取店长助理"));
        }
        if (TRANSFER_LEVEL3.equals(code))
        {
            Integer managerSort = directoryMapper.selectActivePostSortByCode(
                    STORE_MANAGER_POST);
            Integer operationsSort = directoryMapper.selectActivePostSortByCode(
                    OPERATIONS_DIRECTOR_POST);
            Integer generalSort = directoryMapper.selectActivePostSortByCode(
                    GENERAL_MANAGER_POST);
            if (managerSort == null || operationsSort == null || generalSort == null)
            {
                return new ApprovalCandidateResolution(List.of(),
                        List.of("岗位排序边界未配置完整"));
            }
            int executiveBoundary = Math.max(operationsSort, generalSort);
            if (executiveBoundary >= managerSort)
            {
                return new ApprovalCandidateResolution(List.of(),
                        List.of("店长与高层岗位排序边界无效"));
            }
            List<ApprovalDirectoryUser> users = directoryMapper
                    .selectCoveredHigherPostUsers(context.anchorDeptId(),
                            managerSort, executiveBoundary,
                            EXCLUDED_LEVEL3_POST_CODES, permission);
            Integer closestSort = users.stream()
                    .map(ApprovalDirectoryUser::getPostSort)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            users = closestSort == null ? List.of() : users.stream()
                    .filter(item -> closestSort.equals(item.getPostSort()))
                    .toList();
            return ApprovalCandidateResolution.of(candidates(users,
                    context.node().getStrategyType(), TRANSFER_LEVEL3,
                    "高于店长、低于运营总监和总经理，取最接近店长的排序档"));
        }
        String postCode = TRANSFER_OPERATIONS_DIRECTOR.equals(code)
                ? OPERATIONS_DIRECTOR_POST : GENERAL_MANAGER_POST;
        if (TRANSFER_GENERAL_MANAGER.equals(code)
                && context.applicantId() != null
                && directoryMapper.selectActivePostCodesByUserId(
                        context.applicantId()).contains(GENERAL_MANAGER_POST))
        {
            throw new ServiceException("总经理不能发起调拨审批");
        }
        return ApprovalCandidateResolution.of(candidates(
                directoryMapper.selectCoveredUsersByPostCode(
                        context.anchorDeptId(), postCode, permission),
                context.node().getStrategyType(), postCode,
                "锚点门店责任范围"));
    }
}
