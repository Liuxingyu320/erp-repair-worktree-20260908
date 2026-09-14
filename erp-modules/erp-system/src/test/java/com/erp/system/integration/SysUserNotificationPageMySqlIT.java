package com.erp.system.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.SysUserNotification;
import com.erp.system.domain.dto.*;
import com.erp.system.domain.vo.*;
import com.erp.system.mapper.*;
import com.erp.system.service.impl.SysUserNotificationServiceImpl;
import com.erp.system.service.impl.InAppNotificationWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Only synthetic rows in newly created local MySQL; no publisher or push client is called. */
class SysUserNotificationPageMySqlIT
{
    static final String MIGRATION="erp_system_user_notification_page_index_20260912.sql";
    static MySQLContainer mysql;static JdbcTemplate jdbc;static DataSourceTransactionManager manager;
    SysUserNotificationMapper mapper;SysUserNotificationServiceImpl service;
    SysUserDeviceTokenMapper devices;SysUserPushDeliveryMapper pushes;
    InAppNotificationWriter writer;
    final Hooks hooks=new Hooks();

    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("s10.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("s10_notifications")
                .withUsername("s10_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withLabels(Map.of("erp.task","s10-notification")).withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_unicode_ci");
        try
        {
            mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
            assertThat(jdbc.queryForObject("select version()",String.class)).startsWith(version);
            String ddl=sql("erp_system_user_notification_20260711.sql");int begin=ddl.indexOf("CREATE TABLE IF NOT EXISTS sys_user_notification");
            jdbc.execute(ddl.substring(begin,ddl.indexOf(';',begin)+1));migrate();migrate();
            System.out.printf("S10 isolated image=%s container=%s host=%s port=%s%n",version,mysql.getContainerId(),mysql.getHost(),mysql.getMappedPort(3306));
        }
        catch(Throwable failure){mysql.stop();throw failure;}
    }
    @AfterAll static void stop()
    {
        if(mysql!=null){String id=mysql.getContainerId();mysql.stop();assertThat(mysql.isRunning()).isFalse();System.out.println("S10 cleaned container="+id);}
    }
    @BeforeEach void fixture() throws Exception
    {
        jdbc.execute("truncate table sys_user_notification");
        Configuration config=new Configuration(new Environment("s10",new SpringManagedTransactionFactory(),manager.getDataSource()));
        config.getTypeAliasRegistry().registerAlias("SysUserNotification",SysUserNotification.class);config.addInterceptor(hooks);
        String resource="mapper/system/SysUserNotificationMapper.xml";
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(SysUserNotificationMapper.class);
        devices=mock(SysUserDeviceTokenMapper.class);pushes=mock(SysUserPushDeliveryMapper.class);
        writer=mock(InAppNotificationWriter.class);
        var target=new SysUserNotificationServiceImpl(mapper,devices,pushes,List.of(),writer);
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(SysUserNotificationServiceImpl)proxy.getProxy();
    }
    @AfterEach void noRealMessageDelivery(){verifyNoInteractions(devices,pushes,writer);}

    @Test void firstSnapshotAndOptionsAreStrictlyPersonal()
    {
        seed(1,42,"own","HR","0","2026-09-12 01:00:00");seed(5,42,"old","INV","1","2026-09-12 01:00:00");
        seed(999,77,"foreign","SECRET","0","2026-09-12 01:00:00");
        var page=page(new SysUserNotificationPageQuery());
        assertThat(page.snapshotMaxId()).isEqualTo("5");assertThat(page.total()).isEqualTo(2);assertThat(page.unreadCount()).isEqualTo(1);
        assertThat(page.routeTypes()).containsExactly("HR","INV");assertThat(ids(page)).containsExactly(5L,1L);
    }
    @Test void sameTimestampPaginationIsStableAndHigherIdsCannotEnterOldSnapshot()
    {
        for(long id=1;id<=4;id++)seed(id,42,"old","HR","0","2026-09-12 01:00:00");
        var q=new SysUserNotificationPageQuery();q.setPageSize(2);var first=page(q);assertThat(ids(first)).containsExactly(4L,3L);
        seed(10,42,"new but earlier time","NEW","0","2026-09-11 00:00:00");
        q.setSnapshotMaxId(first.snapshotMaxId());q.setPageNum(2);var second=page(q);
        assertThat(ids(second)).containsExactly(2L,1L);assertThat(second.total()).isEqualTo(4);assertThat(second.unreadCount()).isEqualTo(5);
        q.setKeyword("new");q.setPageNum(1);assertThat(page(q).rows()).isEmpty();
        q.setSnapshotMaxId(null);assertThat(ids(page(q))).containsExactly(10L);
    }
    @Test void everyFilterAndExclusiveEndApplyToBothRowsAndCount()
    {
        seed(1,42,"target","HR","0","2026-09-11 23:59:59");seed(2,42,"target","HR","0","2026-09-12 00:00:00");
        seed(3,42,"target","HR","0","2026-09-12 23:59:59");seed(4,42,"target","HR","0","2026-09-13 00:00:00");
        seed(5,42,"target","INV","0","2026-09-12 12:00:00");seed(6,42,"target","HR","1","2026-09-12 12:00:00");
        seed(7,77,"target","HR","0","2026-09-12 12:00:00");
        var q=new SysUserNotificationPageQuery();q.setKeyword("target");q.setRouteType("HR");q.setReadStatus("0");q.setStartDate("2026-09-12");q.setEndDate("2026-09-12");
        TimeZone previous=TimeZone.getDefault();
        try{TimeZone.setDefault(TimeZone.getTimeZone("UTC"));var result=page(q);assertThat(ids(result)).containsExactly(3L,2L);assertThat(result.total()).isEqualTo(2);assertThat(result.unreadCount()).isEqualTo(5);}
        finally{TimeZone.setDefault(previous);}
    }
    @ParameterizedTest @ValueSource(strings={"%","_","!","\\","' OR 1=1 --"})
    void wildcardEscapeAndSqlTextRemainLiteral(String keyword)
    {
        seed(1,42,"prefix"+keyword+"suffix","HR","0","2026-09-12 01:00:00");seed(2,42,"unrelated","HR","0","2026-09-12 01:00:00");
        seed(3,42,"bodymatch","HR","0","2026-09-12 01:00:00");jdbc.update("update sys_user_notification set body=? where notification_id=3","prefix"+keyword+"suffix");
        seed(4,77,keyword,"HR","0","2026-09-12 01:00:00");
        var q=new SysUserNotificationPageQuery();q.setKeyword(keyword);var result=page(q);
        assertThat(ids(result)).containsExactly(3L,1L);assertThat(result.total()).isEqualTo(2);
    }
    @Test void readAllIgnoresListFiltersButRespectsOwnerAndSnapshot()
    {
        seed(1,42,"shown","HR","0","2026-09-12 01:00:00");seed(2,42,"hidden","INV","0","2026-09-11 01:00:00");
        seed(3,42,"already read","HR","1","2026-09-12 01:00:00");seed(4,77,"foreign","HR","0","2026-09-12 01:00:00");
        seed(5,42,"new","HR","0","2026-09-12 01:00:00");
        var q=new SysUserNotificationPageQuery();q.setSnapshotMaxId("3");q.setKeyword("shown");q.setRouteType("HR");assertThat(page(q).total()).isEqualTo(1);
        var result=readAll("3");assertThat(result.changed()).isEqualTo(2);assertThat(result.unreadCount()).isEqualTo(1);assertThat(result.snapshotMaxId()).isEqualTo("3");
        assertThat(status(1)).isEqualTo("1");assertThat(status(2)).isEqualTo("1");assertThat(status(4)).isEqualTo("0");assertThat(status(5)).isEqualTo("0");
        assertThat(readAll("3").changed()).isZero();
    }
    @Test void zeroSnapshotNeverMarksNewMessagesAndUnreadStillMeansWholeAccount()
    {
        assertThat(page(new SysUserNotificationPageQuery()).snapshotMaxId()).isEqualTo("0");
        seed(1,42,"new","HR","0","2026-09-12 01:00:00");var q=new SysUserNotificationPageQuery();q.setSnapshotMaxId("0");
        var page=page(q);assertThat(page.rows()).isEmpty();assertThat(page.total()).isZero();assertThat(page.unreadCount()).isEqualTo(1);
        assertThat(readAll("0").changed()).isZero();assertThat(status(1)).isEqualTo("0");
    }
    @Test void longIdsRoundTripExactlyInNewJson() throws Exception
    {
        seed(Long.MAX_VALUE,42,"max","HR","0","2026-09-12 01:00:00");
        var result=page(new SysUserNotificationPageQuery());var json=new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(result));
        assertThat(json.get("snapshotMaxId").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(json.get("rows").get(0).get("notificationId").isTextual()).isTrue();
        assertThat(json.get("rows").get(0).get("notificationId").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(readAll(Long.toString(Long.MAX_VALUE)).changed()).isEqualTo(1);
    }
    @Test void excessivePageOffsetIsLongAndReturnsEmptyWithoutChangingCount()
    {
        seed(1,42,"message","HR","0","2026-09-12 01:00:00");var q=new SysUserNotificationPageQuery();q.setPageNum(Integer.MAX_VALUE);q.setPageSize(100);
        var result=page(q);assertThat(result.rows()).isEmpty();assertThat(result.total()).isEqualTo(1);assertThat(result.pageNum()).isEqualTo(Integer.MAX_VALUE);
    }
    @Test void lastRepresentableBusinessDayDoesNotOverflowUpperBound()
    {
        seed(1,42,"last","HR","0","9999-12-31 23:59:59");var q=new SysUserNotificationPageQuery();q.setStartDate("9999-12-31");q.setEndDate("9999-12-31");
        assertThat(ids(page(q))).containsExactly(1L);
    }
    @Test void readCountFailureRollsBackBulkUpdate()
    {
        seed(1,42,"message","HR","0","2026-09-12 01:00:00");hooks.failUnread=true;
        assertThatThrownBy(() -> readAll("1")).isInstanceOf(RuntimeException.class);
        assertThat(status(1)).isEqualTo("0");assertThat(jdbc.queryForObject("select read_time from sys_user_notification",Timestamp.class)).isNull();
        hooks.failUnread=false;assertThat(readAll("1").changed()).isEqualTo(1);
    }
    @Test void higherMessageCommittedDuringBulkRemainsUnreadAndAppearsInGlobalCount() throws Exception
    {
        seed(1,42,"old","HR","0","2026-09-12 01:00:00");seed(20,42,"known higher","HR","0","2026-09-12 01:00:00");
        hooks.bulkUpdated=new CountDownLatch(1);hooks.continueBulk=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
        try
        {
            Future<SysUserNotificationReadAllResult> result=pool.submit(() -> readAll("1"));assertThat(hooks.bulkUpdated.await(10,TimeUnit.SECONDS)).isTrue();
            seed(30,42,"new higher","HR","0","2026-09-12 01:00:00");hooks.continueBulk.countDown();
            assertThat(result.get(15,TimeUnit.SECONDS).unreadCount()).isEqualTo(2);assertThat(status(20)).isEqualTo("0");assertThat(status(30)).isEqualTo("0");
        }
        finally{hooks.continueBulk.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void concurrentBulkRequestsChangeEachMessageOnce() throws Exception
    {
        seed(1,42,"one","HR","0","2026-09-12 01:00:00");seed(2,42,"two","HR","0","2026-09-12 01:00:00");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2);
        Callable<Integer> call=() -> {ready.countDown();if(!ready.await(10,TimeUnit.SECONDS))throw new IllegalStateException("barrier");return readAll("2").changed();};
        try{var a=pool.submit(call);var b=pool.submit(call);assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(2,0);}
        finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void oldListAndSingleReadKeepTheirOwnerBoundary()
    {
        seed(1,42,"own","HR","0","2026-09-12 01:00:00");seed(2,77,"foreign","HR","0","2026-09-12 01:00:00");
        assertThat(service.selectUserNotifications(42L)).extracting(SysUserNotification::getNotificationId).containsExactly(1L);
        assertThat(service.markRead(2L,42L)).isZero();assertThat(status(2)).isEqualTo("0");assertThat(service.markRead(1L,42L)).isEqualTo(1);
    }
    @Test void indexMigrationRepeatsWithoutDataChangeAndRejectsWrongNamedIndex() throws Exception
    {
        seed(1,42,"retained","HR","0","2026-09-12 01:00:00");migrate();migrate();
        assertThat(jdbc.queryForList("select column_name from information_schema.statistics where table_schema=database() and table_name='sys_user_notification' and index_name='idx_sys_user_notification_user_id' order by seq_in_index",String.class)).containsExactly("user_id","notification_id");
        jdbc.execute("alter table sys_user_notification drop index idx_sys_user_notification_user_id, add index idx_sys_user_notification_user_id(user_id,read_status)");
        try{assertThatThrownBy(SysUserNotificationPageMySqlIT::migrate).hasMessageContaining("incompatible");}
        finally{jdbc.execute("alter table sys_user_notification drop index idx_sys_user_notification_user_id");migrate();}
        assertThat(jdbc.queryForObject("select title from sys_user_notification",String.class)).isEqualTo("retained");
    }
    private SysUserNotificationPageResult page(SysUserNotificationPageQuery q){return service.page(42L,q);}
    private SysUserNotificationReadAllResult readAll(String max){var request=new SysUserNotificationReadAllRequest();request.setSnapshotMaxId(max);return service.markAllRead(42L,request);}
    private List<Long> ids(SysUserNotificationPageResult result){return result.rows().stream().map(SysUserNotification::getNotificationId).toList();}
    private static String status(long id){return jdbc.queryForObject("select read_status from sys_user_notification where notification_id=?",String.class,id);}
    private static void seed(long id,long user,String title,String route,String read,String time)
    {jdbc.update("insert into sys_user_notification(notification_id,user_id,channel,business_key,title,body,route_type,read_status,create_time) values(?,?,'IN_APP',?,?,'body',?,?,?)",id,user,"s10-"+id,title,route,read,time);}
    private static String sql(String file) throws Exception
    {
        Path root=Paths.get(System.getProperty("user.dir")).toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("sql/"+file)))root=root.getParent();
        if(root==null)throw new IllegalStateException("Missing test migration "+file);return Files.readString(root.resolve("sql/"+file));
    }
    private static void migrate() throws Exception
    {
        try(Connection connection=Objects.requireNonNull(manager.getDataSource()).getConnection();Statement statement=connection.createStatement())
        {
            String delimiter=";";StringBuilder pending=new StringBuilder();
            for(String raw:sql(MIGRATION).split("\\R"))
            {
                String line=raw.trim();if(line.isEmpty()||line.startsWith("--"))continue;
                if(line.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")){delimiter=line.substring(10).trim();continue;}
                pending.append(raw).append('\n');if(line.endsWith(delimiter)){String command=pending.toString().trim();statement.execute(command.substring(0,command.length()-delimiter.length()));pending.setLength(0);}
            }
            assertThat(pending.toString().trim()).isEmpty();
        }
    }
    @Intercepts({@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}),
            @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class})})
    static class Hooks implements Interceptor
    {
        volatile boolean failUnread;volatile CountDownLatch bulkUpdated,continueBulk;
        @Override public Object intercept(Invocation call) throws Throwable
        {
            String id=((MappedStatement)call.getArgs()[0]).getId();
            if(failUnread&&id.endsWith(".countUnreadByUserId"))throw new ServiceException("S10 injected count failure");
            Object result=call.proceed();
            if(id.endsWith(".markAllRead")&&bulkUpdated!=null){bulkUpdated.countDown();if(!continueBulk.await(10,TimeUnit.SECONDS))throw new IllegalStateException("bulk barrier");}
            return result;
        }
    }
}
