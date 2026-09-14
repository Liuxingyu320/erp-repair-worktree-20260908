package com.erp.oa.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaLaborContract;
import com.erp.oa.domain.OaLaborContractEvent;
import com.erp.oa.domain.OaLaborContractTemplate;
import com.erp.oa.domain.dto.OaLaborContractSignRequest;
import com.erp.oa.domain.dto.OaLaborContractVerifyRequest;
import com.erp.oa.domain.dto.OaLaborContractVerifyResult;
import com.erp.oa.mapper.OaCompanySealConfigMapper;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaLaborContractEventMapper;
import com.erp.oa.mapper.OaLaborContractMapper;
import com.erp.oa.mapper.OaLaborContractTemplateMapper;
import com.erp.oa.service.IOaLaborContractService;
import com.erp.system.api.model.LoginUser;

@Service
public class OaLaborContractServiceImpl implements IOaLaborContractService
{
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.security.service.LegacySalaryWriteGuard legacySalaryWrites =
            new com.erp.common.security.service.LegacySalaryWriteGuard();

    static final String STATUS_DRAFT = "draft";
    static final String STATUS_PENDING_SIGN = "pending_sign";
    static final String STATUS_SIGNED = "signed";
    static final String STATUS_VOIDED = "voided";
    static final String STATUS_EXPIRED = "expired";
    private static final String PROVIDER_INTERNAL = "internal";
    private static final String SIGN_CONFIRMATION_TEXT = "本人确认签署";
    private static final Pattern MAINLAND_MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern MAINLAND_ID_CARD_PATTERN = Pattern.compile(
            "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");
    private static final int[] ID_CARD_CHECK_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Autowired
    private OaLaborContractMapper contractMapper;

    @Autowired
    private OaLaborContractTemplateMapper templateMapper;

    @Autowired
    private OaLaborContractEventMapper eventMapper;

    @Autowired
    private OaCompanySealConfigMapper sealConfigMapper;

    @Autowired
    private OaDeptScopeMapper deptScopeMapper;

    @Autowired
    private ShopScopeService shopScopeService;

    @Autowired
    private OaLaborContractDocumentService documentService;

    @Override
    public List<OaLaborContractTemplate> selectTemplateList(OaLaborContractTemplate template)
    {
        return templateMapper.selectOaLaborContractTemplateList(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLaborContractTemplate saveTemplate(OaLaborContractTemplate template)
    {
        legacySalaryWrites.reject();
        documentService.assertTemplateContainsRequiredPlaceholders(template.getTemplateFileUrl());
        if (StringUtils.isBlank(template.getStatus()))
        {
            template.setStatus("0");
        }
        if (StringUtils.isBlank(template.getBuiltIn()))
        {
            template.setBuiltIn("N");
        }
        if (template.getTemplateId() == null)
        {
            template.setCreateBy(SecurityUtils.getUsername());
            templateMapper.insertOaLaborContractTemplate(template);
        }
        else
        {
            template.setUpdateBy(SecurityUtils.getUsername());
            templateMapper.updateOaLaborContractTemplate(template);
        }
        return templateMapper.selectOaLaborContractTemplateById(template.getTemplateId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaCompanySealConfig saveSealConfig(OaCompanySealConfig config)
    {
        legacySalaryWrites.reject();
        if (StringUtils.isBlank(config.getStatus()))
        {
            config.setStatus("0");
        }
        if (config.getSealId() == null)
        {
            config.setCreateBy(SecurityUtils.getUsername());
            sealConfigMapper.insertOaCompanySealConfig(config);
        }
        else
        {
            config.setUpdateBy(SecurityUtils.getUsername());
            sealConfigMapper.updateOaCompanySealConfig(config);
        }
        return sealConfigMapper.selectActiveSealConfig();
    }

    @Override
    public OaCompanySealConfig getActiveSealConfig()
    {
        return sealConfigMapper.selectActiveSealConfig();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLaborContract saveContract(OaLaborContract contract, Long selectedShopDeptId)
    {
        legacySalaryWrites.reject();
        if (contract.getContractId() == null)
        {
            validateIdentityFields(contract);
            Long shopDeptId = shopScopeService.resolveRequiredShopDept(
                    selectedShopDeptId != null && selectedShopDeptId > 0 ? selectedShopDeptId : contract.getShopDeptId());
            validateTemplate(contract.getTemplateId());
            validateEmployeeIdentity(contract, shopDeptId, true);
            contract.setShopDeptId(shopDeptId);
            if (StringUtils.isBlank(contract.getStatus()))
            {
                contract.setStatus(STATUS_DRAFT);
            }
            if (!STATUS_DRAFT.equals(contract.getStatus()))
            {
                throw new ServiceException("新建合同只能保存为草稿");
            }
            if (StringUtils.isBlank(contract.getSignProvider()))
            {
                contract.setSignProvider(PROVIDER_INTERNAL);
            }
            contract.setCreateBy(SecurityUtils.getUsername());
            contract.setTotalSalary(calculateTotalSalary(contract));
            contractMapper.insertOaLaborContract(contract);
            recordEvent(contract.getContractId(), "create", "创建劳动合同草稿", null, null, null);
        }
        else
        {
            OaLaborContract db = assertAndGetScopedContractForUpdate(contract.getContractId(), selectedShopDeptId);
            if (STATUS_SIGNED.equals(db.getStatus()))
            {
                throw new ServiceException("已签署合同不允许修改");
            }
            if (!STATUS_DRAFT.equals(db.getStatus()))
            {
                throw new ServiceException("仅草稿合同允许修改");
            }
            validateIdentityFields(contract);
            if (contract.getTemplateId() != null)
            {
                validateTemplate(contract.getTemplateId());
            }
            validateEmployeeIdentity(contract, db.getShopDeptId(), true);
            contract.setStatus(STATUS_DRAFT);
            contract.setShopDeptId(db.getShopDeptId());
            contract.setTotalSalary(calculateTotalSalary(contract));
            contract.setUpdateBy(SecurityUtils.getUsername());
            requireOneChanged(contractMapper.updateOaLaborContract(contract));
            recordEvent(contract.getContractId(), "update", "更新劳动合同草稿", null, null, null);
        }
        if (contract.isIdentityManuallyVerified())
        {
            recordEvent(contract.getContractId(), "identity_verify",
                    "经办人核对员工本人证件：" + StringUtils.trim(contract.getIdentityVerificationNote())
                            .replaceAll("[1-9]\\d{16}[0-9Xx]", "[证件号已隐藏]"),
                    null, null, identityFingerprint(contract));
        }
        return contractMapper.selectOaLaborContractById(contract.getContractId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLaborContract sendContract(Long contractId, Long selectedShopDeptId)
    {
        legacySalaryWrites.reject();
        OaLaborContract contract = assertAndGetScopedContractForUpdate(contractId, selectedShopDeptId);
        if (!STATUS_DRAFT.equals(contract.getStatus()))
        {
            throw new ServiceException("仅草稿合同可以发送签署");
        }
        validateIdentityFields(contract);
        validateEmployeeIdentity(contract, contract.getShopDeptId(), false);
        OaLaborContractTemplate template = requireActiveTemplate(contract.getTemplateId());
        OaCompanySealConfig sealConfig = requireActiveSeal();
        OaLaborContractTemplate frozenTemplate = documentService.freezeTemplateSnapshot(contract, template);
        OaCompanySealConfig frozenSealConfig = documentService.freezeSealSnapshot(contract, sealConfig);
        String documentVersion = generateDocumentVersion(contractId);
        String templateHash = documentService.calculateFileUrlSha256(frozenTemplate.getTemplateFileUrl());
        String sealHash = documentService.calculateFileUrlSha256(frozenSealConfig.getSealImageUrl());
        contract.setDocumentVersion(documentVersion);
        contract.setTemplateFileHash(templateHash);
        contract.setSealImageHash(sealHash);
        GeneratedContractFile generated = documentService.generatePreview(contract, frozenTemplate, frozenSealConfig);

        OaLaborContract update = new OaLaborContract();
        update.setContractId(contractId);
        update.setStatus(STATUS_PENDING_SIGN);
        update.setPreviewFileUrl(generated.getDocxUrl());
        update.setPdfFileUrl(generated.getPdfUrl());
        update.setDocumentVersion(documentVersion);
        update.setPreviewFileHash(generated.getSha256());
        update.setTemplateFileHash(templateHash);
        update.setSealImageHash(sealHash);
        update.setContractFileHash(generated.getSha256());
        update.setSentTime(new Date());
        update.setUpdateBy(SecurityUtils.getUsername());
        requireOneChanged(contractMapper.updateOaLaborContract(update));
        recordEvent(contractId, "send", "发送员工签署并冻结合同版本", null, null, documentVersion,
                generated.getSha256());
        return contractMapper.selectOaLaborContractById(contractId);
    }

    @Override
    public List<OaLaborContract> selectContractList(OaLaborContract contract, Long selectedShopDeptId)
    {
        shopScopeService.appendShopScope(contract, selectedShopDeptId);
        List<OaLaborContract> list = contractMapper.selectOaLaborContractList(contract);
        for (OaLaborContract row : list)
        {
            sanitizeContractListRow(row);
        }
        return list;
    }

    @Override
    public OaLaborContract getContractDetail(Long contractId, Long selectedShopDeptId)
    {
        OaLaborContract contract = assertAndGetScopedContract(contractId, selectedShopDeptId);
        contract.setEvents(eventMapper.selectEventsByContractId(contractId));
        return contract;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLaborContract voidContract(Long contractId, Long selectedShopDeptId)
    {
        OaLaborContract contract = assertAndGetScopedContractForUpdate(contractId, selectedShopDeptId);
        if (STATUS_VOIDED.equals(contract.getStatus()))
        {
            return contract;
        }
        if (STATUS_SIGNED.equals(contract.getStatus()))
        {
            throw new ServiceException("已签署合同不能直接作废，请走解除/终止流程");
        }
        if (!STATUS_DRAFT.equals(contract.getStatus()) && !STATUS_PENDING_SIGN.equals(contract.getStatus()))
        {
            throw new ServiceException("当前合同状态不允许作废，请刷新后核对");
        }
        OaLaborContract update = new OaLaborContract();
        update.setContractId(contractId);
        update.setStatus(STATUS_VOIDED);
        update.setVoidedTime(new Date());
        update.setUpdateBy(SecurityUtils.getUsername());
        requireOneChanged(contractMapper.markVoided(update, contract.getStatus()));
        recordEvent(contractId, "void", "作废劳动合同", null, null, contract.getContractFileHash());
        return contractMapper.selectOaLaborContractById(contractId);
    }

    @Override
    public OaLaborContract getPreviewContract(Long contractId, Long selectedShopDeptId)
    {
        OaLaborContract contract = getContractDetail(contractId, selectedShopDeptId);
        if (StringUtils.isBlank(contract.getPreviewFileUrl()) && StringUtils.isBlank(contract.getArchiveFileUrl()))
        {
            throw new ServiceException("合同预览文件尚未生成");
        }
        return contract;
    }

    @Override
    public List<OaLaborContract> selectMyContracts(OaLaborContract contract)
    {
        contract.setEmployeeId(SecurityUtils.getUserId());
        return contractMapper.selectMyOaLaborContractList(contract);
    }

    @Override
    public OaLaborContract getMyContractDetail(Long contractId)
    {
        OaLaborContract contract = requireContract(contractId);
        assertEmployeeOwner(contract);
        contract.setEvents(eventMapper.selectEventsByContractId(contractId));
        return contract;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLaborContract signContract(Long contractId, OaLaborContractSignRequest request)
    {
        OaLaborContract contract = requireContractForUpdate(contractId);
        assertEmployeeOwner(contract);
        if (!STATUS_PENDING_SIGN.equals(contract.getStatus()))
        {
            throw new ServiceException("当前合同不在待签署状态");
        }
        if (request == null || !request.isConfirmed())
        {
            throw new ServiceException("请先确认已阅读并同意合同内容");
        }
        if (StringUtils.isBlank(request.getSignatureDataUrl()))
        {
            throw new ServiceException("签名不能为空");
        }
        if (!SIGN_CONFIRMATION_TEXT.equals(request.getSignConfirmText() == null ? "" : request.getSignConfirmText().trim()))
        {
            throw new ServiceException("请输入“" + SIGN_CONFIRMATION_TEXT + "”完成二次确认");
        }
        assertFrozenDocumentMatches(contract, request);
        OaLaborContractTemplate template = documentService.loadFrozenTemplateSnapshot(contract,
                requireTemplateForEvidence(contract.getTemplateId()));
        OaCompanySealConfig sealConfig = documentService.loadFrozenSealSnapshot(contract);
        Date signedTime = new Date();
        contract.setSignerIp(request.getSignerIp());
        contract.setSignerUserAgent(request.getSignerUserAgent());
        contract.setSignedTime(signedTime);
        return documentService.withSignedArchiveAttempt(contract, () -> {
            GeneratedContractFile generated = documentService.generateSignedArchive(contract, template, sealConfig, request);

            OaLaborContract update = new OaLaborContract();
            update.setContractId(contractId);
            update.setStatus(STATUS_SIGNED);
            update.setArchiveFileUrl(generated.getDocxUrl());
            update.setPdfFileUrl(generated.getPdfUrl());
            update.setSignatureFileUrl(generated.getSignatureFileUrl());
            update.setArchiveFileHash(generated.getSha256());
            update.setCertificateFileUrl(generated.getCertificateFileUrl());
            update.setCertificateFileHash(generated.getCertificateSha256());
            update.setContractFileHash(generated.getSha256());
            update.setSignerIp(request.getSignerIp());
            update.setSignerUserAgent(request.getSignerUserAgent());
            update.setSignedTime(signedTime);
            update.setUpdateBy(SecurityUtils.getUsername());
            requireOneChanged(contractMapper.markSigned(update, contract.getDocumentVersion(), contract.getPreviewFileHash()));
            recordEvent(contractId, "sign", "员工完成线上签署", request.getSignerIp(), request.getSignerUserAgent(),
                    contract.getDocumentVersion(), generated.getSha256());
            return contractMapper.selectOaLaborContractById(contractId);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLaborContractVerifyResult verifyContractHash(OaLaborContractVerifyRequest request)
    {
        return verifyContractHash(request, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaLaborContractVerifyResult verifyContractHash(OaLaborContractVerifyRequest request, Long selectedShopDeptId)
    {
        if (request == null || StringUtils.isBlank(request.getHash()))
        {
            throw new ServiceException("文件哈希不能为空");
        }
        String hash = request.getHash().trim().toLowerCase();
        OaLaborContract contract = contractMapper.selectOaLaborContractByEvidenceHash(hash);
        if (contract == null)
        {
            return OaLaborContractVerifyResult.unmatched(hash);
        }
        if (!isHashVerificationVisible(contract, selectedShopDeptId))
        {
            return OaLaborContractVerifyResult.unmatched(hash);
        }
        OaLaborContractVerifyResult result = new OaLaborContractVerifyResult();
        result.setMatched(true);
        result.setContractId(contract.getContractId());
        result.setContractNo(contract.getContractNo());
        result.setEmployeeName(contract.getEmployeeName());
        result.setSignedTime(contract.getSignedTime());
        result.setDocumentVersion(contract.getDocumentVersion());
        result.setHash(hash);
        result.setFileKind(resolveEvidenceKind(contract, hash));
        recordEvent(contract.getContractId(), "verify", "校验合同文件哈希", null, null,
                contract.getDocumentVersion(), hash);
        return result;
    }

    @Override
    public void downloadContractFile(Long contractId, String kind, Long selectedShopDeptId, HttpServletResponse response)
            throws IOException
    {
        OaLaborContract contract = resolveDownloadContract(contractId, selectedShopDeptId);
        Path file = documentService.resolveContractFile(contract.getContractId(), kind);
        if (!Files.exists(file) || !Files.isRegularFile(file))
        {
            throw new ServiceException("合同文件不存在");
        }
        response.setContentType(contentType(kind));
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"");
        Files.copy(file, response.getOutputStream());
        recordEvent(contractId, "download", "下载劳动合同文件: " + kind, null, null,
                contract.getDocumentVersion(), evidenceHashForKind(contract, kind));
    }

    private OaLaborContract requireContract(Long contractId)
    {
        OaLaborContract contract = contractMapper.selectOaLaborContractById(contractId);
        if (contract == null)
        {
            throw new ServiceException("劳动合同不存在");
        }
        return contract;
    }

    private OaLaborContract requireContractForUpdate(Long contractId)
    {
        try
        {
            OaLaborContract contract = contractMapper.selectOaLaborContractByIdForUpdate(contractId);
            if (contract == null) throw new ServiceException("劳动合同不存在");
            return contract;
        }
        catch (org.springframework.dao.PessimisticLockingFailureException e)
        {
            throw new ServiceException("合同正在处理中，请稍后刷新并重试原操作");
        }
    }

    private OaLaborContract assertAndGetScopedContractForUpdate(Long contractId, Long selectedShopDeptId)
    {
        return assertContractScope(requireContractForUpdate(contractId), selectedShopDeptId);
    }

    private void requireOneChanged(int changed)
    {
        if (changed != 1) throw new ServiceException("合同状态或文档版本已变化，请刷新后核对");
    }

    private OaLaborContract assertAndGetScopedContract(Long contractId, Long selectedShopDeptId)
    {
        return assertContractScope(requireContract(contractId), selectedShopDeptId);
    }

    private OaLaborContract assertContractScope(OaLaborContract contract, Long selectedShopDeptId)
    {
        if (SecurityUtils.isAdmin())
        {
            return contract;
        }
        Long rootDeptId = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        int visible = deptScopeMapper.countDeptInScope(rootDeptId, contract.getShopDeptId());
        if (visible <= 0)
        {
            throw new ServiceException("无权访问该店铺合同");
        }
        return contract;
    }

    private void assertEmployeeOwner(OaLaborContract contract)
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null || contract.getEmployeeId() == null || !userId.equals(contract.getEmployeeId()))
        {
            throw new ServiceException("只能签署本人的劳动合同");
        }
    }

    private OaLaborContract resolveDownloadContract(Long contractId, Long selectedShopDeptId)
    {
        OaLaborContract contract = requireContract(contractId);
        if (isEmployeeOwner(contract))
        {
            return contract;
        }
        if (!hasContractQueryPermission())
        {
            throw new ServiceException("无权下载该合同文件");
        }
        return assertAndGetScopedContract(contractId, selectedShopDeptId);
    }

    private boolean isEmployeeOwner(OaLaborContract contract)
    {
        Long userId = SecurityUtils.getUserId();
        return userId != null && contract.getEmployeeId() != null && userId.equals(contract.getEmployeeId());
    }

    private boolean hasContractQueryPermission()
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Set<String> permissions = loginUser == null ? null : loginUser.getPermissions();
        return permissions != null && (permissions.contains("oa:laborContract:query") || permissions.contains("*:*:*"));
    }

    private void assertFrozenDocumentMatches(OaLaborContract contract, OaLaborContractSignRequest request)
    {
        if (StringUtils.isBlank(contract.getDocumentVersion()) || StringUtils.isBlank(contract.getPreviewFileHash()))
        {
            throw new ServiceException("合同签署版本未冻结，请重新发送合同");
        }
        if (!contract.getDocumentVersion().equals(request.getDocumentVersion())
                || !contract.getPreviewFileHash().equalsIgnoreCase(request.getPreviewFileHash()))
        {
            throw new ServiceException("合同文件已更新，请重新打开合同后签署");
        }
        String storedHash = documentService.calculateStoredFileSha256(contract.getContractId(), "preview-pdf");
        if (!contract.getPreviewFileHash().equalsIgnoreCase(storedHash))
        {
            throw new ServiceException("合同文件已更新，请重新打开合同后签署");
        }
    }

    private void validateTemplate(Long templateId)
    {
        if (templateId == null || templateMapper.selectOaLaborContractTemplateById(templateId) == null)
        {
            throw new ServiceException("合同模板不存在");
        }
    }

    private OaLaborContractTemplate requireActiveTemplate(Long templateId)
    {
        OaLaborContractTemplate template = templateMapper.selectOaLaborContractTemplateById(templateId);
        if (template == null)
        {
            throw new ServiceException("合同模板不存在");
        }
        if (!"0".equals(template.getStatus()))
        {
            throw new ServiceException("合同模板已停用");
        }
        return template;
    }

    private OaLaborContractTemplate requireTemplateForEvidence(Long templateId)
    {
        OaLaborContractTemplate template = templateMapper.selectOaLaborContractTemplateById(templateId);
        if (template == null)
        {
            throw new ServiceException("合同模板不存在");
        }
        return template;
    }

    private OaCompanySealConfig requireActiveSeal()
    {
        OaCompanySealConfig sealConfig = sealConfigMapper.selectActiveSealConfig();
        if (sealConfig == null || !"0".equals(sealConfig.getStatus()))
        {
            throw new ServiceException("请先配置启用状态的企业章");
        }
        return sealConfig;
    }

    private boolean isHashVerificationVisible(OaLaborContract contract, Long selectedShopDeptId)
    {
        if (contract == null)
        {
            return false;
        }
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        try
        {
            Long rootDeptId = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
            return deptScopeMapper.countDeptInScope(rootDeptId, contract.getShopDeptId()) > 0;
        }
        catch (ServiceException e)
        {
            return false;
        }
    }

    private void validateIdentityFields(OaLaborContract contract)
    {
        if (contract == null)
        {
            return;
        }
        String idCard = StringUtils.trim(contract.getEmployeeIdCard());
        if (StringUtils.isBlank(idCard) || !isValidMainlandIdCard(idCard))
        {
            throw new ServiceException("身份证号格式不正确");
        }
        String phone = StringUtils.trim(contract.getEmployeePhone());
        if (StringUtils.isBlank(phone) || !MAINLAND_MOBILE_PATTERN.matcher(phone).matches())
        {
            throw new ServiceException("手机号格式不正确");
        }
        contract.setEmployeeIdCard(idCard.toUpperCase());
        contract.setEmployeePhone(phone);
    }

    private boolean isValidMainlandIdCard(String idCard)
    {
        if (!MAINLAND_ID_CARD_PATTERN.matcher(idCard).matches())
        {
            return false;
        }
        String normalized = idCard.toUpperCase();
        int sum = 0;
        for (int i = 0; i < ID_CARD_CHECK_WEIGHTS.length; i++)
        {
            sum += Character.digit(normalized.charAt(i), 10) * ID_CARD_CHECK_WEIGHTS[i];
        }
        return ID_CARD_CHECK_CODES[sum % 11] == normalized.charAt(17);
    }

    private void validateEmployeeIdentity(OaLaborContract contract, Long shopDeptId, boolean saving)
    {
        OaLaborContract identity = contractMapper.selectScopedEmployeeIdentity(contract.getEmployeeId(), shopDeptId);
        if (identity == null)
        {
            throw new ServiceException("员工不存在或不在当前合同组织范围内");
        }
        if (saving)
        {
            contract.setEmployeeDeptId(identity.getEmployeeDeptId());
            contract.setEmployeeName(identity.getEmployeeName());
        }
        else if (!java.util.Objects.equals(contract.getEmployeeName(), identity.getEmployeeName()))
        {
            throw new ServiceException("员工档案姓名已变化，请核对并保存草稿后再发送");
        }
        String authoritativeId = StringUtils.trim(identity.getEmployeeIdCard());
        if (StringUtils.isNotBlank(authoritativeId))
        {
            if (!authoritativeId.equalsIgnoreCase(contract.getEmployeeIdCard()))
            {
                throw new ServiceException("合同身份证与员工档案不一致，请核对员工后保存");
            }
            contract.setIdentityManuallyVerified(false);
            return;
        }
        if (saving)
        {
            String note = StringUtils.trim(contract.getIdentityVerificationNote());
            if (!contract.isIdentityManuallyVerified() || note == null || note.length() < 4 || note.length() > 200)
            {
                throw new ServiceException("员工档案未登记身份证，请核对本人证件并填写身份核对说明");
            }
            return;
        }
        String fingerprint = identityFingerprint(contract);
        boolean verified = eventMapper.selectEventsByContractId(contract.getContractId()).stream()
                .anyMatch(event -> "identity_verify".equals(event.getEventType())
                        && fingerprint.equals(event.getFileHash()));
        if (!verified)
        {
            throw new ServiceException("员工档案未登记身份证，请返回草稿完成身份核对并保存后再发送");
        }
    }

    private String identityFingerprint(OaLaborContract contract)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    (contract.getEmployeeId() + ":" + contract.getEmployeeIdCard()).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private void sanitizeContractListRow(OaLaborContract row)
    {
        if (row == null)
        {
            return;
        }
        row.setEmployeeIdCard(null);
        row.setEmployeePhone(null);
        row.setPreviewFileUrl(null);
        row.setArchiveFileUrl(null);
        row.setPdfFileUrl(null);
        row.setSignatureFileUrl(null);
        row.setSignerIp(null);
        row.setSignerUserAgent(null);
    }

    private BigDecimal calculateTotalSalary(OaLaborContract contract)
    {
        if (contract.getTotalSalary() != null)
        {
            return contract.getTotalSalary();
        }
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(nvl(contract.getBaseSalary()));
        total = total.add(nvl(contract.getManagementAllowance()));
        total = total.add(nvl(contract.getOvertimePay()));
        total = total.add(nvl(contract.getRewardAllowance()));
        total = total.add(nvl(contract.getFullAttendanceBonus()));
        total = total.add(nvl(contract.getSocialSubsidy()));
        total = total.add(nvl(contract.getCommuteSubsidy()));
        return total;
    }

    private BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void recordEvent(Long contractId, String eventType, String summary, String ip, String userAgent,
            String fileHash)
    {
        recordEvent(contractId, eventType, summary, ip, userAgent, null, fileHash);
    }

    private void recordEvent(Long contractId, String eventType, String summary, String ip, String userAgent,
            String documentVersion, String documentHash)
    {
        OaLaborContractEvent event = new OaLaborContractEvent();
        Date createTime = new Date();
        String prevEventHash = eventMapper.selectLatestEventHashByContractId(contractId);
        event.setContractId(contractId);
        event.setEventType(eventType);
        event.setEventSummary(summary);
        event.setOperatorId(SecurityUtils.getUserId());
        event.setOperatorName(SecurityUtils.getUsername());
        event.setClientIp(ip);
        event.setUserAgent(userAgent);
        event.setFileHash(documentHash);
        event.setDocumentVersion(documentVersion);
        event.setDocumentHash(documentHash);
        event.setPrevEventHash(prevEventHash);
        event.setRequestId(UUID.randomUUID().toString().replace("-", ""));
        event.setCreateTime(createTime);
        event.setCreateBy(SecurityUtils.getUsername());
        event.setEventHash(calculateEventHash(event));
        if (eventMapper.insertOaLaborContractEvent(event) != 1)
        {
            throw new ServiceException("合同事件记录未成功保存，请核对后重试");
        }
    }

    private String contentType(String kind)
    {
        if ("signature".equals(kind))
        {
            return "image/png";
        }
        if ("preview-pdf".equals(kind) || "archive-pdf".equals(kind) || "certificate".equals(kind))
        {
            return "application/pdf";
        }
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    private String generateDocumentVersion(Long contractId)
    {
        return "LC-" + contractId + "-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    private String resolveEvidenceKind(OaLaborContract contract, String hash)
    {
        if (equalsHash(hash, contract.getPreviewFileHash()))
        {
            return "preview";
        }
        if (equalsHash(hash, contract.getArchiveFileHash()) || equalsHash(hash, contract.getContractFileHash()))
        {
            return "archive";
        }
        if (equalsHash(hash, contract.getCertificateFileHash()))
        {
            return "certificate";
        }
        return "unknown";
    }

    private String evidenceHashForKind(OaLaborContract contract, String kind)
    {
        if ("preview".equals(kind) || "preview-pdf".equals(kind))
        {
            return contract.getPreviewFileHash();
        }
        if ("archive".equals(kind) || "archive-pdf".equals(kind))
        {
            return StringUtils.isNotBlank(contract.getArchiveFileHash())
                    ? contract.getArchiveFileHash() : contract.getContractFileHash();
        }
        if ("certificate".equals(kind))
        {
            return contract.getCertificateFileHash();
        }
        return null;
    }

    private boolean equalsHash(String left, String right)
    {
        return StringUtils.isNotBlank(left) && StringUtils.isNotBlank(right) && left.equalsIgnoreCase(right);
    }

    private String calculateEventHash(OaLaborContractEvent event)
    {
        String payload = safe(event.getContractId()) + "|" + safe(event.getEventType()) + "|"
                + safe(event.getOperatorId()) + "|" + safe(event.getClientIp()) + "|"
                + safe(event.getUserAgent()) + "|" + safe(event.getDocumentVersion()) + "|"
                + safe(event.getDocumentHash()) + "|" + safe(event.getPrevEventHash()) + "|"
                + (event.getCreateTime() == null ? "" : event.getCreateTime().getTime()) + "|"
                + safe(event.getRequestId());
        return sha256(payload);
    }

    private String sha256(String text)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ServiceException("计算审计事件哈希失败");
        }
    }

    private String safe(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }
}
