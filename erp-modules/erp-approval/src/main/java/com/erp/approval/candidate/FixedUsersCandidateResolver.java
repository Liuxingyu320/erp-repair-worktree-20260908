package com.erp.approval.candidate;

import java.util.ArrayList;
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
public class FixedUsersCandidateResolver extends AbstractDirectoryCandidateResolver
        implements ApprovalCandidateResolver
{
    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalPermissionPolicy permissionPolicy;
    private final ApprovalJsonSupport jsonSupport;

    public FixedUsersCandidateResolver(ApprovalCandidateDirectoryMapper directoryMapper,
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
        return ApprovalDefinitionConstants.STRATEGY_FIXED_USERS
                .equals(node.getStrategyType());
    }

    @Override
    public ApprovalCandidateResolution resolve(ApprovalCandidateContext context)
    {
        Map<String, Object> config = jsonSupport.readMap(
                context.node().getStrategyConfig());
        Object raw = config.get("userIds");
        if (!(raw instanceof List<?> values) || values.isEmpty())
        {
            throw new ServiceException("固定用户策略缺少userIds");
        }
        List<Long> userIds = new ArrayList<>();
        for (Object value : values)
        {
            try
            {
                userIds.add(Long.valueOf(value.toString()));
            }
            catch (RuntimeException ignored)
            {
                throw new ServiceException("固定用户ID无效: " + value);
            }
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
        return ApprovalCandidateResolution.of(candidates(
                directoryMapper.selectActiveFixedUsers(userIds, permission),
                context.node().getStrategyType(), "FIXED_USERS",
                "发布版本固定用户"));
    }
}
