package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementExportBatch;
import com.erp.oa.domain.OaReimbursementInvoice;
import com.erp.oa.domain.OaReimbursementItem;

@DisplayName("费用报销 MyBatis 映射")
class OaReimbursementMapperBindingTest
{
    private static final String NAMESPACE =
            "com.erp.oa.mapper.OaReimbursementMapper";
    private static final String XML =
            "mapper/oa/OaReimbursementMapper.xml";

    @Test
    @DisplayName("全部报销语句可解析且财务选择保持审批状态和组织范围")
    void shouldBindStatementsAndFinanceScope() throws Exception
    {
        Configuration configuration = configuration();
        for (String statement : List.of(
                "insertReimbursement", "updateReimbursement",
                "markApprovalSubmitting", "finalizeApprovalStart",
                "selectById", "selectByIdForUpdate", "selectMyList",
                "selectFinanceList", "selectFinanceListByIds",
                "deleteItemsByReimbursementId", "clearInvoiceItemLinks",
                "insertItem", "bindInvoiceItem", "deleteItem",
                "selectItemsByReimbursementId", "countInvoices",
                "selectInvoiceByReimbursementAndSha", "insertInvoice",
                "selectInvoiceById",
                "selectInvoicesByReimbursementId",
                "upsertInvoiceRecognition",
                "selectApprovedDuplicateReimbursement",
                "selectApprovedDuplicateByNumber",
                "countInvoicesNotReady", "updateInvoiceDuplicate",
                "deleteInvoiceRecognition", "deleteInvoice",
                "insertExportBatch", "insertExportBatchItem",
                "selectExportBatchById", "markExported"))
        {
            assertThat(configuration.hasStatement(
                    NAMESPACE + "." + statement)).isTrue();
        }

        BoundSql finance = configuration.getMappedStatement(
                NAMESPACE + ".selectFinanceListByIds").getBoundSql(Map.of(
                        "reimbursementIds", List.of(11L, 12L),
                        "scopeDeptIds", List.of(21L, 22L)));
        assertThat(normalized(finance.getSql()))
                .contains("r.status = 'approved'")
                .contains("r.reimbursement_id in ( ? , ? )")
                .contains("r.shop_dept_id in ( ? , ? )");
    }

    private Configuration configuration() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "OaReimbursement", OaReimbursement.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "OaReimbursementItem", OaReimbursementItem.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "OaReimbursementInvoice", OaReimbursementInvoice.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "OaReimbursementExportBatch",
                OaReimbursementExportBatch.class);
        try (InputStream input = Resources.getResourceAsStream(XML))
        {
            new XMLMapperBuilder(input, configuration, XML,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalized(String value)
    {
        return value.replaceAll("\\s+", " ").trim();
    }
}
