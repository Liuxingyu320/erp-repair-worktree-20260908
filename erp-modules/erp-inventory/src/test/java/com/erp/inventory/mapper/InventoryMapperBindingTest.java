package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.annotation.Excel;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvDeliveryNotice;
import com.erp.inventory.domain.InvDeliveryNoticeDetail;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvPurchaseReturnDetail;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesReturnDetail;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferApprovalTask;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferStatusLog;

@DisplayName("进销存 Mapper 绑定")
class InventoryMapperBindingTest
{
    @Test
    @DisplayName("明细 Mapper 中被服务调用的方法都有 XML 绑定")
    void shouldBindDetailMapperStatements() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);

        parseMapper(configuration, "mapper/inventory/InvPurchaseDetailMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvSalesDetailMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvPurchaseReturnDetailMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvSalesReturnDetailMapper.xml");

        assertMapped(configuration, InvPurchaseDetailMapper.class,
                "selectInvPurchaseDetailByOrderIdForUpdate");
        assertMapped(configuration, InvSalesDetailMapper.class,
                "selectInvSalesDetailByOrderIdForUpdate");
        assertMapped(configuration, InvPurchaseReturnDetailMapper.class,
                "selectInvPurchaseReturnDetailByReturnIdForUpdate");
        assertMapped(configuration, InvPurchaseReturnDetailMapper.class,
                "sumHistoricalReturnQuantity");
        assertMapped(configuration, InvPurchaseReturnDetailMapper.class,
                "sumHistoricalReturnQuantityByPurchaseDetailId");
        assertMapped(configuration, InvSalesReturnDetailMapper.class,
                "selectInvSalesReturnDetailByReturnIdForUpdate");
        assertMapped(configuration, InvSalesReturnDetailMapper.class,
                "sumHistoricalReturnQuantity");
        assertMapped(configuration, InvSalesReturnDetailMapper.class,
                "sumHistoricalReturnQuantityBySalesDetailId");
    }

    @Test
    @DisplayName("库存加权成本更新先读取旧数量和旧成本")
    void shouldCalculateWeightedCostBeforeMutatingStockTotals() throws Exception
    {
        String mapperXml = resourceText("mapper/inventory/InvStockMapper.xml");
        int updateStart = mapperXml.indexOf("<update id=\"addInvStockWithCost\">");
        int updateEnd = mapperXml.indexOf("</update>", updateStart);
        String updateSql = mapperXml.substring(updateStart, updateEnd);

        int costPriceIndex = updateSql.indexOf("cost_price =");
        int quantityIndex = updateSql.indexOf("current_quantity =");
        int totalCostIndex = updateSql.indexOf("total_cost =");

        assertThat(costPriceIndex).isGreaterThanOrEqualTo(0);
        assertThat(quantityIndex).isGreaterThanOrEqualTo(0);
        assertThat(totalCostIndex).isGreaterThanOrEqualTo(0);
        assertThat(updateSql.substring(costPriceIndex, quantityIndex)).contains("#{incomingCost}", "#{quantity}");
        assertThat(costPriceIndex)
                .as("cost_price must be assigned before mutating current_quantity")
                .isLessThan(quantityIndex);
        assertThat(costPriceIndex)
                .as("cost_price must be assigned before mutating total_cost")
                .isLessThan(totalCostIndex);
    }

    @Test
    @DisplayName("库存更新使用版本号做乐观锁兜底")
    void shouldUseVersionGuardForStockMutations() throws Exception
    {
        String mapperXml = resourceText("mapper/inventory/InvStockMapper.xml");

        assertThat(mapperXml)
                .contains(
                        "property=\"version\" column=\"version\"",
                        "s.version",
                        "version",
                        "#{version}");
        assertVersionedUpdate(mapperXml, "updateInvStock");
        assertVersionedUpdate(mapperXml, "addInvStock");
        assertVersionedUpdate(mapperXml, "addInvStockWithCost");
        assertVersionedUpdate(mapperXml, "deductInvStock");
        assertVersionedUpdate(mapperXml, "deductInvStockWithCost");
    }

    @Test
    @DisplayName("商品新增插入使用请求状态而不是固定启用")
    void shouldInsertProductStatusFromRequest() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvProductMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvProductMapper.xml");
        int insertStart = mapperXml.indexOf("<insert id=\"insertInvProduct\"");
        int insertEnd = mapperXml.indexOf("</insert>", insertStart);
        String insertSql = mapperXml.substring(insertStart, insertEnd);

        assertMapped(configuration, InvProductMapper.class, "insertInvProduct");
        assertThat(insertSql)
                .as("status column must bind the incoming product status")
                .contains("#{status}, '0', #{createBy}");
        assertThat(insertSql)
                .as("status must not be hard-coded to enabled")
                .doesNotContain("'0', '0', #{createBy}");
    }

    @Test
    @DisplayName("库存预警按商品安全库存下限判断并回退默认阈值")
    void shouldUseProductSafetyStockMinForStockWarnings() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvStockMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvStockMapper.xml");

        assertMapped(configuration, InvStockMapper.class, "selectInvStockList");
        assertMapped(configuration, InvStockMapper.class, "selectInvStockSummary");
        assertThat(mapperXml)
                .as("warning filters and summary should use per-product safety stock with default 10")
                .contains("coalesce(p.safety_stock_min, 10)");
        assertThat(mapperXml)
                .as("warning logic should not keep a bare hard-coded threshold")
                .doesNotContain("&lt;= 10");
    }

    @Test
    @DisplayName("门店要货来源库存按来源仓库商品范围和启用状态过滤")
    void shouldFilterReplenishmentSourceProductsByOwnerScope()
            throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvStockMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvStockMapper.xml");

        assertMapped(configuration, InvStockMapper.class,
                "selectInvStockList");
        assertThat(mapperXml)
                .contains(
                        "params.itemOwnerScopeDeptIds",
                        "params.transferSourceItemQualified",
                        "coalesce(s.item_type, 'product') != 'product'",
                        "p.status = '0'",
                        "gift.status = '0'",
                        "collection=\"params.itemOwnerScopeDeptIds\"");

        String statementId = InvStockMapper.class.getName()
                + ".selectInvStockList";
        InvStock scopedQuery = new InvStock();
        scopedQuery.getParams().put("transferSourceItemQualified",
                Boolean.TRUE);
        scopedQuery.getParams().put("itemOwnerScopeDeptIds",
                java.util.List.of(10L, 20L));
        String scopedSql = configuration.getMappedStatement(statementId)
                .getBoundSql(scopedQuery).getSql()
                .replaceAll("\\s+", " ").toLowerCase();
        assertThat(scopedSql)
                .contains("p.shop_dept_id in (?,?)", "p.status = '0'",
                        "gift.status = '0'")
                .doesNotContain("p.shop_dept_id is null");

        InvStock emptyScopeQuery = new InvStock();
        emptyScopeQuery.getParams().put("itemOwnerScopeDeptIds",
                java.util.List.of());
        String emptyScopeSql = configuration.getMappedStatement(statementId)
                .getBoundSql(emptyScopeQuery).getSql()
                .replaceAll("\\s+", " ").toLowerCase();
        assertThat(emptyScopeSql)
                .contains("coalesce(s.item_type, 'product') != 'product'")
                .doesNotContain("p.shop_dept_id is null",
                        "p.shop_dept_id in");
    }

    @Test
    @DisplayName("库存批次效期序列号和库位字段在库存及日志 Mapper 中可见")
    void shouldExposeBatchExpirySerialAndLocationFieldsInStockMappers() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvStockMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvStockLogMapper.xml");

        String stockMapperXml = resourceText("mapper/inventory/InvStockMapper.xml");
        String stockLogMapperXml = resourceText("mapper/inventory/InvStockLogMapper.xml");

        assertMapped(configuration, InvStockMapper.class, "selectInvStockList");
        assertMapped(configuration, InvStockLogMapper.class, "selectInvStockLogList");
        assertMapped(configuration, InvStockMapper.class, "insertInvStock");
        assertMapped(configuration, InvStockLogMapper.class, "insertInvStockLog");

        assertThat(stockMapperXml)
                .contains(
                        "property=\"batchNo\" column=\"batch_no\"",
                        "property=\"expiryDate\" column=\"expiry_date\"",
                        "property=\"serialNo\" column=\"serial_no\"",
                        "property=\"locationCode\" column=\"location_code\"",
                        "property=\"locationName\" column=\"location_name\"",
                        "s.batch_no, s.expiry_date, s.serial_no, s.location_code, s.location_name",
                        "batch_no, expiry_date, serial_no, location_code, location_name",
                        "#{batchNo}, #{expiryDate}, #{serialNo}, #{locationCode}, #{locationName}");

        assertThat(stockMapperXml)
                .as("stock list filters should support exact metadata visibility queries")
                .contains(
                        "and s.batch_no = #{batchNo}",
                        "and s.expiry_date = #{expiryDate}",
                        "and s.serial_no = #{serialNo}",
                        "and s.location_code = #{locationCode}");

        assertThat(stockLogMapperXml)
                .contains(
                        "property=\"batchNo\" column=\"batch_no\"",
                        "property=\"expiryDate\" column=\"expiry_date\"",
                        "property=\"serialNo\" column=\"serial_no\"",
                        "property=\"locationCode\" column=\"location_code\"",
                        "property=\"locationName\" column=\"location_name\"",
                        "l.batch_no, l.expiry_date, l.serial_no, l.location_code, l.location_name",
                        "batch_no, expiry_date, serial_no, location_code, location_name",
                        "#{batchNo}, #{expiryDate}, #{serialNo}, #{locationCode}, #{locationName}");

        assertThat(stockLogMapperXml)
                .as("stock log filters should support exact metadata visibility queries")
                .contains(
                        "and l.batch_no = #{batchNo}",
                        "and l.expiry_date = #{expiryDate}",
                        "and l.serial_no = #{serialNo}",
                        "and l.location_code = #{locationCode}");
    }

    @Test
    @DisplayName("库存现量和流水支持商品 OE 礼盒通用物料字段")
    void shouldExposeGenericInventoryItemFieldsInStockMappers() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvStockMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvStockLogMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvDeliveryNoticeDetailMapper.xml");

        String stockMapperXml = resourceText("mapper/inventory/InvStockMapper.xml");
        String stockLogMapperXml = resourceText("mapper/inventory/InvStockLogMapper.xml");
        String deliveryNoticeDetailMapperXml = resourceText("mapper/inventory/InvDeliveryNoticeDetailMapper.xml");

        assertMapped(configuration, InvStockMapper.class, "selectInvStockByItemShopWarehouse");
        assertMapped(configuration, InvStockMapper.class, "selectInvStockByItemShopWarehouseForUpdate");
        assertMapped(configuration, InvStockMapper.class, "selectInvStockList");
        assertMapped(configuration, InvStockLogMapper.class, "selectInvStockLogList");
        assertMapped(configuration, InvDeliveryNoticeDetailMapper.class, "batchInsertInvDeliveryNoticeDetail");

        assertThat(stockMapperXml)
                .contains(
                        "property=\"itemType\" column=\"item_type\"",
                        "property=\"itemId\" column=\"item_id\"",
                        "s.item_type",
                        "s.item_id",
                        "left join inv_oe_item oe",
                        "left join inv_gift_box gift",
                        "coalesce(s.item_type, 'product')",
                        "coalesce(s.item_id, s.product_id)",
                        "and coalesce(s.item_type, 'product') = #{itemType}",
                        "and coalesce(s.item_id, s.product_id) = #{itemId}",
                        "<when test=\"itemType == 'oe'\">",
                        "oe.category_id = #{categoryId}",
                        "<when test=\"itemType == 'gift'\">",
                        "gift.category_id = #{categoryId}");

        assertThat(stockLogMapperXml)
                .contains(
                        "property=\"itemType\" column=\"item_type\"",
                        "property=\"itemId\" column=\"item_id\"",
                        "l.item_type",
                        "l.item_id",
                        "left join inv_oe_item oe",
                        "left join inv_gift_box gift");

        assertThat(deliveryNoticeDetailMapperXml)
                .contains(
                        "property=\"itemType\" column=\"item_type\"",
                        "property=\"itemId\" column=\"item_id\"",
                        "item_type, item_id, item_code, item_name",
                        "coalesce(#{item.itemType}, 'product')",
                        "coalesce(#{item.itemId}, #{item.productId})");
    }

    @Test
    @DisplayName("调拨审批规则不宽松匹配所有区域和大区规则")
    void shouldNotBroadlyMatchRegionAndAreaTransferApprovalRules() throws Exception
    {
        String mapperXml = resourceText("mapper/inventory/InvTransferApprovalRuleMapper.xml");

        assertThat(mapperXml)
                .as("region and area transfer approval rules must still match by scope_id")
                .doesNotContain("or scope_type in ('region', 'area')");
    }

    @Test
    @DisplayName("调拨审批规则编辑和删除使用数据库乐观锁")
    void shouldUseOptimisticLockForTransferApprovalRuleWrites() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvTransferApprovalRuleMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvTransferApprovalRuleMapper.xml");
        assertMapped(configuration, InvTransferApprovalRuleMapper.class, "updateRule");
        assertMapped(configuration, InvTransferApprovalRuleMapper.class, "deleteRuleByIdAndVersion");
        assertThat(mapperXml).contains(
                "version = version + 1",
                "and version = #{version}",
                "and version = #{expectedVersion}");
    }

    @Test
    @DisplayName("调拨审批规则预筛按调出和调入部门祖先匹配区域大区")
    void shouldPrefilterRegionAndAreaTransferApprovalRulesByDeptAncestors() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvTransferApprovalRuleMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvTransferApprovalRuleMapper.xml");
        int selectStart = mapperXml.indexOf("<select id=\"selectEnabledRulesForMatch\"");
        int selectEnd = mapperXml.indexOf("</select>", selectStart);
        String matchSql = mapperXml.substring(selectStart, selectEnd);

        assertMapped(configuration, InvTransferApprovalRuleMapper.class, "selectEnabledRulesForMatch");
        assertThat(matchSql)
                .as("dept/from/to dept rules must remain exact while region/area can match ancestors")
                .contains(
                        "scope_type in ('from_dept', 'dept') and scope_id = #{fromDeptId}",
                        "scope_type in ('to_dept', 'dept') and scope_id = #{toDeptId}",
                        "scope_type in ('region', 'area')",
                        "from_dept.dept_id = #{fromDeptId}",
                        "to_dept.dept_id = #{toDeptId}",
                        "find_in_set(inv_transfer_approval_rule.scope_id, from_dept.ancestors)",
                        "find_in_set(inv_transfer_approval_rule.scope_id, to_dept.ancestors)")
                .doesNotContain(
                        "scope_type in ('from_dept', 'dept', 'region', 'area')",
                        "scope_type in ('to_dept', 'dept', 'region', 'area')",
                        "or scope_type in ('region', 'area')");
    }

    @Test
    @DisplayName("运营总监和总经理按目标门店责任范围匹配")
    void shouldResolveFixedTransferApproversByTargetStoreScope() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvTransferApprovalCandidateMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvTransferApprovalCandidateMapper.xml");
        assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectCoveredUsersByPostCode");
        int selectStart = mapperXml.indexOf("<select id=\"selectCoveredUsersByPostCode\"");
        int selectEnd = mapperXml.indexOf("</select>", selectStart);
        String candidateSql = mapperXml.substring(selectStart, selectEnd);

        assertThat(candidateSql)
                .as("fixed approvers must cover the target store and remain active and authorized")
                .contains(
                        "join sys_dept target_dept on target_dept.dept_id = #{targetDeptId}",
                        "join sys_dept scope_dept on scope_dept.dept_id = us.dept_id",
                        "find_in_set(scope_dept.dept_id, target_dept.ancestors)",
                        "p.status = '0'",
                        "profile.employee_status",
                        "m.perms = 'inv:transfer:approve'")
                .doesNotContain("us.dept_id = #{targetDeptId}")
                .doesNotContain("and u.dept_id = #{targetDeptId}");
    }

    @Test
    @DisplayName("调拨候选人受三级边界和审批权限约束")
    void shouldBindBoundedAndAuthorizedTransferApproverQueries() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvTransferApprovalCandidateMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvTransferApprovalCandidateMapper.xml");

        assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectActivePostSortByCode");
        assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "countActiveTargetStore");
        assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectDirectStoreUsersByPostCode");
        assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectCoveredHigherPostUsers");
        assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectCoveredUsersByPostCode");
        assertThat(mapperXml)
                .contains(
                        "p.post_code = #{postCode}",
                        "u.dept_id = target_dept.dept_id",
                        "direct_scope.dept_id = target_dept.dept_id",
                        "find_in_set(scope_dept.dept_id, target_dept.ancestors)",
                        "p.post_sort &gt; #{executiveBoundarySort}",
                        "p.post_sort &lt; #{managerPostSort}",
                        "excludedPostCodes",
                        "m.perms = 'inv:transfer:approve'",
                        "p.status = '0'",
                        "profile.employee_status",
                        "coalesce(nullif(trim(u.nick_name), ''), '姓名未配置') as userName",
                        "order by u.user_id")
                .contains("order by p.post_sort desc, u.user_id")
                .doesNotContain("excludedUserIds")
                .doesNotContain("u.user_name) as userName")
                .doesNotContain(
                        "selectApprovalOrgProfile",
                        "selectHighestLeaderUsers",
                        "profile.dept_level3_name",
                        "profile.store_name");
    }

    @Test
    @DisplayName("调拨审批进度只读查询都有 Mapper 绑定")
    void shouldBindTransferApprovalProgressQueries() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvTransferApprovalInstanceMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvTransferApprovalTaskMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvTransferStatusLogMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvTransferApprovalCandidateMapper.xml");

        assertMapped(configuration, InvTransferApprovalInstanceMapper.class, "selectInstancesByTransferId");
        assertMapped(configuration, InvTransferApprovalInstanceMapper.class,
                "selectRunningInstancesByTransferIdForUpdate");
        assertMapped(configuration, InvTransferApprovalInstanceMapper.class,
                "closeRunningInstancesByTransferId");
        assertMapped(configuration, InvTransferApprovalTaskMapper.class, "selectTasksByInstanceIds");
        assertMapped(configuration, InvTransferApprovalTaskMapper.class, "selectApprovalSummariesByTransferIds");
        assertMapped(configuration, InvTransferApprovalTaskMapper.class,
                "skipPendingTasksByTransferId");
        assertMapped(configuration, InvTransferStatusLogMapper.class, "selectLogsByTransferId");
        assertMapped(configuration, InvTransferApprovalCandidateMapper.class, "selectSafeDisplayNamesByUserIds");

        String taskMapperXml = resourceText("mapper/inventory/InvTransferApprovalTaskMapper.xml");
        int summaryStart = taskMapperXml.indexOf("<select id=\"selectApprovalSummariesByTransferIds\"");
        int summaryEnd = taskMapperXml.indexOf("</select>", summaryStart);
        assertThat(summaryStart).isGreaterThanOrEqualTo(0);
        assertThat(summaryEnd).isGreaterThan(summaryStart);
        assertThat(taskMapperXml.substring(summaryStart, summaryEnd))
                .contains("when o.status = 'draft' and i.status = 'closed' then 'not_started'")
                .doesNotContain("rule_snapshot", "candidate_user_ids as currentCandidateDisplayNames");
        assertThat(taskMapperXml)
                .contains("<update id=\"skipPendingTasksByTransferId\">",
                        "set status = 'skipped'",
                        "and status = 'pending'");

        String instanceMapperXml = resourceText(
                "mapper/inventory/InvTransferApprovalInstanceMapper.xml");
        assertThat(instanceMapperXml)
                .contains("<select id=\"selectRunningInstancesByTransferIdForUpdate\"",
                        "<update id=\"closeRunningInstancesByTransferId\">",
                        "set status = 'closed'",
                        "and status = 'running'");
    }

    @Test
    @DisplayName("库存门店范围SQL支持授权上级公司继承下级门店")
    void shouldResolveInventoryShopScopeFromAuthorizedAncestor() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvDeptScopeMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvDeptScopeMapper.xml");
        int scopeStart = mapperXml.indexOf("<select id=\"countUserShopScope\"");
        int scopeEnd = mapperXml.indexOf("</select>", scopeStart);
        String scopeSql = mapperXml.substring(scopeStart, scopeEnd);

        assertMapped(configuration, InvDeptScopeMapper.class, "countUserShopScope");
        assertMapped(configuration, InvDeptScopeMapper.class,
                "selectRawBusinessRootDeptId");
        assertMapped(configuration, InvDeptScopeMapper.class,
                "selectActiveRelatedDeptIdsForReplenishment");
        assertThat(mapperXml)
                .contains("<select id=\"selectRawBusinessRootDeptId\"",
                        "substring_index(substring_index(d.ancestors, ',', 2), ',', -1)",
                        "<select id=\"selectActiveRelatedDeptIdsForReplenishment\"",
                        "current_dept.dept_type = 'WAREHOUSE'",
                        "current_dept.del_flag = '0'",
                        "current_dept.status = '0'",
                        "d.del_flag = '0'",
                        "d.status = '0'");
        assertThat(scopeSql)
                .contains(
                        "inner join sys_dept scope_dept on scope_dept.dept_id = us.dept_id",
                        "inner join sys_dept target_dept on target_dept.dept_id = #{deptId}",
                        "scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')",
                        "target_dept.dept_type in ('STORE', 'WAREHOUSE')",
                        "find_in_set(scope_dept.dept_id, target_dept.ancestors)")
                .doesNotContain("and us.dept_id = #{deptId}");

        int listStart = mapperXml.indexOf("<select id=\"selectUserStoreScopeDeptIds\"");
        int listEnd = mapperXml.indexOf("</select>", listStart);
        String listSql = mapperXml.substring(listStart, listEnd);
        assertThat(listSql)
                .contains(
                        "select d.dept_id",
                        "scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')",
                        "find_in_set(scope_dept.dept_id, d.ancestors)",
                        "group by d.dept_id, d.parent_id, d.order_num",
                        "order by d.parent_id, d.order_num, d.dept_id")
                .doesNotContain(
                        "select us.dept_id",
                        "select distinct d.dept_id");
    }

    @Test
    @DisplayName("调拨 Mapper 持久化参考成本和总价字段")
    void shouldPersistTransferReferenceAmountFields() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvTransferOrderMapper.xml");
        parseMapper(configuration, "mapper/inventory/InvTransferDetailMapper.xml");

        String orderMapperXml = resourceText("mapper/inventory/InvTransferOrderMapper.xml");
        String detailMapperXml = resourceText("mapper/inventory/InvTransferDetailMapper.xml");

        assertMapped(configuration, InvTransferOrderMapper.class, "insertInvTransferOrder");
        assertMapped(configuration, InvTransferOrderMapper.class,
                "selectBySourceBusinessTypeIdWarehouse");
        assertMapped(configuration, InvTransferOrderMapper.class,
                "selectBySourceBusinessTypeIdWarehouseForUpdate");
        assertMapped(configuration, InvTransferDetailMapper.class, "batchInsertInvTransferDetail");
        assertThat(orderMapperXml)
                .contains(
                        "property=\"detailLineCount\" column=\"detail_line_count\"",
                        "where line.transfer_id = inv_transfer_order.transfer_id",
                        "<if test=\"params.rowLimit != null\">",
                        "limit #{params.rowLimit}",
                        "property=\"totalAmount\" column=\"total_amount\"",
                        "status, total_quantity, total_amount, transfer_type",
                        "#{totalAmount}",
                        "total_amount = #{totalAmount}",
                        "source_business_type = #{sourceBusinessType}",
                        "source_business_id = #{sourceBusinessId}",
                        "from_warehouse_id = #{fromWarehouseId}",
                        "limit 1 for update");
        assertThat(detailMapperXml)
                .contains(
                        "property=\"costPrice\" column=\"cost_price\"",
                        "property=\"amount\" column=\"amount\"",
                        "cost_price, amount",
                        "#{item.costPrice}",
                        "#{item.amount}");
    }

    @Test
    @DisplayName("发货通知列表按仓库过滤时兼容通知明细仓库")
    void shouldFilterDeliveryNoticeByNoticeOrDetailWarehouse() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvDeliveryNoticeMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvDeliveryNoticeMapper.xml");
        int selectStart = mapperXml.indexOf("<select id=\"selectInvDeliveryNoticeList\"");
        int selectEnd = mapperXml.indexOf("</select>", selectStart);
        String selectSql = mapperXml.substring(selectStart, selectEnd);

        assertMapped(configuration, InvDeliveryNoticeMapper.class, "selectInvDeliveryNoticeList");
        assertThat(selectSql)
                .as("warehouse context must use the frozen notice-detail warehouse")
                .contains(
                        "n.warehouse_id = #{warehouseId}",
                        "nd_warehouse.warehouse_id = #{warehouseId}");
        assertThat(selectSql)
                .doesNotContain("inv_sales_detail sd_warehouse",
                        "sd_warehouse.warehouse_id");
        assertThat(mapperXml)
                .contains("count(distinct nd.warehouse_id)",
                        "planned_warehouse.dept_id = nd.warehouse_id",
                        "nd_scope.warehouse_id in")
                .doesNotContain("inv_sales_detail sd_warehouse",
                        "inv_sales_detail sd_scope",
                        "inner join inv_sales_detail sd on");
        assertThat(selectSql)
                .as("warehouse filtering must not require the notice shop_dept_id to equal the warehouse")
                .doesNotContain("and n.warehouse_id = #{warehouseId}</if>");
    }

    @Test
    @DisplayName("发货通知明细返回每行所属仓库")
    void shouldExposeDeliveryNoticeDetailWarehouse() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvDeliveryNoticeDetailMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvDeliveryNoticeDetailMapper.xml");

        assertMapped(configuration, InvDeliveryNoticeDetailMapper.class, "selectInvDeliveryNoticeDetailByNoticeId");
        assertMapped(configuration, InvDeliveryNoticeDetailMapper.class,
                "accumulateDelivery");
        assertThat(mapperXml)
                .contains(
                        "property=\"warehouseId\" column=\"warehouse_id\"",
                        "property=\"warehouseName\" column=\"warehouse_name\"",
                        "d.warehouse_id",
                        "left join sys_dept warehouse_dept on warehouse_dept.dept_id = d.warehouse_id",
                        "warehouse_dept.dept_name as warehouse_name",
                        "delivered_qty = coalesce(delivered_qty, 0) + #{batchQty}",
                        "delivered_cost_amount = coalesce(delivered_cost_amount, 0) + #{batchCost}",
                        "coalesce(delivered_qty, 0) = #{expectedDeliveredQty}");
        assertThat(mapperXml)
                .as("delivery notice details must keep their frozen warehouse snapshot")
                .doesNotContain("left join inv_sales_detail sd on sd.detail_id = d.sales_detail_id");
    }

    @Test
    @DisplayName("调拨列表只隔离草稿并支持授权店铺交集筛选")
    void shouldBindTransferAuditFieldsAndScopedStoreFilter() throws Exception
    {
        Configuration configuration = new Configuration();
        registerAliases(configuration);
        parseMapper(configuration, "mapper/inventory/InvTransferOrderMapper.xml");

        String mapperXml = resourceText("mapper/inventory/InvTransferOrderMapper.xml");
        assertThat(mapperXml)
                .contains(
                        "property=\"createdByUserId\" column=\"created_by_user_id\"",
                        "property=\"createdByName\" column=\"created_by_name\"",
                        "property=\"submittedByUserId\" column=\"submitted_by_user_id\"",
                        "property=\"submittedByName\" column=\"submitted_by_name\"",
                        "property=\"toDeptHierarchy\" column=\"to_dept_hierarchy\"",
                        "params.hideDraftTransfers",
                        "params.storeDeptId",
                        "params.currentScopeDeptId",
                        "params.scopeDeptIds",
                        "${params.dataScope}",
                        "from_dept_id = #{params.storeDeptId}",
                        "to_dept_id = #{params.storeDeptId}",
                        "group_concat(parent_dept.dept_name",
                        "find_in_set(parent_dept.dept_id, target_dept.ancestors)",
                        "separator ' / '")
                .doesNotContain("hidePreApprovalTransfers", "status not in ('draft', 'submitted')");

        String statementId = InvTransferOrderMapper.class.getName()
                + ".selectInvTransferOrderList";
        assertThat(configuration.getMappedStatement(statementId).getResultMaps())
                .flatExtracting(resultMap -> resultMap.getResultMappings())
                .extracting(mapping -> mapping.getProperty())
                .contains("createdByUserId", "createdByName",
                        "submittedByUserId", "submittedByName",
                        "toDeptHierarchy");
        assertThat(InvTransferOrder.class.getDeclaredField("createdByUserId")
                .getAnnotation(Excel.class)).isNull();
        assertThat(InvTransferOrder.class.getDeclaredField("createdByName")
                .getAnnotation(Excel.class)).isNull();
        assertThat(InvTransferOrder.class.getDeclaredField("submittedByUserId")
                .getAnnotation(Excel.class)).isNull();
        assertThat(InvTransferOrder.class.getDeclaredField("submittedByName")
                .getAnnotation(Excel.class)).isNull();
        assertThat(InvTransferOrder.class.getDeclaredField("toDeptHierarchy")
                .getAnnotation(Excel.class)).isNull();

        InvTransferOrder query = new InvTransferOrder();
        query.getParams().put("hideDraftTransfers", true);
        query.getParams().put("storeDeptId", 202L);
        BoundSql boundSql = configuration.getMappedStatement(statementId)
                .getBoundSql(query);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").toLowerCase();
        assertThat(sql)
                .contains("status <> 'draft'")
                .contains("(from_dept_id = ? or to_dept_id = ?)");
    }

    private static void registerAliases(Configuration configuration)
    {
        configuration.getTypeAliasRegistry().registerAlias("InvPurchaseDetail", InvPurchaseDetail.class);
        configuration.getTypeAliasRegistry().registerAlias("InvDeliveryNotice", InvDeliveryNotice.class);
        configuration.getTypeAliasRegistry().registerAlias("InvDeliveryNoticeDetail", InvDeliveryNoticeDetail.class);
        configuration.getTypeAliasRegistry().registerAlias("InvProduct", InvProduct.class);
        configuration.getTypeAliasRegistry().registerAlias("InvSalesDetail", InvSalesDetail.class);
        configuration.getTypeAliasRegistry().registerAlias("InvPurchaseReturnDetail", InvPurchaseReturnDetail.class);
        configuration.getTypeAliasRegistry().registerAlias("InvSalesReturnDetail", InvSalesReturnDetail.class);
        configuration.getTypeAliasRegistry().registerAlias("InvStock", InvStock.class);
        configuration.getTypeAliasRegistry().registerAlias("InvStockLog", InvStockLog.class);
        configuration.getTypeAliasRegistry().registerAlias("InvTransferApprovalRule", InvTransferApprovalRule.class);
        configuration.getTypeAliasRegistry().registerAlias("InvTransferApprovalInstance", InvTransferApprovalInstance.class);
        configuration.getTypeAliasRegistry().registerAlias("InvTransferApprovalTask", InvTransferApprovalTask.class);
        configuration.getTypeAliasRegistry().registerAlias("InvTransferDetail", InvTransferDetail.class);
        configuration.getTypeAliasRegistry().registerAlias("InvTransferOrder", InvTransferOrder.class);
        configuration.getTypeAliasRegistry().registerAlias("InvTransferStatusLog", InvTransferStatusLog.class);
    }

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(inputStream, configuration, resource,
                    configuration.getSqlFragments());
            mapperBuilder.parse();
        }
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertMapped(Configuration configuration, Class<?> mapperType, String methodName)
    {
        assertThat(configuration.hasStatement(mapperType.getName() + "." + methodName))
                .as(mapperType.getSimpleName() + "." + methodName)
                .isTrue();
    }

    private static void assertVersionedUpdate(String mapperXml, String updateId)
    {
        int updateStart = mapperXml.indexOf("<update id=\"" + updateId + "\"");
        int updateEnd = mapperXml.indexOf("</update>", updateStart);
        String updateSql = mapperXml.substring(updateStart, updateEnd);

        assertThat(updateSql)
                .as(updateId + " should bump version and guard by old version")
                .contains("version = version + 1", "stock_id = #{stockId}", "version = #{version}");
    }
}
