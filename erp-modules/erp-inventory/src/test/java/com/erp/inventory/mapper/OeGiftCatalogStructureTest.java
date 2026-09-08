package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OE与礼盒资料模块结构")
class OeGiftCatalogStructureTest
{
    @Test
    @DisplayName("OE和礼盒使用独立资料表且不进入商品库存链路")
    void shouldUseIndependentCatalogTables() throws Exception
    {
        String sql = readRepoFile("sql/erp_inventory_oe_gift_management_20260706.sql").toLowerCase();

        assertThat(sql)
                .contains(
                        "create table if not exists inv_oe_category",
                        "create table if not exists inv_oe_item",
                        "create table if not exists inv_gift_category",
                        "create table if not exists inv_gift_box",
                        "oa_fixed_asset_config_backup_20260706",
                        "oa_fixed_asset_repair_backup_20260706",
                        "change column product_id oe_item_id",
                        "change column product_name oe_item_name")
                .doesNotContain("inv_gift_stock");

        assertThat(sql)
                .as("OE/礼盒第一期不应接入商品库存表")
                .doesNotContain("alter table inv_product")
                .doesNotContain("alter table inv_stock")
                .doesNotContain("insert into inv_product");
    }

    @Test
    @DisplayName("OE和礼盒后端接口按资料模块独立暴露")
    void shouldExposeIndependentBackendEndpoints() throws Exception
    {
        String oeController = readModuleFile("src/main/java/com/erp/inventory/controller/InvOeController.java");
        String oeCategoryController = readModuleFile("src/main/java/com/erp/inventory/controller/InvOeCategoryController.java");
        String giftController = readModuleFile("src/main/java/com/erp/inventory/controller/InvGiftController.java");
        String giftCategoryController = readModuleFile("src/main/java/com/erp/inventory/controller/InvGiftCategoryController.java");
        String oeMapper = readModuleFile("src/main/resources/mapper/inventory/InvOeMapper.xml");
        String giftMapper = readModuleFile("src/main/resources/mapper/inventory/InvGiftMapper.xml");

        assertThat(oeController)
                .contains("@RequestMapping(\"/oe\")", "inv:oe:list", "inv:oe:import", "inv:oe:export")
                .doesNotContain("InvProduct");
        assertThat(oeCategoryController)
                .contains("@RequestMapping(\"/oe/category\")", "inv:oeCategory:tree");
        assertThat(giftController)
                .contains("@RequestMapping(\"/gift\")", "inv:gift:list", "inv:gift:import", "inv:gift:export")
                .doesNotContain("InvProduct");
        assertThat(giftCategoryController)
                .contains("@RequestMapping(\"/gift/category\")", "inv:giftCategory:tree",
                        "inv:transfer:list", "inv:transfer:add");

        assertThat(oeMapper)
                .contains("inv_oe_item", "cost_price", "supplier_name", "supplier_phone")
                .doesNotContain("inv_product p");
        assertThat(giftMapper)
                .contains("inv_gift_box", "cost_price", "guide_price_1", "guide_price_2", "supplier_name");
    }

    private String readModuleFile(String relativePath) throws Exception
    {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    private String readRepoFile(String relativePath) throws Exception
    {
        return Files.readString(Path.of("..", "..", relativePath), StandardCharsets.UTF_8);
    }
}
