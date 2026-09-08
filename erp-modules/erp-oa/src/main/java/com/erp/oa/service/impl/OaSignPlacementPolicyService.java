package com.erp.oa.service.impl;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.service.impl.OaSignedPdfService.PdfImagePlacement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Freezes and resolves the image-placement policy carried by a package document.
 * Package snapshots are authoritative after generation; template changes are never
 * consulted while signing or finalizing an existing package.
 */
@Service
public class OaSignPlacementPolicyService
{
    public static final String SNAPSHOT_V1 = "SNAPSHOT_V1";
    public static final String LEGACY_APPEND_ONLY = "LEGACY_APPEND_ONLY";
    public static final String APPENDED_CONFIRMATION_PAGE =
            "{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}";
    /** Template-only marker. It must be replaced by the generated review PDF SHA before freeze. */
    public static final String GENERATED_REVIEW_PDF_SHA256 = "${generatedReviewPdfSha256}";

    private static final String MODE_APPENDED = "APPENDED_CONFIRMATION_PAGE";
    private static final String MODE_PLACED = "PLACED";
    private static final String MODE_LAST_PAGE = "LAST_PAGE";
    private static final String MODE_PLACED_MULTI = "PLACED_MULTI";
    private static final String MODE_LAST_PAGE_MULTI = "LAST_PAGE_MULTI";
    private static final Set<String> APPENDED_FIELDS = Set.of("mode");
    private static final Set<String> PLACED_FIELDS = Set.of(
            "mode", "pageNumber", "x", "y", "width", "height");
    private static final Set<String> LAST_PAGE_FIELDS = Set.of(
            "mode", "x", "y", "width", "height");
    private static final Set<String> MULTI_FIELDS = Set.of(
            "mode", "templateType", "templateVersion", "reviewPdfHash",
            "placementConfigVersion", "profileId", "expectedBodyPageCount",
            "signingSequencePolicy", "placements", "protectedRegions", "textOverlays");
    private static final String SIGNATURE_FIRST_BODY_PLACEMENT =
            "SIGNATURE_FIRST_BODY_PLACEMENT";
    private static final String COMPANY_FIRST_DISPLAY_EXPORT =
            "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT";
    private static final Set<String> SIGNING_SEQUENCE_POLICIES = Set.of(
            SIGNATURE_FIRST_BODY_PLACEMENT, COMPANY_FIRST_DISPLAY_EXPORT);
    private static final Set<String> PLACEMENT_FIELDS = Set.of(
            "pageNumber", "x", "y", "width", "height");
    private static final Set<String> LAST_PAGE_PLACEMENT_FIELDS = Set.of(
            "x", "y", "width", "height");
    private static final Set<String> PROTECTED_REGION_FIELDS = Set.of(
            "pageNumber", "x", "y", "width", "height");
    private static final Set<String> TEXT_OVERLAY_FIELDS = Set.of(
            "field", "pageNumber", "x", "y", "width", "height", "fontSize");
    private static final Set<String> TEXT_OVERLAY_NAMES = Set.of(
            "companyLegalRepresentative", "attachmentHandbookMark",
            "archiveEvidenceNotice");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LABOR_CONTRACT = "ONBOARD_LABOR_CONTRACT";
    private final OaSignLaborPlacementProfileRegistry laborProfiles =
            new OaSignLaborPlacementProfileRegistry();
    private final OaSignLaborAnchorPlacementResolver laborAnchorResolver =
            new OaSignLaborAnchorPlacementResolver();

    /**
     * Validates the actual generated PDF before its package-document row is inserted, then
     * freezes one exact hash-bound profile. Labor contracts never silently fall back to an
     * appended evidence page: an unreviewed template, reflowed page or missing local anchor is a
     * fail-closed generation error.
     */
    public GeneratedPlacementPolicies validateAndFreezeGeneratedPositions(
            String templateType, String templateVersion, String templateSourceHash,
            String configuredSignatureJson, String configuredSealJson, Path generatedReviewPdf,
            String generatedReviewPdfHash, boolean employeeSignRequired,
            boolean companySealRequired, String expectedLegalRepresentative,
            boolean expectedHandbookIncluded)
    {
        return validateAndFreezeGeneratedPositions(templateType, templateVersion,
                templateSourceHash, configuredSignatureJson, configuredSealJson,
                generatedReviewPdf, generatedReviewPdfHash, employeeSignRequired,
                companySealRequired, expectedLegalRepresentative, expectedHandbookIncluded,
                OaSignSigningSequence.SIGNATURE_FIRST);
    }

    public GeneratedPlacementPolicies validateAndFreezeGeneratedPositions(
            String templateType, String templateVersion, String templateSourceHash,
            String configuredSignatureJson, String configuredSealJson, Path generatedReviewPdf,
            String generatedReviewPdfHash, boolean employeeSignRequired,
            boolean companySealRequired, String expectedLegalRepresentative,
            boolean expectedHandbookIncluded, String signingSequence)
    {
        if (!LABOR_CONTRACT.equalsIgnoreCase(StringUtils.trim(templateType)))
        {
            return new GeneratedPlacementPolicies(
                    freezeGeneratedSignaturePosition(templateType, templateVersion,
                            configuredSignatureJson, generatedReviewPdfHash,
                            employeeSignRequired),
                    freezeGeneratedCompanySealPosition(templateType, templateVersion,
                            configuredSealJson, generatedReviewPdfHash,
                            companySealRequired), null);
        }
        if (!employeeSignRequired || !companySealRequired)
        {
            throw new ServiceException("劳动合同正文落位必须显式要求四处员工签名和一处企业章");
        }
        requireSha256(templateSourceHash, "劳动合同模板源文件指纹");
        requireSha256(generatedReviewPdfHash, "劳动合同生成源PDF指纹");
        if (generatedReviewPdf == null || !Files.isRegularFile(generatedReviewPdf))
        {
            throw new ServiceException("劳动合同生成源PDF不存在");
        }
        if (!sameHash(generatedReviewPdfHash, sha256(generatedReviewPdf)))
        {
            throw new ServiceException("劳动合同生成源PDF指纹不一致");
        }
        OaSignLaborPlacementProfileRegistry.BlockedTemplate blocked =
                laborProfiles.blockedTemplate(templateType, templateVersion,
                        templateSourceHash);
        if (blocked != null)
        {
            throw new ServiceException("劳动合同模板版本未获准正文落位："
                    + blocked.templateVersion() + "（" + blocked.reason() + "）");
        }
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile =
                selectValidatedProfile(templateType, templateVersion, templateSourceHash,
                        generatedReviewPdf, false, expectedLegalRepresentative,
                        expectedHandbookIncluded, false);
        String sequencePolicy = signingSequencePolicy(signingSequence);
        return new GeneratedPlacementPolicies(
                policyJson(profile, templateType, templateVersion,
                        generatedReviewPdfHash, sequencePolicy, true),
                policyJson(profile, templateType, templateVersion,
                        generatedReviewPdfHash, sequencePolicy, false), profile.profileId());
    }

    public void assertTemplateGenerationAllowed(String templateType, String templateVersion,
            String templateSourceHash)
    {
        if (!LABOR_CONTRACT.equalsIgnoreCase(StringUtils.trim(templateType)))
        {
            return;
        }
        requireSha256(templateSourceHash, "劳动合同模板源文件指纹");
        OaSignLaborPlacementProfileRegistry.BlockedTemplate blocked =
                laborProfiles.blockedTemplate(templateType, templateVersion,
                        templateSourceHash);
        if (blocked != null)
        {
            throw new ServiceException("劳动合同模板版本未获准正文落位："
                    + blocked.templateVersion() + "（" + blocked.reason() + "）");
        }
        if (laborProfiles.futureProfiles(templateType, templateVersion,
                templateSourceHash).isEmpty()
                && StringUtils.isBlank(laborProfiles.futureResolverId(templateType,
                        templateVersion, templateSourceHash)))
        {
            throw new ServiceException("劳动合同模板版本或源模板指纹未纳入正文落位清单");
        }
    }

    /**
     * Legacy entry retained for non-labor templates. Labor contracts must use the actual-PDF
     * validation boundary above and therefore cannot be frozen through this hash-only method.
     */
    public String freezeGeneratedSignaturePosition(String templateType, String templateVersion,
            String configuredJson, String generatedReviewPdfHash, boolean employeeSignRequired)
    {
        if (!employeeSignRequired)
        {
            return freezeSignaturePosition(configuredJson, false);
        }
        if (LABOR_CONTRACT.equalsIgnoreCase(StringUtils.trim(templateType)))
        {
            throw new ServiceException("劳动合同必须先校验实际生成PDF再冻结签名位置");
        }
        return freezeSignaturePosition(materializeGeneratedReviewHash(configuredJson,
                generatedReviewPdfHash, "员工签名定位"), true);
    }

    public String freezeGeneratedCompanySealPosition(String templateType, String templateVersion,
            String configuredJson, String generatedReviewPdfHash, boolean companySealRequired)
    {
        if (!companySealRequired)
        {
            return freezeCompanySealPosition(configuredJson, false);
        }
        if (LABOR_CONTRACT.equalsIgnoreCase(StringUtils.trim(templateType)))
        {
            throw new ServiceException("劳动合同必须先校验实际生成PDF再冻结企业章位置");
        }
        return freezeCompanySealPosition(materializeGeneratedReviewHash(configuredJson,
                generatedReviewPdfHash, "企业章定位"), true);
    }

    public String freezeSignaturePosition(String configuredJson, boolean employeeSignRequired)
    {
        if (!employeeSignRequired)
        {
            return null;
        }
        if (StringUtils.isBlank(configuredJson))
        {
            return APPENDED_CONFIRMATION_PAGE;
        }
        return normalize(configuredJson, "员工签名定位", true).canonicalJson();
    }

    public String normalizeCompanySealRequired(String configuredValue, boolean employeeVisible)
    {
        if (!employeeVisible)
        {
            if (StringUtils.isNotBlank(configuredValue) && !"N".equals(configuredValue))
            {
                throw new ServiceException("非员工可见文件不得要求盖章");
            }
            return "N";
        }
        if (!"Y".equals(configuredValue) && !"N".equals(configuredValue))
        {
            throw new ServiceException("企业章要求必须显式配置为Y或N");
        }
        return configuredValue;
    }

    public String freezeCompanySealPosition(String configuredJson, boolean companySealRequired)
    {
        if (!companySealRequired)
        {
            if (StringUtils.isNotBlank(configuredJson))
            {
                throw new ServiceException("不需要盖章的文件不得配置印章坐标");
            }
            return null;
        }
        if (StringUtils.isBlank(configuredJson))
        {
            throw new ServiceException("需要盖章的文件缺少企业章定位");
        }
        return normalize(configuredJson, "企业章定位", true).canonicalJson();
    }

    public PdfImagePlacement resolveSignaturePlacement(OaSignPackageDocument document)
    {
        requireDocumentPolicy(document);
        boolean required = yesNo(document.getEmployeeSignRequired(), "员工签署要求");
        if (!required)
        {
            if (StringUtils.isNotBlank(document.getSignaturePositionJson()))
            {
                throw new ServiceException("非必签文件不得携带签名定位快照");
            }
            return null;
        }
        if (LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode()))
        {
            if (StringUtils.isNotBlank(document.getSignaturePositionJson())
                    && !isAppended(document.getSignaturePositionJson()))
            {
                throw new ServiceException("历史追加页文件不得携带新定位快照");
            }
            return null;
        }
        PlacementPolicy policy = normalizeRequired(document.getSignaturePositionJson(),
                "员工签名定位", true, document);
        validateMultiPolicyPair(document, policy, true);
        if (policy.multi()
                && COMPANY_FIRST_DISPLAY_EXPORT.equals(policy.signingSequencePolicy()))
        {
            return null;
        }
        return policy.appended() ? null : policy.placement();
    }

    public PdfImagePlacement resolveCompanySealPlacement(OaSignPackageDocument document)
    {
        requireDocumentPolicy(document);
        boolean required = yesNo(document.getCompanySealRequired(), "企业章要求");
        if (!required)
        {
            if (StringUtils.isNotBlank(document.getCompanySealPositionJson()))
            {
                throw new ServiceException("不需要盖章的文件不得携带印章定位快照");
            }
            return null;
        }
        if (LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode()))
        {
            if (StringUtils.isNotBlank(document.getCompanySealPositionJson())
                    && !isAppended(document.getCompanySealPositionJson()))
            {
                throw new ServiceException("历史追加页文件不得携带新定位快照");
            }
            return null;
        }
        PlacementPolicy policy = normalizeRequired(document.getCompanySealPositionJson(),
                "企业章定位", true, document);
        validateMultiPolicyPair(document, policy, false);
        return policy.appended() ? null : policy.placement();
    }

    /**
     * Binds a frozen MULTI policy to the package signing sequence in both directions.
     *
     * <p>APPENDED and other legacy single-position policies do not carry sequence semantics and
     * remain readable. A MULTI snapshot created before {@code signingSequencePolicy} was added is
     * interpreted only as the historical signature-first body-placement contract; it is never
     * inferred to be company-first. New MULTI snapshots must carry the explicit field.</p>
     */
    public void assertSigningSequenceMatches(OaSignPackage signPackage,
            OaSignPackageDocument document)
    {
        requireDocumentPolicySnapshot(document);
        PlacementPolicy policy = null;
        boolean signaturePolicy = false;
        if (StringUtils.isNotBlank(document.getSignaturePositionJson()))
        {
            PlacementPolicy candidate = normalizeRequired(document.getSignaturePositionJson(),
                    "员工签名定位", true, document);
            if (candidate.multi())
            {
                policy = candidate;
                signaturePolicy = true;
            }
        }
        if (policy == null && StringUtils.isNotBlank(document.getCompanySealPositionJson()))
        {
            PlacementPolicy candidate = normalizeRequired(document.getCompanySealPositionJson(),
                    "企业章定位", true, document);
            if (candidate.multi())
            {
                policy = candidate;
            }
        }
        if (policy == null)
        {
            return;
        }
        validateMultiPolicyPair(document, policy, signaturePolicy);
        if (signPackage == null || StringUtils.isBlank(signPackage.getSigningSequence()))
        {
            throw new ServiceException("MULTI签章策略缺少签约包顺序快照");
        }
        String expectedPolicy = signingSequencePolicy(signPackage.getSigningSequence());
        String frozenPolicy = policy.signingSequencePolicy() == null
                ? SIGNATURE_FIRST_BODY_PLACEMENT : policy.signingSequencePolicy();
        if (!expectedPolicy.equals(frozenPolicy))
        {
            throw new ServiceException("签约包顺序与MULTI签章策略冻结语义不一致");
        }
    }

    /**
     * Resolves a historical APPENDED snapshot by verified layout, not by package allow-list.
     * Every document must independently prove its immutable template source, review PDF, archive
     * PDF and the exact local anchors of one reviewed profile. The golden package is used only for
     * a missing representative repair, never as the placement feature boundary.
     */
    public HistoricalExportPolicy resolveHistoricalExportPolicy(
            OaSignPackageDocument document, OaSignPackage signPackage,
            String templateSourceHash,
            Path reviewPdfPath, Path archivePdfPath, String archivePdfHash)
    {
        requireDocumentPolicy(document);
        boolean appended = LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode())
                || isAppended(document.getSignaturePositionJson())
                || isAppended(document.getCompanySealPositionJson());
        if (!appended)
        {
            return null;
        }
        if (!LABOR_CONTRACT.equalsIgnoreCase(StringUtils.trim(document.getTemplateType()))
                || StringUtils.isBlank(document.getTemplateVersionSnapshot()))
        {
            throw new ServiceException("该历史合同缺少模板版本或签章位置证据，暂不能恢复");
        }
        requireSha256(templateSourceHash, "历史合同模板源文件指纹");
        requireSha256(document.getReviewPdfHash(), "历史合同源PDF指纹");
        requireSha256(archivePdfHash, "历史合同归档PDF指纹");
        requireSha256(document.getFinalContentHash(), "历史合同稳定正文指纹");
        if (reviewPdfPath == null || !Files.isRegularFile(reviewPdfPath)
                || !sameHash(document.getReviewPdfHash(), sha256(reviewPdfPath)))
        {
            throw new ServiceException("历史合同源PDF指纹不一致");
        }
        if (archivePdfPath == null || !Files.isRegularFile(archivePdfPath)
                || !sameHash(archivePdfHash, sha256(archivePdfPath)))
        {
            throw new ServiceException("历史合同归档PDF指纹不一致");
        }
        if (signPackage == null || StringUtils.isBlank(signPackage.getPackageNo()))
        {
            throw new ServiceException("历史合同缺少签约包冻结证据，暂不能恢复");
        }
        OaSignLaborPlacementProfileRegistry.HistoricalArchiveEvidence archiveEvidence =
                laborProfiles.historicalArchiveEvidence(signPackage.getPackageNo(),
                        new OaSignLaborPlacementProfileRegistry.OaSignPackageDocumentSnapshot(
                                document.getTemplateType(),
                                document.getTemplateVersionSnapshot(),
                                document.getDocumentPolicyMode(),
                                document.getSignaturePositionJson(),
                                document.getCompanySealPositionJson(),
                                document.getReviewPdfHash(), document.getFinalContentHash()),
                        templateSourceHash, archivePdfHash);
        OaSignLaborPlacementProfileRegistry.HistoricalRepresentativeRepair representativeRepair =
                StringUtils.isBlank(signPackage.getLegalRepresentativeSnapshot())
                        ? laborProfiles.historicalRepresentativeRepair(
                                signPackage.getLegalEntityIdSnapshot(),
                                signPackage.getLegalEntityNameSnapshot(),
                                signPackage.getFinalConfirmedTime())
                        : null;
        String expectedRepresentative = StringUtils.isNotBlank(
                signPackage.getLegalRepresentativeSnapshot())
                        ? signPackage.getLegalRepresentativeSnapshot()
                        : representativeRepair == null ? null
                                : representativeRepair.legalRepresentative();
        OaSignLaborPlacementProfileRegistry.PlacementProfile reviewProfile;
        OaSignLaborPlacementProfileRegistry.PlacementProfile archiveProfile;
        if (archiveEvidence != null)
        {
            // The golden archive retains its independently reviewed, immutable coordinates.
            // Package identity is evidence for this exact regression only; generic v7 support
            // below remains layout- and hash-based rather than package allow-listed.
            reviewProfile = laborProfiles.profile(archiveEvidence.profileId());
            archiveProfile = reviewProfile;
            validateProfileAgainstPdf(reviewProfile, reviewPdfPath, false);
            validateProfileAgainstPdf(archiveProfile, archivePdfPath, true);
        }
        else
        {
            reviewProfile = selectValidatedProfile(document.getTemplateType(),
                    document.getTemplateVersionSnapshot(), templateSourceHash,
                    reviewPdfPath, false, expectedRepresentative, null, true);
            archiveProfile = selectValidatedProfile(document.getTemplateType(),
                    document.getTemplateVersionSnapshot(), templateSourceHash,
                    archivePdfPath, true, expectedRepresentative, null, true);
        }
        if (!reviewProfile.profileId().equals(archiveProfile.profileId())
                || !laborAnchorResolver.sameGeometry(reviewProfile, archiveProfile))
        {
            throw new ServiceException("历史合同源PDF与归档PDF版式证据不一致");
        }
        if (archiveEvidence != null
                && !reviewProfile.profileId().equals(archiveEvidence.profileId()))
        {
            throw new ServiceException("历史合同归档证据与实际版式不一致");
        }
        if (archiveEvidence != null
                && (!sameHash(signPackage.getSignatureSampleHash(),
                        archiveEvidence.signatureSampleHash())
                        || !sameHash(signPackage.getSealImageHashSnapshot(),
                                archiveEvidence.sealImageHash())))
        {
            throw new ServiceException("历史合同签名或印章素材与审计证据不一致");
        }
        if (archiveEvidence != null)
        {
            if (StringUtils.isNotBlank(archiveEvidence.legalRepresentativeSnapshot())
                    && !sameText(archiveEvidence.legalRepresentativeSnapshot(),
                            signPackage.getLegalRepresentativeSnapshot()))
            {
                throw new ServiceException("历史合同冻结甲方代表与审计证据不一致");
            }
            if (StringUtils.isBlank(archiveEvidence.legalRepresentativeSnapshot())
                    && (representativeRepair == null
                            || !sameText(archiveEvidence.legalRepresentativeRepairId(),
                                    representativeRepair.repairId())))
            {
                throw new ServiceException("历史合同甲方代表修复依据与审计证据不一致");
            }
        }
        return historicalPolicy(reviewProfile, archiveEvidence, representativeRepair);
    }

    public boolean requiresHistoricalExportPolicy(OaSignPackageDocument document)
    {
        requireDocumentPolicy(document);
        return LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode())
                || isAppended(document.getSignaturePositionJson())
                || isAppended(document.getCompanySealPositionJson());
    }

    public PdfImagePlacement resolveFinalExportSignaturePlacement(
            OaSignPackageDocument document, HistoricalExportPolicy historicalPolicy)
    {
        if (!requiresEmployeeSignature(document))
        {
            return null;
        }
        if (requiresHistoricalExportPolicy(document))
        {
            return requireHistoricalPolicy(historicalPolicy).signaturePlacement();
        }
        PlacementPolicy policy = normalizeRequired(document.getSignaturePositionJson(),
                "员工签名定位", true, document);
        validateMultiPolicyPair(document, policy, true);
        return policy.appended() ? null : policy.placement();
    }

    public PdfImagePlacement resolveFinalExportSignaturePlacement(
            OaSignPackageDocument document)
    {
        return resolveFinalExportSignaturePlacement(document, null);
    }

    public PdfImagePlacement resolveFinalExportCompanySealPlacement(
            OaSignPackageDocument document, HistoricalExportPolicy historicalPolicy)
    {
        PdfImagePlacement placement = resolveCompanySealPlacement(document);
        if (placement != null || !requiresCompanySeal(document))
        {
            return placement;
        }
        return requireHistoricalPolicy(historicalPolicy).sealPlacement();
    }

    public PdfImagePlacement resolveFinalExportCompanySealPlacement(
            OaSignPackageDocument document)
    {
        return resolveFinalExportCompanySealPlacement(document, null);
    }

    /**
     * Resolves optional, versioned text repairs for a display derivative.  These repairs are
     * carried by the same frozen position snapshots as the signature/seal images, so a history
     * export never guesses coordinates from the current template or from a global page number.
     */
    public List<OaSignedPdfService.PdfTextPlacement> resolveDisplayTextPlacements(
            OaSignPackageDocument document)
    {
        requireDocumentPolicy(document);
        List<OaSignedPdfService.PdfTextPlacement> placements = new ArrayList<>();
        Set<String> fields = new java.util.HashSet<>();
        if (LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode()))
        {
            return List.of();
        }
        validateDocumentMultiPolicyPairIfPresent(document);
        List<String> positionJsons = new ArrayList<>();
        positionJsons.add(document.getSignaturePositionJson());
        positionJsons.add(document.getCompanySealPositionJson());
        for (String json : positionJsons)
        {
            if (StringUtils.isBlank(json))
            {
                continue;
            }
            PlacementPolicy policy = normalizeRequired(json, "签章定位", true, document);
            if (policy.appended())
            {
                continue;
            }
            addUniqueTextPlacements(placements, fields, policy.textPlacements());
        }
        return List.copyOf(placements);
    }

    public List<OaSignedPdfService.PdfTextPlacement> resolveFinalExportDisplayTextPlacements(
            OaSignPackageDocument document, HistoricalExportPolicy historicalPolicy)
    {
        List<OaSignedPdfService.PdfTextPlacement> placements =
                new ArrayList<>(resolveDisplayTextPlacements(document));
        boolean appended = LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode())
                || isAppended(document.getSignaturePositionJson())
                || isAppended(document.getCompanySealPositionJson());
        if (!appended)
        {
            return List.copyOf(placements);
        }
        Set<String> fields = new java.util.HashSet<>();
        for (OaSignedPdfService.PdfTextPlacement placement : placements)
        {
            fields.add(placement.field());
        }
        addUniqueTextPlacements(placements, fields,
                requireHistoricalPolicy(historicalPolicy).textPlacements());
        return List.copyOf(placements);
    }

    public List<OaSignedPdfService.PdfTextPlacement> resolveFinalExportDisplayTextPlacements(
            OaSignPackageDocument document)
    {
        return resolveFinalExportDisplayTextPlacements(document, null);
    }

    public Integer resolveFinalExportExpectedBodyPageCount(OaSignPackageDocument document,
            HistoricalExportPolicy historicalPolicy)
    {
        requireDocumentPolicy(document);
        if (LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode())
                || isAppended(document.getSignaturePositionJson())
                || isAppended(document.getCompanySealPositionJson()))
        {
            return requireHistoricalPolicy(historicalPolicy).expectedBodyPageCount();
        }
        boolean multi = isMulti(document.getSignaturePositionJson())
                || isMulti(document.getCompanySealPositionJson());
        if (!multi)
        {
            return null;
        }
        PlacementPolicy signaturePolicy = StringUtils.isBlank(document.getSignaturePositionJson())
                ? null : normalizeRequired(document.getSignaturePositionJson(), "员工签名定位",
                        true, document);
        PlacementPolicy sealPolicy = StringUtils.isBlank(document.getCompanySealPositionJson())
                ? null : normalizeRequired(document.getCompanySealPositionJson(), "企业章定位",
                        true, document);
        PlacementPolicy policy = signaturePolicy != null && signaturePolicy.multi()
                ? signaturePolicy : sealPolicy;
        validateMultiPolicyPair(document, policy, policy == signaturePolicy);
        return policy.expectedBodyPageCount();
    }

    public Integer resolveFinalExportExpectedBodyPageCount(OaSignPackageDocument document)
    {
        return resolveFinalExportExpectedBodyPageCount(document, null);
    }

    private void addUniqueTextPlacements(List<OaSignedPdfService.PdfTextPlacement> target,
            Set<String> fields, List<OaSignedPdfService.PdfTextPlacement> additions)
    {
        for (OaSignedPdfService.PdfTextPlacement placement : additions)
        {
            if (!fields.add(placement.field()))
            {
                throw new ServiceException("签章展示文本定位重复：" + placement.field());
            }
            target.add(placement);
        }
    }

    private boolean isAppended(String json)
    {
        return StringUtils.isNotBlank(json)
                && normalize(json, "签章定位", true).appended();
    }

    private boolean isMulti(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return false;
        }
        PlacementPolicy policy = normalize(json, "签章定位", true);
        return policy.multi();
    }

    private String materializeGeneratedReviewHash(String json, String generatedReviewPdfHash,
            String label)
    {
        if (StringUtils.isBlank(json))
        {
            return json;
        }
        ObjectNode root;
        try
        {
            JsonNode parsed = OBJECT_MAPPER.readTree(json);
            if (!(parsed instanceof ObjectNode objectNode))
            {
                throw new ServiceException(label + "必须是JSON对象");
            }
            root = objectNode;
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException(label + "JSON不合法")
                    .setDetailMessage(exception.getOriginalMessage());
        }
        String mode = root.path("mode").asText();
        if ((MODE_PLACED_MULTI.equals(mode) || MODE_LAST_PAGE_MULTI.equals(mode))
                && GENERATED_REVIEW_PDF_SHA256.equals(root.path("reviewPdfHash").asText()))
        {
            requireSha256(generatedReviewPdfHash, label + "生成源PDF指纹");
            root.put("reviewPdfHash", generatedReviewPdfHash.toLowerCase(Locale.ROOT));
        }
        return writeJson(root, label);
    }

    private String policyJson(OaSignLaborPlacementProfileRegistry.PlacementProfile profile,
            String templateType, String templateVersion, String generatedReviewPdfHash,
            String signingSequencePolicy, boolean signature)
    {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("mode", MODE_PLACED_MULTI);
        root.put("templateType", StringUtils.trim(templateType));
        root.put("templateVersion", StringUtils.trim(templateVersion));
        root.put("reviewPdfHash", generatedReviewPdfHash.toLowerCase(Locale.ROOT));
        root.put("placementConfigVersion", laborProfiles.configVersion());
        root.put("profileId", profile.profileId());
        root.put("expectedBodyPageCount", profile.expectedBodyPageCount());
        root.put("signingSequencePolicy", signingSequencePolicy);
        ArrayNode placements = root.putArray("placements");
        List<OaSignLaborPlacementProfileRegistry.PlacementRect> configured = signature
                ? profile.signaturePlacements() : profile.sealPlacements();
        configured.forEach(value -> addRect(placements, value));
        ArrayNode protectedRegions = root.putArray("protectedRegions");
        profile.protectedRegions().forEach(value -> addRect(protectedRegions, value));
        // Future documents already render the frozen legal representative and attachment
        // checklist into the review PDF.  Text overlays are historical APPENDED repair data and
        // must never be frozen here, otherwise a later export would draw duplicate text.
        return normalize(writeJson(root, "劳动合同多位置策略"), "劳动合同多位置策略",
                false).canonicalJson();
    }

    private ObjectNode addRect(ArrayNode target,
            OaSignLaborPlacementProfileRegistry.Rect value)
    {
        ObjectNode node = target.addObject();
        node.put("pageNumber", value.pageNumber());
        node.put("x", value.x());
        node.put("y", value.y());
        node.put("width", value.width());
        node.put("height", value.height());
        return node;
    }

    private OaSignLaborPlacementProfileRegistry.PlacementProfile selectValidatedProfile(
            String templateType, String templateVersion, String templateSourceHash,
            Path pdfPath, boolean archiveWithConfirmationPage,
            String expectedLegalRepresentative, Boolean expectedHandbookIncluded,
            boolean includeHistoricalTextOverlays)
    {
        String resolverId = laborProfiles.futureResolverId(templateType, templateVersion,
                templateSourceHash);
        if (StringUtils.isNotBlank(resolverId))
        {
            if (!OaSignLaborAnchorPlacementResolver.PROFILE_ID.equals(resolverId))
            {
                throw new ServiceException("劳动合同动态落位解析器未获准：" + resolverId);
            }
            return laborAnchorResolver.resolve(pdfPath, archiveWithConfirmationPage,
                    expectedLegalRepresentative, expectedHandbookIncluded,
                    includeHistoricalTextOverlays);
        }
        List<OaSignLaborPlacementProfileRegistry.PlacementProfile> candidates =
                laborProfiles.futureProfiles(templateType, templateVersion, templateSourceHash);
        if (candidates.isEmpty())
        {
            OaSignLaborPlacementProfileRegistry.BlockedTemplate blocked =
                    laborProfiles.blockedTemplate(templateType, templateVersion,
                            templateSourceHash);
            if (blocked != null)
            {
                throw new ServiceException("劳动合同模板版本未获准正文落位："
                        + blocked.templateVersion() + "（" + blocked.reason() + "）");
            }
            throw new ServiceException("劳动合同模板版本或源模板指纹未纳入正文落位清单");
        }
        List<OaSignLaborPlacementProfileRegistry.PlacementProfile> matched = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (OaSignLaborPlacementProfileRegistry.PlacementProfile candidate : candidates)
        {
            try
            {
                validateProfileAgainstPdf(candidate, pdfPath, archiveWithConfirmationPage);
                matched.add(candidate);
            }
            catch (ServiceException failure)
            {
                failures.add(candidate.profileId() + ":" + failure.getMessage());
            }
        }
        if (matched.size() != 1)
        {
            String detail = matched.isEmpty() ? String.join("；", failures)
                    : matched.stream().map(
                            OaSignLaborPlacementProfileRegistry.PlacementProfile::profileId)
                            .sorted().toList().toString();
            throw new ServiceException(matched.isEmpty()
                    ? "劳动合同实际生成PDF页数或局部锚点未命中核准版式"
                    : "劳动合同实际生成PDF同时命中多个版式，无法安全冻结")
                    .setDetailMessage(detail);
        }
        return matched.get(0);
    }

    private void validateProfileAgainstPdf(
            OaSignLaborPlacementProfileRegistry.PlacementProfile profile, Path pdfPath,
            boolean archiveWithConfirmationPage)
    {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile()))
        {
            int expectedTotal = profile.expectedBodyPageCount()
                    + (archiveWithConfirmationPage ? 1 : 0);
            if (document.getNumberOfPages() != expectedTotal)
            {
                throw new ServiceException("正文页数不匹配：expected="
                        + profile.expectedBodyPageCount() + ", actual="
                        + (document.getNumberOfPages()
                                - (archiveWithConfirmationPage ? 1 : 0)));
            }
            for (OaSignLaborPlacementProfileRegistry.AnchorRect anchor : profile.anchors())
            {
                String actual = positionedText(document, anchor);
                for (String required : anchor.requiredTexts())
                {
                    if (!actual.contains(canonicalText(required)))
                    {
                        throw new ServiceException("局部锚点缺失：" + anchor.name()
                                + "/" + required);
                    }
                }
            }
            List<OaSignLaborPlacementProfileRegistry.Rect> protectedRegions =
                    new ArrayList<>(profile.protectedRegions());
            List<OaSignLaborPlacementProfileRegistry.Rect> targets = new ArrayList<>();
            targets.addAll(profile.signaturePlacements());
            targets.addAll(profile.sealPlacements());
            if (profile.textOverlays() != null)
            {
                targets.addAll(profile.textOverlays());
            }
            for (OaSignLaborPlacementProfileRegistry.Rect target : targets)
            {
                validateRectBounds(document, target);
                for (OaSignLaborPlacementProfileRegistry.Rect protectedRegion : protectedRegions)
                {
                    if (target.pageNumber().equals(protectedRegion.pageNumber())
                            && intersects(target, protectedRegion))
                    {
                        throw new ServiceException("签章或文本位置与受保护区域重叠");
                    }
                }
            }
        }
        catch (ServiceException failure)
        {
            throw failure;
        }
        catch (IOException | RuntimeException failure)
        {
            throw new ServiceException("劳动合同实际生成PDF版式校验失败")
                    .setDetailMessage(failure.getMessage());
        }
    }

    private String positionedText(PDDocument document,
            OaSignLaborPlacementProfileRegistry.AnchorRect anchor) throws IOException
    {
        PDPage page = document.getPage(anchor.pageNumber() - 1);
        PDRectangle box = page.getCropBox();
        float top = box.getHeight() - (anchor.y() - box.getLowerLeftY()) - anchor.height();
        Rectangle2D rectangle = new Rectangle2D.Float(
                anchor.x() - box.getLowerLeftX(), top, anchor.width(), anchor.height());
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);
        stripper.addRegion("anchor", rectangle);
        stripper.extractRegions(page);
        return canonicalText(stripper.getTextForRegion("anchor"));
    }

    private void validateRectBounds(PDDocument document,
            OaSignLaborPlacementProfileRegistry.Rect value)
    {
        PDRectangle box = document.getPage(value.pageNumber() - 1).getCropBox();
        if (value.x() < box.getLowerLeftX() || value.y() < box.getLowerLeftY()
                || (double) value.x() + value.width() > box.getUpperRightX()
                || (double) value.y() + value.height() > box.getUpperRightY())
        {
            throw new ServiceException("劳动合同签章或文本位置越界");
        }
    }

    private boolean intersects(OaSignLaborPlacementProfileRegistry.Rect left,
            OaSignLaborPlacementProfileRegistry.Rect right)
    {
        return Math.min(left.x() + left.width(), right.x() + right.width())
                > Math.max(left.x(), right.x())
                && Math.min(left.y() + left.height(), right.y() + right.height())
                        > Math.max(left.y(), right.y());
    }

    private String canonicalText(String value)
    {
        return Normalizer.normalize(StringUtils.defaultString(value), Normalizer.Form.NFKC)
                .replaceAll("\\s+", "");
    }

    private HistoricalExportPolicy historicalPolicy(
            OaSignLaborPlacementProfileRegistry.PlacementProfile profile,
            OaSignLaborPlacementProfileRegistry.HistoricalArchiveEvidence archiveEvidence,
            OaSignLaborPlacementProfileRegistry.HistoricalRepresentativeRepair representative)
    {
        List<OaSignedPdfService.PdfProtectedRegion> protectedRegions =
                profile.protectedRegions().stream()
                        .map(value -> new OaSignedPdfService.PdfProtectedRegion(
                                value.pageNumber(), value.x(), value.y(), value.width(),
                                value.height()))
                        .toList();
        PdfImagePlacement signatures = PdfImagePlacement.composite(
                profile.signaturePlacements().stream()
                        .map(value -> new PdfImagePlacement(value.pageNumber(), value.x(),
                                value.y(), value.width(), value.height(), protectedRegions))
                        .toList());
        PdfImagePlacement seal = profile.sealPlacements().stream()
                .map(value -> new PdfImagePlacement(value.pageNumber(), value.x(), value.y(),
                        value.width(), value.height(), protectedRegions))
                .findFirst().orElseThrow();
        List<OaSignedPdfService.PdfTextPlacement> textPlacements =
                profile.textOverlays() == null ? List.of()
                        : profile.textOverlays().stream()
                                .map(value -> new OaSignedPdfService.PdfTextPlacement(
                                        value.field(), value.pageNumber(), value.x(), value.y(),
                                        value.width(), value.height(), value.fontSize(),
                                        protectedRegions))
                                .toList();
        List<String> repairFields = new ArrayList<>();
        repairFields.add("employeeSignaturePositions");
        repairFields.add("companySealPosition");
        textPlacements.stream().map(OaSignedPdfService.PdfTextPlacement::field)
                .forEach(repairFields::add);
        repairFields = repairFields.stream().filter(StringUtils::isNotBlank).distinct()
                .sorted().toList();
        if (archiveEvidence != null)
        {
            List<String> declared = archiveEvidence.repairFields().stream()
                    .filter(StringUtils::isNotBlank).distinct().sorted().toList();
            if (!declared.equals(repairFields))
            {
                throw new ServiceException("历史合同修复字段声明与实际定位不一致");
            }
        }
        String repairId = archiveEvidence == null ? null : archiveEvidence.evidenceId();
        if (representative != null)
        {
            repairId = StringUtils.isBlank(repairId) ? representative.repairId()
                    : repairId + "," + representative.repairId();
        }
        String representativeEvidence = representative == null ? null
                : representative.evidenceSource() + "#sha256="
                        + representative.evidenceSha256();
        return new HistoricalExportPolicy(laborProfiles.configVersion(), profile.profileId(),
                signatures, seal, textPlacements, profile.expectedBodyPageCount(),
                repairId,
                representative == null ? null : representative.legalRepresentative(),
                representativeEvidence,
                archiveEvidence == null ? List.of()
                        : List.copyOf(archiveEvidence.requiredVisibleDocumentTypes()),
                repairFields,
                archiveEvidence == null ? null : archiveEvidence.finalRootHash());
    }

    private HistoricalExportPolicy requireHistoricalPolicy(HistoricalExportPolicy value)
    {
        if (value == null)
        {
            throw new ServiceException("该历史合同模板版本、源文件或局部版式证据不足，暂不能恢复");
        }
        return value;
    }

    private String sha256(Path path)
    {
        try (InputStream input = Files.newInputStream(path))
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (IOException | NoSuchAlgorithmException failure)
        {
            throw new ServiceException("读取劳动合同PDF指纹失败")
                    .setDetailMessage(failure.getMessage());
        }
    }

    private boolean sameHash(String left, String right)
    {
        return StringUtils.isNotBlank(left) && StringUtils.isNotBlank(right)
                && left.equalsIgnoreCase(right);
    }

    private static void requireSha256(String value, String label)
    {
        if (StringUtils.isBlank(value) || !value.matches("[0-9a-fA-F]{64}"))
        {
            throw new ServiceException(label + "必须是SHA-256");
        }
    }

    public boolean requiresEmployeeSignature(OaSignPackageDocument document)
    {
        requireDocumentPolicy(document);
        return yesNo(document.getEmployeeSignRequired(), "员工签署要求");
    }

    public boolean requiresCompanySeal(OaSignPackageDocument document)
    {
        requireDocumentPolicy(document);
        return yesNo(document.getCompanySealRequired(), "企业章要求");
    }

    private PlacementPolicy normalizeRequired(String json, String label, boolean appendedAllowed,
            OaSignPackageDocument document)
    {
        if (StringUtils.isBlank(json))
        {
            throw new ServiceException(label + "快照缺失");
        }
        PlacementPolicy policy = normalize(json, label, appendedAllowed);
        validateDocumentFingerprint(policy, document, label);
        return policy;
    }

    private void validateDocumentFingerprint(PlacementPolicy policy,
            OaSignPackageDocument document, String label)
    {
        if (!policy.multi())
        {
            return;
        }
        if (document == null || StringUtils.isBlank(document.getTemplateType())
                || StringUtils.isBlank(document.getTemplateVersionSnapshot())
                || StringUtils.isBlank(document.getReviewPdfHash()))
        {
            throw new ServiceException(label + "缺少模板版本或源PDF指纹");
        }
        if (!sameText(policy.templateType(), document.getTemplateType())
                || !sameText(policy.templateVersion(), document.getTemplateVersionSnapshot()))
        {
            throw new ServiceException(label + "模板版本指纹不匹配");
        }
        if (!sameText(policy.reviewPdfHash(), document.getReviewPdfHash()))
        {
            throw new ServiceException(label + "源PDF指纹不匹配");
        }
    }

    /**
     * MULTI snapshots are a paired, immutable audit record.  The signature and seal carry
     * different rectangles, but they must identify the same reviewed layout configuration and
     * expected body page count.  A mixed or partially upgraded pair must never be exported.
     */
    private void validateMultiPolicyPair(OaSignPackageDocument document,
            PlacementPolicy current, boolean currentIsSignature)
    {
        if (current == null || !current.multi())
        {
            return;
        }
        boolean companionRequired = currentIsSignature
                ? yesNo(document.getCompanySealRequired(), "企业章要求")
                : yesNo(document.getEmployeeSignRequired(), "员工签署要求");
        String companionJson = currentIsSignature
                ? document.getCompanySealPositionJson() : document.getSignaturePositionJson();
        if (!companionRequired)
        {
            if (StringUtils.isNotBlank(companionJson))
            {
                throw new ServiceException("MULTI签章策略与签章要求不一致");
            }
            return;
        }
        String companionLabel = currentIsSignature ? "企业章定位" : "员工签名定位";
        PlacementPolicy companion = normalizeRequired(companionJson, companionLabel, true,
                document);
        if (!companion.multi())
        {
            throw new ServiceException("MULTI签名与印章策略模式不一致");
        }
        if (!sameText(current.templateType(), companion.templateType())
                || !sameText(current.templateVersion(), companion.templateVersion())
                || !sameText(current.reviewPdfHash(), companion.reviewPdfHash())
                || !sameText(current.placementConfigVersion(),
                        companion.placementConfigVersion())
                || !sameText(current.profileId(), companion.profileId())
                || !Objects.equals(current.expectedBodyPageCount(),
                        companion.expectedBodyPageCount())
                || !Objects.equals(current.signingSequencePolicy(),
                        companion.signingSequencePolicy()))
        {
            throw new ServiceException("MULTI签名与印章策略冻结证据不一致");
        }
    }

    private void validateDocumentMultiPolicyPairIfPresent(OaSignPackageDocument document)
    {
        if (StringUtils.isNotBlank(document.getSignaturePositionJson()))
        {
            PlacementPolicy signature = normalizeRequired(document.getSignaturePositionJson(),
                    "员工签名定位", true, document);
            if (signature.multi())
            {
                validateMultiPolicyPair(document, signature, true);
                return;
            }
        }
        if (StringUtils.isNotBlank(document.getCompanySealPositionJson()))
        {
            PlacementPolicy seal = normalizeRequired(document.getCompanySealPositionJson(),
                    "企业章定位", true, document);
            if (seal.multi())
            {
                validateMultiPolicyPair(document, seal, false);
            }
        }
    }

    private boolean sameText(String left, String right)
    {
        return StringUtils.trim(left).equals(StringUtils.trim(right));
    }

    private PlacementPolicy normalize(String json, String label, boolean appendedAllowed)
    {
        ObjectNode root;
        try
        {
            JsonNode parsed = OBJECT_MAPPER.readTree(json);
            if (!(parsed instanceof ObjectNode objectNode))
            {
                throw new ServiceException(label + "必须是JSON对象");
            }
            root = objectNode;
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException(label + "JSON不合法").setDetailMessage(e.getOriginalMessage());
        }

        JsonNode modeNode = root.get("mode");
        if (modeNode == null || !modeNode.isTextual() || StringUtils.isBlank(modeNode.textValue()))
        {
            throw new ServiceException(label + "缺少明确mode");
        }
        String mode = modeNode.textValue();
        if (MODE_APPENDED.equals(mode))
        {
            if (!appendedAllowed)
            {
                throw new ServiceException(label + "不支持追加确认页策略");
            }
            rejectUnknownFields(root, APPENDED_FIELDS, label);
            return new PlacementPolicy(APPENDED_CONFIRMATION_PAGE, null, null, null, null,
                    null, null, null, null, false, true, List.of());
        }
        if (MODE_PLACED_MULTI.equals(mode) || MODE_LAST_PAGE_MULTI.equals(mode))
        {
            return normalizeMulti(root, label, mode);
        }
        if (MODE_LAST_PAGE.equals(mode))
        {
            rejectUnknownFields(root, LAST_PAGE_FIELDS, label);
            float x = requiredFiniteFloat(root, "x", label, false);
            float y = requiredFiniteFloat(root, "y", label, false);
            float width = requiredFiniteFloat(root, "width", label, true);
            float height = requiredFiniteFloat(root, "height", label, true);

            ObjectNode canonical = OBJECT_MAPPER.createObjectNode();
            canonical.put("mode", MODE_LAST_PAGE);
            canonical.put("x", x);
            canonical.put("y", y);
            canonical.put("width", width);
            canonical.put("height", height);
            return new PlacementPolicy(writeJson(canonical, label),
                    PdfImagePlacement.lastPage(x, y, width, height), null, null, null,
                    null, null, null, null, false, false, List.of());
        }
        if (!MODE_PLACED.equals(mode))
        {
            throw new ServiceException(label + "的mode不支持");
        }
        rejectUnknownFields(root, PLACED_FIELDS, label);

        int pageNumber = requiredPositiveInteger(root, "pageNumber", label);
        float x = requiredFiniteFloat(root, "x", label, false);
        float y = requiredFiniteFloat(root, "y", label, false);
        float width = requiredFiniteFloat(root, "width", label, true);
        float height = requiredFiniteFloat(root, "height", label, true);

        ObjectNode canonical = OBJECT_MAPPER.createObjectNode();
        canonical.put("mode", MODE_PLACED);
        canonical.put("pageNumber", pageNumber);
        canonical.put("x", x);
        canonical.put("y", y);
        canonical.put("width", width);
        canonical.put("height", height);
        return new PlacementPolicy(writeJson(canonical, label),
                new PdfImagePlacement(pageNumber, x, y, width, height), null, null, null,
                null, null, null, null, false, false, List.of());
    }

    private PlacementPolicy normalizeMulti(ObjectNode root, String label, String mode)
    {
        rejectUnknownFields(root, MULTI_FIELDS, label);
        String templateType = requiredText(root, "templateType", label);
        String templateVersion = requiredText(root, "templateVersion", label);
        String reviewPdfHash = requiredHash(root, "reviewPdfHash", label);
        String placementConfigVersion = requiredText(root, "placementConfigVersion", label);
        String profileId = requiredText(root, "profileId", label);
        int expectedBodyPageCount = requiredPositiveInteger(root,
                "expectedBodyPageCount", label);
        String signingSequencePolicy = optionalSigningSequencePolicy(root, label);
        List<OaSignedPdfService.PdfProtectedRegion> protectedRegions = parseProtectedRegions(
                root.get("protectedRegions"), label);
        List<OaSignedPdfService.PdfTextPlacement> textPlacements = parseTextOverlays(
                root.get("textOverlays"), protectedRegions, label);
        JsonNode placementsNode = root.get("placements");
        if (placementsNode == null || !placementsNode.isArray() || placementsNode.size() == 0)
        {
            throw new ServiceException(label + "必须包含至少一个签章位置");
        }
        List<PdfImagePlacement> placements = new ArrayList<>();
        ArrayNode canonicalPlacements = OBJECT_MAPPER.createArrayNode();
        for (JsonNode node : placementsNode)
        {
            if (!(node instanceof ObjectNode placementNode))
            {
                throw new ServiceException(label + "签章位置必须是JSON对象");
            }
            Set<String> allowed = MODE_LAST_PAGE_MULTI.equals(mode)
                    ? LAST_PAGE_PLACEMENT_FIELDS : PLACEMENT_FIELDS;
            rejectUnknownFields(placementNode, allowed, label);
            float x = requiredFiniteFloat(placementNode, "x", label, false);
            float y = requiredFiniteFloat(placementNode, "y", label, false);
            float width = requiredFiniteFloat(placementNode, "width", label, true);
            float height = requiredFiniteFloat(placementNode, "height", label, true);
            ObjectNode canonicalPlacement = OBJECT_MAPPER.createObjectNode();
            PdfImagePlacement placement;
            if (MODE_LAST_PAGE_MULTI.equals(mode))
            {
                placement = PdfImagePlacement.lastPage(x, y, width, height, protectedRegions);
            }
            else
            {
                int pageNumber = requiredPositiveInteger(placementNode, "pageNumber", label);
                canonicalPlacement.put("pageNumber", pageNumber);
                placement = new PdfImagePlacement(pageNumber, x, y, width, height,
                        protectedRegions);
            }
            canonicalPlacement.put("x", x);
            canonicalPlacement.put("y", y);
            canonicalPlacement.put("width", width);
            canonicalPlacement.put("height", height);
            canonicalPlacements.add(canonicalPlacement);
            placements.add(placement);
        }
        ObjectNode canonical = OBJECT_MAPPER.createObjectNode();
        canonical.put("mode", mode);
        canonical.put("templateType", templateType);
        canonical.put("templateVersion", templateVersion);
        canonical.put("reviewPdfHash", reviewPdfHash);
        canonical.put("placementConfigVersion", placementConfigVersion);
        canonical.put("profileId", profileId);
        canonical.put("expectedBodyPageCount", expectedBodyPageCount);
        if (signingSequencePolicy != null)
        {
            canonical.put("signingSequencePolicy", signingSequencePolicy);
        }
        canonical.set("placements", canonicalPlacements);
        if (!protectedRegions.isEmpty())
        {
            ArrayNode canonicalRegions = OBJECT_MAPPER.createArrayNode();
            for (OaSignedPdfService.PdfProtectedRegion region : protectedRegions)
            {
                ObjectNode regionNode = OBJECT_MAPPER.createObjectNode();
                regionNode.put("pageNumber", region.pageNumber());
                regionNode.put("x", region.x());
                regionNode.put("y", region.y());
                regionNode.put("width", region.width());
                regionNode.put("height", region.height());
                canonicalRegions.add(regionNode);
            }
            canonical.set("protectedRegions", canonicalRegions);
        }
        if (!textPlacements.isEmpty())
        {
            ArrayNode canonicalTextOverlays = OBJECT_MAPPER.createArrayNode();
            for (OaSignedPdfService.PdfTextPlacement textPlacement : textPlacements)
            {
                ObjectNode textNode = OBJECT_MAPPER.createObjectNode();
                textNode.put("field", textPlacement.field());
                textNode.put("pageNumber", textPlacement.pageNumber());
                textNode.put("x", textPlacement.x());
                textNode.put("y", textPlacement.y());
                textNode.put("width", textPlacement.width());
                textNode.put("height", textPlacement.height());
                textNode.put("fontSize", textPlacement.fontSize());
                canonicalTextOverlays.add(textNode);
            }
            canonical.set("textOverlays", canonicalTextOverlays);
        }
        return new PlacementPolicy(writeJson(canonical, label),
                PdfImagePlacement.composite(placements), templateType, templateVersion,
                reviewPdfHash, placementConfigVersion, profileId, expectedBodyPageCount,
                signingSequencePolicy, true, false, textPlacements);
    }

    private List<OaSignedPdfService.PdfTextPlacement> parseTextOverlays(JsonNode node,
            List<OaSignedPdfService.PdfProtectedRegion> protectedRegions, String label)
    {
        if (node == null || node.isNull())
        {
            return List.of();
        }
        if (!node.isArray())
        {
            throw new ServiceException(label + "textOverlays必须是数组");
        }
        List<OaSignedPdfService.PdfTextPlacement> result = new ArrayList<>();
        for (JsonNode value : node)
        {
            if (!(value instanceof ObjectNode textNode))
            {
                throw new ServiceException(label + "文本定位必须是JSON对象");
            }
            rejectUnknownFields(textNode, TEXT_OVERLAY_FIELDS, label);
            String field = requiredText(textNode, "field", label);
            if (!TEXT_OVERLAY_NAMES.contains(field))
            {
                throw new ServiceException(label + "文本字段不支持：" + field);
            }
            int pageNumber = requiredPositiveInteger(textNode, "pageNumber", label);
            float x = requiredFiniteFloat(textNode, "x", label, false);
            float y = requiredFiniteFloat(textNode, "y", label, false);
            float width = requiredFiniteFloat(textNode, "width", label, true);
            float height = requiredFiniteFloat(textNode, "height", label, true);
            float fontSize = requiredFiniteFloat(textNode, "fontSize", label, true);
            result.add(new OaSignedPdfService.PdfTextPlacement(field, pageNumber, x, y,
                    width, height, fontSize, protectedRegions));
        }
        return List.copyOf(result);
    }

    private List<OaSignedPdfService.PdfProtectedRegion> parseProtectedRegions(JsonNode node,
            String label)
    {
        if (node == null || node.isNull())
        {
            return List.of();
        }
        if (!node.isArray())
        {
            throw new ServiceException(label + "protectedRegions必须是数组");
        }
        List<OaSignedPdfService.PdfProtectedRegion> regions = new ArrayList<>();
        for (JsonNode value : node)
        {
            if (!(value instanceof ObjectNode regionNode))
            {
                throw new ServiceException(label + "保护区域必须是JSON对象");
            }
            rejectUnknownFields(regionNode, PROTECTED_REGION_FIELDS, label);
            int pageNumber = requiredPositiveInteger(regionNode, "pageNumber", label);
            float x = requiredFiniteFloat(regionNode, "x", label, false);
            float y = requiredFiniteFloat(regionNode, "y", label, false);
            float width = requiredFiniteFloat(regionNode, "width", label, true);
            float height = requiredFiniteFloat(regionNode, "height", label, true);
            regions.add(new OaSignedPdfService.PdfProtectedRegion(pageNumber, x, y,
                    width, height));
        }
        return List.copyOf(regions);
    }

    private String requiredText(ObjectNode root, String field, String label)
    {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || StringUtils.isBlank(value.textValue()))
        {
            throw new ServiceException(label + "缺少" + field);
        }
        return value.textValue().trim();
    }

    private String optionalSigningSequencePolicy(ObjectNode root, String label)
    {
        JsonNode value = root.get("signingSequencePolicy");
        if (value == null || value.isNull())
        {
            // Backward compatibility for already frozen MULTI snapshots. New exact-v7
            // snapshots always include this field and therefore never infer COMPANY_FIRST.
            return null;
        }
        if (!value.isTextual() || !SIGNING_SEQUENCE_POLICIES.contains(value.textValue()))
        {
            throw new ServiceException(label + "的signingSequencePolicy不受支持");
        }
        return value.textValue();
    }

    private String signingSequencePolicy(String signingSequence)
    {
        String normalized = OaSignSigningSequence.normalize(signingSequence);
        return OaSignSigningSequence.COMPANY_FIRST.equals(normalized)
                ? COMPANY_FIRST_DISPLAY_EXPORT : SIGNATURE_FIRST_BODY_PLACEMENT;
    }

    private String requiredHash(ObjectNode root, String field, String label)
    {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()
                || !value.textValue().matches("[0-9a-fA-F]{64}"))
        {
            throw new ServiceException(label + "的" + field + "必须是SHA-256");
        }
        return value.textValue().toLowerCase();
    }

    private void rejectUnknownFields(ObjectNode root, Set<String> allowed, String label)
    {
        Iterator<String> fields = root.fieldNames();
        while (fields.hasNext())
        {
            String field = fields.next();
            if (!allowed.contains(field))
            {
                throw new ServiceException(label + "包含未知字段：" + field);
            }
        }
    }

    private int requiredPositiveInteger(ObjectNode root, String field, String label)
    {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() <= 0)
        {
            throw new ServiceException(label + "的" + field + "必须是大于0的整数");
        }
        return value.intValue();
    }

    private float requiredFiniteFloat(ObjectNode root, String field, String label, boolean positive)
    {
        JsonNode value = root.get(field);
        double number = value == null || !value.isNumber() ? Double.NaN : value.doubleValue();
        float converted = (float) number;
        boolean underflowedToZero = number != 0D && converted == 0F;
        if (!Double.isFinite(number) || !Float.isFinite(converted) || underflowedToZero
                || (positive ? converted <= 0 : converted < 0))
        {
            throw new ServiceException(label + "的" + field
                    + (positive ? "必须是大于0的有限数值" : "必须是非负有限数值"));
        }
        return converted;
    }

    private String writeJson(ObjectNode root, String label)
    {
        try
        {
            return OBJECT_MAPPER.writeValueAsString(root);
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException(label + "无法序列化").setDetailMessage(e.getOriginalMessage());
        }
    }

    private boolean yesNo(String value, String label)
    {
        if ("Y".equals(value))
        {
            return true;
        }
        if ("N".equals(value))
        {
            return false;
        }
        throw new ServiceException(label + "快照不合法");
    }

    private void requireDocumentPolicy(OaSignPackageDocument document)
    {
        if (document == null || document.getDocumentId() == null)
        {
            throw new ServiceException("签约文档不存在");
        }
        requireDocumentPolicySnapshot(document);
    }

    private void requireDocumentPolicySnapshot(OaSignPackageDocument document)
    {
        if (document == null)
        {
            throw new ServiceException("签约文档不存在");
        }
        if (!SNAPSHOT_V1.equals(document.getDocumentPolicyMode())
                && !LEGACY_APPEND_ONLY.equals(document.getDocumentPolicyMode()))
        {
            throw new ServiceException("文档签章策略快照缺失");
        }
    }

    private record PlacementPolicy(String canonicalJson, PdfImagePlacement placement,
            String templateType, String templateVersion, String reviewPdfHash,
            String placementConfigVersion, String profileId, Integer expectedBodyPageCount,
            String signingSequencePolicy, boolean multi, boolean appended,
            List<OaSignedPdfService.PdfTextPlacement> textPlacements) {}

    public record GeneratedPlacementPolicies(String signaturePositionJson,
            String companySealPositionJson, String profileId) {}

    public record HistoricalExportPolicy(String configVersion, String profileId,
            PdfImagePlacement signaturePlacement, PdfImagePlacement sealPlacement,
            List<OaSignedPdfService.PdfTextPlacement> textPlacements,
            int expectedBodyPageCount, String repairId,
            String legalRepresentativeFallback, String legalRepresentativeEvidence,
            List<String> requiredVisibleDocumentTypes, List<String> repairFields,
            String expectedArchiveFinalRootHash) {}
}
