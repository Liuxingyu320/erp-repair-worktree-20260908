package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.redis.service.RedisService;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.dto.OaSignPlanPublishRequest;
import com.erp.oa.domain.vo.OaSignPlanPublishPreview;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.service.IOaSignPlanVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Actual production SQL + transactions. PDF validation and Redis transport are mocked; ticket JSON is real. */
class OaSignPlanPublicationMySqlIT
{
    static MySQLContainer mysql;static JdbcTemplate jdbc;static DataSourceTransactionManager transactions;static OaSignPlanVersionMapper mapper;
    static final Map<String,String> tickets=new ConcurrentHashMap<>();
    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("hr.plan.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("plan_publication_it").withUsername("plan_it").withPassword(UUID.randomUUID().toString()).withReuse(false).withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withEnv("MYSQL_INITDB_SKIP_TZINFO","1");
        try{
            mysql.start();var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());jdbc=new JdbcTemplate(source);transactions=new DataSourceTransactionManager(source);
            try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("sign-plan-publication-schema.sql"));}
            Configuration config=new Configuration(new Environment("plan-it",new SpringManagedTransactionFactory(),source));String resource="mapper/oa/OaSignPlanVersionMapper.xml";
            try(InputStream in=OaSignPlanPublicationMySqlIT.class.getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
            mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(OaSignPlanVersionMapper.class);
            assertThat(jdbc.queryForObject("select version()",String.class)).startsWith(version);
            System.out.printf("HR plan isolated image=%s container=%s port=%s%n",version,mysql.getContainerId(),mysql.getMappedPort(3306));
        }catch(Throwable e){mysql.stop();throw e;}
    }
    @AfterAll static void stop(){if(mysql!=null){mysql.stop();assertThat(mysql.isRunning()).isFalse();}}
    @BeforeEach void fixture(){for(String table:List.of("oa_sign_task","oa_sign_plan_version_template","oa_sign_plan_version","oa_sign_plan_template","oa_sign_template","oa_sign_plan"))jdbc.update("delete from "+table);tickets.clear();login(7);
        jdbc.update("insert into oa_sign_plan(plan_id,plan_name,scenario,shop_dept_id,legal_entity_id,legal_entity_name,rule_json,default_values_json,sign_deadline_days,reminder_policy_json,auto_send_condition_json,status) values(100,'fixture','regularize',0,9001,'fixture','{}','{}',7,'{}','{}','0')");
        jdbc.update("insert into oa_sign_template(template_id,template_type,template_name,template_version,scenario,file_url,file_hash,required_placeholders,employee_sign_required,company_seal_required,signature_position_json,company_seal_position_json,match_condition_json,status) values(10,'REGULARIZE_CONFIRMATION','fixture','v1','regularize','/fixture.docx','source-hash','employeeName','Y','Y','{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}','{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}','{}','0')");
        jdbc.update("insert into oa_sign_plan_template(id,plan_id,template_id,template_type,sort_order) values(1,100,10,'REGULARIZE_CONFIRMATION',10)");
    }
    @AfterEach void clear(){SecurityContextHolder.remove();}
    static void login(long id){SecurityContextHolder.remove();SecurityContextHolder.setUserId(String.valueOf(id));SecurityContextHolder.setUserName("fixture-hr");}
    static IOaSignPlanVersionService service(OaSignPlanVersionMapper actual)
    {
        var target=new OaSignPlanVersionServiceImpl();var json=new ObjectMapper();var redis=mock(RedisService.class);
        doAnswer(c->{tickets.put(c.getArgument(0),c.getArgument(1));return null;}).when(redis).setCacheObject(anyString(),any(),anyLong(),any(TimeUnit.class));
        when(redis.getCacheObject(anyString())).thenAnswer(c->tickets.get(c.getArgument(0)));
        var documents=mock(OaSignDocumentService.class);when(documents.calculateFileUrlSha256(anyString())).thenReturn("source-hash");
        ReflectionTestUtils.setField(target,"versionMapper",actual);ReflectionTestUtils.setField(target,"documentService",documents);ReflectionTestUtils.setField(target,"objectMapper",json);ReflectionTestUtils.setField(target,"versionFingerprint",new OaSignPlanVersionFingerprint());ReflectionTestUtils.setField(target,"previewStore",new OaSignPlanPublishPreviewStore(redis,json));
        var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (IOaSignPlanVersionService)proxy.getProxy();
    }
    static OaSignPlanPublishRequest request(OaSignPlanPublishPreview preview){return new OaSignPlanPublishRequest(preview.previewToken(),preview.restoreVersionId());}
    record Restore(IOaSignPlanVersionService service,Long v1,Long v2,OaSignPlanPublishPreview preview){}
    Restore restoreFixture(){var s=service(mapper);Long v1=s.publish(100L,null).getVersionId();jdbc.update("update oa_sign_plan set plan_name='revision-2' where plan_id=100");Long v2=s.publish(100L,null).getVersionId();jdbc.update("insert into oa_sign_task values(1,?)",v2);jdbc.update("update oa_sign_plan set plan_name='fixture' where plan_id=100");return new Restore(s,v1,v2,s.previewPublish(100L,null));}
    void active(Long...ids){assertThat(mapper.selectPublishedMatchingCandidates("regularize",null,null)).extracting(OaSignPlanVersion::getVersionId).containsExactly(ids);}

    @Test void twoConcurrentNewPublicationsCreateOneImmutableVersion() throws Exception
    {
        var s=service(mapper);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{Callable<Long> action=()->{login(7);try{gate.await();return s.publish(100L,null).getVersionId();}finally{SecurityContextHolder.remove();}};var a=pool.submit(action);var b=pool.submit(action);gate.countDown();Long first=a.get(10,TimeUnit.SECONDS);assertThat(b.get(10,TimeUnit.SECONDS)).isEqualTo(first);active(first);assertThat(jdbc.queryForObject("select count(*) from oa_sign_plan_version",Integer.class)).isEqualTo(1);}
        finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void twoRestoresOfSamePreviewAllowOneTransitionAndKeepExistingTaskVersion() throws Exception
    {
        var f=restoreFixture();var s=f.service();var second=s.previewPublish(100L,null);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{Callable<String> one=()->{login(7);try{gate.await();return s.confirmPublish(100L,request(f.preview()),null).getAction();}catch(ServiceException e){return "REJECTED";}finally{SecurityContextHolder.remove();}};Callable<String> two=()->{login(7);try{gate.await();return s.confirmPublish(100L,request(second),null).getAction();}catch(ServiceException e){return "REJECTED";}finally{SecurityContextHolder.remove();}};
            var a=pool.submit(one);var b=pool.submit(two);gate.countDown();assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder("RESTORED","REJECTED");active(f.v1());assertThat(jdbc.queryForObject("select plan_version_id from oa_sign_task where task_id=1",Long.class)).isEqualTo(f.v2());assertThat(jdbc.queryForObject("select count(*) from oa_sign_plan_version",Integer.class)).isEqualTo(2);
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void restoreAndDisableAreSerializedWithExplicitFinalState(boolean restoreFirst) throws Exception
    {
        var f=restoreFixture();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var entered=new CountDownLatch(1);var first=new AtomicBoolean(true);var gated=spy(mapper);
        doAnswer(c->{var row=mapper.lockPlanById(100L);if(first.compareAndSet(true,false)){locked.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}return row;}).when(gated).lockPlanById(100L);
        var s=service(gated);var pool=Executors.newFixedThreadPool(2);
        try{var winner=pool.submit(()->{login(7);try{return restoreFirst?s.confirmPublish(100L,request(f.preview()),null):s.disableForNewMatching(f.v2(),null);}finally{SecurityContextHolder.remove();}});assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var later=pool.submit(()->{login(7);try{entered.countDown();return restoreFirst?s.disableForNewMatching(f.v1(),null):s.confirmPublish(100L,request(f.preview()),null);}finally{SecurityContextHolder.remove();}});assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();release.countDown();winner.get(10,TimeUnit.SECONDS);
            if(restoreFirst)later.get(10,TimeUnit.SECONDS);else assertThatThrownBy(()->later.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(ServiceException.class);
            active();assertThat(jdbc.queryForObject("select plan_version_id from oa_sign_task where task_id=1",Long.class)).isEqualTo(f.v2());
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void failureAfterEnablingHistoryRollsBackWholeActivationSwitch()
    {
        var f=restoreFixture();var faulty=spy(mapper);doThrow(new ServiceException("injected switch failure")).when(faulty).disableOtherPublishedMatchingVersions(100L,f.v1());
        assertThatThrownBy(()->service(faulty).confirmPublish(100L,request(f.preview()),null)).hasMessageContaining("injected");active(f.v2());assertThat(mapper.selectPlanVersionById(f.v1()).getMatchingStatus()).isEqualTo("DISABLED");
    }
    @Test void changedSourceAndOtherActorCannotConsumeRestorePreview()
    {
        var f=restoreFixture();login(8);assertThatThrownBy(()->f.service().confirmPublish(100L,request(f.preview()),null)).hasMessageContaining("预览已失效");login(7);jdbc.update("update oa_sign_plan set plan_name='changed-again' where plan_id=100");assertThatThrownBy(()->f.service().confirmPublish(100L,request(f.preview()),null)).hasMessageContaining("已变化");active(f.v2());
    }
}
