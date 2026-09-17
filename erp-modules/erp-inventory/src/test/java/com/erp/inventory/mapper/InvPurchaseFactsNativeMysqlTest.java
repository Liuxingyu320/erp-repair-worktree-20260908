package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.vo.InvReportSummary;

@EnabledIfEnvironmentVariable(named = "ERP_REPAIR_NATIVE_MYSQL", matches = "1")
class InvPurchaseFactsNativeMysqlTest
{
    @Test void pendingReceiptsAreExcludedAndAcceptedEventsUseActualDates() throws Exception
    {
        String base = "jdbc:mysql://127.0.0.1:3306/";
        String suffix = "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai";
        JdbcTemplate admin = new JdbcTemplate(new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + "mysql" + suffix, "root", ""));
        String db = "erp_facts_repair_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create database " + db);
        try
        {
            UnpooledDataSource ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + db + suffix, "root", "");
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            String schema = Files.readString(Path.of("../../scripts/fixtures/repair-purchase-facts-20260909.sql"));
            for (String statement : schema.split(";\\s*(?:\\r?\\n|$)")) if (!statement.isBlank()) jdbc.execute(statement);
            jdbc.execute("ALTER TABLE inv_purchase_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0");
            Configuration config = new Configuration(new Environment("native", new JdbcTransactionFactory(), ds));
            config.getTypeAliasRegistry().registerAlias("InvStock", InvStock.class);
            config.getTypeAliasRegistry().registerAlias("InvPurchaseOrder", InvPurchaseOrder.class);
            config.getTypeAliasRegistry().registerAlias("InvPurchaseDetail", InvPurchaseDetail.class);
            for (String name : new String[] { "InvPurchaseDetailMapper", "InvPurchaseOrderMapper", "InvReportMapper" })
            {
                String resource = "mapper/inventory/" + name + ".xml";
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource))
                { new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse(); }
            }
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
            jdbc.update("insert into sys_dept(dept_id,dept_name) values(20,'test warehouse')");
            jdbc.update("insert into inv_product(product_id,product_name,shop_dept_id) values(1,'test',20)");
            jdbc.update("insert into inv_purchase_order(order_id,order_no,applicant_id,shop_dept_id,status,order_date) values(10,'test-purchase',7,20,'submitted','2026-01-01')");
            jdbc.update("insert into inv_purchase_detail(detail_id,order_id,item_id,product_id,unit_price,quantity,received_quantity) values(101,10,1,1,10,100,100)");
            jdbc.update("insert into inv_inbound_record(purchase_order_id,purchase_detail_id,item_id,product_id,shop_dept_id,quantity,receipt_batch_detail_id,qc_result,accepted_quantity,concession_quantity) values(10,101,1,1,20,100,6,'pending',0,0)");
            InvStock filter = new InvStock();
            filter.setShopDeptId(20L);
            filter.getParams().put("beginTime", "2026-09-09");
            filter.getParams().put("endTime", "2026-09-09");
            try (SqlSession session = factory.openSession(true))
            {
                InvPurchaseDetailMapper details = session.getMapper(InvPurchaseDetailMapper.class);
                InvReportMapper reports = session.getMapper(InvReportMapper.class);
                InvPurchaseOrderMapper orders = session.getMapper(InvPurchaseOrderMapper.class);
                InvPurchaseOrder query = new InvPurchaseOrder();
                query.setShopDeptId(20L);
                assertThat(orders.selectReturnablePurchaseOrderList(query)).isEmpty();
                assertThat(details.selectInvPurchaseDetailByOrderId(10L).get(0).getStockedQuantity()).isEqualByComparingTo("0");
                assertThat(reports.selectReportSummary(filter).getPurchaseAmount()).isEqualByComparingTo("0");
                jdbc.update("insert into inv_quality_inspection(inspection_no,receipt_batch_id,batch_detail_id,purchase_order_id,purchase_detail_id,inspected_quantity,accepted_quantity,concession_quantity,conclusion,inspection_time) values('Q1',5,6,10,101,20,20,0,'passed','2026-09-09 12:00:00'),('Q2',5,6,10,101,15,10,5,'concession','2026-09-10 12:00:00')");
                jdbc.update("update inv_inbound_record set accepted_quantity=30,concession_quantity=5");
                session.clearCache();
                assertThat(details.selectInvPurchaseDetailByOrderId(10L).get(0).getStockedQuantity()).isEqualByComparingTo("35");
                InvReportSummary accepted = reports.selectReportSummary(filter);
                assertThat(accepted.getPurchaseAmount()).isEqualByComparingTo("200");
                assertThat(accepted.getPurchaseOrderCount()).isEqualTo(1L);
                assertThat(orders.selectReturnablePurchaseOrderList(query)).hasSize(1);
                jdbc.update("insert into inv_purchase_return(return_id,purchase_order_id,purchase_order_no,return_no,return_title,shop_dept_id,supplier_name,status,return_date) values(50,10,'test-purchase','test-return','test',20,'test','returned','2026-01-01')");
                jdbc.update("insert into inv_purchase_return_detail(return_id,product_id,product_name,quantity,returned_quantity,unit_price,amount) values(50,1,'test',2,2,10,20)");
                jdbc.update("insert into inv_stock_log(item_id,product_id,shop_dept_id,movement_type,business_type,business_id,change_quantity,create_time) values(1,1,20,'purchase_return_out','purchase_return',50,-2,'2026-09-09 13:00:00')");
                session.clearCache();
                assertThat(reports.selectReportSummary(filter).getPurchaseAmount()).isEqualByComparingTo("180");
                jdbc.update("update inv_purchase_return_detail set quantity=35, returned_quantity=35,purchase_detail_id=101");
                session.clearCache();
                assertThat(orders.selectReturnablePurchaseOrderList(query)).isEmpty();
                filter.setShopDeptId(30L);
                assertThat(reports.selectReportSummary(filter).getPurchaseAmount()).isEqualByComparingTo("0");
            }
        }
        finally
        {
            if (db.matches("erp_facts_repair_[0-9a-f]{32}")) admin.execute("drop database " + db);
        }
    }
}
