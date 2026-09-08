package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
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
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@DisplayName("OA劳动合同签约服务")
class OaLaborContractServiceImplTest
{
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("草稿合同发送后生成预览并进入待员工签署状态")
    void shouldSendDraftContractAndGeneratePreview()
    {
        loginAsAdmin();
        FakeContractMapper contractMapper = new FakeContractMapper(draftContract());
        FakeEventMapper eventMapper = new FakeEventMapper();
        OaLaborContractServiceImpl service = contractService(contractMapper, eventMapper);

        OaLaborContract sent = service.sendContract(100L, 201L);

        assertThat(sent.getStatus()).isEqualTo("pending_sign");
        assertThat(sent.getPreviewFileUrl()).isEqualTo("/oa/laborContract/download/100/preview");
        assertThat(sent.getPdfFileUrl()).isEqualTo("/oa/laborContract/download/100/preview-pdf");
        assertThat(sent.getDocumentVersion()).startsWith("LC-100-");
        assertThat(sent.getPreviewFileHash()).isEqualTo("preview-hash-100");
        assertThat(sent.getTemplateFileHash()).isEqualTo("template-hash-10");
        assertThat(sent.getSealImageHash()).isEqualTo("seal-hash-1");
        assertThat(sent.getContractFileHash()).isEqualTo("preview-hash-100");
        assertThat(eventMapper.events).extracting(OaLaborContractEvent::getEventType)
                .containsExactly("send");
        assertThat(eventMapper.events.get(0).getDocumentVersion()).isEqualTo(sent.getDocumentVersion());
        assertThat(eventMapper.events.get(0).getDocumentHash()).isEqualTo("preview-hash-100");
        assertThat(eventMapper.events.get(0).getPrevEventHash()).isNull();
        assertThat(eventMapper.events.get(0).getEventHash()).hasSize(64);
    }

    @Test
    @DisplayName("员工只能签署自己的待签合同并写入证据字段")
    void shouldSignOwnPendingContractAndRecordEvidence()
    {
        loginAsEmployee(88L, "seller01");
        FakeContractMapper contractMapper = new FakeContractMapper(pendingContract());
        FakeEventMapper eventMapper = new FakeEventMapper();
        OaLaborContractServiceImpl service = contractService(contractMapper, eventMapper);

        OaLaborContract signed = service.signContract(100L, signRequest());

        assertThat(signed.getStatus()).isEqualTo("signed");
        assertThat(signed.getSignatureFileUrl()).isEqualTo("/oa/laborContract/download/100/signature");
        assertThat(signed.getArchiveFileUrl()).isEqualTo("/oa/laborContract/download/100/archive");
        assertThat(signed.getPdfFileUrl()).isEqualTo("/oa/laborContract/download/100/archive-pdf");
        assertThat(signed.getArchiveFileHash()).isEqualTo("archive-hash-100");
        assertThat(signed.getCertificateFileUrl()).isEqualTo("/oa/laborContract/download/100/certificate");
        assertThat(signed.getCertificateFileHash()).isEqualTo("certificate-hash-100");
        assertThat(signed.getContractFileHash()).isEqualTo("archive-hash-100");
        assertThat(signed.getSignerIp()).isEqualTo("127.0.0.1");
        assertThat(signed.getSignerUserAgent()).isEqualTo("JUnit");
        assertThat(eventMapper.events).extracting(OaLaborContractEvent::getEventType)
                .containsExactly("sign");
        assertThat(eventMapper.events.get(0).getDocumentVersion()).isEqualTo("LC-100-20260614153022");
        assertThat(eventMapper.events.get(0).getDocumentHash()).isEqualTo("archive-hash-100");
        assertThat(eventMapper.events.get(0).getEventHash()).hasSize(64);
    }

    @Test
    @DisplayName("员工签署归档必须使用发送时冻结的模板和企业章快照")
    void shouldSignWithFrozenTemplateAndSealSnapshots()
    {
        loginAsEmployee(88L, "seller01");
        FakeContractMapper contractMapper = new FakeContractMapper(pendingContract());
        FakeEventMapper eventMapper = new FakeEventMapper();
        FakeDocumentService documentService = new FakeDocumentService();
        documentService.expectedArchiveTemplateUrl = "/private/labor-contract/100/template.docx";
        documentService.expectedArchiveSealUrl = "/private/labor-contract/100/seal.png";
        OaLaborContractServiceImpl service = contractService(contractMapper, eventMapper, documentService);

        OaLaborContract signed = service.signContract(100L, signRequest());

        assertThat(signed.getStatus()).isEqualTo("signed");
        assertThat(documentService.archiveTemplateUrl).isEqualTo(documentService.expectedArchiveTemplateUrl);
        assertThat(documentService.archiveSealUrl).isEqualTo(documentService.expectedArchiveSealUrl);
    }

    @Test
    @DisplayName("签署请求版本与冻结版本不一致时拒绝签署")
    void shouldRejectSigningWhenDocumentVersionChanged()
    {
        loginAsEmployee(88L, "seller01");
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(pendingContract()), new FakeEventMapper());
        OaLaborContractSignRequest request = signRequest();
        request.setDocumentVersion("LC-100-OLD");

        assertThatThrownBy(() -> service.signContract(100L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合同文件已更新");
    }

    @Test
    @DisplayName("签署请求预览哈希与冻结哈希不一致时拒绝签署")
    void shouldRejectSigningWhenPreviewHashChanged()
    {
        loginAsEmployee(88L, "seller01");
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(pendingContract()), new FakeEventMapper());
        OaLaborContractSignRequest request = signRequest();
        request.setPreviewFileHash("changed-preview-hash");

        assertThatThrownBy(() -> service.signContract(100L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合同文件已更新");
    }

    @Test
    @DisplayName("未输入二次确认短语时拒绝签署")
    void shouldRejectSigningWithoutIntentConfirmationText()
    {
        loginAsEmployee(88L, "seller01");
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(pendingContract()), new FakeEventMapper());
        OaLaborContractSignRequest request = signRequest();
        request.setSignConfirmText("本人已阅读");

        assertThatThrownBy(() -> service.signContract(100L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("本人确认签署");
    }

    @Test
    @DisplayName("保存合同模板时必须校验自动填充占位符")
    void shouldRejectTemplateWithoutRequiredPlaceholders()
    {
        loginAsAdmin();
        FakeDocumentService documentService = new FakeDocumentService();
        documentService.templateHasRequiredPlaceholders = false;
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(draftContract()),
                new FakeEventMapper(), documentService);
        OaLaborContractTemplate template = new OaLaborContractTemplate();
        template.setTemplateName("新版空线劳动合同");
        template.setSocialType("有社保");
        template.setTemplateFileUrl("no-placeholder.docx");

        assertThatThrownBy(() -> service.saveTemplate(template))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少合同占位符");
    }

    @Test
    @DisplayName("保存合同草稿时拒绝无效身份证和手机号")
    void shouldRejectInvalidIdentityFieldsWhenSavingContract()
    {
        loginAsAdmin();
        OaLaborContract contract = draftContract();
        contract.setContractId(null);
        contract.setEmployeePhone("12345");
        contract.setEmployeeIdCard("ABC");
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(null), new FakeEventMapper());

        assertThatThrownBy(() -> service.saveContract(contract, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("身份证号格式不正确");
    }

    @Test
    @DisplayName("已签署合同不能走普通作废入口")
    void shouldRejectVoidingSignedContract()
    {
        loginAsAdmin();
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(signedContract()), new FakeEventMapper());

        assertThatThrownBy(() -> service.voidContract(100L, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已签署合同不能直接作废");
    }

    @Test
    @DisplayName("审计事件入库时间必须使用参与哈希计算的createTime")
    void eventMapperShouldPersistCreateTimeFromEventPayload() throws Exception
    {
        String mapperXml = Files.readString(Paths.get("src/main/resources/mapper/oa/OaLaborContractEventMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(mapperXml).contains("#{createTime}");
        assertThat(mapperXml).doesNotContain("sysdate()");
    }

    @Test
    @DisplayName("哈希验签能识别归档文件和完成证明")
    void shouldVerifyContractEvidenceHash()
    {
        loginAsAdmin();
        FakeContractMapper contractMapper = new FakeContractMapper(signedContract());
        FakeEventMapper eventMapper = new FakeEventMapper();
        OaLaborContractServiceImpl service = contractService(contractMapper, eventMapper);
        OaLaborContractVerifyRequest request = new OaLaborContractVerifyRequest();
        request.setHash("certificate-hash-100");

        OaLaborContractVerifyResult result = service.verifyContractHash(request);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getContractId()).isEqualTo(100L);
        assertThat(result.getContractNo()).isEqualTo("LC20260614001");
        assertThat(result.getEmployeeName()).isEqualTo("张三");
        assertThat(result.getFileKind()).isEqualTo("certificate");
        assertThat(eventMapper.events).extracting(OaLaborContractEvent::getEventType)
                .containsExactly("verify");
        assertThat(eventMapper.events.get(0).getDocumentHash()).isEqualTo("certificate-hash-100");
    }

    @Test
    @DisplayName("哈希验签必须按当前组织范围过滤")
    void shouldFilterHashVerificationBySelectedShopScope()
    {
        loginAsEmployeeWithPermissions(99L, "hr01", "oa:laborContract:query");
        OaLaborContract contract = signedContract();
        contract.setEmployeeId(88L);
        contract.setShopDeptId(202L);
        FakeContractMapper contractMapper = new FakeContractMapper(contract);
        OaLaborContractServiceImpl service = contractService(contractMapper, new FakeEventMapper());
        OaLaborContractVerifyRequest request = new OaLaborContractVerifyRequest();
        request.setHash("archive-hash-100");

        OaLaborContractVerifyResult result = service.verifyContractHash(request, 201L);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("签约单列表不批量返回身份证、手机号、文件地址和签署环境")
    void shouldSanitizeSensitiveFieldsFromContractList()
    {
        loginAsAdmin();
        OaLaborContract contract = signedContract();
        contract.setEmployeePhone("13800000000");
        contract.setEmployeeIdCard("33010019900101001X");
        contract.setSignerIp("127.0.0.1");
        contract.setSignerUserAgent("JUnit");
        FakeContractMapper contractMapper = new FakeContractMapper(contract);
        OaLaborContractServiceImpl service = contractService(contractMapper, new FakeEventMapper());

        List<OaLaborContract> list = service.selectContractList(new OaLaborContract(), 201L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getEmployeePhone()).isNull();
        assertThat(list.get(0).getEmployeeIdCard()).isNull();
        assertThat(list.get(0).getPreviewFileUrl()).isNull();
        assertThat(list.get(0).getArchiveFileUrl()).isNull();
        assertThat(list.get(0).getPdfFileUrl()).isNull();
        assertThat(list.get(0).getSignatureFileUrl()).isNull();
        assertThat(list.get(0).getSignerIp()).isNull();
        assertThat(list.get(0).getSignerUserAgent()).isNull();
    }

    @Test
    @DisplayName("非本人不能签署员工合同")
    void shouldRejectSigningOtherEmployeeContract()
    {
        loginAsEmployee(99L, "other");
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(pendingContract()), new FakeEventMapper());

        assertThatThrownBy(() -> service.signContract(100L, signRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能签署本人的劳动合同");
    }

    @Test
    @DisplayName("已签合同不允许再次修改")
    void shouldRejectEditingSignedContract()
    {
        loginAsAdmin();
        OaLaborContract signed = pendingContract();
        signed.setStatus("signed");
        OaLaborContract update = draftContract();
        update.setContractId(100L);
        update.setEmployeeName("新姓名");
        OaLaborContractServiceImpl service = contractService(new FakeContractMapper(signed), new FakeEventMapper());

        assertThatThrownBy(() -> service.saveContract(update, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已签署合同不允许修改");
    }

    @Test
    @DisplayName("合同文件下载从私有目录写入响应")
    void shouldDownloadContractFileFromPrivateStorage() throws Exception
    {
        loginAsEmployee(88L, "seller01");
        FakeContractMapper contractMapper = new FakeContractMapper(pendingContract());
        FakeDocumentService documentService = new FakeDocumentService();
        ReflectionTestUtils.setField(documentService, "localFilePath", tempDir.toString());
        Path archive = documentService.resolveContractFile(100L, "archive");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "private archive", StandardCharsets.UTF_8);
        OaLaborContractServiceImpl service = contractService(contractMapper, new FakeEventMapper(), documentService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.downloadContractFile(100L, "archive", null, response);

        assertThat(response.getContentAsString()).isEqualTo("private archive");
        assertThat(response.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(response.getHeader("Content-Disposition")).contains("archive.docx");
    }

    @Test
    @DisplayName("非本人不能通过店铺上下文绕过权限下载合同")
    void shouldRejectNonOwnerDownloadWithoutManagementPermission() throws Exception
    {
        loginAsEmployee(99L, "other");
        FakeContractMapper contractMapper = new FakeContractMapper(pendingContract());
        FakeDocumentService documentService = new FakeDocumentService();
        ReflectionTestUtils.setField(documentService, "localFilePath", tempDir.toString());
        Path archive = documentService.resolveContractFile(100L, "archive");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "private archive", StandardCharsets.UTF_8);
        OaLaborContractServiceImpl service = contractService(contractMapper, new FakeEventMapper(), documentService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> service.downloadContractFile(100L, "archive", 201L, response))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权下载该合同文件");
    }

    private OaLaborContractServiceImpl contractService(FakeContractMapper contractMapper, FakeEventMapper eventMapper)
    {
        return contractService(contractMapper, eventMapper, new FakeDocumentService());
    }

    private OaLaborContractServiceImpl contractService(FakeContractMapper contractMapper, FakeEventMapper eventMapper,
            OaLaborContractDocumentService documentService)
    {
        OaLaborContractServiceImpl service = new OaLaborContractServiceImpl();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        ReflectionTestUtils.setField(service, "contractMapper", contractMapper);
        ReflectionTestUtils.setField(service, "templateMapper", new FakeTemplateMapper());
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "sealConfigMapper", new FakeSealConfigMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "shopScopeService", shopScopeService(deptScopeMapper));
        ReflectionTestUtils.setField(service, "documentService", documentService);
        return service;
    }

    private OaShopScopeService shopScopeService(OaDeptScopeMapper deptScopeMapper)
    {
        OaShopScopeService shopScopeService = new OaShopScopeService();
        ReflectionTestUtils.setField(shopScopeService, "deptScopeMapper", deptScopeMapper);
        return shopScopeService;
    }

    private OaLaborContract draftContract()
    {
        OaLaborContract contract = new OaLaborContract();
        contract.setContractId(100L);
        contract.setTemplateId(10L);
        contract.setEmployeeId(88L);
        contract.setEmployeeName("张三");
        contract.setEmployeePhone("13800000000");
        contract.setEmployeeIdCard("33010019900101001X");
        contract.setShopDeptId(201L);
        contract.setPostName("茶艺师");
        contract.setSocialType("有社保");
        contract.setContractStartDate("2026-05-01");
        contract.setContractEndDate("2028-04-30");
        contract.setProbationStartDate("2026-05-01");
        contract.setProbationEndDate("2026-06-30");
        contract.setBaseSalary(new BigDecimal("2660.00"));
        contract.setFullAttendanceBonus(new BigDecimal("300.00"));
        contract.setOvertimePay(new BigDecimal("800.00"));
        contract.setSocialSubsidy(BigDecimal.ZERO);
        contract.setCommuteSubsidy(new BigDecimal("200.00"));
        contract.setStatus("draft");
        return contract;
    }

    private OaLaborContract pendingContract()
    {
        OaLaborContract contract = draftContract();
        contract.setStatus("pending_sign");
        contract.setPreviewFileUrl("/profile/contracts/preview-100.docx");
        contract.setPdfFileUrl("/oa/laborContract/download/100/preview-pdf");
        contract.setDocumentVersion("LC-100-20260614153022");
        contract.setPreviewFileHash("preview-hash-100");
        contract.setTemplateFileHash("template-hash-10");
        contract.setSealImageHash("seal-hash-1");
        contract.setContractFileHash("preview-hash-100");
        return contract;
    }

    private OaLaborContract signedContract()
    {
        OaLaborContract contract = pendingContract();
        contract.setStatus("signed");
        contract.setArchiveFileUrl("/oa/laborContract/download/100/archive");
        contract.setPdfFileUrl("/oa/laborContract/download/100/archive-pdf");
        contract.setArchiveFileHash("archive-hash-100");
        contract.setCertificateFileUrl("/oa/laborContract/download/100/certificate");
        contract.setCertificateFileHash("certificate-hash-100");
        contract.setContractNo("LC20260614001");
        contract.setContractFileHash("archive-hash-100");
        return contract;
    }

    private OaLaborContractSignRequest signRequest()
    {
        OaLaborContractSignRequest request = new OaLaborContractSignRequest();
        request.setConfirmed(true);
        request.setDocumentVersion("LC-100-20260614153022");
        request.setPreviewFileHash("preview-hash-100");
        request.setSignatureDataUrl("data:image/png;base64,AAAA");
        request.setSignConfirmText("本人确认签署");
        request.setSignerIp("127.0.0.1");
        request.setSignerUserAgent("JUnit");
        return request;
    }

    private void loginAsAdmin()
    {
        LoginUser loginUser = new LoginUser();
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUserName("admin");
        user.setDeptId(201L);
        loginUser.setSysUser(user);
        loginUser.setRoles(Collections.singleton("admin"));
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private void loginAsEmployee(Long userId, String userName)
    {
        LoginUser loginUser = new LoginUser();
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setDeptId(201L);
        loginUser.setSysUser(user);
        SecurityContextHolder.setUserId(String.valueOf(userId));
        SecurityContextHolder.setUserName(userName);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private void loginAsEmployeeWithPermissions(Long userId, String userName, String... permissions)
    {
        LoginUser loginUser = new LoginUser();
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setDeptId(201L);
        loginUser.setSysUser(user);
        loginUser.setPermissions(new java.util.HashSet<>(java.util.Arrays.asList(permissions)));
        SecurityContextHolder.setUserId(String.valueOf(userId));
        SecurityContextHolder.setUserName(userName);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static class FakeContractMapper implements OaLaborContractMapper
    {
        private OaLaborContract stored;

        private FakeContractMapper(OaLaborContract stored)
        {
            this.stored = stored;
        }

        @Override
        public int insertOaLaborContract(OaLaborContract contract)
        {
            contract.setContractId(100L);
            stored = contract;
            return 1;
        }

        @Override
        public int updateOaLaborContract(OaLaborContract contract)
        {
            if (contract.getEmployeeName() != null) stored.setEmployeeName(contract.getEmployeeName());
            if (contract.getStatus() != null) stored.setStatus(contract.getStatus());
            if (contract.getPreviewFileUrl() != null) stored.setPreviewFileUrl(contract.getPreviewFileUrl());
            if (contract.getArchiveFileUrl() != null) stored.setArchiveFileUrl(contract.getArchiveFileUrl());
            if (contract.getPdfFileUrl() != null) stored.setPdfFileUrl(contract.getPdfFileUrl());
            if (contract.getSignatureFileUrl() != null) stored.setSignatureFileUrl(contract.getSignatureFileUrl());
            if (contract.getDocumentVersion() != null) stored.setDocumentVersion(contract.getDocumentVersion());
            if (contract.getPreviewFileHash() != null) stored.setPreviewFileHash(contract.getPreviewFileHash());
            if (contract.getArchiveFileHash() != null) stored.setArchiveFileHash(contract.getArchiveFileHash());
            if (contract.getCertificateFileUrl() != null) stored.setCertificateFileUrl(contract.getCertificateFileUrl());
            if (contract.getCertificateFileHash() != null) stored.setCertificateFileHash(contract.getCertificateFileHash());
            if (contract.getTemplateFileHash() != null) stored.setTemplateFileHash(contract.getTemplateFileHash());
            if (contract.getSealImageHash() != null) stored.setSealImageHash(contract.getSealImageHash());
            if (contract.getContractFileHash() != null) stored.setContractFileHash(contract.getContractFileHash());
            if (contract.getSignerIp() != null) stored.setSignerIp(contract.getSignerIp());
            if (contract.getSignerUserAgent() != null) stored.setSignerUserAgent(contract.getSignerUserAgent());
            if (contract.getSignedTime() != null) stored.setSignedTime(contract.getSignedTime());
            if (contract.getUpdateBy() != null) stored.setUpdateBy(contract.getUpdateBy());
            return 1;
        }

        @Override
        public OaLaborContract selectOaLaborContractById(Long contractId)
        {
            return stored;
        }

        @Override
        public List<OaLaborContract> selectOaLaborContractList(OaLaborContract contract)
        {
            return stored == null ? Collections.emptyList() : Collections.singletonList(stored);
        }

        @Override
        public List<OaLaborContract> selectMyOaLaborContractList(OaLaborContract contract)
        {
            return selectOaLaborContractList(contract);
        }

        @Override
        public OaLaborContract selectOaLaborContractByEvidenceHash(String hash)
        {
            if (stored == null)
            {
                return null;
            }
            return hash.equals(stored.getPreviewFileHash())
                    || hash.equals(stored.getArchiveFileHash())
                    || hash.equals(stored.getCertificateFileHash())
                    || hash.equals(stored.getContractFileHash()) ? stored : null;
        }
    }

    private static class FakeTemplateMapper implements OaLaborContractTemplateMapper
    {
        @Override
        public int insertOaLaborContractTemplate(OaLaborContractTemplate template)
        {
            template.setTemplateId(10L);
            return 1;
        }

        @Override
        public OaLaborContractTemplate selectOaLaborContractTemplateById(Long templateId)
        {
            OaLaborContractTemplate template = new OaLaborContractTemplate();
            template.setTemplateId(templateId);
            template.setTemplateName("有社保模板");
            template.setSocialType("有社保");
            template.setTemplateFileUrl("classpath:/templates/labor-contract/social.docx");
            template.setStatus("0");
            return template;
        }

        @Override
        public List<OaLaborContractTemplate> selectOaLaborContractTemplateList(OaLaborContractTemplate template)
        {
            return Collections.emptyList();
        }

        @Override
        public int updateOaLaborContractTemplate(OaLaborContractTemplate template)
        {
            return 1;
        }
    }

    private static class FakeEventMapper implements OaLaborContractEventMapper
    {
        private final List<OaLaborContractEvent> events = new ArrayList<>();

        @Override
        public int insertOaLaborContractEvent(OaLaborContractEvent event)
        {
            events.add(event);
            return 1;
        }

        @Override
        public List<OaLaborContractEvent> selectEventsByContractId(Long contractId)
        {
            return events;
        }

        @Override
        public String selectLatestEventHashByContractId(Long contractId)
        {
            return events.isEmpty() ? null : events.get(events.size() - 1).getEventHash();
        }
    }

    private static class FakeSealConfigMapper implements OaCompanySealConfigMapper
    {
        @Override
        public OaCompanySealConfig selectActiveSealConfig()
        {
            OaCompanySealConfig seal = new OaCompanySealConfig();
            seal.setSealId(1L);
            seal.setSealName("默认公章");
            seal.setSealImageUrl("/profile/contracts/seal.png");
            seal.setStatus("0");
            return seal;
        }

        @Override
        public OaCompanySealConfig selectSealById(Long sealId)
        {
            return selectActiveSealConfig();
        }

        @Override
        public List<OaCompanySealConfig> selectSealsByLegalEntity(Long legalEntityId,
                boolean activeOnly)
        {
            return Collections.emptyList();
        }

        @Override
        public OaCompanySealConfig selectDefaultSealByLegalEntity(Long legalEntityId)
        {
            return null;
        }

        @Override
        public int clearDefaultSeal(Long legalEntityId, Long exceptSealId)
        {
            return 0;
        }

        @Override
        public int insertOaCompanySealConfig(OaCompanySealConfig config)
        {
            return 1;
        }

        @Override
        public int updateOaCompanySealConfig(OaCompanySealConfig config)
        {
            return 1;
        }
    }

    private static class FakeDeptScopeMapper implements OaDeptScopeMapper
    {
        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectUserShopDeptIds(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Long> selectUserAuthorizedOaDeptIds(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Long> selectAllActiveOaDeptIds()
        {
            return Collections.emptyList();
        }

        @Override
        public String selectDeptName(Long deptId)
        {
            return "测试门店";
        }

        @Override
        public com.erp.oa.domain.vo.OaLegalEntityCandidate selectLegalEntityCandidate(Long deptId)
        {
            return null;
        }

        @Override
        public List<com.erp.system.api.domain.SysLegalEntity> selectActiveLegalEntities()
        {
            return Collections.emptyList();
        }

        @Override
        public com.erp.system.api.domain.SysLegalEntity selectActiveLegalEntityById(Long legalEntityId)
        {
            return null;
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return scopeDeptId != null && scopeDeptId.equals(targetDeptId) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return 1;
        }
        @Override
        public int countActiveStoreDept(Long deptId)
        {
            return 1;
        }
    }

    private static class FakeDocumentService extends OaLaborContractDocumentService
    {
        private boolean templateHasRequiredPlaceholders = true;
        private String expectedArchiveTemplateUrl;
        private String expectedArchiveSealUrl;
        private String archiveTemplateUrl;
        private String archiveSealUrl;

        public void assertTemplateContainsRequiredPlaceholders(String templateFileUrl)
        {
            if (!templateHasRequiredPlaceholders)
            {
                throw new ServiceException("合同模板缺少合同占位符");
            }
        }

        public OaLaborContractTemplate freezeTemplateSnapshot(OaLaborContract contract,
                OaLaborContractTemplate template)
        {
            OaLaborContractTemplate frozen = new OaLaborContractTemplate();
            frozen.setTemplateId(template.getTemplateId());
            frozen.setTemplateName(template.getTemplateName());
            frozen.setSocialType(template.getSocialType());
            frozen.setTemplateVersion(template.getTemplateVersion());
            frozen.setTemplateFileUrl("/private/labor-contract/" + contract.getContractId() + "/template.docx");
            frozen.setStatus(template.getStatus());
            return frozen;
        }

        public OaCompanySealConfig freezeSealSnapshot(OaLaborContract contract, OaCompanySealConfig sealConfig)
        {
            OaCompanySealConfig frozen = new OaCompanySealConfig();
            frozen.setSealId(sealConfig.getSealId());
            frozen.setSealName(sealConfig.getSealName());
            frozen.setSealImageUrl("/private/labor-contract/" + contract.getContractId() + "/seal.png");
            frozen.setStatus(sealConfig.getStatus());
            return frozen;
        }

        public OaLaborContractTemplate loadFrozenTemplateSnapshot(OaLaborContract contract,
                OaLaborContractTemplate template)
        {
            return freezeTemplateSnapshot(contract, template);
        }

        public OaCompanySealConfig loadFrozenSealSnapshot(OaLaborContract contract)
        {
            OaCompanySealConfig sealConfig = new OaCompanySealConfig();
            sealConfig.setSealId(1L);
            sealConfig.setSealName("冻结企业章");
            sealConfig.setSealImageUrl("/private/labor-contract/" + contract.getContractId() + "/seal.png");
            sealConfig.setStatus("0");
            return sealConfig;
        }

        @Override
        public GeneratedContractFile generatePreview(OaLaborContract contract, OaLaborContractTemplate template,
                OaCompanySealConfig sealConfig)
        {
            return new GeneratedContractFile("/oa/laborContract/download/" + contract.getContractId() + "/preview",
                    "/oa/laborContract/download/" + contract.getContractId() + "/preview-pdf",
                    "preview-hash-" + contract.getContractId(), null);
        }

        @Override
        public GeneratedContractFile generateSignedArchive(OaLaborContract contract, OaLaborContractTemplate template,
                OaCompanySealConfig sealConfig, OaLaborContractSignRequest request)
        {
            archiveTemplateUrl = template.getTemplateFileUrl();
            archiveSealUrl = sealConfig.getSealImageUrl();
            if (expectedArchiveTemplateUrl != null)
            {
                assertThat(archiveTemplateUrl).isEqualTo(expectedArchiveTemplateUrl);
            }
            if (expectedArchiveSealUrl != null)
            {
                assertThat(archiveSealUrl).isEqualTo(expectedArchiveSealUrl);
            }
            return new GeneratedContractFile("/oa/laborContract/download/" + contract.getContractId() + "/archive",
                    "/oa/laborContract/download/" + contract.getContractId() + "/archive-pdf",
                    "archive-hash-" + contract.getContractId(),
                    "/oa/laborContract/download/" + contract.getContractId() + "/signature",
                    "/oa/laborContract/download/" + contract.getContractId() + "/certificate",
                    "certificate-hash-" + contract.getContractId());
        }

        @Override
        public String calculateFileUrlSha256(String fileUrl)
        {
            if (fileUrl.contains("seal"))
            {
                return "seal-hash-1";
            }
            return "template-hash-10";
        }

        @Override
        public String calculateStoredFileSha256(Long contractId, String kind)
        {
            return kind.startsWith("preview") ? "preview-hash-" + contractId : "archive-hash-" + contractId;
        }
    }
}
