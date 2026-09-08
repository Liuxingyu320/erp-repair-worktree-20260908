package com.erp.approval.candidate;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.service.ApprovalPermissionPolicy;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;

/** Resolves active permission holders whose authorized organization covers the anchor. */
@Component
public class PermissionHolderCandidateResolver
        extends AbstractDirectoryCandidateResolver
        implements ApprovalCandidateResolver
{
    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalPermissionPolicy permissionPolicy;
    private final ApprovalJsonSupport jsonSupport;

    public PermissionHolderCandidateResolver(
            ApprovalCandidateDirectoryMapper directoryMapper,
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
        return ApprovalDefinitionConstants.STRATEGY_BUSINESS
                .equals(node.getStrategyType())
                && ApprovalDefinitionConstants.PERMISSION_HOLDER
                        .equals(node.getStrategyCode());
    }

    @Override
    public ApprovalCandidateResolution resolve(ApprovalCandidateContext context)
    {
        if (context.anchorDeptId() == null)
        {
            throw new ServiceException("权限责任人解析缺少锚点组织");
        }
        Map<String, Object> config = jsonSupport.readMap(
                context.node().getStrategyConfig());
        String permission = text(config, "permissionKey");
        if (permission == null)
        {
            throw new ServiceException("权限责任人策略缺少permissionKey");
        }
        String businessCode = context.template().getBusinessCode();
        String requiredPermission = permissionPolicy.requiredPermission(
                businessCode);
        if (!permissionPolicy.isCandidatePermissionAllowed(
                businessCode, permission))
        {
            throw new ServiceException("权限责任人不能绕过业务审批权限: "
                    + requiredPermission);
        }
        return ApprovalCandidateResolution.of(candidates(
                directoryMapper.selectPermissionHolders(
                        context.anchorDeptId(), permission),
                context.node().getStrategyType(), permission,
                "持有审批权限且授权组织覆盖锚点"));
    }
}
