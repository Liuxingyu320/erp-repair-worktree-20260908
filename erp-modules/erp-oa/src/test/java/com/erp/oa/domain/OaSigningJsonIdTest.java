package com.erp.oa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("手机签署业务 JSON ID")
class OaSigningJsonIdTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("签约包与文件 ID 必须按字符串输出")
    void shouldSerializeSignPackageIdentifiersAsStrings() throws Exception
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(9_999_999_999_999_999L);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(9_888_888_888_888_888L);
        document.setPackageId(9_999_999_999_999_999L);

        assertThat(objectMapper.writeValueAsString(signPackage))
                .contains("\"packageId\":\"9999999999999999\"");
        assertThat(objectMapper.writeValueAsString(document))
                .contains("\"documentId\":\"9888888888888888\"")
                .contains("\"packageId\":\"9999999999999999\"");
    }

    @Test
    @DisplayName("劳动合同 ID 必须按字符串输出")
    void shouldSerializeLaborContractIdentifierAsString() throws Exception
    {
        OaLaborContract contract = new OaLaborContract();
        contract.setContractId(9_777_777_777_777_777L);

        assertThat(objectMapper.writeValueAsString(contract))
                .contains("\"contractId\":\"9777777777777777\"");
    }
}
