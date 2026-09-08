package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.OaSignTaskEvent;

public class OaSignTaskDetail
{
    private OaSignTask task;
    private OaSignPackage signPackage;
    private List<OaSignTaskEvent> events = new ArrayList<>();
    private String documentVersion;
    private String snapshotHash;
    private String confirmationToken;
    private Boolean confirmationCurrent;
    private Boolean hasConfirmation;
    private Boolean businessActionsAllowed;
    private Boolean technicalEvidenceView;
    private String latestNotificationStatus;
    private String latestNotificationChannel;
    private Integer latestNotificationRetryCount;
    private Date latestNotificationTime;
    private Date latestNotificationNextRetryTime;
    private String latestNotificationBusinessKey;

    public OaSignTask getTask() { return task; }
    public void setTask(OaSignTask task) { this.task = task; }
    public OaSignPackage getSignPackage() { return signPackage; }
    public void setSignPackage(OaSignPackage signPackage) { this.signPackage = signPackage; }
    public List<OaSignTaskEvent> getEvents() { return events; }
    public void setEvents(List<OaSignTaskEvent> events)
    {
        this.events = events == null ? new ArrayList<>() : events;
    }
    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }
    public String getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
    public String getConfirmationToken() { return confirmationToken; }
    public void setConfirmationToken(String confirmationToken) { this.confirmationToken = confirmationToken; }
    public Boolean getConfirmationCurrent() { return confirmationCurrent; }
    public void setConfirmationCurrent(Boolean confirmationCurrent) { this.confirmationCurrent = confirmationCurrent; }
    public Boolean getHasConfirmation() { return hasConfirmation; }
    public void setHasConfirmation(Boolean hasConfirmation) { this.hasConfirmation = hasConfirmation; }
    public Boolean getBusinessActionsAllowed() { return businessActionsAllowed; }
    public void setBusinessActionsAllowed(Boolean businessActionsAllowed) { this.businessActionsAllowed = businessActionsAllowed; }
    public Boolean getTechnicalEvidenceView() { return technicalEvidenceView; }
    public void setTechnicalEvidenceView(Boolean technicalEvidenceView) { this.technicalEvidenceView = technicalEvidenceView; }
    public String getLatestNotificationStatus() { return latestNotificationStatus; }
    public void setLatestNotificationStatus(String latestNotificationStatus) { this.latestNotificationStatus = latestNotificationStatus; }
    public String getLatestNotificationChannel() { return latestNotificationChannel; }
    public void setLatestNotificationChannel(String latestNotificationChannel) { this.latestNotificationChannel = latestNotificationChannel; }
    public Integer getLatestNotificationRetryCount() { return latestNotificationRetryCount; }
    public void setLatestNotificationRetryCount(Integer latestNotificationRetryCount) { this.latestNotificationRetryCount = latestNotificationRetryCount; }
    public Date getLatestNotificationTime() { return latestNotificationTime; }
    public void setLatestNotificationTime(Date latestNotificationTime) { this.latestNotificationTime = latestNotificationTime; }
    public Date getLatestNotificationNextRetryTime() { return latestNotificationNextRetryTime; }
    public void setLatestNotificationNextRetryTime(Date latestNotificationNextRetryTime) { this.latestNotificationNextRetryTime = latestNotificationNextRetryTime; }
    public String getLatestNotificationBusinessKey() { return latestNotificationBusinessKey; }
    public void setLatestNotificationBusinessKey(String latestNotificationBusinessKey) { this.latestNotificationBusinessKey = latestNotificationBusinessKey; }
}
