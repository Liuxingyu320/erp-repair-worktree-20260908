package com.erp.inventory.domain.dto;

import java.util.List;

public class InvTransferDiscrepancyResolveRequest
{
    private String requestId;
    private Long version;
    private String decision;
    private String responsibleParty;
    private String note;
    private List<InvTransferDiscrepancyResolutionItem> items;

    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { decision = value; }
    public String getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(String value) { responsibleParty = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public List<InvTransferDiscrepancyResolutionItem> getItems() { return items; }
    public void setItems(List<InvTransferDiscrepancyResolutionItem> value) { items = value; }
}
