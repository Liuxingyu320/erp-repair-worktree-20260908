package com.erp.system.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HrOnboardingIdentifierSerializationTest
{
    @Test
    void listAndInheritedDetailIdentifiersAreExactTextWhileVersionsRemainNumbers() throws Exception
    {
        ObjectMapper json = new ObjectMapper();
        for (HrOnboardingListVo result : new HrOnboardingListVo[] {new HrOnboardingListVo(), new HrOnboardingDetailVo()})
        {
            result.setOnboardingId(9007199254740993L); result.setVersion(7);
            var node = json.readTree(json.writeValueAsString(result));
            assertThat(node.path("onboardingId").isTextual()).isTrue();
            assertThat(node.path("onboardingId").textValue()).isEqualTo("9007199254740993");
            assertThat(node.path("version").isIntegralNumber()).isTrue();
        }
    }
    @Test
    void confirmationAndImportReceiptPreserveMaximumLongIdentifiers() throws Exception
    {
        ObjectMapper json = new ObjectMapper();
        HrOnboardingConfirmResult result = new HrOnboardingConfirmResult();
        result.setOnboardingId(Long.MAX_VALUE); result.setUserId(Long.MAX_VALUE - 1);
        var node = json.readTree(json.writeValueAsString(result));
        assertThat(node.path("onboardingId").textValue()).isEqualTo("9223372036854775807");
        assertThat(node.path("userId").textValue()).isEqualTo("9223372036854775806");
        HrOnboardingImportPreviewVo.RowVo row = new HrOnboardingImportPreviewVo.RowVo();
        row.setRowId(Long.MAX_VALUE); row.setCandidateOnboardingId(Long.MAX_VALUE); row.setResultOnboardingId(Long.MAX_VALUE);
        node = json.readTree(json.writeValueAsString(row));
        for (String field : new String[] {"rowId", "candidateOnboardingId", "resultOnboardingId"})
            assertThat(node.path(field).textValue()).isEqualTo("9223372036854775807");
    }
}
