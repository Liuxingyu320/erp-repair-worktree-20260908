package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.*;
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
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.InvSalesReturnSourceQuery;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.InvSalesReturnServiceImpl;

/** Actual source SQL, PageHelper and transactional service against a fresh task-owned MySQL. */
class InvSalesReturnSpecialistMySqlIT
{
    static MySQLContainer mysql;
    static String containerId;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    SqlSessionTemplate session;
    InvSalesReturnServiceImpl service;
    final Fault fault=new Fault();
    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("f5.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("f5_specialist").withUsername("f5_it")
                .withPassword(UUID.randomUUID().toString()).withReuse(false).withLabels(Map.of("erp.task","b05-f5"))
                .withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withEnv("MYSQL_INITDB_SKIP_TZINFO","1")
                .withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_unicode_ci");
        try {
            mysql.start();containerId=mysql.getContainerId();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            System.out.printf("F5 isolated image=%s container=%s host=%s port=%s%n",version,containerId,mysql.getHost(),mysql.getMappedPort(3306));
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
            // Existing accepted synthetic schemas, not current business data.
            execute(resource("purchase-receive-b04a-baseline.sql"));execute(resource("purchase-return-b04c-baseline.sql"));execute(resource("stock-cost-b04d-baseline.sql"));
            jdbc.update("insert ignore into sys_dept(dept_id,dept_name) values(20,'store'),(99,'foreign')");
            assertThat(jdbc.queryForObject("select count(*) from inv_product where product_id=1",Integer.class)).isEqualTo(1);
        } catch(Throwable failure) { mysql.stop();throw failure; }
    }
    @AfterAll static void stop()
    { if(mysql!=null) {String id=containerId;mysql.stop();assertThat(mysql.isRunning()).isFalse();System.out.println("F5 cleaned container="+id);} }
    @BeforeEach void fixture() throws Exception
    {
        for(String table:List.of("inv_sales_return_detail","inv_sales_return","inv_sales_detail","inv_sales_order")) jdbc.update("delete from "+table);
        Configuration config=new Configuration(new Environment("f5",new SpringManagedTransactionFactory(),manager.getDataSource()));
        config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain");config.addInterceptor(fault);
        PageInterceptor page=new PageInterceptor();Properties properties=new Properties();properties.setProperty("helperDialect","mysql");page.setProperties(properties);config.addInterceptor(page);
        for(String mapper:List.of("InvSalesOrderMapper","InvSalesDetailMapper","InvSalesReturnMapper","InvSalesReturnDetailMapper","InvNumberSequenceMapper"))
        {
            String resource="mapper/inventory/"+mapper+".xml";
            try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)) {new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        }
        session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        InvSalesReturnServiceImpl target=new InvSalesReturnServiceImpl();
        for(var field:target.getClass().getDeclaredFields()) if(config.hasMapper(field.getType())) ReflectionTestUtils.setField(target,field.getName(),session.getMapper(field.getType()));
        InvDeptScopeMapper scope=mock(InvDeptScopeMapper.class);
        when(scope.selectDeptTypeById(20L)).thenReturn("STORE");when(scope.countUserShopScope(42L,20L)).thenReturn(1);
        when(scope.countDeptInScope(anyLong(),anyLong())).thenAnswer(c -> Objects.equals(c.getArgument(0),c.getArgument(1))?1:0);
        ReflectionTestUtils.setField(target,"deptScopeMapper",scope);
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service=(InvSalesReturnServiceImpl)proxy.getProxy();login();
    }
    @AfterEach void clear(){SecurityContextHolder.remove();}
    @Test void filtersRealReturnableRowsBeforePaginationSoOldOrderIsVisible()
    {
        source(1,"delivered","3",20,"old customer","2026-08-01");
        for(int id=2;id<=22;id++) source(id,"draft","3",20,"new draft","2026-09-12");
        source(23,"submitted","0",20,"undelivered","2026-09-12");source(24,"delivered","3",99,"foreign","2026-09-12");
        assertThat(ids(list(new InvSalesReturnSourceQuery()))).containsExactly(1L);
    }
    @Test void stablePagesKeepRealTotalAndDoNotClampOutOfRangePage()
    {
        for(int id=1;id<=5;id++) source(id,"notified","3",20,"customer","2026-09-12");
        InvSalesReturnSourceQuery query=new InvSalesReturnSourceQuery();query.setPageSize(2);
        List<InvSalesOrder> first=list(query);assertThat(first).isInstanceOf(Page.class);assertThat(((Page<?>)first).getTotal()).isEqualTo(5);assertThat(ids(first)).containsExactly(5L,4L);
        query.setPageNum(2);assertThat(ids(list(query))).containsExactly(3L,2L);
        query.setPageNum(3);assertThat(ids(list(query))).containsExactly(1L);
        query.setPageNum(Integer.MAX_VALUE);assertThat(list(query)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"%","_","!","' OR 1=1 --","\\"})
    void keywordMatchesLiteralCustomerAndDoesNotExpandSql(String keyword)
    {
        source(1,"submitted","3",20,"target"+keyword,"2026-09-12");source(2,"submitted","3",20,"other","2026-09-12");
        InvSalesReturnSourceQuery query=new InvSalesReturnSourceQuery();query.setKeyword(keyword);assertThat(ids(list(query))).containsExactly(1L);
    }
    @Test void orderCustomerAndInclusiveDateFiltersCombineBeforePage()
    {
        source(1,"submitted","3",20,"张三","2026-09-01");source(2,"submitted","3",20,"张三","2026-09-12");source(3,"submitted","3",20,"李四","2026-09-12");source(4,"submitted","3",20,"张三","2026-09-13");
        InvSalesReturnSourceQuery query=new InvSalesReturnSourceQuery();query.setStartDate("2026-09-01");query.setEndDate("2026-09-12");query.setCustomerName("张");
        assertThat(ids(list(query))).containsExactly(2L,1L);query.setOrderNo("SO-1");assertThat(ids(list(query))).containsExactly(1L);
    }
    @Test void actualReservationsExcludeFullyReturnedAndSubmittedButNotCancelledOrDraft()
    {
        for(int id=1;id<=5;id++) source(id,"submitted","3",20,"customer","2026-09-12");
        draft(11,1,"3");jdbc.update("update inv_sales_return set status='submitted' where return_id=11");
        draft(12,2,"3");jdbc.update("update inv_sales_return set status='returned' where return_id=12");
        draft(13,3,"3");jdbc.update("update inv_sales_return set status='cancelled' where return_id=13");draft(14,4,"3");
        source(6,"cancelled","3",20,"customer","2026-09-12");
        assertThat(ids(list(new InvSalesReturnSourceQuery()))).containsExactly(5L,4L,3L);
        assertThatThrownBy(() -> service.getReturnableSourceOrder(1L,20L)).isInstanceOf(ServiceException.class).hasMessageContaining("暂无可退");
    }
    @Test void partialAndLegacyMaterialReservationsAreReflectedInDetailAndList()
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"2");jdbc.update("update inv_sales_return set status='submitted'");
        jdbc.update("update inv_sales_return_detail set sales_detail_id=null");
        assertThat(service.getReturnableSourceOrder(1L,20L).getDetails().get(0).getReturnableQuantity()).isEqualByComparingTo("1");
        assertThat(ids(list(new InvSalesReturnSourceQuery()))).containsExactly(1L);
        jdbc.update("update inv_sales_return_detail set quantity=3");assertThat(list(new InvSalesReturnSourceQuery())).isEmpty();
    }
    @Test void productAndGiftWithSameNumericIdentityDoNotConsumeEachOther()
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"3");jdbc.update("update inv_sales_return set status='submitted'");
        jdbc.update("insert into inv_sales_detail(detail_id,order_id,item_type,item_id,product_name,quantity,delivered_quantity,unit_price,amount) values(12,1,'gift',1,'gift',2,2,8,16)");
        var result=service.getReturnableSourceOrder(1L,20L);assertThat(result.getDetails().stream().filter(d -> "gift".equals(d.getItemType())).findFirst().orElseThrow().getReturnableQuantity()).isEqualByComparingTo("2");
        assertThat(ids(list(new InvSalesReturnSourceQuery()))).containsExactly(1L);
    }
    @Test void wholeMaterialReservationCapCatchesUnmappedHistoricalLine()
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"3");jdbc.update("update inv_sales_return set status='submitted'");
        jdbc.update("update inv_sales_return_detail set sales_detail_id=9999");
        assertThat(list(new InvSalesReturnSourceQuery())).isEmpty();assertThatThrownBy(() -> service.getReturnableSourceOrder(1L,20L)).isInstanceOf(ServiceException.class);
    }
    @Test void sourceIdsRemainExactStringsAndForeignOrderIsRejected() throws Exception
    {
        source(1,"submitted","3",20,"customer","2026-09-12");
        jdbc.update("delete from inv_sales_detail where order_id=1");
        jdbc.update("update inv_sales_order set order_id=? where order_id=1",Long.MAX_VALUE);
        jdbc.update("insert into inv_sales_detail(detail_id,order_id,item_type,item_id,product_id,quantity,delivered_quantity,unit_price) values(?,?,'product',1,1,3,3,9.99)",Long.MAX_VALUE,Long.MAX_VALUE);
        var mapper=new ObjectMapper();
        var json=mapper.readTree(mapper.writeValueAsString(service.getReturnableSourceOrder(Long.MAX_VALUE,20L)));
        assertThat(json.get("orderId").isTextual()).isTrue();assertThat(json.get("orderId").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(json.get("details").get(0).get("detailId").isTextual()).isTrue();
        assertThat(json.get("details").get(0).get("detailId").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        source(2,"submitted","3",99,"foreign","2026-09-12");
        assertThatThrownBy(() -> service.getReturnableSourceOrder(2L,20L)).isInstanceOf(ServiceException.class).hasMessageContaining("当前门店");
        assertThat(ids(list(new InvSalesReturnSourceQuery()))).containsExactly(Long.MAX_VALUE);
    }
    @Test void savedSubmitPreservesHeaderAndDetailsInsteadOfRewritingAClientBody()
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"2");
        var before=jdbc.queryForMap("select * from inv_sales_return_detail where return_id=11");
        var result=service.submitSavedReturn(11L, 0L, 20L);assertThat(result.getReturnTitle()).isEqualTo("frozen");assertThat(result.getDetails().get(0).getQuantity()).isEqualByComparingTo("2");
        assertThat(jdbc.queryForMap("select * from inv_sales_return_detail where return_id=11")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from inv_sales_return where return_id=11",String.class)).isEqualTo("submitted");
        assertThatThrownBy(() -> service.submitSavedReturn(11L, 0L, 20L)).isInstanceOf(ServiceException.class);
    }
    @Test void lateStateWriteFailureRollsBackTheRealUpdate()
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"2");fault.afterStateWrite=true;
        assertThatThrownBy(() -> service.submitSavedReturn(11L, 0L, 20L)).isInstanceOf(ServiceException.class);
        assertThat(jdbc.queryForObject("select status from inv_sales_return where return_id=11",String.class)).isEqualTo("draft");
        assertThat(jdbc.queryForObject("select quantity from inv_sales_return_detail where return_id=11",BigDecimal.class)).isEqualByComparingTo("2");
    }
    @Test void concurrentDifferentSavedDraftsCannotOverReserveOneSource() throws Exception
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"2");draft(12,1,"2");
        List<Object> result=concurrent(() -> service.submitSavedReturn(11L, 0L, 20L),() -> service.submitSavedReturn(12L, 0L, 20L));
        assertThat(result.stream().filter(x -> x instanceof InvSalesReturn).count()).isEqualTo(1);
        assertThat(result.stream().filter(x -> x instanceof ServiceException).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(d.quantity) from inv_sales_return_detail d join inv_sales_return r on r.return_id=d.return_id where r.status='submitted'",BigDecimal.class)).isEqualByComparingTo("2");
    }
    @Test void concurrentSameDraftSubmitsOnce() throws Exception
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"2");
        var result=concurrent(() -> service.submitSavedReturn(11L, 0L, 20L),() -> service.submitSavedReturn(11L, 0L, 20L));
        assertThat(result.stream().filter(x -> x instanceof InvSalesReturn).count()).isEqualTo(1);
        assertThat(result.stream().filter(x -> x instanceof ServiceException).count()).isEqualTo(1);
    }
    @Test void oldRepeatableReadSnapshotCannotReplaceNewerSavedDraft() throws Exception
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"1");
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            assertThat(session.getMapper(InvSalesReturnMapper.class).selectInvSalesReturnById(11L).getReturnTitle()).isEqualTo("frozen");
            var pool=Executors.newSingleThreadExecutor();
            try {
                pool.submit(() -> {login();try { InvSalesReturn edit=new InvSalesReturn();edit.setVersion(0L);edit.setReturnId(11L);edit.setSalesOrderId(1L);edit.setReturnTitle("new saved title");
                    InvSalesReturnDetail line=new InvSalesReturnDetail();line.setSalesDetailId(11L);line.setItemType("product");line.setProductId(1L);line.setQuantity(new BigDecimal("2"));
                    service.saveDraft(edit,List.of(line),20L); } finally {SecurityContextHolder.remove();} }).get(10,TimeUnit.SECONDS);
            } catch(Exception e) {throw new RuntimeException(e);} finally {pool.shutdownNow();}
            service.submitSavedReturn(11L, 0L, 20L);
        })).isInstanceOf(ServiceException.class).hasMessageContaining("版本");
        var submitted=service.submitSavedReturn(11L, 1L, 20L);assertThat(submitted.getReturnTitle()).isEqualTo("new saved title");assertThat(submitted.getDetails().get(0).getQuantity()).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select return_title from inv_sales_return where return_id=11",String.class)).isEqualTo("new saved title");
    }
    @Test void actionContextUsesActualHeaderAndRejectsForeignOrMissingWithoutLeakingDetail() throws Exception
    {
        source(1,"submitted","3",20,"customer","2026-09-12");draft(11,1,"2");
        var mapper=new ObjectMapper();var json=mapper.readTree(mapper.writeValueAsString(service.getActionContext(11L,20L)));
        assertThat(json.get("returnId").asText()).isEqualTo("11");assertThat(json.get("status").asText()).isEqualTo("draft");assertThat(json.get("_specialistSummaryOnly").asBoolean()).isTrue();
        assertThat(json.has("customerName")).isFalse();assertThat(json.has("details")).isFalse();assertThat(json.has("totalAmount")).isFalse();
        jdbc.update("update inv_sales_return set shop_dept_id=99 where return_id=11");assertThatThrownBy(()->service.getActionContext(11L,20L)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(()->service.getActionContext(999L,20L)).isInstanceOf(ServiceException.class);
    }
    List<InvSalesOrder> list(InvSalesReturnSourceQuery query){return service.selectReturnableSourceOrders(query,20L);}
    static List<Long> ids(List<InvSalesOrder> rows){return rows.stream().map(InvSalesOrder::getOrderId).toList();}
    static void source(long id,String status,String delivered,long dept,String customer,String date)
    {
        jdbc.update("insert into inv_sales_order(order_id,order_no,customer_name,order_date,status,shop_dept_id,applicant_id,create_time) values(?,?,?, ?,?,?,42,'2026-09-12 10:00:00')",id,"SO-"+id,customer,date,status,dept);
        jdbc.update("insert into inv_sales_detail(detail_id,order_id,item_type,item_id,product_id,product_name,quantity,delivered_quantity,unit_price,amount) values(?,?,'product',1,1,'tea',3,?,9.99,29.97)",id*10+1,id,new BigDecimal(delivered));
    }
    static void draft(long id,long sourceId,String quantity)
    {
        jdbc.update("insert into inv_sales_return(return_id,return_no,sales_order_id,sales_order_no,return_title,customer_name,shop_dept_id,status,remark) values(?,?,?,?,'frozen','customer',20,'draft','frozen remark')",id,"SR-"+id,sourceId,"SO-"+sourceId);
        jdbc.update("insert into inv_sales_return_detail(return_id,sales_detail_id,item_type,item_id,product_id,product_name,quantity,unit_price,amount,returned_quantity,returned_cost_amount) values(?,?,'product',1,1,'tea',?,9.99,19.98,0,0)",id,sourceId*10+1,new BigDecimal(quantity));
    }
    static void login(){SecurityContextHolder.setUserId("42");SecurityContextHolder.setUserName("f5-specialist");}
    static String resource(String name) throws Exception {try(InputStream input=InvSalesReturnSpecialistMySqlIT.class.getClassLoader().getResourceAsStream(name)){return new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);}}
    static void execute(String sql){for(String statement:sql.replaceAll("(?m)^\\s*--.*$","").split(";"))if(!statement.isBlank())jdbc.execute(statement);}
    List<Object> concurrent(Callable<Object> first,Callable<Object> second) throws Exception
    {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch barrier=new CountDownLatch(2);
        try {List<Future<Object>> results=new ArrayList<>();for(Callable<Object> job:List.of(first,second))results.add(pool.submit(() -> {login();try {barrier.countDown();if(!barrier.await(5,TimeUnit.SECONDS))throw new AssertionError("barrier");return job.call();}catch(ServiceException e){return e;}finally{SecurityContextHolder.remove();}}));return List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
    public static class Fault implements Interceptor
    {
        boolean afterStateWrite;
        @Override public Object intercept(Invocation call)throws Throwable
        {Object result=call.proceed();if(afterStateWrite&&((MappedStatement)call.getArgs()[0]).getId().endsWith("InvSalesReturnMapper.updateInvSalesReturn"))return 0;return result;}
    }
}
