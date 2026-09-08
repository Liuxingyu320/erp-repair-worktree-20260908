package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("商品分类 Mapper 契约")
class InvProductCategoryMapperContractTest
{
    @Test
    @DisplayName("分类更新必须持久化普通备注")
    void updateShouldPersistRemark() throws IOException
    {
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/inventory/InvProductCategoryMapper.xml"))
        {
            assertThat(input).isNotNull();
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("remark = #{remark}");
        }
    }
}
