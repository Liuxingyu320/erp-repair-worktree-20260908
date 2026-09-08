package com.erp.system.domain.vo;

/** Safe release diagnostics exposed to authenticated users. */
public class SysBuildInfoVo
{
    private String commit;
    private String buildTime;
    private String version;

    public SysBuildInfoVo()
    {
    }

    public SysBuildInfoVo(String commit, String buildTime, String version)
    {
        this.commit = commit;
        this.buildTime = buildTime;
        this.version = version;
    }

    public static SysBuildInfoVo unavailable()
    {
        return new SysBuildInfoVo("UNSET", "UNSET", "UNSET");
    }

    public String getCommit() { return commit; }
    public void setCommit(String commit) { this.commit = commit; }
    public String getBuildTime() { return buildTime; }
    public void setBuildTime(String buildTime) { this.buildTime = buildTime; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
