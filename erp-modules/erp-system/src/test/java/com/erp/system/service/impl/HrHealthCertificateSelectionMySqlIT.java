package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
import org.testcontainers.mysql.MySQLContainer;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteFileService;
import com.erp.system.domain.dto.HrHealthCertificateReviewRequest;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.mapper.HrHealthCertificateMapper;
import com.erp.system.service.IHrHealthCertificateService;
import com.erp.system.support.HrHealthCertificateSelection;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Real service transactions + production mapper SQL; authorization and remote delivery are isolated mocks. */
class HrHealthCertificateSelectionMySqlIT
{
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    static HrHealthCertificateMapper mapper;
    static final LocalDate DAY=LocalDate.of(2026,9,13);
    IHrHealthCertificateService service;

    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("hr.selection.mysql.version","5.7.44");
        assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("health_selection_it")
                .withUsername("health_it").withPassword(UUID.randomUUID().toString())
                .withReuse(false).withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO","1");
        try {
            mysql.start();
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc=new JdbcTemplate(source);transactions=new DataSourceTransactionManager(source);
            assertThat(jdbc.queryForObject("select version()",String.class)).startsWith(version);
            jdbc.execute("create table sys_user(user_id bigint primary key,nick_name varchar(80),dept_id bigint,del_flag char(1),status char(1)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table sys_user_profile(user_id bigint primary key,employee_no varchar(50),employee_status varchar(20)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(80),ancestors varchar(80)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table hr_employee_health_certificate(certificate_id bigint primary key,user_id bigint,dept_id_snapshot bigint,certificate_no varchar(100),issued_date date,valid_from date,expires_on date,issuer_name varchar(100),attachment_node_id bigint,review_status varchar(30),current_flag char(1),reviewed_by_user_id bigint,reviewed_by_name varchar(80),reviewed_time datetime,rejection_reason varchar(300),approval_instance_id bigint,approval_round int,last_approval_event_key varchar(100),version bigint default 0,del_flag char(1) default '0',create_by varchar(80),create_time datetime,update_by varchar(80),update_time datetime,unique key uk_current(user_id,current_flag),key by_user(user_id)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table hr_health_certificate_approval_start_outbox(outbox_id bigint primary key,certificate_id bigint,business_round int,status varchar(30)) engine=InnoDB default charset=utf8mb4");
            jdbc.update("insert into sys_user values(7,'employee-a',10,'0','0'),(8,'employee-b',20,'0','0')");
            jdbc.update("insert into sys_user_profile values(7,'E7','正式'),(8,'E8','正式')");
            jdbc.update("insert into sys_dept values(10,'shop-a','0'),(20,'shop-b','0')");
            Configuration config=new Configuration(new Environment("health-it",new SpringManagedTransactionFactory(),source));
            String resource="mapper/system/HrHealthCertificateMapper.xml";
            try(InputStream in=HrHealthCertificateSelectionMySqlIT.class.getClassLoader().getResourceAsStream(resource))
            {new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
            mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(HrHealthCertificateMapper.class);
            System.out.printf("HR selection isolated image=%s container=%s port=%s%n",version,mysql.getContainerId(),mysql.getMappedPort(3306));
        } catch(Throwable error){mysql.stop();throw error;}
    }
    @AfterAll static void stop(){if(mysql!=null){mysql.stop();assertThat(mysql.isRunning()).isFalse();}}
    @BeforeEach void fixture(){jdbc.update("delete from hr_health_certificate_approval_start_outbox");jdbc.update("delete from hr_employee_health_certificate");service=service(mapper);}
    static IHrHealthCertificateService service(HrHealthCertificateMapper actual)
    {
        var target=new HrHealthCertificateServiceImpl(actual,mock(HrEmployeeAccessService.class),mock(HrHealthCertificateAccessService.class),mock(RemoteFileService.class),mock(HrHealthCertificateFeatureService.class),Clock.fixed(DAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(target,"objectMapper",new ObjectMapper());
        ProxyFactory proxy=new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return (IHrHealthCertificateService)proxy.getProxy();
    }
    static void certificate(long id,long user,LocalDate start,LocalDate end,String status,String flag)
    {jdbc.update("insert into hr_employee_health_certificate(certificate_id,user_id,issued_date,valid_from,expires_on,review_status,current_flag,version,del_flag,approval_round,create_time,reviewed_time) values(?,?,?,?,?,?,?,0,'0',1,?,?)",id,user,start,start,end,status,flag,java.sql.Timestamp.valueOf(DAY.minusDays(id).atStartOfDay()),java.sql.Timestamp.valueOf(DAY.atStartOfDay()));}
    static HrHealthCertificateVo query(String status)
    {var q=new HrHealthCertificateVo();q.setReviewStatus(status);q.setAsOfDate(DAY);q.setAsOfTime(DAY.atTime(12,0));q.getParams().put("dataScope"," and d.dept_id=10");return q;}
    static HrHealthCertificateReviewRequest approve(){var r=new HrHealthCertificateReviewRequest();r.setVersion(0L);r.setDecision("APPROVED");return r;}
    static ApprovalBusinessCallbackRequest callback()
    {var r=new ApprovalBusinessCallbackRequest();r.setBusinessCode("HR_HEALTH_CERTIFICATE");r.setBusinessId("102");r.setBusinessRound(1);r.setInstanceId(9001L);r.setEventKey("health-event-9001");r.setAction("APPROVE");r.setPayload("{\"targetStatus\":\"APPROVED\",\"operatorId\":9,\"operatorName\":\"reviewer\"}");return r;}

    @Test void futureCacheCannotDisplaceValidHistoryAndAllReadConsumersChooseSameCertificate()
    {
        certificate(101,7,DAY.minusDays(20),DAY,"APPROVED",null);
        certificate(102,7,DAY.plusDays(1),DAY.plusYears(1),"APPROVED","Y");
        certificate(201,8,DAY.minusDays(5),DAY.plusDays(4),"APPROVED","Y");
        var rows=mapper.selectCurrentByUserIds(List.of(7L,8L),DAY);
        assertThat(rows).extracting(HrHealthCertificateVo::getCertificateId).containsExactlyInAnyOrder(101L,201L);
        assertThat(rows.stream().filter(r->r.getUserId()==7L).findFirst().orElseThrow().getNextValidFrom()).isEqualTo(DAY.plusDays(1));
        var q=query(null);q.setHealthCertificateStatus("EXPIRING");
        assertThat(mapper.selectScopedList(q)).extracting(HrHealthCertificateVo::getCertificateId).containsExactly(101L);
        assertThat(mapper.selectOpsSummary(query(null)).getExpiringCount()).isEqualTo(1);
        assertThat(mapper.selectReminderCandidates(DAY,DAY.plusDays(30),0L,200)).extracting(HrHealthCertificateVo::getCertificateId).containsExactly(101L,201L);
        assertThat(mapper.selectCurrentByUserIds(List.of(7L),DAY.plusDays(1))).extracting(HrHealthCertificateVo::getCertificateId).containsExactly(102L);
        assertThat(HrHealthCertificateSelection.choose(mapper.selectByUserId(7L),DAY).getCertificateId()).isEqualTo(101L);
    }
    @Test void pendingCountersAndFailedSubsetEqualScopedQueueSetsWithoutTaskMultiplication()
    {
        String[] states={"PENDING_REVIEW","APPROVAL_PENDING","APPROVAL_SUBMITTING","RETURNED","APPROVED"};
        for(int i=0;i<states.length;i++)certificate(101+i,7,DAY.minusDays(10),DAY.plusYears(1),states[i],null);
        certificate(201,8,DAY.minusDays(10),DAY.plusYears(1),"APPROVAL_PENDING",null);
        jdbc.update("insert into hr_health_certificate_approval_start_outbox values(1,103,1,'FAILED'),(2,103,0,'FAILED')");
        var summary=mapper.selectOpsSummary(query(null));
        assertThat(summary.getPendingReviewCount()).isEqualTo(2);assertThat(summary.getLegacyPendingCount()).isEqualTo(1);assertThat(summary.getApprovalPendingCount()).isEqualTo(1);
        assertThat(summary.getApprovalSubmittingCount()).isEqualTo(1);assertThat(summary.getApprovalStartFailedCount()).isEqualTo(1);
        assertThat(mapper.selectScopedList(query("PENDING_ALL"))).hasSize((int)summary.getPendingReviewCount());
        assertThat(mapper.selectScopedList(query("APPROVAL_START_FAILED"))).extracting(HrHealthCertificateVo::getCertificateId).containsExactly(103L);
        assertThat(summary.getOldestPendingTime()).isNotNull();
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void concurrentApprovalsSerializeAndKeepOneEffectiveCurrent(boolean callbackCompetitor) throws Exception
    {
        certificate(101,7,DAY,DAY.plusYears(1),"PENDING_REVIEW",null);
        certificate(102,7,DAY,DAY.plusYears(1),callbackCompetitor?"APPROVAL_PENDING":"PENDING_REVIEW",null);
        jdbc.update("update hr_employee_health_certificate set approval_instance_id=9001 where certificate_id=102");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{
            Future<?> a=pool.submit(()->{gate.await();return service.review(101L,approve(),9L,"reviewer");});
            Future<?> b=pool.submit(()->{gate.await();return callbackCompetitor?service.applyApprovalCallback(callback()):service.review(102L,approve(),9L,"reviewer");});
            gate.countDown();a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select count(*) from hr_employee_health_certificate where review_status='APPROVED'",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from hr_employee_health_certificate where current_flag='Y'",Integer.class)).isEqualTo(1);
            assertThat(mapper.selectCurrentByUserIds(List.of(7L),DAY)).hasSize(1);
            if(callbackCompetitor)assertThat(service.applyApprovalCallback(callback()).getCode()).isEqualTo("IDEMPOTENT");
        }finally{pool.shutdownNow();}
    }
    @Test void failureAfterClearingExpiredCacheRollsBackApprovalAndOriginalFlag()
    {
        certificate(101,7,DAY.minusDays(20),DAY.minusDays(1),"APPROVED","Y");
        certificate(102,7,DAY,DAY.plusYears(1),"PENDING_REVIEW",null);
        var faulty=spy(mapper);doThrow(new ServiceException("injected current write failure")).when(faulty).setCurrentCertificate(eq(7L),eq(102L),eq(DAY),anyString());
        assertThatThrownBy(()->service(faulty).review(102L,approve(),9L,"reviewer")).hasMessageContaining("injected");
        assertThat(mapper.selectById(102L).getReviewStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(mapper.selectById(101L).getCurrentFlag()).isEqualTo("Y");
    }
    @Test void uniqueEffectiveCacheRemainsUntilExpirationEvenAfterNextCertificateBecomesEligible()
    {
        certificate(101,7,DAY.minusDays(10),DAY.plusDays(10),"APPROVED","Y");
        certificate(102,7,DAY.plusDays(1),DAY.plusYears(1),"APPROVED",null);
        assertThat(mapper.selectCurrentByUserIds(List.of(7L),DAY.plusDays(1))).extracting(HrHealthCertificateVo::getCertificateId).containsExactly(101L);
        assertThat(mapper.selectCurrentByUserIds(List.of(7L),DAY.plusDays(11))).extracting(HrHealthCertificateVo::getCertificateId).containsExactly(102L);
    }
    @Test void certificateLockReadsDoNotWaitForUnrelatedProfileUserOrDepartmentLocks() throws Exception
    {
        certificate(101,7,DAY,DAY.plusYears(1),"APPROVAL_SUBMITTING",null);
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);
        try {
            Future<?> profile=pool.submit(()->transaction.execute(status->{
                jdbc.queryForObject("select user_id from sys_user where user_id=7 for update",Long.class);
                jdbc.queryForObject("select user_id from sys_user_profile where user_id=7 for update",Long.class);
                jdbc.queryForObject("select dept_id from sys_dept where dept_id=10 for update",Long.class);
                locked.countDown();try {assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}return null;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            Future<?> certificate=pool.submit(()->transaction.execute(status->{
                var row=mapper.selectByIdForUpdate(101L);
                assertThat(row.getEmployeeName()).isEqualTo("employee-a");
                assertThat(row.getCurrentDeptName()).isEqualTo("shop-a");
                assertThat(mapper.selectByUserIdForUpdate(7L)).hasSize(1);return null;
            }));
            // With the old joined FOR UPDATE this waits on the profile/user owner and times out.
            certificate.get(3,TimeUnit.SECONDS);release.countDown();profile.get(5,TimeUnit.SECONDS);
        } finally {release.countDown();pool.shutdownNow();}
    }

}
