package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaSignFinalConfirmationDocument;

public interface OaSignFinalConfirmationMapper
{
    OaSignFinalConfirmation selectByRequestId(@Param("requestId") String requestId);
    OaSignFinalConfirmation selectByPackageAndVersion(@Param("packageId") Long packageId,
            @Param("finalDocumentVersion") String finalDocumentVersion);
    List<OaSignFinalConfirmationDocument> selectDocumentsByPackageAndVersion(
            @Param("packageId") Long packageId,
            @Param("finalDocumentVersion") String finalDocumentVersion);
    int insertConfirmation(OaSignFinalConfirmation confirmation);
    int insertConfirmationDocument(OaSignFinalConfirmationDocument document);
    int countByPackageId(Long packageId);
    int deleteConfirmationDocumentsByPackageId(Long packageId);
    int deleteConfirmationsByPackageId(Long packageId);
}
