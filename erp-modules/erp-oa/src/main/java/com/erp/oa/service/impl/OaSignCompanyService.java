package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.vo.OaLegalEntityCandidate;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.mapper.OaCompanySealConfigMapper;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.system.api.domain.SysLegalEntity;

/** 公司主体自动识别及按公司隔离的合同印章配置。 */
@Service
public class OaSignCompanyService
{
    public static final String MATCH_POLICY_VERSION = "COMPANY_MATCH_V1";
    private static final int POLICY_FINGERPRINT_LENGTH = 12;

    @Value("${oa.sign.company-match.auto-threshold:0.6000}")
    private BigDecimal autoMatchThreshold = new BigDecimal("0.6000");

    @Value("${oa.sign.company-match.leading-margin:0.0800}")
    private BigDecimal leadingMargin = new BigDecimal("0.0800");

    @Value("${oa.sign.company-match.max-candidates:5}")
    private int maxCandidates = 5;

    private final OaDeptScopeMapper deptScopeMapper;
    private final OaCompanySealConfigMapper sealMapper;
    private final OaSignDocumentService documentService;

    public OaSignCompanyService(OaDeptScopeMapper deptScopeMapper,
            OaCompanySealConfigMapper sealMapper, OaSignDocumentService documentService)
    {
        this.deptScopeMapper = deptScopeMapper;
        this.sealMapper = sealMapper;
        this.documentService = documentService;
    }

    public OaSignCompanyOptions options(OaSignPackage signPackage)
    {
        if (signPackage == null)
        {
            throw new ServiceException("签约包不存在");
        }
        OaLegalEntityCandidate candidate = resolveCandidate(signPackage);
        Long selectedEntityId = signPackage.getLegalEntityIdSnapshot() != null
                ? signPackage.getLegalEntityIdSnapshot()
                : candidate == null ? null : candidate.getLegalEntityId();
        List<OaCompanySealConfig> seals = selectedEntityId == null ? List.of()
                : sealMapper.selectSealsByLegalEntity(selectedEntityId, true);
        if (seals == null) seals = List.of();
        seals = seals.stream().filter(this::isContractSeal).toList();
        OaSignCompanyOptions options = new OaSignCompanyOptions();
        options.setAutomaticCandidate(candidate);
        options.setLegalEntities(deptScopeMapper.selectActiveLegalEntities());
        options.setSeals(seals);
        if (selectedEntityId != null)
        {
            OaCompanySealConfig recommended = recommendContractSeal(selectedEntityId).getSelectedSeal();
            options.setRecommendedSealId(recommended == null ? null : recommended.getSealId());
        }
        return options;
    }

    public OaLegalEntityCandidate resolveCandidate(OaSignPackage signPackage)
    {
        Long deptId = signPackage == null ? null : signPackage.getDeptIdSnapshot();
        if (deptId == null && signPackage != null)
        {
            deptId = signPackage.getShopDeptId();
        }
        return deptId == null ? null : deptScopeMapper.selectLegalEntityCandidate(deptId);
    }

    /**
     * Identifies both the matching algorithm and the configuration that can
     * change an automatic company decision. A preview created under a different
     * value must be recalculated before generation.
     */
    public String currentMatchPolicyVersion()
    {
        String policy = MATCH_POLICY_VERSION + "|"
                + normalizedPolicyDecimal(autoMatchThreshold) + "|"
                + normalizedPolicyDecimal(leadingMargin) + "|"
                + Math.max(1, maxCandidates);
        try
        {
            String fingerprint = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(policy.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return MATCH_POLICY_VERSION + "_"
                    + fingerprint.substring(0, POLICY_FINGERPRINT_LENGTH);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private String normalizedPolicyDecimal(BigDecimal value)
    {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * Deterministically ranks active legal entities from the three existing Excel facts.
     * The result never writes an alias and only auto-selects a clearly leading candidate.
     */
    public CompanyMatchResult matchExcelCompany(String companyName,
            String legalRepresentative, String registeredAddress, Long employeeDeptId)
    {
        OaLegalEntityCandidate departmentCandidate = employeeDeptId == null ? null
                : deptScopeMapper.selectLegalEntityCandidate(employeeDeptId);
        List<SysLegalEntity> active = deptScopeMapper.selectActiveLegalEntities();
        List<CompanyMatchCandidate> ranked = new ArrayList<>();
        if (active != null)
        {
            for (SysLegalEntity entity : active)
            {
                if (entity == null || entity.getLegalEntityId() == null) continue;
                ranked.add(new CompanyMatchCandidate(entity,
                        companyScore(companyName, legalRepresentative, registeredAddress, entity),
                        contractMasterMissingFields(entity)));
            }
        }
        ranked.sort(Comparator.comparing(CompanyMatchCandidate::getScore).reversed()
                .thenComparing(value -> value.getEntity().getLegalEntityId()));
        CompanyMatchCandidate first = ranked.isEmpty() ? null : ranked.get(0);
        CompanyMatchCandidate second = ranked.size() < 2 ? null : ranked.get(1);
        boolean hasInput = StringUtils.isNotBlank(companyName)
                || StringUtils.isNotBlank(legalRepresentative)
                || StringUtils.isNotBlank(registeredAddress);
        String mode;
        SysLegalEntity selected = null;
        if (!hasInput)
        {
            mode = "HR_REQUIRED_NO_INPUT";
        }
        else if (first == null)
        {
            mode = "HR_REQUIRED_NO_CANDIDATE";
        }
        else if (first.getScore().compareTo(autoMatchThreshold) < 0)
        {
            mode = "HR_REQUIRED_LOW_CONFIDENCE";
        }
        else if (second != null && first.getScore().subtract(second.getScore())
                .compareTo(leadingMargin) < 0)
        {
            mode = "HR_REQUIRED_AMBIGUOUS";
        }
        else
        {
            mode = "EXCEL_AUTO";
            selected = first.getEntity();
        }
        List<CompanyMatchCandidate> candidates = ranked.stream()
                .limit(Math.max(1, maxCandidates)).toList();
        boolean departmentConflict = selected != null && departmentCandidate != null
                && !Objects.equals(selected.getLegalEntityId(),
                        departmentCandidate.getLegalEntityId());
        return new CompanyMatchResult(selected, candidates, departmentCandidate, mode,
                first == null ? BigDecimal.ZERO.setScale(4) : first.getScore(),
                second == null ? BigDecimal.ZERO.setScale(4) : second.getScore(),
                autoMatchThreshold.setScale(4, RoundingMode.HALF_UP),
                leadingMargin.setScale(4, RoundingMode.HALF_UP), departmentConflict);
    }

    public SysLegalEntity requireContractReadyEntity(Long legalEntityId)
    {
        SysLegalEntity entity = requireActiveEntity(legalEntityId);
        List<String> missing = contractMasterMissingFields(entity);
        if (!missing.isEmpty())
        {
            throw new ServiceException("所选公司主数据不完整：" + String.join("、", missing));
        }
        return entity;
    }

    public List<String> contractMasterMissingFields(SysLegalEntity entity)
    {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        if (entity == null || entity.getLegalEntityId() == null) missing.add("公司主数据ID");
        if (entity == null || StringUtils.isBlank(entity.getLegalEntityCode())) missing.add("公司编码");
        if (entity == null || invalidLegalText(entity.getLegalEntityName())) missing.add("公司法定全称");
        if (entity == null || StringUtils.isBlank(entity.getUnifiedSocialCreditCode())) missing.add("统一社会信用代码");
        if (entity == null || invalidLegalText(entity.getRegisteredAddress())) missing.add("注册地址");
        if (entity == null || invalidLegalText(entity.getLegalRepresentative())) missing.add("法定代表人");
        return new ArrayList<>(missing);
    }

    /** Recommends a seal only when the active contract-seal set is unambiguous. */
    public SealRecommendation recommendContractSeal(Long legalEntityId)
    {
        requireActiveEntity(legalEntityId);
        List<OaCompanySealConfig> seals = sealMapper.selectSealsByLegalEntity(legalEntityId, true);
        if (seals == null) seals = List.of();
        seals = seals.stream().filter(this::isContractSeal).toList();
        List<OaCompanySealConfig> defaults = seals.stream()
                .filter(value -> "Y".equalsIgnoreCase(value.getIsDefault())).toList();
        OaCompanySealConfig selected = null;
        String mode;
        if (seals.isEmpty())
        {
            mode = "HR_REQUIRED_NO_ACTIVE_SEAL";
        }
        else if (defaults.size() == 1)
        {
            selected = defaults.get(0);
            mode = "UNIQUE_DEFAULT";
        }
        else if (defaults.size() > 1)
        {
            mode = "HR_REQUIRED_MULTIPLE_DEFAULT_SEALS";
        }
        else if (seals.size() == 1)
        {
            selected = seals.get(0);
            mode = "UNIQUE_ACTIVE";
        }
        else
        {
            mode = "HR_REQUIRED_MULTIPLE_ACTIVE_SEALS";
        }
        return new SealRecommendation(selected, List.copyOf(seals), mode);
    }

    public OaCompanySealConfig requireContractReadySeal(Long sealId, Long legalEntityId)
    {
        return requireActiveSeal(sealId, legalEntityId);
    }

    public SysLegalEntity requireActiveEntity(Long legalEntityId)
    {
        SysLegalEntity entity = legalEntityId == null ? null
                : deptScopeMapper.selectActiveLegalEntityById(legalEntityId);
        if (entity == null)
        {
            throw new ServiceException("所选公司不存在或已停用");
        }
        return entity;
    }

    public OaCompanySealConfig requireActiveSeal(Long sealId, Long legalEntityId)
    {
        OaCompanySealConfig seal = sealId == null ? null : sealMapper.selectSealById(sealId);
        if (seal == null || !"0".equals(seal.getStatus())
                || !Objects.equals(legalEntityId, seal.getLegalEntityId()))
        {
            throw new ServiceException("所选印章不存在、已停用或不属于当前公司");
        }
        long now = System.currentTimeMillis();
        if (seal.getValidFrom() != null && seal.getValidFrom().getTime() > now
                || seal.getValidTo() != null && seal.getValidTo().getTime() < now)
        {
            throw new ServiceException("所选印章不在有效期内");
        }
        requireCompleteRegisteredContractSeal(seal);
        byte[] image = documentService.readConfiguredFileBytes(seal.getSealImageUrl());
        String actualHash = sha256(image);
        if (!actualHash.equalsIgnoreCase(seal.getSealImageHash()))
        {
            throw new ServiceException("印章图片与登记时不一致，请重新配置印章");
        }
        OaSignImageValidator.requireCompanySealExtension(image);
        seal.setSealImageHash(actualHash);
        return seal;
    }

    public List<OaCompanySealConfig> listSeals(Long legalEntityId, boolean activeOnly)
    {
        requireActiveEntity(legalEntityId);
        return sealMapper.selectSealsByLegalEntity(legalEntityId, activeOnly);
    }

    @Transactional(rollbackFor = Exception.class)
    public OaCompanySealConfig saveSeal(OaCompanySealConfig seal)
    {
        if (seal == null || seal.getLegalEntityId() == null)
        {
            throw new ServiceException("请选择印章所属公司");
        }
        requireActiveEntity(seal.getLegalEntityId());
        if (StringUtils.isBlank(seal.getSealName()) || StringUtils.isBlank(seal.getSealImageUrl()))
        {
            throw new ServiceException("印章名称和印章图片不能为空");
        }
        if (seal.getValidFrom() != null && seal.getValidTo() != null
                && seal.getValidFrom().after(seal.getValidTo()))
        {
            throw new ServiceException("印章有效期开始时间不能晚于结束时间");
        }
        OaCompanySealConfig existing = seal.getSealId() == null ? null
                : sealMapper.selectSealById(seal.getSealId());
        if (existing != null && !seal.getLegalEntityId().equals(existing.getLegalEntityId()))
        {
            throw new ServiceException("已登记印章不能变更所属公司，请新建印章");
        }
        seal.setSealType("CONTRACT");
        seal.setStatus(StringUtils.isBlank(seal.getStatus()) ? "0" : seal.getStatus());
        seal.setIsDefault("Y".equalsIgnoreCase(seal.getIsDefault()) ? "Y" : "N");
        if (seal.getSealId() == null && sealMapper.selectSealsByLegalEntity(
                seal.getLegalEntityId(), false).isEmpty())
        {
            seal.setIsDefault("Y");
        }
        if (StringUtils.isBlank(seal.getSealCode()))
        {
            seal.setSealCode(("YZ-" + seal.getLegalEntityId() + "-"
                    + Long.toString(System.currentTimeMillis(), 36)).toUpperCase(Locale.ROOT));
        }
        byte[] sealImage = documentService.readConfiguredFileBytes(seal.getSealImageUrl());
        OaSignImageValidator.requireCompanySealExtension(sealImage);
        seal.setSealImageHash(sha256(sealImage));
        if ("Y".equals(seal.getIsDefault()))
        {
            sealMapper.clearDefaultSeal(seal.getLegalEntityId(), seal.getSealId());
        }
        if (seal.getSealId() == null)
        {
            seal.setCreateBy(SecurityUtils.getUsername());
            if (sealMapper.insertOaCompanySealConfig(seal) != 1)
            {
                throw new ServiceException("印章保存失败");
            }
        }
        else
        {
            seal.setUpdateBy(SecurityUtils.getUsername());
            if (sealMapper.updateOaCompanySealConfig(seal) != 1)
            {
                throw new ServiceException("印章保存失败");
            }
        }
        return sealMapper.selectSealById(seal.getSealId());
    }

    private BigDecimal companyScore(String excelCompanyName, String excelRepresentative,
            String excelAddress, SysLegalEntity entity)
    {
        double name = similarity(excelCompanyName, entity.getLegalEntityName());
        double representative = similarity(excelRepresentative,
                entity.getLegalRepresentative());
        double address = similarity(excelAddress, entity.getRegisteredAddress());
        // Company names in operational spreadsheets can differ substantially from the
        // registered full name. Representative + registered address can therefore reach the
        // conservative default threshold, while a name-only match still requires HR review.
        double score = name * 0.40d + representative * 0.25d + address * 0.35d;
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    private double similarity(String left, String right)
    {
        String first = normalizeMatchText(left);
        String second = normalizeMatchText(right);
        if (first.isEmpty() || second.isEmpty()) return 0d;
        if (first.equals(second)) return 1d;
        double containment = first.contains(second) || second.contains(first)
                ? 0.75d + 0.20d * Math.min(first.length(), second.length())
                        / Math.max(first.length(), second.length()) : 0d;
        double edit = 1d - (double) levenshtein(first, second)
                / Math.max(first.length(), second.length());
        double dice = diceCoefficient(first, second);
        return Math.max(containment, Math.max(edit, dice));
    }

    private String normalizeMatchText(String value)
    {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++)
        {
            char current = normalized.charAt(index);
            if (Character.isLetterOrDigit(current)) result.append(current);
        }
        return result.toString();
    }

    private int levenshtein(String left, String right)
    {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int row = 1; row <= left.length(); row++)
        {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++)
            {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(Math.min(previous[column] + 1,
                        current[column - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private double diceCoefficient(String left, String right)
    {
        if (left.length() < 2 || right.length() < 2) return 0d;
        List<String> remaining = new ArrayList<>();
        for (int index = 0; index < right.length() - 1; index++)
            remaining.add(right.substring(index, index + 2));
        int intersection = 0;
        for (int index = 0; index < left.length() - 1; index++)
        {
            String pair = left.substring(index, index + 2);
            int matched = remaining.indexOf(pair);
            if (matched >= 0)
            {
                intersection++;
                remaining.remove(matched);
            }
        }
        return 2d * intersection / (left.length() + right.length() - 2d);
    }

    private boolean invalidLegalText(String value)
    {
        if (StringUtils.isBlank(value)) return true;
        String normalized = value.trim();
        return normalized.matches("[0-9]+") || normalized.length() < 2;
    }

    private boolean isContractSeal(OaCompanySealConfig seal)
    {
        return seal != null && "CONTRACT".equalsIgnoreCase(StringUtils.trim(seal.getSealType()));
    }

    private void requireCompleteRegisteredContractSeal(OaCompanySealConfig seal)
    {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(seal.getSealCode())) missing.add("印章编码");
        if (StringUtils.isBlank(seal.getSealName())) missing.add("印章名称");
        if (!"CONTRACT".equalsIgnoreCase(StringUtils.trim(seal.getSealType())))
            missing.add("合同章类型");
        if (StringUtils.isBlank(seal.getSealImageUrl())) missing.add("印章图片");
        String registeredHash = StringUtils.trim(seal.getSealImageHash());
        if (registeredHash == null || !registeredHash.matches("(?i)[0-9a-f]{64}"))
            missing.add("印章图片SHA-256");
        if (!missing.isEmpty())
        {
            throw new ServiceException("所选印章主数据不完整：" + String.join("、", missing));
        }
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("文件校验算法不可用", e);
        }
    }

    public static final class CompanyMatchCandidate
    {
        private final SysLegalEntity entity;
        private final BigDecimal score;
        private final List<String> missingMasterFields;

        CompanyMatchCandidate(SysLegalEntity entity, BigDecimal score,
                List<String> missingMasterFields)
        {
            this.entity = entity;
            this.score = score;
            this.missingMasterFields = List.copyOf(missingMasterFields);
        }

        public SysLegalEntity getEntity() { return entity; }
        public BigDecimal getScore() { return score; }
        public List<String> getMissingMasterFields() { return missingMasterFields; }
        public boolean isContractReady() { return missingMasterFields.isEmpty(); }
    }

    public static final class CompanyMatchResult
    {
        private final SysLegalEntity selectedEntity;
        private final List<CompanyMatchCandidate> candidates;
        private final OaLegalEntityCandidate departmentCandidate;
        private final String mode;
        private final BigDecimal firstScore;
        private final BigDecimal secondScore;
        private final BigDecimal threshold;
        private final BigDecimal leadingMargin;
        private final boolean departmentConflict;

        CompanyMatchResult(SysLegalEntity selectedEntity,
                List<CompanyMatchCandidate> candidates,
                OaLegalEntityCandidate departmentCandidate, String mode,
                BigDecimal firstScore, BigDecimal secondScore, BigDecimal threshold,
                BigDecimal leadingMargin, boolean departmentConflict)
        {
            this.selectedEntity = selectedEntity;
            this.candidates = List.copyOf(candidates);
            this.departmentCandidate = departmentCandidate;
            this.mode = mode;
            this.firstScore = firstScore;
            this.secondScore = secondScore;
            this.threshold = threshold;
            this.leadingMargin = leadingMargin;
            this.departmentConflict = departmentConflict;
        }

        public SysLegalEntity getSelectedEntity() { return selectedEntity; }
        public List<CompanyMatchCandidate> getCandidates() { return candidates; }
        public OaLegalEntityCandidate getDepartmentCandidate() { return departmentCandidate; }
        public String getMode() { return mode; }
        public BigDecimal getFirstScore() { return firstScore; }
        public BigDecimal getSecondScore() { return secondScore; }
        public BigDecimal getThreshold() { return threshold; }
        public BigDecimal getLeadingMargin() { return leadingMargin; }
        public boolean isDepartmentConflict() { return departmentConflict; }
        public boolean isAutoSelected() { return selectedEntity != null; }
    }

    public static final class SealRecommendation
    {
        private final OaCompanySealConfig selectedSeal;
        private final List<OaCompanySealConfig> candidates;
        private final String mode;

        SealRecommendation(OaCompanySealConfig selectedSeal,
                List<OaCompanySealConfig> candidates, String mode)
        {
            this.selectedSeal = selectedSeal;
            this.candidates = candidates;
            this.mode = mode;
        }

        public OaCompanySealConfig getSelectedSeal() { return selectedSeal; }
        public List<OaCompanySealConfig> getCandidates() { return candidates; }
        public String getMode() { return mode; }
    }
}
