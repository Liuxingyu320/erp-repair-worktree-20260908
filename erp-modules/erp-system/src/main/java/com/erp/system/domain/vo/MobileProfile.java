package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MobileProfile implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private boolean admin;
    private Set<String> roles = new LinkedHashSet<>();
    private Set<String> permissions = new LinkedHashSet<>();
    private List<MobileEntry> adminEntries = new ArrayList<>();

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public boolean isAdmin()
    {
        return admin;
    }

    public void setAdmin(boolean admin)
    {
        this.admin = admin;
    }

    public Set<String> getRoles()
    {
        return roles;
    }

    public void setRoles(Set<String> roles)
    {
        this.roles = roles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roles);
    }

    public Set<String> getPermissions()
    {
        return permissions;
    }

    public void setPermissions(Set<String> permissions)
    {
        this.permissions = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
    }

    public List<MobileEntry> getAdminEntries()
    {
        return adminEntries;
    }

    public void setAdminEntries(List<MobileEntry> adminEntries)
    {
        this.adminEntries = adminEntries == null ? new ArrayList<>() : new ArrayList<>(adminEntries);
    }
}
