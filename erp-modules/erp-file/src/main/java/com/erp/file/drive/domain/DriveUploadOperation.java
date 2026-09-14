package com.erp.file.drive.domain;

/** Internal durable receipt. Never serialize this persistence model to clients. */
public class DriveUploadOperation
{
    private String operationId;
    private Long actorId;
    private Long spaceId;
    private Long parentId;
    private String fileName;
    private Long sizeBytes;
    private String sha256;
    private String status;
    private String owner;
    private Long nodeId;
    public String getOperationId() { return operationId; }
    public void setOperationId(String value) { operationId = value; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { actorId = value; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long value) { spaceId = value; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { parentId = value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { fileName = value; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long value) { sizeBytes = value; }
    public String getSha256() { return sha256; }
    public void setSha256(String value) { sha256 = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getOwner() { return owner; }
    public void setOwner(String value) { owner = value; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long value) { nodeId = value; }
}
