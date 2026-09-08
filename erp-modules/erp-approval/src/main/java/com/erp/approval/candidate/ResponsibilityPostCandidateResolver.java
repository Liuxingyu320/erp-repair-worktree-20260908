package com.erp.approval.candidate;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.service.ApprovalPermissionPolicy;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;

@Component
public class ResponsibilityPostCandidateResolver
        extends AbstractDirectoryCandidateResolver
        implements ApprovalCandidateResolver
{
    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalPermissionPolicy permissionPolicy;
    private final ApprovalJsonSupport jsonSupport;

    public ResponsibilityPostCandidateResolver(
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
        return ApprovalDefinitionConstants.STRATEGY_RESPONSIBILITY_POST
                .equals(node.getStrategyType());
    }

    @Override
    public ApprovalCandidateResolution resolve(ApprovalCandidateContext context)
    {
        if (context.anchorDeptId() == null)
        {
            throw new ServiceException("责任岗位解析缺少锚点组织");
        }
        Map<String, Object> config = jsonSupport.readMap(
                context.node().getStrategyConfig());
        String postCode = text(config, "postCode");
        if (postCode == null)
        {
            throw new ServiceException("责任岗位策略缺少postCode");
        }
        String permission = permissionPolicy.requiredPermission(
                context.template().getBusinessCode());
        String configuredPermission = text(config, "permission");
        if (configuredPermission != null
                && !permission.equals(configuredPermission))
        {
            throw new ServiceException("节点不能绕过业务审批权限: "
                    + permission);
        }
        boolean directOnly = bool(config, "directOnly", false);
        return ApprovalCandidateResolution.of(candidates(
                directoryMapper.selectResponsibilityPostUsers(
                        context.anchorDeptId(), postCode, permission, directOnly),
                context.node().getStrategyType(), postCode,
                directOnly ? "锚点组织直接责任岗位" : "责任范围岗位"));
    }
}
