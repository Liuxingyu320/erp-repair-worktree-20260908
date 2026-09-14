package com.erp.file.drive.exception;

/** This request stopped before claim. It is not a durable failure verdict for an older command. */
public final class DriveUploadPreClaimRejectedException extends DriveException
{
    private static final long serialVersionUID = 1L;
    private final String operationId;
    public DriveUploadPreClaimRejectedException(String operationId, DriveException cause)
    {
        super(cause.getBusinessCode(), cause.getMessage());
        this.operationId = operationId;
    }
    public String getOperationId() { return operationId; }
}
