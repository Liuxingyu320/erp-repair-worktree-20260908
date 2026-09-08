package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.system.api.domain.SignCandidateUser;

@DisplayName("Excel 入职签约事件工厂")
class OaOnboardSignEventFactoryTest
{
    @Test
    @DisplayName("导入生成事件固定标记来源、审计事实和合同生效日")
    void shouldFreezeExcelSourceAuditFactsAndContractEffectiveDate()
    {
        OaSignOnboardImportBatch batch = new OaSignOnboardImportBatch();
        batch.setBatchId(31L);
        batch.setShopDeptId(1171L);
        batch.setShopDeptName("北京区域");
        batch.setFileSha256("excel-file-sha256");

        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setRowId(501L);
        row.setSourceRowNumber(8);
        row.setEmployeeId(201L);
        row.setSourceEventVersion(3L);
        row.setNoExternalContractConfirmed(Boolean.TRUE);
        row.setHistoricalSupplement(Boolean.TRUE);
        row.setHistoricalReason("补签存量入职合同");

        LocalDate effectiveDate = LocalDate.of(2026, 7, 16);
        OaSignOnboardContractSnapshot contract = new OaSignOnboardContractSnapshot();
        contract.setContractStartDate(effectiveDate);
        contract.setRecommendedCompany("舟山茗汇文化传播有限公司");
        contract.setLegalRepresentative("杜翠香");
        contract.setRegisteredAddress("浙江省舟山市嵊泗县");

        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(201L);
        candidate.setNickName("李曼");
        // 员工首次阅读与签名阶段不应因档案中的公司而预绑定法律主体。
        candidate.setLegalEntityId(99L);
        candidate.setLegalEntityCode("COMPANY-99");
        candidate.setLegalEntity("档案中的公司");

        HrSignBusinessEvent event = new OaOnboardSignEventFactory().create(
                batch, row, contract, candidate, 101L, "generate-request-1");

        assertThat(event.getEventId()).isEqualTo("OA-ONBOARD-EXCEL:501:3");
        assertThat(event.getScenario()).isEqualTo("ONBOARD");
        assertThat(event.getSourceType()).isEqualTo("MANUAL_SIGN_EXCEL_IMPORT");
        assertThat(event.getSourceType()).isEqualTo(OaOnboardSignEventFactory.SOURCE_TYPE);
        assertThat(event.getSourceBusinessId()).isEqualTo("501");
        assertThat(event.getSourceEventVersion()).isEqualTo(3L);
        assertThat(event.getEmployeeId()).isEqualTo(201L);
        assertThat(event.getOperatorUserId()).isEqualTo(101L);
        assertThat(event.getOccurredTime()).isNotNull();
        assertThat(event.getAttributes())
                .containsEntry("importBatchId", 31L)
                .containsEntry("importRowId", 501L)
                .containsEntry("sourceRowNumber", 8)
                .containsEntry("fileSha256", "excel-file-sha256")
                .containsEntry("generationRequestId", "generate-request-1")
                .containsEntry("noExternalContractConfirmed", Boolean.TRUE)
                .containsEntry("historicalSupplement", Boolean.TRUE)
                .containsEntry("historicalSupplementReason", "补签存量入职合同")
                .containsEntry("contractEffectiveDate", effectiveDate)
                .containsEntry("recommendedCompany", "舟山茗汇文化传播有限公司")
                .containsEntry("recommendedLegalRepresentative", "杜翠香")
                .containsEntry("recommendedRegisteredAddress", "浙江省舟山市嵊泗县");
        assertThat(event.getBeforeSnapshot()).isSameAs(event.getAfterSnapshot());
        assertThat(event.getAfterSnapshot().getContractStartDate()).isEqualTo(effectiveDate);
        assertThat(event.getAfterSnapshot().getLegalEntityId()).isNull();
        assertThat(event.getAfterSnapshot().getLegalEntityCode()).isNull();
        assertThat(event.getAfterSnapshot().getLegalEntityName()).isNull();
    }
}
