package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.*;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import org.testcontainers.mysql.MySQLContainer;

/** Real catalog locks/counts/deletion and writer transactions in task-owned tmpfs MySQL. */
class InvCatalogReferenceMySqlIT
{
    static final List<String> REFERENCES = List.of("inv_stock",
            "inv_stock_log",
            "inv_purchase_detail",
            "inv_sales_detail",
            "inv_purchase_return_detail",
            "inv_sales_return_detail",
            "inv_delivery_notice_detail",
            "inv_inbound_record",
            "inv_outbound_record",
            "inv_transfer_detail",
            "inv_transfer_shipment_detail",
            "inv_stock_check_detail",
            "inv_receipt_batch_detail",
            "inv_transfer_reservation",
            "inv_transfer_discrepancy_detail",
            "inv_inventory_lot",
            "inv_inventory_serial",
            "inv_item_fulfillment_policy",
            "inv_stock_balance_detail",
            "inv_stock_ledger_detail",
            "inv_transfer_quarantine_reservation",
            "inv_transfer_receipt_discrepancy_adjudication_workflow_link",
            "inv_transfer_receipt_discrepancy_damage_loss_ledger",
            "inv_transfer_receipt_discrepancy_responsibility_ledger",
            "inv_transfer_receipt_discrepancy_shortage_loss_ledger",
            "inv_transfer_shipment_receipt_allocation",
            "inv_transfer_shipment_serial",
            "inv_warehouse_task_detail");
    static MySQLContainer mysql; static JdbcTemplate jdbc; static DataSourceTransactionManager manager;
    InvCatalogReferenceMapper mapper; InventoryItemResolver resolver; InvCatalogDeletionService deletion;
    static List<String> references() { return REFERENCES; }
    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("inventory.mysql.version","5.7.44");
        assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("joint_inventory_catalog")
                .withUsername("catalog_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
                .withLabels(Map.of("erp.task","joint-catalog-reference-20260913"));
        try {
            mysql.start(); assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc=new JdbcTemplate(source); manager=new DataSourceTransactionManager(source);
            jdbc.execute("create table inv_oe_item(oe_item_id bigint primary key,status char(1),del_flag char(1)) engine=InnoDB");
            jdbc.execute("create table inv_gift_box(gift_id bigint primary key,status char(1),del_flag char(1)) engine=InnoDB");
            jdbc.execute("create table oa_fixed_asset_config(id bigint primary key auto_increment,oe_item_id bigint) engine=InnoDB");
            for(String table:REFERENCES) jdbc.execute("create table "+table+"(id bigint primary key auto_increment,item_type varchar(20),item_id bigint,quantity decimal(18,4) default 0,index ref_idx(item_type,item_id)) engine=InnoDB");
        } catch(Throwable error) { mysql.stop(); throw error; }
    }
    @AfterAll static void stop() { if(mysql!=null)mysql.stop(); }
    @BeforeEach void setup() throws Exception
    {
        for(String table:REFERENCES)jdbc.update("delete from "+table);
        jdbc.update("delete from oa_fixed_asset_config");jdbc.update("delete from inv_oe_item");jdbc.update("delete from inv_gift_box");
        jdbc.update("insert into inv_oe_item values(1,'0','0'),(2,'0','0')");jdbc.update("insert into inv_gift_box values(1,'0','0'),(2,'0','0')");
        Configuration config=new Configuration(new Environment("catalog",new SpringManagedTransactionFactory(),manager.getDataSource()));
        String resource="mapper/inventory/InvCatalogReferenceMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(InvCatalogReferenceMapper.class);
        resolver=new InventoryItemResolver();ReflectionTestUtils.setField(resolver,"catalogReferenceMapper",mapper);
        deletion=deletion(mapper);login();
    }
    @AfterEach void clear(){SecurityContextHolder.remove();}
    InvCatalogDeletionService deletion(InvCatalogReferenceMapper catalog)
    {
        var value=new InvCatalogDeletionService();var scope=mock(InvDeptScopeMapper.class);
        when(scope.selectDeptTypeById(anyLong())).thenReturn("WAREHOUSE");
        ReflectionTestUtils.setField(value,"deptScopeMapper",scope);ReflectionTestUtils.setField(value,"catalogReferenceMapper",catalog);
        ReflectionTestUtils.setField(value,"transactionManager",manager);return value;
    }
    @ParameterizedTest @MethodSource("references")
    void everyHistoricalReferenceIncludingZeroFactsBlocksWholeBatch(String table)
    {
        jdbc.update("insert into "+table+"(item_type,item_id,quantity) values('oe',2,0)");
        assertThatThrownBy(()->deletion.delete("oe",new Long[]{2L,1L},10L)).isInstanceOf(ServiceException.class).hasMessageContaining("引用");
        assertThat(jdbc.queryForObject("select count(*) from inv_oe_item where del_flag='0'",Integer.class)).isEqualTo(2);
    }
    @Test void unusedGiftCanBeDeletedWhileSameNumericOeAndFixedAssetReferencesRemain()
    {
        jdbc.update("insert into oa_fixed_asset_config(oe_item_id) values(1)");
        assertThatThrownBy(()->deletion.delete("oe",new Long[]{1L},10L)).isInstanceOf(ServiceException.class);
        deletion.delete("gift",new Long[]{1L,1L},10L);
        assertThat(jdbc.queryForObject("select del_flag from inv_gift_box where gift_id=1",String.class)).isEqualTo("2");
    }
    @Test void outerRepeatableReadTransactionIsRejectedAndFreshDeletionUsesReadCommitted()
    {
        new TransactionTemplate(manager).executeWithoutResult(tx-> {
            jdbc.queryForObject("select count(*) from inv_oe_item",Integer.class);
            assertThatThrownBy(()->deletion.delete("oe",new Long[]{1L},10L)).isInstanceOf(ServiceException.class).hasMessageContaining("嵌套");
        });
        var checked=deletion(delegate(countId->{assertThat(jdbc.queryForObject("select @@transaction_isolation",String.class)).isEqualTo("READ-COMMITTED");}));
        checked.delete("oe",new Long[]{1L},10L);
    }
    @Test void writerHoldingMasterCommitsBeforeDeleteAndNewReferenceIsVisible() throws Exception
    {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var writer=pool.submit(()->new TransactionTemplate(manager).execute(tx->{
                resolver.lockReferences(List.of(new InventoryItemResolver.ReferenceKey("oe",1L)));
                locked.countDown();await(release);jdbc.update("insert into inv_purchase_detail(item_type,item_id) values('oe',1)");return true;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();var attempt=pool.submit(()->{login();try{deletion.delete("oe",new Long[]{1L},10L);return null;}catch(ServiceException error){return error;}finally{SecurityContextHolder.remove();}});
            assertThatThrownBy(()->attempt.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();assertThat(writer.get(10,TimeUnit.SECONDS)).isTrue();assertThat(attempt.get(10,TimeUnit.SECONDS)).isInstanceOf(ServiceException.class);
            assertThat(jdbc.queryForObject("select del_flag from inv_oe_item where oe_item_id=1",String.class)).isEqualTo("0");
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void deletionHoldingMasterCommitsBeforeWriterWhichCannotCreateAnOrphan() throws Exception
    {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        var paused=deletion(delegate(id->{locked.countDown();await(release);}));
        try {
            var deleting=pool.submit(()->{login();try{paused.delete("oe",new Long[]{1L},10L);}finally{SecurityContextHolder.remove();}});
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();var writing=pool.submit(()->{
                try { new TransactionTemplate(manager).executeWithoutResult(tx->{
                    resolver.lockReferences(List.of(new InventoryItemResolver.ReferenceKey("oe",1L)));
                    jdbc.update("insert into inv_sales_detail(item_type,item_id) values('oe',1)");
                });return null; }catch(ServiceException error){return error;}
            });
            assertThatThrownBy(()->writing.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();deleting.get(10,TimeUnit.SECONDS);assertThat(writing.get(10,TimeUnit.SECONDS)).isInstanceOf(ServiceException.class);
            assertThat(jdbc.queryForObject("select count(*) from inv_sales_detail",Integer.class)).isZero();
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void reverseBulkRequestsUseStableOrderAndDoNotPartiallyDelete() throws Exception
    {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> work=new ArrayList<>();
            for(Long[] ids:List.of(new Long[]{2L,1L},new Long[]{1L,2L}))work.add(pool.submit(()->{login();await(start);try{deletion.delete("oe",ids,10L);return true;}catch(ServiceException conflict){return false;}finally{SecurityContextHolder.remove();}}));
            start.countDown();int successes=0;for(var future:work)if(future.get(10,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from inv_oe_item where del_flag='2'",Integer.class)).isEqualTo(2);
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void actualIndexMigrationIsRepeatablePreservesExistingIndexesAndSkipsAbsentTables() throws Exception
    {
        java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath();
        while(root!=null&&!java.nio.file.Files.exists(root.resolve("sql/erp_inventory_catalog_reference_indexes_20260913.sql")))root=root.getParent();
        assertThat(root).isNotNull();
        String script=java.nio.file.Files.readString(root.resolve("sql/erp_inventory_catalog_reference_indexes_20260913.sql"));
        jdbc.execute("alter table inv_stock drop index ref_idx");
        jdbc.execute("rename table inv_warehouse_task_detail to inventory_it_absent_reference");
        jdbc.update("insert into inv_stock(item_type,item_id,quantity) values('oe',1,0)");
        try {
            for(int run=0;run<2;run++)jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
                // Session variables and PREPARE/EXECUTE must stay on this one connection.
                try(var statement=connection.createStatement()){
                    for(String sql:script.replaceAll("(?m)^--.*$", "").split(";"))if(!sql.isBlank())statement.execute(sql.trim());
                }return null;
            });
            assertThat(jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name='inv_stock' and index_name='idx_catalog_ref_20260913'",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name='inv_sales_detail' and index_name='ref_idx'",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name='inv_sales_detail' and index_name='idx_catalog_ref_20260913'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=database() and table_name='inv_warehouse_task_detail'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from inv_stock where item_type='oe' and item_id=1 and quantity=0",Integer.class)).isEqualTo(1);
            // A skipped missing schema must not turn deletion into permission to erase history.
            assertThatThrownBy(()->deletion.delete("oe",new Long[]{2L},10L)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("select del_flag from inv_oe_item where oe_item_id=2",String.class)).isEqualTo("0");
        }finally{jdbc.execute("rename table inventory_it_absent_reference to inv_warehouse_task_detail");}
    }
    InvCatalogReferenceMapper delegate(Consumer<Long> beforeCount)
    {
        return new InvCatalogReferenceMapper(){
            public String selectStatusForUpdate(String type,Long id){return mapper.selectStatusForUpdate(type,id);}
            public long countReferences(String type,Long id){beforeCount.accept(id);return mapper.countReferences(type,id);}
            public int deleteUnreferenced(String type,Long id){return mapper.deleteUnreferenced(type,id);}
        };
    }
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("barrier timed out");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}
    static void login(){SecurityContextHolder.setUserId("1");SecurityContextHolder.setUserName("catalog-it");SysUser user=new SysUser();user.setUserId(1L);user.setDeptId(10L);LoginUser login=new LoginUser();login.setUserid(1L);login.setUsername("catalog-it");login.setSysUser(user);SecurityContextHolder.set(SecurityConstants.LOGIN_USER,login);}
}
