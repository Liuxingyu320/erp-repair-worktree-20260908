package com.erp.approval.candidate;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.service.ApprovalPermissionPolicy;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;

@Component
public class OrgLeaderCandidateResolver extends AbstractDirectoryCandidateResolver
        implements ApprovalCandidateResolver
{
    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalPermissionPolicy permissionPolicy;
    private final ApprovalJsonSupport jsonSupport;

    public OrgLeaderCandidateResolver(ApprovalCandidateDirectoryMapper directoryMapper,
            ApprovalPermissionPolicy permissionPolicy,
            ApprovalJsonSupport jsonSupport)
    {
        this.directoryMapper = directoryMapper;
        this.permissionPolicy = permissionPolicy;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public boolean supports(ApprovalVersionNode node)
    {
        return ApprovalDefinitionConstants.STRATEGY_ORG_LEADER
                .equals(node.getStrategyType());
    }

    @Override
    public ApprovalCandidateResolution resolve(ApprovalCandidateContext context)
    {
        if (context.anchorDeptId() == null)
        {
            throw new ServiceException("组织负责人解析缺少锚点组织");
        }
        Map<String, Object> config = jsonSupport.readMap(
                context.node().getStrategyConfig());
        String permission = permissionPolicy.requiredPermission(
                context.template().getBusinessCode());
        String configuredPermission = text(config, "permission");
        if (configuredPermission != null
                && !permission.equals(configuredPermission))
        {
            throw new ServiceException("节点不能绕过业务审批权限: "
                    + permission);
        }
        String deptType = text(config, "deptType");
        List<ResolvedApprovalCandidate> resolved = candidates(
                directoryMapper.selectOrgLeaderUsers(context.anchorDeptId(),
                        deptType, permission),
                context.node().getStrategyType(), deptType,
                "距离锚点最近的组织负责人");
        return ApprovalCandidateResolution.of(resolved.isEmpty()
                ? resolved : List.of(resolved.get(0)));
    }
}
