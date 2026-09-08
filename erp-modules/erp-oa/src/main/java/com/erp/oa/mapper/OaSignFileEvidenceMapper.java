package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.domain.OaSignFileEvidence;

public interface OaSignFileEvidenceMapper
{
    int insertOaSignFileEvidence(OaSignFileEvidence evidence);

    List<OaSignFileEvidence> selectEvidenceByPackageId(Long packageId);

    List<OaSignFileEvidence> selectEvidenceByDocumentVersion(@Param("documentId") Long documentId,
            @Param("documentVersion") String documentVersion);

    OaSignFileEvidence selectEvidenceByType(@Param("documentId") Long documentId,
            @Param("documentVersion") String documentVersion,
            @Param("evidenceType") OaSignFileEvidenceType evidenceType);

    int deleteEvidenceByPackageIdAndDocumentVersion(@Param("packageId") Long packageId,
            @Param("documentVersion") String documentVersion);

    int deleteEvidenceByPackageId(Long packageId);
}
