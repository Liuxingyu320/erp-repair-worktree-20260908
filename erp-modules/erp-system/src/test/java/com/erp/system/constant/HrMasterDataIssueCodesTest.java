package com.erp.system.constant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class HrMasterDataIssueCodesTest
{
    @Test
    void exposesTheFrozenIssueCodeContract()
    {
        assertThat(HrMasterDataIssueCodes.definitions()).extracting(HrMasterDataIssueCodes.Definition::code)
                .containsExactly(
                        "ORG_PATH_INVALID", "COMPANY_NODE_MISSING", "STORE_MAPPING_MISSING",
                        "DEPT_LEADER_MISSING", "DEPT_LEADER_UNRESOLVED", "EMPLOYEE_PROFILE_MISSING",
                        "POST_MISSING_OR_DISABLED", "POSITION_CONFIG_MISSING",
                        "POSITION_CONFIG_ROLE_MISSING", "DICTIONARY_ROUTE_MISSING",
                        "LEGAL_ENTITY_MAPPING_MISSING");
        assertThat(HrMasterDataIssueCodes.definitions()).allSatisfy(definition->{
            assertThat(definition.label()).isNotBlank();assertThat(definition.severity()).isIn("P0","P1","P2");
            assertThat(definition.defaultSuggestion()).isNotBlank();
        });
    }
}
