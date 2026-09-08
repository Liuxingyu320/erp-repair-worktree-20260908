package com.erp.approval.candidate;

import java.util.List;
import org.springframework.stereotype.Component;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.common.core.exception.ServiceException;

@Component
public class ApprovalCandidateResolverRegistry
{
    private final List<ApprovalCandidateResolver> resolvers;

    public ApprovalCandidateResolverRegistry(List<ApprovalCandidateResolver> resolvers)
    {
        this.resolvers = List.copyOf(resolvers);
    }

    public boolean supports(ApprovalVersionNode node)
    {
        return matching(node).size() == 1;
    }

    public ApprovalCandidateResolution resolve(ApprovalCandidateContext context)
    {
        List<ApprovalCandidateResolver> matching = matching(context.node());
        if (matching.isEmpty())
        {
            throw new ServiceException("未注册审批人解析器: "
                    + context.node().getStrategyType() + "/"
                    + context.node().getStrategyCode());
        }
        if (matching.size() > 1)
        {
            throw new ServiceException("审批人解析器冲突: "
                    + context.node().getStrategyType() + "/"
                    + context.node().getStrategyCode());
        }
        return matching.get(0).resolve(context);
    }

    private List<ApprovalCandidateResolver> matching(ApprovalVersionNode node)
    {
        return resolvers.stream().filter(item -> item.supports(node)).toList();
    }
}
