package com.erp.oa.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Immutable, auditable placement ledger for labor-contract display exports.
 *
 * <p>The ledger deliberately lives in a resource instead of Java constants. A future template
 * is accepted only when its type, version and immutable source hash all match. Historical repair
 * additionally binds the generated review, immutable archive and final evidence hashes. Missing
 * historical representative names are resolved only through a separately versioned legal-entity
 * and effective-time ledger; a package number is never the general placement allow-list.
 */
final class OaSignLaborPlacementProfileRegistry
{
    static final String RESOURCE =
            "/oa/sign/labor-contract-placement-profiles-v1.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> HISTORICAL_REPAIR_FIELDS = Set.of(
            "attachmentHandbookMark", "archiveEvidenceNotice",
            "companyLegalRepresentative", "companySealPosition",
            "employeeSignaturePositions");

    private final Ledger ledger;
    private final Map<String, PlacementProfile> profilesById;

    OaSignLaborPlacementProfileRegistry()
    {
        this(readLedger());
    }

    OaSignLaborPlacementProfileRegistry(Ledger ledger)
    {
        this.ledger = requireLedger(ledger);
        Map<String, PlacementProfile> profiles = new HashMap<>();
        for (PlacementProfile profile : ledger.profiles())
        {
            requireText(profile.profileId(), "签章策略profileId");
            if (profiles.put(profile.profileId(), profile) != null)
            {
                throw new ServiceException("劳动合同签章策略存在重复profileId："
                        + profile.profileId());
            }
            validateProfile(profile);
        }
        this.profilesById = Map.copyOf(profiles);
        validateLedgerReferences();
    }

    String configVersion()
    {
        return ledger.configVersion();
    }

    List<PlacementProfile> futureProfiles(String templateType, String templateVersion,
            String templateSourceHash)
    {
        return ledger.futureBindings().stream()
                .filter(binding -> same(binding.templateType(), templateType))
                .filter(binding -> same(binding.templateVersion(), templateVersion))
                .filter(binding -> sameHash(binding.templateSourceHash(), templateSourceHash))
                .map(FutureBinding::profileId)
                .filter(StringUtils::isNotBlank)
                .map(profilesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    String futureResolverId(String templateType, String templateVersion,
            String templateSourceHash)
    {
        List<String> matches = ledger.futureBindings().stream()
                .filter(binding -> same(binding.templateType(), templateType))
                .filter(binding -> same(binding.templateVersion(), templateVersion))
                .filter(binding -> sameHash(binding.templateSourceHash(), templateSourceHash))
                .map(FutureBinding::resolverId)
                .filter(StringUtils::isNotBlank)
                .toList();
        if (matches.size() > 1)
        {
            throw new ServiceException("劳动合同未来模板动态解析器绑定不唯一");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    BlockedTemplate blockedTemplate(String templateType, String templateVersion,
            String templateSourceHash)
    {
        return ledger.blockedTemplates().stream()
                .filter(blocked -> same(blocked.templateType(), templateType))
                .filter(blocked -> same(blocked.templateVersion(), templateVersion))
                .filter(blocked -> sameHash(blocked.templateSourceHash(), templateSourceHash))
                .findFirst().orElse(null);
    }

    HistoricalArchiveEvidence historicalArchiveEvidence(String packageNo,
            OaSignPackageDocumentSnapshot document, String templateSourceHash,
            String archivePdfHash)
    {
        List<HistoricalArchiveEvidence> packageEvidence = ledger.historicalArchiveEvidence()
                .stream().filter(evidence -> same(evidence.packageNo(), packageNo)).toList();
        if (packageEvidence.isEmpty())
        {
            return null;
        }
        List<HistoricalArchiveEvidence> matches = packageEvidence.stream()
                .filter(evidence -> same(evidence.templateType(), document.templateType()))
                .filter(evidence -> same(evidence.templateVersion(), document.templateVersion()))
                .filter(evidence -> sameHash(evidence.templateSourceHash(), templateSourceHash))
                .filter(evidence -> same(evidence.documentPolicyMode(),
                        document.documentPolicyMode()))
                .filter(evidence -> sameJson(evidence.signaturePositionJson(),
                        document.signaturePositionJson()))
                .filter(evidence -> sameJson(evidence.companySealPositionJson(),
                        document.companySealPositionJson()))
                .filter(evidence -> sameHash(evidence.reviewPdfHash(), document.reviewPdfHash()))
                .filter(evidence -> sameHash(evidence.archivePdfHash(), archivePdfHash))
                .filter(evidence -> sameHash(evidence.finalArchivePdfHash(), archivePdfHash))
                .filter(evidence -> sameHash(evidence.finalContentHash(),
                        document.finalContentHash()))
                .toList();
        if (matches.size() != 1)
        {
            throw new ServiceException("黄金历史合同快照与审计证据不一致");
        }
        return matches.get(0);
    }

    HistoricalRepresentativeRepair historicalRepresentativeRepair(Long legalEntityId,
            String legalEntityName, Date signedTime)
    {
        if (legalEntityId == null || StringUtils.isBlank(legalEntityName) || signedTime == null)
        {
            return null;
        }
        Instant signedAt = signedTime.toInstant();
        List<HistoricalRepresentativeRepair> matches = ledger.historicalRepresentativeRepairs()
                .stream()
                .filter(repair -> legalEntityId.equals(repair.legalEntityId()))
                .filter(repair -> same(repair.legalEntityName(), legalEntityName))
                .filter(repair -> !signedAt.isBefore(parseInstant(repair.effectiveFrom(),
                        "历史代表生效时间")))
                .filter(repair -> StringUtils.isBlank(repair.effectiveUntil())
                        || signedAt.isBefore(parseInstant(repair.effectiveUntil(),
                                "历史代表失效时间")))
                .toList();
        if (matches.size() > 1)
        {
            throw new ServiceException("历史甲方代表修复配置存在重叠有效期");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    PlacementProfile profile(String profileId)
    {
        PlacementProfile profile = profilesById.get(profileId);
        if (profile == null)
        {
            throw new ServiceException("劳动合同签章策略配置引用未知profileId：" + profileId);
        }
        return profile;
    }

    private void validateLedgerReferences()
    {
        Set<String> futureKeys = new HashSet<>();
        for (FutureBinding binding : ledger.futureBindings())
        {
            requireText(binding.templateType(), "未来模板类型");
            requireText(binding.templateVersion(), "未来模板版本");
            requireSha(binding.templateSourceHash(), "未来模板源文件指纹");
            boolean fixedProfile = StringUtils.isNotBlank(binding.profileId());
            boolean dynamicResolver = StringUtils.isNotBlank(binding.resolverId());
            if (fixedProfile == dynamicResolver)
            {
                throw new ServiceException("劳动合同未来模板必须且只能绑定固定profile或动态resolver");
            }
            if (fixedProfile)
            {
                profile(binding.profileId());
            }
            if (dynamicResolver
                    && !OaSignLaborAnchorPlacementResolver.PROFILE_ID.equals(
                            binding.resolverId()))
            {
                throw new ServiceException("劳动合同未来模板引用未知动态resolver："
                        + binding.resolverId());
            }
            String key = normalized(binding.templateType()) + "|"
                    + StringUtils.trim(binding.templateVersion()) + "|"
                    + normalizedHash(binding.templateSourceHash());
            if (!futureKeys.add(key))
            {
                throw new ServiceException("劳动合同未来模板绑定重复：" + key);
            }
        }
        Set<String> blockedKeys = new HashSet<>();
        for (BlockedTemplate blocked : ledger.blockedTemplates())
        {
            requireText(blocked.templateType(), "阻断模板类型");
            requireText(blocked.templateVersion(), "阻断模板版本");
            requireSha(blocked.templateSourceHash(), "阻断模板源文件指纹");
            requireText(blocked.reason(), "阻断原因");
            String key = normalized(blocked.templateType()) + "|"
                    + StringUtils.trim(blocked.templateVersion()) + "|"
                    + normalizedHash(blocked.templateSourceHash());
            if (!blockedKeys.add(key))
            {
                throw new ServiceException("劳动合同阻断模板绑定重复：" + key);
            }
            if (futureKeys.contains(key))
            {
                throw new ServiceException("劳动合同模板不能同时配置为未来可用与阻断：" + key);
            }
        }
        for (HistoricalArchiveEvidence evidence : ledger.historicalArchiveEvidence())
        {
            requireText(evidence.evidenceId(), "历史归档证据ID");
            requireText(evidence.packageNo(), "历史包编号");
            requireText(evidence.templateType(), "历史模板类型");
            requireText(evidence.templateVersion(), "历史模板版本");
            requireSha(evidence.templateSourceHash(), "历史模板源文件指纹");
            requireText(evidence.documentPolicyMode(), "历史文档策略模式");
            requireText(evidence.signaturePositionJson(), "历史签名定位快照");
            requireText(evidence.companySealPositionJson(), "历史印章定位快照");
            requireSha(evidence.reviewPdfHash(), "历史review PDF指纹");
            requireSha(evidence.archivePdfHash(), "历史archive PDF指纹");
            requireSha(evidence.finalArchivePdfHash(), "历史最终归档PDF指纹");
            if (!sameHash(evidence.archivePdfHash(), evidence.finalArchivePdfHash()))
            {
                throw new ServiceException("历史归档证据的archive哈希不一致");
            }
            requireSha(evidence.finalContentHash(), "历史稳定正文指纹");
            requireSha(evidence.finalRootHash(), "历史最终集合指纹");
            requireSha(evidence.signatureSampleHash(), "历史签名样本指纹");
            requireSha(evidence.sealImageHash(), "历史印章样本指纹");
            if (StringUtils.isBlank(evidence.legalRepresentativeSnapshot()))
            {
                requireText(evidence.legalRepresentativeRepairId(), "历史代表修复配置ID");
            }
            requireText(evidence.profileId(), "历史归档证据profileId");
            profile(evidence.profileId());
            if (evidence.requiredVisibleDocumentTypes() == null
                    || !evidence.requiredVisibleDocumentTypes().contains(
                            evidence.templateType()))
            {
                throw new ServiceException("历史归档证据必须记录劳动合同及同包可见文档类型");
            }
            requireRepairFields(evidence.repairFields());
        }
        for (HistoricalRepresentativeRepair repair : ledger.historicalRepresentativeRepairs())
        {
            requireText(repair.repairId(), "历史代表修复ID");
            if (repair.legalEntityId() == null || repair.legalEntityId() <= 0)
            {
                throw new ServiceException("历史代表修复法律主体ID不合法");
            }
            requireText(repair.legalEntityName(), "历史代表修复法律主体名称");
            requireText(repair.legalRepresentative(), "历史甲方代表修复值");
            requireText(repair.evidenceSource(), "历史甲方代表证据来源");
            requireSha(repair.evidenceSha256(), "历史甲方代表证据指纹");
            validateRepresentativeEvidenceResource(repair);
            Instant from = parseInstant(repair.effectiveFrom(), "历史代表生效时间");
            if (StringUtils.isNotBlank(repair.effectiveUntil())
                    && !parseInstant(repair.effectiveUntil(), "历史代表失效时间")
                            .isAfter(from))
            {
                throw new ServiceException("历史代表修复有效期不合法");
            }
        }
    }

    private static void requireRepairFields(List<String> repairFields)
    {
        if (repairFields == null || repairFields.isEmpty()
                || repairFields.stream().anyMatch(StringUtils::isBlank))
        {
            throw new ServiceException("历史归档证据必须声明实际修复字段");
        }
        Set<String> normalized = new HashSet<>();
        for (String field : repairFields)
        {
            String value = StringUtils.trim(field);
            if (!HISTORICAL_REPAIR_FIELDS.contains(value))
            {
                throw new ServiceException("历史归档证据包含未知修复字段：" + value);
            }
            if (!normalized.add(value))
            {
                throw new ServiceException("历史归档证据包含重复修复字段：" + value);
            }
        }
    }

    private static void validateProfile(PlacementProfile profile)
    {
        if (profile.expectedBodyPageCount() == null || profile.expectedBodyPageCount() <= 0)
        {
            throw new ServiceException("劳动合同签章策略正文页数必须大于0：" + profile.profileId());
        }
        if (profile.signaturePlacements() == null || profile.signaturePlacements().size() != 4
                || profile.sealPlacements() == null || profile.sealPlacements().size() != 1)
        {
            throw new ServiceException("劳动合同签章策略必须包含四签一章：" + profile.profileId());
        }
        if (profile.protectedRegions() == null || profile.protectedRegions().isEmpty())
        {
            throw new ServiceException("劳动合同签章策略必须显式声明保护区域：" + profile.profileId());
        }
        if (profile.anchors() == null || profile.anchors().isEmpty())
        {
            throw new ServiceException("劳动合同签章策略必须声明局部锚点：" + profile.profileId());
        }
        profile.signaturePlacements().forEach(value -> validateRect(value,
                profile.expectedBodyPageCount(), "签名位置"));
        profile.sealPlacements().forEach(value -> validateRect(value,
                profile.expectedBodyPageCount(), "印章位置"));
        profile.protectedRegions().forEach(value -> validateRect(value,
                profile.expectedBodyPageCount(), "保护区域"));
        if (profile.textOverlays() != null)
        {
            profile.textOverlays().forEach(value -> validateRect(value,
                    profile.expectedBodyPageCount(), "文本位置"));
        }
        profile.anchors().forEach(value -> {
            validateRect(value, profile.expectedBodyPageCount(), "局部锚点");
            if (value.requiredTexts() == null || value.requiredTexts().isEmpty()
                    || value.requiredTexts().stream().anyMatch(StringUtils::isBlank))
            {
                throw new ServiceException("劳动合同局部锚点缺少必需文本：" + value.name());
            }
        });
        validatePlacementAnchorBindings(profile);
    }

    private static void validatePlacementAnchorBindings(PlacementProfile profile)
    {
        Map<String, AnchorRect> anchors = new HashMap<>();
        for (AnchorRect anchor : profile.anchors())
        {
            requireText(anchor.name(), "劳动合同局部锚点名称");
            if (anchors.put(anchor.name(), anchor) != null)
            {
                throw new ServiceException("劳动合同局部锚点名称重复：" + anchor.name());
            }
        }
        List<String> signatureAnchors = List.of(
                "primary-signing-row",
                "attachment-signature-row",
                "dormitory-signature-row",
                "position-confirmation-row");
        for (int index = 0; index < signatureAnchors.size(); index++)
        {
            assertPlacementBoundToAnchor(profile.signaturePlacements().get(index),
                    anchors.get(signatureAnchors.get(index)), signatureAnchors.get(index));
        }
        assertPlacementBoundToAnchor(profile.sealPlacements().get(0),
                anchors.get("primary-signing-row"), "primary-signing-row");
    }

    private static void assertPlacementBoundToAnchor(Rect placement, AnchorRect anchor,
            String anchorName)
    {
        if (anchor == null || !placement.pageNumber().equals(anchor.pageNumber()))
        {
            throw new ServiceException("劳动合同签章位置未绑定命名锚点：" + anchorName);
        }
        float centerX = placement.x() + placement.width() / 2F;
        float centerY = placement.y() + placement.height() / 2F;
        boolean centerInside = centerX >= anchor.x()
                && centerX <= anchor.x() + anchor.width()
                && centerY >= anchor.y()
                && centerY <= anchor.y() + anchor.height();
        float overlapWidth = Math.max(0F, Math.min(placement.x() + placement.width(),
                anchor.x() + anchor.width()) - Math.max(placement.x(), anchor.x()));
        float overlapHeight = Math.max(0F, Math.min(placement.y() + placement.height(),
                anchor.y() + anchor.height()) - Math.max(placement.y(), anchor.y()));
        float overlapRatio = overlapWidth * overlapHeight
                / (placement.width() * placement.height());
        if (!centerInside || overlapRatio < 0.25F)
        {
            throw new ServiceException("劳动合同签章位置偏离命名锚点：" + anchorName);
        }
    }

    private static void validateRect(Rect value, int pageCount, String label)
    {
        if (value == null || value.pageNumber() == null || value.pageNumber() <= 0
                || value.pageNumber() > pageCount || value.x() == null || value.y() == null
                || value.width() == null || value.height() == null
                || !Float.isFinite(value.x()) || !Float.isFinite(value.y())
                || !Float.isFinite(value.width()) || !Float.isFinite(value.height())
                || value.x() < 0 || value.y() < 0 || value.width() <= 0 || value.height() <= 0)
        {
            throw new ServiceException("劳动合同" + label + "不合法");
        }
    }

    private static Ledger readLedger()
    {
        try (InputStream input = OaSignLaborPlacementProfileRegistry.class
                .getResourceAsStream(RESOURCE))
        {
            if (input == null)
            {
                throw new ServiceException("劳动合同签章策略资源不存在：" + RESOURCE);
            }
            return OBJECT_MAPPER.readValue(input, Ledger.class);
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (IOException exception)
        {
            throw new ServiceException("劳动合同签章策略资源无法读取")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private static Ledger requireLedger(Ledger value)
    {
        if (value == null || StringUtils.isBlank(value.configVersion())
                || value.profiles() == null || value.futureBindings() == null
                || value.blockedTemplates() == null
                || value.historicalArchiveEvidence() == null
                || value.historicalRepresentativeRepairs() == null)
        {
            throw new ServiceException("劳动合同签章策略资源不完整");
        }
        return value;
    }

    private static void validateRepresentativeEvidenceResource(
            HistoricalRepresentativeRepair repair)
    {
        if (!repair.evidenceSource().startsWith("/oa/sign/evidence/"))
        {
            throw new ServiceException("历史甲方代表证据必须是受控classpath资源");
        }
        try (InputStream input = OaSignLaborPlacementProfileRegistry.class
                .getResourceAsStream(repair.evidenceSource()))
        {
            if (input == null)
            {
                throw new ServiceException("历史甲方代表证据资源不存在："
                        + repair.evidenceSource());
            }
            byte[] content = input.readAllBytes();
            String actualHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
            if (!sameHash(actualHash, repair.evidenceSha256()))
            {
                throw new ServiceException("历史甲方代表证据资源指纹不一致");
            }
            RepresentativeEvidence evidence = OBJECT_MAPPER.readValue(content,
                    RepresentativeEvidence.class);
            if (evidence.schemaVersion() != 1
                    || !same(evidence.evidenceId(), repair.repairId())
                    || !repair.legalEntityId().equals(evidence.legalEntityId())
                    || !same(evidence.legalEntityName(), repair.legalEntityName())
                    || !same(evidence.legalRepresentative(), repair.legalRepresentative())
                    || !same(evidence.effectiveFrom(), repair.effectiveFrom())
                    || !same(evidence.effectiveUntil(), repair.effectiveUntil()))
            {
                throw new ServiceException("历史甲方代表证据内容与修复配置不一致");
            }
            requireText(evidence.sourceDescription(), "历史甲方代表证据来源说明");
            requireSha(evidence.sourceArtifactSha256(), "历史甲方代表原始证据指纹");
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (IOException | NoSuchAlgorithmException exception)
        {
            throw new ServiceException("历史甲方代表证据资源无法校验")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private static void requireText(String value, String label)
    {
        if (StringUtils.isBlank(value))
        {
            throw new ServiceException(label + "不能为空");
        }
    }

    private static void requireSha(String value, String label)
    {
        if (StringUtils.isBlank(value) || !value.matches("[0-9a-fA-F]{64}"))
        {
            throw new ServiceException(label + "必须是SHA-256");
        }
    }

    private static boolean same(String left, String right)
    {
        return normalized(left).equals(normalized(right));
    }

    private static boolean sameHash(String left, String right)
    {
        return normalizedHash(left).equals(normalizedHash(right));
    }

    private static boolean sameJson(String left, String right)
    {
        try
        {
            return OBJECT_MAPPER.readTree(left).equals(OBJECT_MAPPER.readTree(right));
        }
        catch (IOException exception)
        {
            throw new ServiceException("历史定位快照JSON不合法")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private static String normalized(String value)
    {
        return StringUtils.trim(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizedHash(String value)
    {
        return StringUtils.trim(value).toLowerCase(Locale.ROOT);
    }

    private static Instant parseInstant(String value, String label)
    {
        requireText(value, label);
        try
        {
            return Instant.parse(value);
        }
        catch (DateTimeParseException exception)
        {
            throw new ServiceException(label + "必须是ISO-8601 UTC时间")
                    .setDetailMessage(exception.getMessage());
        }
    }

    record Ledger(String configVersion, List<PlacementProfile> profiles,
            List<FutureBinding> futureBindings, List<BlockedTemplate> blockedTemplates,
            List<HistoricalArchiveEvidence> historicalArchiveEvidence,
            List<HistoricalRepresentativeRepair> historicalRepresentativeRepairs) {}

    record FutureBinding(String templateType, String templateVersion,
            String templateSourceHash, String profileId, String resolverId) {}

    record BlockedTemplate(String templateType, String templateVersion,
            String templateSourceHash, String reason, String replacementVersion) {}

    record PlacementProfile(String profileId, Integer expectedBodyPageCount,
            List<PlacementRect> signaturePlacements, List<PlacementRect> sealPlacements,
            List<PlacementRect> protectedRegions, List<TextRect> textOverlays,
            List<AnchorRect> anchors) {}

    interface Rect
    {
        Integer pageNumber();
        Float x();
        Float y();
        Float width();
        Float height();
    }

    record PlacementRect(Integer pageNumber, Float x, Float y, Float width,
            Float height) implements Rect {}

    record TextRect(String field, Integer pageNumber, Float x, Float y, Float width,
            Float height, Float fontSize) implements Rect {}

    record AnchorRect(String name, Integer pageNumber, Float x, Float y, Float width,
            Float height, List<String> requiredTexts) implements Rect {}

    record HistoricalArchiveEvidence(String evidenceId, String profileId, String packageNo,
            String templateType, String templateVersion, String templateSourceHash,
            String documentPolicyMode, String signaturePositionJson,
            String companySealPositionJson, String reviewPdfHash, String archivePdfHash,
            String finalArchivePdfHash, String finalContentHash, String finalRootHash,
            String signatureSampleHash, String sealImageHash,
            String legalRepresentativeSnapshot, String legalRepresentativeRepairId,
            List<String> requiredVisibleDocumentTypes, List<String> repairFields) {}

    record OaSignPackageDocumentSnapshot(String templateType, String templateVersion,
            String documentPolicyMode, String signaturePositionJson,
            String companySealPositionJson, String reviewPdfHash, String finalContentHash) {}

    record HistoricalRepresentativeRepair(String repairId, Long legalEntityId,
            String legalEntityName, String effectiveFrom, String effectiveUntil,
            String legalRepresentative, String evidenceSource, String evidenceSha256) {}

    record RepresentativeEvidence(int schemaVersion, String evidenceId, Long legalEntityId,
            String legalEntityName, String legalRepresentative, String effectiveFrom,
            String effectiveUntil, String sourceDescription, String sourceArtifactSha256) {}
}
