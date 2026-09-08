package com.erp.oa.mapper;

import java.util.Date;
import java.util.List;
import com.erp.oa.domain.OaSignPackageDocument;
import org.apache.ibatis.annotations.Param;

public interface OaSignPackageDocumentMapper
{
    int insertOaSignPackageDocument(OaSignPackageDocument document);

    int updateOaSignPackageDocument(OaSignPackageDocument document);

    int markFinalReadConfirmed(@Param("documentId") Long documentId,
            @Param("packageId") Long packageId,
            @Param("finalDocumentVersion") String finalDocumentVersion,
            @Param("finalPdfHash") String finalPdfHash,
            @Param("confirmedTime") Date confirmedTime);

    int deleteDocumentsByPackageId(Long packageId);

    OaSignPackageDocument selectOaSignPackageDocumentById(Long documentId);

    List<OaSignPackageDocument> selectDocumentsByPackageId(Long packageId);

    List<OaSignPackageDocument> selectRequiredDocumentsByPackageId(Long packageId);
}
