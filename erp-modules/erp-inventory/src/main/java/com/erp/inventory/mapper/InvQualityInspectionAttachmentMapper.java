package com.erp.inventory.mapper;

import java.util.List;
import com.erp.inventory.domain.InvQualityInspectionAttachment;

public interface InvQualityInspectionAttachmentMapper
{
    int batchInsert(List<InvQualityInspectionAttachment> attachments);
    List<InvQualityInspectionAttachment> selectByInspectionId(Long inspectionId);
}
