package com.erp.inventory.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** OE同款购买参考维护策略。 */
public class InvOePurchaseReferencePolicyVo
{
    private boolean configured;
    private List<String> allowedHosts = new ArrayList<>();

    public boolean isConfigured()
    {
        return configured;
    }

    public void setConfigured(boolean configured)
    {
        this.configured = configured;
    }

    public List<String> getAllowedHosts()
    {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts)
    {
        this.allowedHosts = allowedHosts == null ? new ArrayList<>() : new ArrayList<>(allowedHosts);
    }
}
