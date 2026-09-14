package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.vo.InvReportWarningExportRow;
import com.erp.inventory.mapper.InvReportMapper;
import com.erp.inventory.service.impl.InvReportServiceImpl;

/** Actual mapper SQL against synthetic facts, never a production database. */
class InvTypedReportMySqlIT
{
    static MySQLContainer mysql; static JdbcTemplate jdbc; static InvReportServiceImpl service;
    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("inventory.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("joint_typed_report").withUsername("report_it")
            .withPassword(UUID.randomUUID().toString()).withReuse(false).withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
            .withLabels(Map.of("erp.task","joint-typed-report-20260913"));
        try {
            mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());jdbc=new JdbcTemplate(source);
            for(String fixture:List.of("purchase-receive-b04a-baseline.sql","purchase-return-b04c-baseline.sql","stock-cost-b04d-baseline.sql"))execute(resource(fixture));
            jdbc.execute("create table inv_quality_inspection(purchase_order_id bigint,purchase_detail_id bigint,accepted_quantity decimal(18,4),concession_quantity decimal(18,4),inspection_time datetime) engine=InnoDB");
            jdbc.update("insert into inv_product_category values(1,'Product category','0','0')");
            jdbc.update("insert into inv_oe_category values(1,'OE category','0','0')");
            jdbc.update("insert into inv_gift_category values(1,'Gift category','0','0')");
            jdbc.update("update inv_product set category_id=1,safety_stock_min=10,product_code='P-1',product_name='Product 1',unit='bag',spec='product spec' where product_id=1");
            jdbc.update("insert into inv_oe_item(oe_item_id,oe_item_code,oe_item_name,category_id,item_description,order_unit) values(1,'OE-1','OE 100% item',1,'OE spec','piece')");
            jdbc.update("insert into inv_gift_box(gift_id,gift_code,gift_name,category_id,spec,replenishment_unit) values(1,'GIFT-1','Gift item',1,'gift spec','box')");
            // Deliberate legacy product_id collisions on non-product rows must never borrow product names/thresholds.
            for(String type:List.of("product","oe","gift"))jdbc.update("insert into inv_stock(item_type,item_id,product_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,total_cost) values(?,1,1,20,20,5,5,100)",type);
            jdbc.update("insert into inv_stock(item_type,item_id,product_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,total_cost) values('oe',1,1,30,30,99,99,990)");
            jdbc.update("insert into inv_customer(customer_id,customer_name,shop_dept_id,status) values(1,'report customer',20,'0')");
            int id=0;for(String type:List.of("product","oe","gift")){
                id++;jdbc.update("insert into inv_sales_order(order_id,order_no,customer_id,status,shop_dept_id,applicant_id,order_date) values(?, ?,1,'delivered',20,77,'2026-09-13')",id,"REPORT-"+id);
                jdbc.update("insert into inv_sales_detail(order_id,item_type,item_id,product_id,quantity,delivered_quantity,unit_price) values(?,?,1,1,2,2,10)",id,type);
                jdbc.update("insert into inv_stock_log(item_type,item_id,product_id,shop_dept_id,warehouse_id,movement_type,business_type,business_id,change_quantity,cost_amount) values(?,1,1,20,20,'sales_out','sales',?,-2,5)",type,id);
            }
            Configuration configuration=new Configuration(new Environment("report",new SpringManagedTransactionFactory(),source));String xml="mapper/inventory/InvReportMapper.xml";
            try(InputStream input=InvTypedReportMySqlIT.class.getClassLoader().getResourceAsStream(xml)){new XMLMapperBuilder(input,configuration,xml,configuration.getSqlFragments()).parse();}
            var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(InvReportMapper.class);
            service=new InvReportServiceImpl();ReflectionTestUtils.setField(service,"reportMapper",mapper);
            var scope=mock(ShopScopeService.class);when(scope.resolveRequiredShopDept(20L)).thenReturn(20L);when(scope.resolveRequiredShopDept(30L)).thenReturn(30L);ReflectionTestUtils.setField(service,"shopScopeService",scope);
        }catch(Throwable error){mysql.stop();throw error;}
    }
    @AfterAll static void stop(){if(mysql!=null)mysql.stop();}
    @Test void equalIdsKeepThreeNamesCategoriesUnitsAndUnknownThresholds()
    {
        var rows=service.selectStockWarningList(new InvStock(),20L);assertThat(rows).hasSize(3);
        for(InvStock row:rows){assertThat(row.getItemId()).isEqualTo(1L);assertThat(row.getCategoryType()).isEqualTo(row.getItemType());assertThat(row.getAvailableQuantity()).isEqualByComparingTo("5");
            var exported=InvReportWarningExportRow.from(row);assertThat(exported.getProductCode()).isEqualTo(row.getItemCode());assertThat(exported.getCategoryName()).isEqualTo(row.getItemCategoryFullPath());assertThat(exported.getUnit()).isEqualTo(row.getItemUnit());
            if("product".equals(row.getItemType())){assertThat(row.getSafetyStockMin()).isEqualByComparingTo("10");assertThat(exported.getWarningStatus()).isEqualTo("低库存");}
            else{assertThat(row.getProductName()).isNull();assertThat(row.getSafetyStockMin()).isNull();assertThat(exported.getSafetyStockMin()).isNull();assertThat(exported.getWarningStatus()).isEqualTo("阈值未配置");}
        }
        assertThat(rows).extracting(InvStock::getItemName).containsExactlyInAnyOrder("Product 1","OE 100% item","Gift item");
        assertThat(rows).extracting(InvStock::getItemCategoryName).containsExactlyInAnyOrder("Product category","OE category","Gift category");
    }
    @Test void scopedCandidateQuerySeparatesEqualIdsAndEscapesLiteralKeyword()
    {
        var all=service.selectItemOptions(null,null,20,20L);assertThat(all).hasSize(3);assertThat(all).extracting(o->o.getItemType()+":"+o.getItemId()).doesNotHaveDuplicates();
        assertThat(service.selectItemOptions("oe","100%",20,20L)).singleElement().satisfies(o->{assertThat(o.getItemType()).isEqualTo("oe");assertThat(o.getItemName()).isEqualTo("OE 100% item");});
        assertThat(service.selectItemOptions("gift",null,20,30L)).isEmpty();
        assertThat(service.selectProductOptions(null,20,20L)).singleElement().satisfies(o->assertThat(o.getProductId()).isEqualTo(1L));
    }
    @Test void typedFilterAppliesToInventorySalesCountsAmountsAndCostLogs()
    {
        for(String type:List.of("product","oe","gift")){
            InvStock query=query(type);query.setShopDeptId(30L);query.setWarehouseId(30L);query.getParams().put("scopeDeptIds",List.of(30L));
            var summary=service.selectReportSummary(query,20L);assertThat(summary.getStockItemCount()).isEqualTo(1L);assertThat(summary.getSalesOrderCount()).isEqualTo(1L);
            assertThat(summary.getSalesAmount()).isEqualByComparingTo("20");assertThat(summary.getSalesCost()).isEqualByComparingTo("5");
            assertThat(summary.getWarningStockCount()).isEqualTo("product".equals(type)?1L:0L);
            assertThat(summary.getMissingSafetyStockCount()).isEqualTo("product".equals(type)?0L:1L);
            assertThat(service.selectStockWarningList(query(type),20L)).singleElement().satisfies(row->assertThat(row.getItemType()).isEqualTo(type));
        }
    }
    @Test void incompatibleOrUntypedFiltersFailBeforeSql()
    {
        InvStock untyped=new InvStock();untyped.setItemId(1L);assertThatThrownBy(()->service.selectReportSummary(untyped,20L)).isInstanceOf(ServiceException.class);
        InvStock mixed=query("oe");mixed.setProductId(1L);assertThatThrownBy(()->service.selectStockWarningList(mixed,20L)).isInstanceOf(ServiceException.class);
        InvStock category=query("gift");category.setCategoryId(1L);assertThatThrownBy(()->service.selectReportSummary(category,20L)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(()->service.selectItemOptions("other",null,20,20L)).isInstanceOf(ServiceException.class);
    }
    static InvStock query(String type){InvStock q=new InvStock();q.setItemType(type);q.setItemId(1L);q.getParams().put("beginTime","2026-09-13");q.getParams().put("endTime","2026-09-13");return q;}
    static String resource(String name)throws Exception{try(InputStream input=InvTypedReportMySqlIT.class.getClassLoader().getResourceAsStream(name)){return new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);}}
    static void execute(String sql){jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{try(var statement=connection.createStatement()){for(String fragment:sql.replaceAll("(?m)^\\s*--.*$","").split(";"))if(!fragment.isBlank())statement.execute(fragment);}return null;});}
}
