package com.erp.inventory.domain.transfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;

/** Browser-safe command; authoritative fingerprints stay server-side. */
public record InvTransferReceiptDiscrepancyAdjudicationCommand(
        String requestId,
        String basisToken,
        Long caseId,
        Long caseVersion,
        String adjudicationNote,
        String evidenceRefs,
        List<ActionInput> actions)
{
    public InvTransferReceiptDiscrepancyAdjudicationCommand
    {
        actions = actions == null ? null : Collections.unmodifiableList(
                new ArrayList<>(actions));
    }

    @Override
    public String toString()
    {
        return "InvTransferReceiptDiscrepancyAdjudicationCommand["
                + "requestId=" + requestId + ", basisToken=[REDACTED]"
                + ", caseId=" + caseId + ", caseVersion=" + caseVersion
                + ", actionCount=" + (actions == null ? null
                        : actions.size())
                + "]";
    }
}
