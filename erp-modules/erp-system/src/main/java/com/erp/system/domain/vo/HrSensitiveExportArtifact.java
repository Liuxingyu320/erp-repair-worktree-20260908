package com.erp.system.domain.vo;

/** Prepared sensitive workbook; SUCCESS means generated, not delivered. */
public final class HrSensitiveExportArtifact
{
    private final byte[] content;
    private final String exportScope;
    private final int rowCount;

    public HrSensitiveExportArtifact(byte[] content,String exportScope,int rowCount)
    {
        this.content=content==null?new byte[0]:content.clone();
        this.exportScope=exportScope;
        this.rowCount=rowCount;
    }
    public byte[] getContent(){return content.clone();}
    public String getExportScope(){return exportScope;}
    public int getRowCount(){return rowCount;}
}
