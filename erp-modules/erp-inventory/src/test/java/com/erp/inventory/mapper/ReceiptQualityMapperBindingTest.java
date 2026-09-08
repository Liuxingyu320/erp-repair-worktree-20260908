package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("收货批次与逐行质检 Mapper 绑定")
class ReceiptQualityMapperBindingTest
{
    @Test
    @DisplayName("批次、明细、质检和附件都有完整 XML 绑定")
    void shouldBindReceiptQualityPersistence() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAlias(configuration, "InvReceiptBatch", "com.erp.inventory.domain.InvReceiptBatch");
        registerAlias(configuration, "InvReceiptBatchDetail", "com.erp.inventory.domain.InvReceiptBatchDetail");
        registerAlias(configuration, "InvQualityInspection", "com.erp.inventory.domain.InvQualityInspection");
        registerAlias(configuration, "InvInboundRecord", "com.erp.inventory.domain.InvInboundRecord");

        parseMapper(configuration, "mapper/inventory/InvReceiptBatchMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvReceiptBatchDetailMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvQualityInspectionMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvQualityInspectionAttachmentMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvInboundRecordMapper.xml");

        assertMapped(configuration, InvReceiptBatchMapper.class, "selectByIdForUpdate");
        assertMapped(configuration, InvReceiptBatchMapper.class, "selectPendingByOrderId");
        assertMapped(configuration, InvReceiptBatchMapper.class, "updateProgress");
        assertMapped(configuration, InvReceiptBatchDetailMapper.class, "selectByBatchIdForUpdate");
        assertMapped(configuration, InvReceiptBatchDetailMapper.class, "updateInspectionProgress");
        assertMapped(configuration, InvQualityInspectionMapper.class, "insertInvQualityInspection");
        assertMapped(configuration, InvQualityInspectionAttachmentMapper.class, "batchInsert");
        assertMapped(configuration, InvInboundRecordMapper.class, "selectByBatchDetailIdForUpdate");
    }

    private static void registerAlias(Configuration configuration, String alias, String className)
            throws Exception
    {
        configuration.getTypeAliasRegistry().registerAlias(alias, Class.forName(className));
    }

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        assertThat(ReceiptQualityMapperBindingTest.class.getClassLoader().getResource(resource))
                .as(resource)
                .isNotNull();
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static void assertMapped(Configuration configuration, Class<?> mapperType, String methodName)
    {
        assertThat(configuration.hasStatement(mapperType.getName() + "." + methodName))
                .as(mapperType.getSimpleName() + "." + methodName)
                .isTrue();
    }
}
