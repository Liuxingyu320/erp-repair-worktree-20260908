package com.erp.inventory.mapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import java.sql.Statement;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.erp.inventory.domain.InvOutboundRecord;
import com.erp.inventory.domain.InvSalesReturnDetail;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.service.impl.InvSalesReturnServiceImpl;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "IMAGE_GALLERY_TEST_JDBC_URL", matches = ".+codex_image_gallery_.+")
class InvSalesReturnCostMySqlTest {
    @Test void mapsOnlyTheSelectedSourceLineAndRejectsCrossOrderNoticeEvidence() throws Exception {
        try (SqlSession session = openSession()) {
            try {
                try (Statement sql = session.getConnection().createStatement()) {
                    sql.executeUpdate("insert into inv_delivery_notice (notice_id,notice_no,sales_order_id,sales_order_no,shop_dept_id,customer_name) values (900901,'TEST-COST-1',10,'SO-10',1,'isolated'),(900902,'TEST-COST-2',11,'SO-11',1,'isolated')");
                    sql.executeUpdate("insert into inv_delivery_notice_detail (detail_id,notice_id,sales_detail_id,item_type,item_id,product_name,notice_qty,warehouse_id) values (900911,900901,601,'product',101,'first',2,1),(900912,900901,602,'product',101,'second',2,1),(900913,900901,603,'gift',101,'gift',2,1),(900914,900902,601,'product',101,'cross-order',2,1)");
                    sql.executeUpdate("insert into inv_outbound_record (sales_order_id,item_type,item_id,shop_dept_id,quantity,notice_id,notice_detail_id,cost_amount) values (10,'product',101,1,2,900901,900911,20),(10,'product',101,1,2,900901,900912,60),(10,'gift',101,1,2,900901,900913,80),(10,'product',101,1,2,900902,900914,999)");
                }
                InvOutboundRecordMapper mapper = session.getMapper(InvOutboundRecordMapper.class);
                assertThat(mapper.selectInvOutboundRecordBySalesDetailId(10L, 601L)).singleElement().satisfies(row -> assertThat(row.getCostAmount()).isEqualByComparingTo("20"));
                assertThat(mapper.selectInvOutboundRecordBySalesDetailId(10L, 602L)).singleElement().satisfies(row -> assertThat(row.getCostAmount()).isEqualByComparingTo("60"));
                assertThat(mapper.selectInvOutboundRecordBySalesDetailId(10L, 603L)).singleElement().satisfies(row -> {
                    assertThat(row.getItemType()).isEqualTo("gift"); assertThat(row.getCostAmount()).isEqualByComparingTo("80");
                });
            } finally {
                session.rollback(true);
            }
        }
    }
    @Test void repeatedSourceRowsUseEarlierMovementInSameTransactionAndSettleCost() throws Exception {
        try (SqlSession session = openSession()) {
            try {
                try (Statement sql = session.getConnection().createStatement()) {
                    sql.executeUpdate("insert into inv_sales_return (return_id,return_no,sales_order_id,sales_order_no,return_title,customer_name,shop_dept_id,status) values (900901,'TEST-COST',10,'SO-10','test','isolated',1,'submitted')");
                }
                InvSalesReturnDetailMapper mapper = session.getMapper(InvSalesReturnDetailMapper.class);
                InvSalesReturnDetail first = detail(601L, "1"); InvSalesReturnDetail second = detail(601L, "2");
                mapper.insertInvSalesReturnDetail(first); mapper.insertInvSalesReturnDetail(second);
                assertThat(mapper.selectReturnedCostFacts(10L, 601L, "product", 101L)).isEmpty();
                InvSalesReturnServiceImpl service = new InvSalesReturnServiceImpl();
                ReflectionTestUtils.setField(service, "salesReturnDetailMapper", mapper);
                InvOutboundRecordMapper outbound = mock(InvOutboundRecordMapper.class);
                ReflectionTestUtils.setField(service, "outboundRecordMapper", outbound);
                InvOutboundRecord record = new InvOutboundRecord(); record.setItemType("product"); record.setItemId(101L);
                record.setQuantity(new BigDecimal("3")); record.setCostAmount(new BigDecimal("5"));
                when(outbound.selectInvOutboundRecordBySalesDetailId(10L, 601L)).thenReturn(List.of(record));
                InvSalesDetail source = new InvSalesDetail(); source.setDetailId(601L); source.setItemType("product");
                source.setItemId(101L); source.setDeliveredQuantity(new BigDecimal("3"));
                List<InvSalesReturnDetail> rows = mapper.selectInvSalesReturnDetailByReturnIdForUpdate(900901L);
                rows.sort(java.util.Comparator.comparing(InvSalesReturnDetail::getDetailId));
                for (InvSalesReturnDetail row : rows) {
                    BigDecimal amount = ReflectionTestUtils.invokeMethod(service, "resolveReturnCostAmount", 10L, row, source, List.of(source), row.getQuantity());
                    row.setReturnedCostAmount(amount); row.setReturnedQuantity(row.getQuantity());
                    assertThat(mapper.updateInvSalesReturnDetail(row)).isEqualTo(1);
                }
                assertThat(rows.get(0).getReturnedCostAmount()).isEqualByComparingTo("1.67");
                assertThat(rows.get(1).getReturnedCostAmount()).isEqualByComparingTo("3.33");
                assertThat(mapper.selectReturnedCostFacts(10L, 601L, "product", 101L)).hasSize(2);
            } finally {
                session.rollback(true);
            }
        }
    }

    @Test void costFactsExcludeReservationsButExposeUnknownCompletedLegacyReturns() throws Exception {
        try (SqlSession session = openSession()) {
            try {
                try (Statement sql = session.getConnection().createStatement()) {
                    sql.executeUpdate("insert into inv_sales_return (return_id,return_no,sales_order_id,sales_order_no,return_title,customer_name,shop_dept_id,status) values (900901,'TEST-COST',10,'SO-10','test','isolated',1,'submitted'),(900902,'TEST-OLD',10,'SO-10','test','isolated',1,'returned')");
                    sql.executeUpdate("insert into inv_sales_return_detail (return_id,sales_detail_id,item_type,item_id,product_id,product_name,quantity,unit_price,amount,returned_quantity,returned_cost_amount) values (900901,601,'product',101,101,'reserved',1,1,1,0,0),(900902,601,'product',101,101,'known',1,1,1,1,5),(900902,null,null,null,101,'old',1,1,1,1,null),(900902,602,'product',101,101,'different-line',1,1,1,1,6),(900902,603,'gift',101,null,'gift',1,1,1,1,8)");
                }
                List<InvSalesReturnDetail> facts = session.getMapper(InvSalesReturnDetailMapper.class).selectReturnedCostFacts(10L, 601L, "product", 101L);
                assertThat(facts).hasSize(2);
                assertThat(facts.stream().filter(row -> row.getSalesDetailId() == null)).singleElement().satisfies(row -> assertThat(row.getReturnedCostAmount()).isNull());
            } finally {
                session.rollback(true);
            }
        }
    }

    @Test void reportUsesExactReturnedAmountWhileOldLogsKeepKnownHistoricalFormula() throws Exception {
        try (SqlSession session = openSession()) {
            try {
                try (Statement sql = session.getConnection().createStatement()) {
                    sql.executeUpdate("insert into inv_sales_return (return_id,return_no,sales_order_id,sales_order_no,return_title,customer_name,shop_dept_id,status) values (900901,'TEST-COST',10,'SO-10','test','isolated',1,'returned')");
                }
                InvStockLogMapper logs = session.getMapper(InvStockLogMapper.class);
                InvStockLog log = new InvStockLog(); log.setItemType("product"); log.setItemId(101L); log.setProductId(101L);
                log.setShopDeptId(1L); log.setMovementType("sales_return_in"); log.setBusinessType("sales_return"); log.setBusinessId(900901L);
                log.setChangeQuantity(new BigDecimal("3")); log.setCostPrice(new BigDecimal("1.67")); log.setCostAmount(new BigDecimal("5"));
                logs.insertInvStockLog(log);
                InvReportMapper report = session.getMapper(InvReportMapper.class);
                assertThat(report.selectReportSummary(new InvStock()).getSalesCost()).isEqualByComparingTo("-5");
                log.setLogId(null); log.setChangeQuantity(BigDecimal.ONE); log.setCostPrice(new BigDecimal("2")); log.setCostAmount(null);
                logs.insertInvStockLog(log);
                assertThat(report.selectReportSummary(new InvStock()).getSalesCost()).isEqualByComparingTo("-7");
            } finally {
                session.rollback(true);
            }
        }
    }

    @Test void fractionalDeliveryAndFullReturnHaveZeroNetReportCost() throws Exception {
        try (SqlSession session = openSession()) {
            try {
                try (Statement sql = session.getConnection().createStatement()) {
                    sql.executeUpdate("insert into inv_sales_order (order_id,order_no,shop_dept_id,applicant_id,status) values (10,'SO-10',1,1,'delivered')");
                    sql.executeUpdate("insert into inv_sales_return (return_id,return_no,sales_order_id,sales_order_no,return_title,customer_name,shop_dept_id,status) values (900901,'TEST-COST',10,'SO-10','test','isolated',1,'returned')");
                }
                InvStockLogMapper logs = session.getMapper(InvStockLogMapper.class);
                InvStockLog log = new InvStockLog(); log.setItemType("product"); log.setItemId(101L); log.setProductId(101L);
                log.setShopDeptId(1L); log.setMovementType("sales_out"); log.setBusinessType("sales"); log.setBusinessId(10L);
                log.setChangeQuantity(new BigDecimal("-0.50")); log.setCostPrice(new BigDecimal("0.01")); log.setCostAmount(new BigDecimal("0.01"));
                logs.insertInvStockLog(log);
                InvReportMapper report = session.getMapper(InvReportMapper.class);
                assertThat(report.selectReportSummary(new InvStock()).getSalesCost()).isEqualByComparingTo("0.01");
                log.setLogId(null); log.setMovementType("sales_return_in"); log.setBusinessType("sales_return"); log.setBusinessId(900901L);
                log.setChangeQuantity(new BigDecimal("0.50")); log.setCostPrice(new BigDecimal("0.02"));
                logs.insertInvStockLog(log);
                assertThat(report.selectReportSummary(new InvStock()).getSalesCost()).isEqualByComparingTo("0");
            } finally {
                session.rollback(true);
            }
        }
    }

    private static InvSalesReturnDetail detail(Long sourceId, String quantity) {
        InvSalesReturnDetail row = new InvSalesReturnDetail(); row.setReturnId(900901L); row.setSalesDetailId(sourceId);
        row.setItemType("product"); row.setItemId(101L); row.setProductId(101L); row.setProductName("test");
        row.setQuantity(new BigDecimal(quantity)); row.setUnitPrice(BigDecimal.TEN); row.setAmount(row.getQuantity().multiply(BigDecimal.TEN));
        return row;
    }

    private SqlSession openSession() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(System.getenv("IMAGE_GALLERY_TEST_JDBC_URL"), "root", "");
        Configuration config = new Configuration(new Environment("cost-test", new JdbcTransactionFactory(), ds));
        config.getTypeAliasRegistry().registerAlias("InvOutboundRecord", InvOutboundRecord.class);
        config.getTypeAliasRegistry().registerAlias("InvSalesReturnDetail", InvSalesReturnDetail.class);
        config.getTypeAliasRegistry().registerAlias("InvStockLog", InvStockLog.class);
        config.getTypeAliasRegistry().registerAlias("InvStock", InvStock.class);
        for (String mapper : new String[] { "InvOutboundRecordMapper", "InvSalesReturnDetailMapper", "InvStockLogMapper", "InvReportMapper" }) {
            String path = "mapper/inventory/" + mapper + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
                new XMLMapperBuilder(input, config, path, config.getSqlFragments()).parse();
            }
        }
        return new SqlSessionFactoryBuilder().build(config).openSession(false);
    }

}
