package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.vo.OaLegalEntityCandidate;
import com.erp.oa.domain.vo.OaSignCompanyOptions;
import com.erp.oa.mapper.OaCompanySealConfigMapper;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.system.api.domain.SysLegalEntity;

@DisplayName("合同公司和印章服务")
class OaSignCompanyServiceTest
{
    private OaDeptScopeMapper deptScopeMapper;
    private OaCompanySealConfigMapper sealMapper;
    private OaSignDocumentService documentService;
    private OaSignCompanyService service;

    @BeforeEach
    void setUp()
    {
        deptScopeMapper = mock(OaDeptScopeMapper.class);
        sealMapper = mock(OaCompanySealConfigMapper.class);
        documentService = mock(OaSignDocumentService.class);
        service = new OaSignCompanyService(deptScopeMapper, sealMapper, documentService);
    }

    @Test
    @DisplayName("按员工归属部门识别公司并只推荐该公司的默认印章")
    void shouldResolveCompanyFromDepartmentAndRecommendScopedSeal()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setDeptIdSnapshot(88L);
        OaLegalEntityCandidate candidate = new OaLegalEntityCandidate();
        candidate.setLegalEntityId(1L);
        candidate.setLegalEntityName("示例餐饮有限公司");
        candidate.setSourceDeptId(80L);
        OaCompanySealConfig seal = seal(10L, 1L);
        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(1L);
        entity.setLegalEntityName("示例餐饮有限公司");
        when(deptScopeMapper.selectLegalEntityCandidate(88L)).thenReturn(candidate);
        when(deptScopeMapper.selectActiveLegalEntities()).thenReturn(List.of(entity));
        when(deptScopeMapper.selectActiveLegalEntityById(1L)).thenReturn(entity);
        when(sealMapper.selectSealsByLegalEntity(1L, true)).thenReturn(List.of(seal));
        when(sealMapper.selectDefaultSealByLegalEntity(1L)).thenReturn(seal);

        OaSignCompanyOptions options = service.options(signPackage);

        assertThat(options.getAutomaticCandidate()).isSameAs(candidate);
        assertThat(options.getLegalEntities()).containsExactly(entity);
        assertThat(options.getSeals()).containsExactly(seal);
        assertThat(options.getRecommendedSealId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("不允许把其他公司的印章用于当前合同公司")
    void shouldRejectSealOwnedByAnotherCompany()
    {
        when(sealMapper.selectSealById(10L)).thenReturn(seal(10L, 2L));

        assertThatThrownBy(() -> service.requireActiveSeal(10L, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不属于当前公司");
        verify(documentService, never()).readConfiguredFileBytes(any());
    }

    @Test
    @DisplayName("启用印章在使用前重新校验已登记图片哈希")
    void shouldVerifyRegisteredSealImageHashBeforeUse() throws Exception
    {
        OaCompanySealConfig seal = seal(10L, 1L);
        byte[] image = sealJpeg();
        seal.setSealImageUrl("/profile/seal.jpeg");
        seal.setSealImageHash(sha256(image));
        when(sealMapper.selectSealById(10L)).thenReturn(seal);
        when(documentService.readConfiguredFileBytes("/profile/seal.jpeg"))
                .thenReturn(image);

        OaCompanySealConfig result = service.requireActiveSeal(10L, 1L);

        assertThat(result.getSealImageHash()).isEqualTo(sha256(image));
    }

    @Test
    @DisplayName("新增印章时接受JPEG并登记原始文件哈希")
    void shouldSaveJpegSealAndRegisterOriginalHash() throws Exception
    {
        SysLegalEntity entity = entity(1L, "A", "甲公司", "甲法人", "甲注册地址");
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setLegalEntityId(1L);
        seal.setSealName("合同章");
        seal.setSealImageUrl("/profile/seal.jpg");
        byte[] image = sealJpeg();
        when(deptScopeMapper.selectActiveLegalEntityById(1L)).thenReturn(entity);
        when(sealMapper.selectSealsByLegalEntity(1L, false)).thenReturn(List.of());
        when(documentService.readConfiguredFileBytes("/profile/seal.jpg")).thenReturn(image);
        when(sealMapper.insertOaCompanySealConfig(any())).thenAnswer(invocation -> {
            ((OaCompanySealConfig) invocation.getArgument(0)).setSealId(13L);
            return 1;
        });
        when(sealMapper.selectSealById(13L)).thenReturn(seal);

        OaCompanySealConfig saved = service.saveSeal(seal);

        assertThat(saved.getSealImageHash()).isEqualTo(sha256(image));
        assertThat(saved.getSealType()).isEqualTo("CONTRACT");
        assertThat(saved.getIsDefault()).isEqualTo("Y");
        verify(sealMapper).insertOaCompanySealConfig(seal);
    }

    @Test
    @DisplayName("印章文件哈希匹配但图片内容无效时仍拒绝")
    void shouldRejectInvalidSealImageEvenWhenRegisteredHashMatches() throws Exception
    {
        byte[] invalid = "not-an-image".getBytes(StandardCharsets.UTF_8);
        OaCompanySealConfig seal = seal(12L, 1L);
        seal.setSealImageHash(sha256(invalid));
        when(sealMapper.selectSealById(12L)).thenReturn(seal);
        when(documentService.readConfiguredFileBytes("/profile/seal.png"))
                .thenReturn(invalid);

        assertThatThrownBy(() -> service.requireActiveSeal(12L, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("企业章图片不合法");
    }

    @Test
    @DisplayName("印章编码名称类型图片或登记哈希缺失时硬阻断")
    void shouldRejectIncompleteRegisteredSealMasterData()
    {
        OaCompanySealConfig missingCode = seal(20L, 1L);
        missingCode.setSealCode(null);
        assertIncompleteSeal(missingCode, "印章编码");

        OaCompanySealConfig missingName = seal(21L, 1L);
        missingName.setSealName(" ");
        assertIncompleteSeal(missingName, "印章名称");

        OaCompanySealConfig missingType = seal(22L, 1L);
        missingType.setSealType(null);
        assertIncompleteSeal(missingType, "合同章类型");

        OaCompanySealConfig missingImage = seal(23L, 1L);
        missingImage.setSealImageUrl(null);
        assertIncompleteSeal(missingImage, "印章图片");

        OaCompanySealConfig invalidHash = seal(24L, 1L);
        invalidHash.setSealImageHash("not-a-sha256");
        assertIncompleteSeal(invalidHash, "印章图片SHA-256");
        verify(documentService, never()).readConfiguredFileBytes(any());
    }

    @Test
    @DisplayName("公司名差异较大时可由法人和注册地稳定定位")
    void shouldMatchByRepresentativeAndRegisteredAddressWhenOperationalNameDiffers()
    {
        SysLegalEntity expected = entity(1L, "HZ-XR", "杭州翕然茶业有限公司",
                "王庆旭", "浙江省杭州市西湖区转塘街道珊瑚沙路369号2号楼西玥酒店一层");
        SysLegalEntity other = entity(2L, "ZS-MH", "舟山茗汇文化传播有限公司",
                "杜翠香", "浙江省舟山市嵊泗县枇杞乡奇观村育才路9号");
        when(deptScopeMapper.selectActiveLegalEntities()).thenReturn(List.of(other, expected));

        OaSignCompanyService.CompanyMatchResult result = service.matchExcelCompany(
                "西玥酒店茶艺部", "王庆旭",
                "浙江省杭州市西湖区转塘街道珊瑚沙路369号2号楼西玥酒店一层", 88L);

        assertThat(result.isAutoSelected()).isTrue();
        assertThat(result.getSelectedEntity()).isSameAs(expected);
        assertThat(result.getMode()).isEqualTo("EXCEL_AUTO");
        assertThat(result.getFirstScore()).isGreaterThanOrEqualTo(result.getThreshold());
    }

    @Test
    @DisplayName("前两名分数接近时不自动选公司")
    void shouldRequireHrWhenTopCandidatesAreTied()
    {
        SysLegalEntity first = entity(1L, "A", "甲公司", "王庆旭", "杭州市西湖区测试路1号");
        SysLegalEntity second = entity(2L, "B", "乙公司", "王庆旭", "杭州市西湖区测试路1号");
        when(deptScopeMapper.selectActiveLegalEntities()).thenReturn(List.of(first, second));

        OaSignCompanyService.CompanyMatchResult result = service.matchExcelCompany(
                null, "王庆旭", "杭州市西湖区测试路1号", 88L);

        assertThat(result.getSelectedEntity()).isNull();
        assertThat(result.getMode()).isEqualTo("HR_REQUIRED_AMBIGUOUS");
        assertThat(result.getFirstScore()).isEqualByComparingTo(result.getSecondScore());
    }

    @Test
    @DisplayName("Excel匹配公司优先于部门候选并记录冲突")
    void shouldRecordDepartmentConflictWithoutOverridingExcelResult()
    {
        SysLegalEntity excel = entity(1L, "A", "甲公司", "甲法人", "甲注册地址");
        SysLegalEntity department = entity(2L, "B", "乙公司", "乙法人", "乙注册地址");
        OaLegalEntityCandidate departmentCandidate = new OaLegalEntityCandidate();
        departmentCandidate.setLegalEntityId(2L);
        when(deptScopeMapper.selectLegalEntityCandidate(88L)).thenReturn(departmentCandidate);
        when(deptScopeMapper.selectActiveLegalEntities()).thenReturn(List.of(department, excel));

        OaSignCompanyService.CompanyMatchResult result = service.matchExcelCompany(
                "甲公司", "甲法人", "甲注册地址", 88L);

        assertThat(result.getSelectedEntity()).isSameAs(excel);
        assertThat(result.isDepartmentConflict()).isTrue();
        assertThat(result.getDepartmentCandidate()).isSameAs(departmentCandidate);
    }

    @Test
    @DisplayName("公司匹配策略标识稳定包含阈值配置变化")
    void shouldChangePolicyVersionWhenDecisionThresholdChanges()
    {
        String original = service.currentMatchPolicyVersion();

        assertThat(original).matches("COMPANY_MATCH_V1_[0-9a-f]{12}");
        assertThat(service.currentMatchPolicyVersion()).isEqualTo(original);

        ReflectionTestUtils.setField(service, "autoMatchThreshold",
                new BigDecimal("0.6500"));

        assertThat(service.currentMatchPolicyVersion()).isNotEqualTo(original);
        assertThat(service.currentMatchPolicyVersion()).hasSizeLessThanOrEqualTo(32);
    }

    @Test
    @DisplayName("公司主数据任一法定字段缺失均硬阻断")
    void shouldRejectIncompleteCompanyMasterData()
    {
        SysLegalEntity incomplete = entity(1L, null, "1", null, null);
        incomplete.setUnifiedSocialCreditCode(null);
        when(deptScopeMapper.selectActiveLegalEntityById(1L)).thenReturn(incomplete);

        assertThatThrownBy(() -> service.requireContractReadyEntity(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("公司编码")
                .hasMessageContaining("公司法定全称")
                .hasMessageContaining("统一社会信用代码")
                .hasMessageContaining("注册地址")
                .hasMessageContaining("法定代表人");
    }

    @Test
    @DisplayName("只有一枚有效合同章时即使未标默认也自动选择")
    void shouldRecommendOnlyActiveContractSeal()
    {
        SysLegalEntity entity = entity(1L, "A", "甲公司", "甲法人", "甲注册地址");
        OaCompanySealConfig only = seal(10L, 1L);
        only.setIsDefault("N");
        when(deptScopeMapper.selectActiveLegalEntityById(1L)).thenReturn(entity);
        when(sealMapper.selectSealsByLegalEntity(1L, true)).thenReturn(List.of(only));

        OaSignCompanyService.SealRecommendation result = service.recommendContractSeal(1L);

        assertThat(result.getSelectedSeal()).isSameAs(only);
        assertThat(result.getMode()).isEqualTo("UNIQUE_ACTIVE");
    }

    @Test
    @DisplayName("多枚有效章且无唯一默认时转HR选择")
    void shouldRequireHrForAmbiguousActiveSeals()
    {
        SysLegalEntity entity = entity(1L, "A", "甲公司", "甲法人", "甲注册地址");
        OaCompanySealConfig first = seal(10L, 1L);
        OaCompanySealConfig second = seal(11L, 1L);
        first.setIsDefault("N");
        second.setIsDefault("N");
        when(deptScopeMapper.selectActiveLegalEntityById(1L)).thenReturn(entity);
        when(sealMapper.selectSealsByLegalEntity(1L, true)).thenReturn(List.of(first, second));

        OaSignCompanyService.SealRecommendation result = service.recommendContractSeal(1L);

        assertThat(result.getSelectedSeal()).isNull();
        assertThat(result.getMode()).isEqualTo("HR_REQUIRED_MULTIPLE_ACTIVE_SEALS");
    }

    @Test
    @DisplayName("印章过期或图片哈希变化时拒绝生成")
    void shouldRejectExpiredOrChangedSeal() throws Exception
    {
        OaCompanySealConfig expired = seal(10L, 1L);
        expired.setValidTo(new Date(System.currentTimeMillis() - 1_000L));
        when(sealMapper.selectSealById(10L)).thenReturn(expired);
        assertThatThrownBy(() -> service.requireActiveSeal(10L, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("有效期");

        OaCompanySealConfig changed = seal(11L, 1L);
        changed.setSealImageHash("0".repeat(64));
        when(sealMapper.selectSealById(11L)).thenReturn(changed);
        when(documentService.readConfiguredFileBytes("/profile/seal.png"))
                .thenReturn(sealJpeg());
        assertThatThrownBy(() -> service.requireActiveSeal(11L, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("与登记时不一致");
    }

    private SysLegalEntity entity(Long id, String code, String name,
            String representative, String address)
    {
        SysLegalEntity entity = new SysLegalEntity();
        entity.setLegalEntityId(id);
        entity.setLegalEntityCode(code);
        entity.setLegalEntityName(name);
        entity.setUnifiedSocialCreditCode("91330100TEST" + id);
        entity.setRegisteredAddress(address);
        entity.setLegalRepresentative(representative);
        entity.setContactPhone("0571-0000000" + id);
        entity.setStatus("0");
        entity.setVersion(1L);
        return entity;
    }

    private OaCompanySealConfig seal(Long sealId, Long legalEntityId)
    {
        OaCompanySealConfig seal = new OaCompanySealConfig();
        seal.setSealId(sealId);
        seal.setLegalEntityId(legalEntityId);
        seal.setSealCode("CONTRACT-" + sealId);
        seal.setSealName("劳动合同专用章");
        seal.setSealType("CONTRACT");
        seal.setSealImageUrl("/profile/seal.png");
        seal.setSealImageHash("bf880f8a0210472b0427e88e092bba4640e99acedc4c9c28c9e1e60a909d1e5e");
        seal.setStatus("0");
        seal.setIsDefault("Y");
        return seal;
    }

    private void assertIncompleteSeal(OaCompanySealConfig seal, String expectedField)
    {
        when(sealMapper.selectSealById(seal.getSealId())).thenReturn(seal);
        assertThatThrownBy(() -> service.requireContractReadySeal(seal.getSealId(), 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("主数据不完整")
                .hasMessageContaining(expectedField);
    }

    private byte[] sealJpeg() throws Exception
    {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.RED);
            graphics.setStroke(new BasicStroke(6f));
            graphics.drawOval(8, 8, 104, 104);
        }
        finally
        {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "jpeg", output)).isTrue();
        return output.toByteArray();
    }

    private String sha256(byte[] bytes) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
