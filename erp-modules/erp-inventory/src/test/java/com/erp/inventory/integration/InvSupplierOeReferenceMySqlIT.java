package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.*;

/** Only fresh task-owned MySQL. Real supplier/OE mappers and both transactional services. */
class InvSupplierOeReferenceMySqlIT
{
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static SqlSessionTemplate session;
    InvSupplierMapper suppliers;
    InvSupplierServiceImpl supplierService;
    InvOeServiceImpl oeService;

    @BeforeAll static void start() throws Exception
    {
        String version = System.getProperty("f1.mysql.version", "5.7.44");
        assertThat(version).isIn("5.7.44", "8.0.36");
        mysql = new MySQLContainer("mysql:" + version).withDatabaseName("f1_supplier")
                .withUsername("f1_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withLabels(Map.of("erp.task", "b05-f1"))
                .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
        try
        {
            mysql.start(); assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
            // OE definition and supplier-name index are taken from the published source migration.
            String ddl = Files.readString(Path.of("../../sql/erp_inventory_oe_gift_management_20260706.sql"));
            for (String table : List.of("inv_oe_category", "inv_oe_item"))
            {
                var matcher = Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS " + table + " \\(.*?;").matcher(ddl);
                assertThat(matcher.find()).isTrue(); jdbc.execute(matcher.group());
            }
            jdbc.execute("alter table inv_oe_item add image_urls longtext, add purchase_reference_url varchar(1000), add purchase_reference_note varchar(500), add purchase_reference_updated_by varchar(64), add purchase_reference_updated_time datetime");
            // Minimum synthetic directory/history schema; no data or credentials copied from any current database.
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(64),ancestors varchar(500),status char(1) default '0',del_flag char(1) default '0') engine=InnoDB");
            jdbc.execute("create table inv_supplier(supplier_id bigint auto_increment primary key,supplier_name varchar(128),supplier_code varchar(64),contact_person varchar(64),contact_phone varchar(32),contact_email varchar(64),address varchar(256),settlement_method varchar(64),cooperation_status char(1),shop_dept_id bigint,status char(1),create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500),key name_scope(supplier_name,shop_dept_id)) engine=InnoDB");
            jdbc.execute("create table inv_product(product_id bigint primary key,supplier_name varchar(128),shop_dept_id bigint,del_flag char(1)) engine=InnoDB");
            jdbc.execute("create table inv_purchase_order(order_id bigint primary key,supplier_id bigint,supplier_name varchar(128),shop_dept_id bigint) engine=InnoDB");
            jdbc.execute("create table inv_purchase_return(return_id bigint primary key,supplier_name varchar(128),shop_dept_id bigint) engine=InnoDB");
            jdbc.execute("create table oa_fixed_asset_config(config_id bigint primary key,oe_item_id bigint,status char(1)) engine=InnoDB");
            Configuration config = new Configuration(new Environment("f1", new SpringManagedTransactionFactory(),source));
            config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain");
            for (String mapper : List.of("InvSupplierMapper","InvOeMapper","InvOeCategoryMapper"))
            {
                String resource = "mapper/inventory/"+mapper+".xml";
                try(InputStream input=InvSupplierOeReferenceMySqlIT.class.getClassLoader().getResourceAsStream(resource))
                { new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse(); }
            }
            session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            System.out.printf("F1 isolated image=%s container=%s host=%s port=%s%n",version,mysql.getContainerId(),mysql.getHost(),mysql.getMappedPort(3306));
        }
        catch(Throwable failure) { mysql.stop(); throw failure; }
    }
    @AfterAll static void stop()
    {
        if(mysql!=null) { String id=mysql.getContainerId(); mysql.stop(); assertThat(mysql.isRunning()).isFalse(); System.out.println("F1 cleaned container="+id); }
    }
    @BeforeEach void fixture()
    {
        for(String table:List.of("inv_oe_item","inv_oe_category","inv_supplier","inv_product","inv_purchase_order","inv_purchase_return","sys_dept")) jdbc.update("delete from "+table);
        jdbc.update("insert into sys_dept(dept_id,dept_name,ancestors) values(20,'warehouse','0'),(99,'foreign','0')");
        jdbc.update("insert into inv_supplier(supplier_id,supplier_name,shop_dept_id,status,cooperation_status) values(1,'供应商A',20,'0','0')");
        jdbc.update("insert into inv_oe_category(category_id,category_name,category_code) values(10,'杯子','C')");
        suppliers = session.getMapper(InvSupplierMapper.class);
        supplierService=wire(new InvSupplierServiceImpl()); oeService=wire(new InvOeServiceImpl()); login();
    }
    @AfterEach void clear() { SecurityContextHolder.remove(); }

    @ParameterizedTest @ValueSource(strings={"0","1"})
    void activeAndDisabledOePreventRenameAndDelete(String status)
    {
        oeService.saveOe(item(),20L); jdbc.update("update inv_oe_item set status=?",status);
        for(boolean rename:List.of(true,false)) assertThatThrownBy(() -> mutate(rename)).isInstanceOf(ServiceException.class).hasMessageContaining("OE引用");
        assertThat(name()).isEqualTo("供应商A"); assertThat(oeCount()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void softDeletedOeDoesNotBlock(boolean rename)
    {
        oeService.saveOe(item(),20L); jdbc.update("update inv_oe_item set del_flag='2'"); mutate(rename);
        assertThat(name()).isEqualTo(rename ? "供应商B" : null);
        assertThat(jdbc.queryForObject("select supplier_name from inv_oe_item",String.class)).isEqualTo("供应商A");
    }
    @Test void phoneEditPreservesOeAndLegacyReferenceBlocks()
    {
        oeService.saveOe(item(),20L); InvSupplier update=supplier("供应商A"); update.setContactPhone("123");
        supplierService.saveSupplier(update,20L);
        assertThat(jdbc.queryForObject("select supplier_name from inv_oe_item",String.class)).isEqualTo("供应商A");
        jdbc.update("delete from inv_oe_item"); jdbc.update("insert into inv_product values(1,'供应商A',20,'0')");
        assertThatThrownBy(() -> mutate(true)).isInstanceOf(ServiceException.class).hasMessageContaining("商品或采购历史");
        assertThatThrownBy(() -> mutate(false)).isInstanceOf(ServiceException.class);
    }
    @Test void sameNameOtherOrganizationIsConservativelyProtected()
    {
        oeService.saveOe(item(),20L);
        jdbc.update("insert into inv_supplier(supplier_id,supplier_name,shop_dept_id,status,cooperation_status) values(2,'供应商A',99,'0','0')");
        assertThatThrownBy(() -> supplierService.deleteSupplierByIds(new Long[]{2L},99L)).isInstanceOf(ServiceException.class).hasMessageContaining("OE引用");
        assertThatThrownBy(() -> supplierService.deleteSupplierByIds(new Long[]{2L},20L)).isInstanceOf(ServiceException.class).hasMessageContaining("无权");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void oeCommitBeforeSupplierMutationIsSeenAfterLockWait(boolean rename) throws Exception
    {
        CountDownLatch saved=new CountDownLatch(1),release=new CountDownLatch(1),started=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try
        {
            Future<?> first=pool.submit(() -> { login(); try { new TransactionTemplate(manager).executeWithoutResult(tx -> {
                oeService.saveOe(item(),20L); saved.countDown(); await(release);
            }); } finally { SecurityContextHolder.remove(); } });
            assertThat(saved.await(5,TimeUnit.SECONDS)).isTrue();
            Future<?> second=pool.submit(() -> { login(); try { started.countDown(); mutate(rename); } finally { SecurityContextHolder.remove(); } });
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); first.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ServiceException.class);
            assertThat(name()).isEqualTo("供应商A"); assertThat(oeCount()).isEqualTo(1);
        }
        finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void supplierCommitBeforeOeInsertRejectsStaleLookup(boolean rename) throws Exception
    {
        CountDownLatch mutated=new CountDownLatch(1),release=new CountDownLatch(1),lookedUp=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        // Spy the real mapper only to signal the preliminary nonlocking name read.
        InvSupplierMapper observed=mock(InvSupplierMapper.class,call -> {
            try {
                Object result=call.getMethod().invoke(suppliers,call.getArguments());
                if(call.getMethod().getName().equals("selectInvSupplierByNameAndShop")) { assertThat(result).isNotNull(); lookedUp.countDown(); }
                return result;
            } catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
        InvOeServiceImpl observedOe=wire(new InvOeServiceImpl(),observed);
        try
        {
            Future<?> first=pool.submit(() -> { login(); try { new TransactionTemplate(manager).executeWithoutResult(tx -> {
                mutate(rename); mutated.countDown(); await(release);
            }); } finally { SecurityContextHolder.remove(); } });
            assertThat(mutated.await(5,TimeUnit.SECONDS)).isTrue();
            Future<?> second=pool.submit(() -> { login(); try { observedOe.saveOe(item(),20L); } finally { SecurityContextHolder.remove(); } });
            assertThat(lookedUp.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); first.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ServiceException.class);
            assertThat(name()).isEqualTo(rename?"供应商B":null); assertThat(oeCount()).isZero();
        }
        finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void importUsesTheSameReferenceGuard()
    {
        assertThat(oeService.importOe(List.of(item()),false,20L)).contains("成功导入1条", "失败0条");
        assertThatThrownBy(() -> mutate(false)).isInstanceOf(ServiceException.class).hasMessageContaining("OE引用");
    }
    @Test void multiDeleteRollsBackUnreferencedSupplierWhenAnotherIsReferenced()
    {
        jdbc.update("insert into inv_supplier(supplier_id,supplier_name,shop_dept_id,status,cooperation_status) values(2,'供应商C',20,'0','0')");
        oeService.saveOe(item(),20L);
        assertThatThrownBy(() -> supplierService.deleteSupplierByIds(new Long[]{2L,1L},20L)).isInstanceOf(ServiceException.class);
        assertThat(jdbc.queryForObject("select count(*) from inv_supplier",Integer.class)).isEqualTo(2);
    }
    @Test void laterImportLockFailureRollsBackEarlierRealInsert()
    {
        java.util.concurrent.atomic.AtomicInteger locks = new java.util.concurrent.atomic.AtomicInteger();
        InvSupplierMapper observed = mock(InvSupplierMapper.class, call -> {
            if (call.getMethod().getName().equals("selectInvSupplierByIdForUpdate") && locks.incrementAndGet() == 2)
            {
                assertThat(oeCount()).isEqualTo(1);
                throw new org.springframework.dao.CannotAcquireLockException("injected translated database lock failure");
            }
            try { return call.getMethod().invoke(suppliers, call.getArguments()); }
            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
        InvOeServiceImpl importer = wire(new InvOeServiceImpl(), observed);
        InvOeItem second = item(); second.setOeItemCode("OE-CUP-2");
        assertThatThrownBy(() -> importer.importOe(List.of(item(), second), false, 20L))
                .isInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
        assertThat(locks.get()).isEqualTo(2); assertThat(oeCount()).isZero();
    }
    <T> T wire(T target) { return wire(target,suppliers); }
    @SuppressWarnings("unchecked") <T> T wire(T target, InvSupplierMapper mapper)
    {
        InvDeptScopeMapper scope=mock(InvDeptScopeMapper.class);
        when(scope.selectRelatedDeptIds(anyLong())).thenAnswer(call -> List.of(call.getArgument(0,Long.class)));
        when(scope.selectDeptTypeById(anyLong())).thenReturn("WAREHOUSE");
        ReflectionTestUtils.setField(target,"deptScopeMapper",scope);
        ReflectionTestUtils.setField(target,"supplierMapper",mapper);
        if(target instanceof InvOeServiceImpl)
        {
            ReflectionTestUtils.setField(target,"oeMapper",session.getMapper(InvOeMapper.class));
            ReflectionTestUtils.setField(target,"categoryMapper",session.getMapper(InvOeCategoryMapper.class));
        }
        ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();
    }
    void mutate(boolean rename) { if(rename) supplierService.saveSupplier(supplier("供应商B"),20L); else supplierService.deleteSupplierByIds(new Long[]{1L},20L); }
    String name() { var names=jdbc.queryForList("select supplier_name from inv_supplier where supplier_id=1",String.class);return names.isEmpty()?null:names.get(0); }
    int oeCount() { return jdbc.queryForObject("select count(*) from inv_oe_item",Integer.class); }
    static void login() { SecurityContextHolder.setUserId("1");SecurityContextHolder.setUserName("f1-it"); }
    static void await(CountDownLatch latch) { try { if(!latch.await(10,TimeUnit.SECONDS)) throw new AssertionError("latch timed out"); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new RuntimeException(e); } }
    static InvSupplier supplier(String name) { InvSupplier s=new InvSupplier();s.setSupplierId(1L);s.setSupplierName(name);return s; }
    static InvOeItem item() { InvOeItem i=new InvOeItem();i.setCategoryId(10L);i.setOeItemName("杯子");i.setOeItemCode("OE-CUP");i.setSupplierName("供应商A");return i; }
}
