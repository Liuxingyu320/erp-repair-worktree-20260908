package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.InvStockCheckServiceImpl;

/** Disposable local MySQL only: real service transactions, header/detail/stock mappers and restart migration. */
class InvStockCheckRestartMySqlIT
{
    static MySQLContainer mysql; static JdbcTemplate jdbc; static DataSourceTransactionManager manager;
    InvStockCheckServiceImpl service; final Faults faults=new Faults();
    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("b04u5.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("b04u5_restart").withUsername("b04u5_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withLabels(Map.of("erp.task","b04u5")).withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withEnv("MYSQL_INITDB_SKIP_TZINFO","1");
        try { mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
            execute(resource("purchase-receive-b04a-baseline.sql"));
            jdbc.execute("CREATE TABLE inv_stock_check (check_id bigint PRIMARY KEY, check_no varchar(64), check_date date,shop_dept_id bigint,warehouse_id bigint,check_scope varchar(20),category_id bigint,sample_size int,blind_check char(1),counter_user_id bigint,counter_name varchar(64),deadline datetime,recount_threshold decimal(16,2),status varchar(32),approval_instance_id bigint,approval_round int,approval_engine varchar(32),last_approval_event_key varchar(200),row_version bigint default 0,submitted_user_id bigint,submitted_by varchar(64),submitted_time datetime,approved_user_id bigint,approved_by varchar(64),approved_time datetime,last_reject_reason varchar(500),last_rejected_user_id bigint,last_rejected_by varchar(64),last_rejected_time datetime,last_invalid_reason varchar(500),last_invalid_detail_snapshot longtext,last_invalidated_time datetime,create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE inv_stock_check_detail(detail_id bigint PRIMARY KEY,check_id bigint,item_type varchar(20),item_id bigint,product_id bigint,product_name varchar(200),product_code varchar(64),unit varchar(32),spec varchar(64),book_qty decimal(16,2),actual_qty decimal(16,2),recount_required char(1),recount_qty decimal(16,2),recount_by varchar(64),recount_time datetime,diff_qty decimal(16,2),diff_type varchar(20),cost_price decimal(16,2),key(check_id)) ENGINE=InnoDB");
            execute(migration());execute(migration());
            System.out.printf("U5 isolated image=%s container=%s port=%s%n",version,mysql.getContainerId(),mysql.getMappedPort(3306));
        } catch(Throwable error){mysql.stop();throw error;}
    }
    @AfterAll static void stop(){if(mysql!=null){String id=mysql.getContainerId();mysql.stop();assertThat(mysql.isRunning()).isFalse();System.out.println("U5 cleaned container="+id);}}
    @AfterEach void clear(){SecurityContextHolder.remove();}
    @BeforeEach void seed() throws Exception
    {
        for(String table:List.of("inv_stock_check_detail","inv_stock_check","inv_stock"))jdbc.update("delete from "+table);
        jdbc.update("insert ignore into sys_dept(dept_id,dept_name) values(20,'warehouse')");
        jdbc.update("insert into inv_stock_check(check_id,shop_dept_id,warehouse_id,status,counter_user_id,blind_check,recount_threshold,last_invalid_detail_snapshot) values(1,20,20,'invalidated',7,'0',1,?)","[{\"detailId\":2,\"bookQuantity\":10.25,\"currentQuantity\":11}]");
        for(int id=1;id<=3;id++) {
            jdbc.update("insert into inv_stock_check_detail(detail_id,check_id,item_type,item_id,product_name,book_qty,actual_qty,recount_required,recount_qty,recount_by,recount_time,diff_qty,diff_type,cost_price) values(?,1,'gift',?,'gift',10.25,8,'1',9,'prior','2026-09-01 00:00:00',-1.25,'loss',1)",id,id);
            jdbc.update("insert into inv_stock(stock_id,item_type,item_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,locked_quantity,cost_price,total_cost,version) values(?,'gift',?,20,20,?,?,0,2,20,0)",id,id,id==1?11:10.25,id==1?11:10.25);
        }
        Configuration config=new Configuration(new Environment("u5",new SpringManagedTransactionFactory(),manager.getDataSource()));config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain");config.addInterceptor(faults);
        for(String name:List.of("InvStockCheckMapper","InvStockCheckDetailMapper","InvStockMapper")){String resource="mapper/inventory/"+name+".xml";try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}}
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));var target=new InvStockCheckServiceImpl();
        ReflectionTestUtils.setField(target,"checkMapper",session.getMapper(InvStockCheckMapper.class));ReflectionTestUtils.setField(target,"checkDetailMapper",session.getMapper(InvStockCheckDetailMapper.class));ReflectionTestUtils.setField(target,"stockMapper",session.getMapper(InvStockMapper.class));
        var scope=mock(InvDeptScopeMapper.class);when(scope.countDeptInScope(20L,20L)).thenReturn(1);when(scope.countUserShopScope(7L,20L)).thenReturn(1);ReflectionTestUtils.setField(target,"deptScopeMapper",scope);
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(InvStockCheckServiceImpl)proxy.getProxy();login();
    }
    @Test void migrationIsRepeatableAndRestartPersistsSelectiveCountsAndEvidence()
    {
        String evidence=jdbc.queryForObject("select last_invalid_detail_snapshot from inv_stock_check",String.class);service.restartCheck(1L,20L);
        assertThat(count(1)).isNull();assertThat(count(2)).isNull();assertThat(count(3)).isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("select recount_by from inv_stock_check_detail where detail_id=3",String.class)).isEqualTo("prior");
        assertThat(jdbc.queryForObject("select cost_price from inv_stock_check_detail where detail_id=3",BigDecimal.class)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select last_invalid_detail_snapshot from inv_stock_check",String.class)).isEqualTo(evidence);
        var rows=service.getCheckDetail(1L,20L).getDetails();assertThat(rows.get(0).getPreviousActualQty()).isEqualByComparingTo("8");assertThat(rows.get(1).isNeedsSnapshotReview()).isTrue();assertThat(rows.get(2).isNeedsSnapshotReview()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='inv_stock_check' and column_name='restart_reference_snapshot'",Integer.class)).isEqualTo(1);
    }
    @Test void staleLastInputRejectsTheWholeBatchBeforeSqlWrites()
    {
        service.restartCheck(1L,20L);var rows=service.getCheckDetail(1L,20L).getDetails();rows.get(0).setActualQty(new BigDecimal("5"));rows.get(1).setActualQty(new BigDecimal("6"));rows.get(1).setSnapshotVersion(null);faults.writes.set(0);
        assertThatThrownBy(()->service.saveDraft(input(rows),20L)).hasMessageContaining("重新");assertThat(faults.writes.get()).isZero();assertThat(count(1)).isNull();
    }
    @Test void lateHeaderFailureRollsBackHistoryAndAllResetRows()
    {
        faults.failHeader=true;assertThatThrownBy(()->service.restartCheck(1L,20L)).isInstanceOf(RuntimeException.class);
        assertThat(count(1)).isEqualByComparingTo("8");assertThat(count(2)).isEqualByComparingTo("8");assertThat(jdbc.queryForObject("select restart_reference_snapshot from inv_stock_check",String.class)).isNull();
        assertThat(jdbc.queryForObject("select status from inv_stock_check",String.class)).isEqualTo("invalidated");
    }
    @Test void anotherRoundRetainsHistoryAndRejectsPreviousToken()
    {
        service.restartCheck(1L,20L);var input=service.getCheckDetail(1L,20L);for(var row:input.getDetails())row.setActualQty(new BigDecimal("7"));service.saveDraft(input,20L);String old=input.getDetails().get(0).getSnapshotVersion();String first=history();
        jdbc.update("update inv_stock_check set status='invalidated'");jdbc.update("update inv_stock set current_quantity=12 where stock_id=1");service.restartCheck(1L,20L);
        var rounds=JSON.parseObject(history()).getJSONArray("rounds");assertThat(rounds).hasSize(2);assertThat(rounds.getJSONObject(0)).isEqualTo(JSON.parseObject(first).getJSONArray("rounds").getJSONObject(0));
        assertThatThrownBy(()->service.saveDraft(input,20L)).hasMessageContaining("重新");assertThat(service.getCheckDetail(1L,20L).getDetails().get(0).getSnapshotVersion()).isNotEqualTo(old);assertThat(count(3)).isEqualByComparingTo("7");
    }
    @Test void oldTabThatWaitedBehindRestartCannotWriteFromItsEarlierRrSnapshot() throws Exception
    {
        jdbc.update("update inv_stock_check set status='draft'");var stale=service.getCheckDetail(1L,20L);for(var row:stale.getDetails())row.setActualQty(new BigDecimal("6"));
        CountDownLatch restarted=new CountDownLatch(1), release=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> restart=pool.submit(()->{login();new TransactionTemplate(manager).executeWithoutResult(tx->{jdbc.update("update inv_stock_check set status='invalidated'");service.restartCheck(1L,20L);restarted.countDown();await(release);});SecurityContextHolder.remove();});
            assertThat(restarted.await(10,TimeUnit.SECONDS)).isTrue();faults.headerLockAttempt=new CountDownLatch(1);
            Future<?> save=pool.submit(()->{login();try{service.saveDraft(stale,20L);}finally{SecurityContextHolder.remove();}});
            assertThat(faults.headerLockAttempt.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();restart.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->save.get(10,TimeUnit.SECONDS)).hasRootCauseInstanceOf(ServiceException.class).hasStackTraceContaining("重新");assertThat(count(1)).isNull();assertThat(count(3)).isEqualByComparingTo("8");
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    static void login(){SecurityContextHolder.setUserId("7");SecurityContextHolder.setUserName("counter");}
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
    static InvStockCheck input(List<InvStockCheckDetail> rows){var check=new InvStockCheck();check.setCheckId(1L);check.setDetails(rows);return check;}
    static BigDecimal count(int id){return jdbc.queryForObject("select actual_qty from inv_stock_check_detail where detail_id=?",BigDecimal.class,id);}
    static String history(){return jdbc.queryForObject("select restart_reference_snapshot from inv_stock_check",String.class);}
    static String resource(String name)throws Exception{try(InputStream in=InvStockCheckRestartMySqlIT.class.getClassLoader().getResourceAsStream(name)){return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}}
    static String migration()throws Exception{Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("sql")))root=root.getParent();return Files.readString(root.resolve("sql/erp_inventory_stock_check_restart_20260912.sql"));}
    static void execute(String sql){jdbc.execute((ConnectionCallback<Void>) connection->{try(Statement statement=connection.createStatement()){for(String part:sql.replaceAll("(?m)^--.*$","").split(";"))if(!part.isBlank())statement.execute(part);}return null;});}
    @Intercepts({@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}),@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class})})
    public static class Faults implements Interceptor {
        volatile boolean failHeader;volatile CountDownLatch headerLockAttempt;final AtomicInteger writes=new AtomicInteger();
        @Override public Object intercept(Invocation invocation)throws Throwable{
            String id=((MappedStatement)invocation.getArgs()[0]).getId();
            if(id.endsWith("selectInvStockCheckByIdForUpdate")&&headerLockAttempt!=null)headerLockAttempt.countDown();
            if(invocation.getMethod().getName().equals("update"))writes.incrementAndGet();
            Object result=invocation.proceed();if(failHeader&&id.endsWith("updateInvStockCheck"))throw new IllegalStateException("late header failure");return result;
        }
    }
}
