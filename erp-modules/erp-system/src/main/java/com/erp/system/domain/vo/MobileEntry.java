package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MobileEntry implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String label;
    private String icon;
    private String path;
    private List<String> permissions = new ArrayList<>();

    public MobileEntry()
    {
    }

    public MobileEntry(String label, String icon, String path, List<String> permissions)
    {
        this.label = label;
        this.icon = icon;
        this.path = path;
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }

    public MobileEntry copy()
    {
        return new MobileEntry(label, icon, path, permissions);
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getIcon()
    {
        return icon;
    }

    public void setIcon(String icon)
    {
        this.icon = icon;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public List<String> getPermissions()
    {
        return Collections.unmodifiableList(permissions);
    }

    public void setPermissions(List<String> permissions)
    {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }
}
