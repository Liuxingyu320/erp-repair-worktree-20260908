package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.mapper.OaSignSalarySourceMapper;

@Testcontainers(disabledWithoutDocker = false)
class OaSignSalarySourceMySqlIT
{
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:5.7.44")
        .withDatabaseName("salary_binding_it").withUsername("salary_it").withPassword("salary_it_test_only")
        .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw","/tmp","rw","/var/run/mysqld","rw"))
        .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M","--character-set-server=utf8mb4","--collation-server=utf8mb4_unicode_ci");
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private OaSignSalarySourceService service;
    private OaSignSalarySourceMapper mapper;
    private OaSignPackage contract;
    @BeforeEach void setup() throws Exception
    {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds); var manager=new DataSourceTransactionManager(ds); tx=new TransactionTemplate(manager);
        for(String table:List.of("sys_employee_salary_current","sys_employee_salary_source","oa_sign_package_salary_source","sys_user_profile","oa_sign_package","oa_sign_task","sys_config","oa_sign_onboard_import_batch","oa_sign_onboard_import_row")) jdbc.execute("drop table if exists "+table);
        jdbc.execute("create table sys_config(config_name varchar(100),config_key varchar(100),config_value varchar(100),config_type char(1),create_by varchar(64),create_time datetime,remark varchar(255))");
        jdbc.execute("create table sys_user_profile(user_id bigint primary key,base_salary decimal(16,2),post_salary decimal(16,2),field_allowance decimal(16,2),performance_salary decimal(16,2),salary_total decimal(16,2)) engine=InnoDB");
        jdbc.execute("create table oa_sign_task(task_id bigint primary key,employee_id bigint,source_type varchar(64),scenario varchar(32),source_business_id varchar(64),source_event_version varchar(64),package_id bigint) engine=InnoDB");
        jdbc.execute("create table oa_sign_package(package_id bigint primary key,task_id bigint,employee_id bigint,status varchar(32),signing_sequence varchar(32),final_confirmation_status varchar(32),initial_signed_time datetime,final_generated_time datetime,contract_start_date varchar(20),base_salary decimal(16,2),post_salary decimal(16,2),field_allowance decimal(16,2),performance_salary decimal(16,2),salary_total decimal(16,2)) engine=InnoDB");
        jdbc.execute("create table oa_sign_onboard_import_batch(batch_id bigint primary key,file_sha256 varchar(64)) engine=InnoDB");
        jdbc.execute("create table oa_sign_onboard_import_row(row_id bigint primary key,batch_id bigint,employee_id bigint,version bigint,historical_supplement boolean,historical_reason varchar(500),snapshot_json json,task_id bigint,package_id bigint,source_event_version bigint) engine=InnoDB");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/erp_employee_salary_source_20260911.sql")).execute(ds);
        jdbc.update("insert into sys_user_profile values(11,5000,0,0,0,5000)");
        jdbc.update("insert into oa_sign_task(task_id,employee_id,source_type) values(8,11,'MANUAL_SIGN_EXCEL_IMPORT')");
        jdbc.update("insert into oa_sign_package(package_id,task_id,employee_id,status) values(9,8,11,'DRAFT')");
        source("source-a",5000); jdbc.update("insert into sys_employee_salary_current values(11,'source-a')");
        var factory=new SqlSessionFactoryBean(); factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/oa/OaSignSalarySourceMapper.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(OaSignSalarySourceMapper.class);
        var proxy=new ProxyFactory(new OaSignSalarySourceService(mapper)); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service=(OaSignSalarySourceService)proxy.getProxy();
        contract=new OaSignPackage();contract.setPackageId(9L);contract.setEmployeeId(11L);
        contract.setBaseSalary(new BigDecimal("5000"));contract.setPostSalary(BigDecimal.ZERO);contract.setFieldAllowance(BigDecimal.ZERO);
        contract.setPerformanceSalary(BigDecimal.ZERO);contract.setSalaryTotal(new BigDecimal("5000"));
    }
    private void source(String id,int salary) {
        jdbc.update("insert into sys_employee_salary_source(source_id,employee_id,source_type,business_id,command_id,request_hash,before_hash,effective_date,operator_user_id,operator_name,reason,verified,base_salary,post_salary,field_allowance,performance_salary,salary_total) values(?,11,'ONBOARD_EXCEL','10',?,?,?,current_date,9,'test_hr','test evidence',1,?,0,0,0,?)",id,id,"a".repeat(64),"b".repeat(64),salary,salary);
    }
    private void changeSalary() { tx.executeWithoutResult(status -> {
        jdbc.queryForObject("select user_id from sys_user_profile where user_id=11 for update",Long.class);
        source("source-b",6000); jdbc.update("update sys_user_profile set base_salary=6000,salary_total=6000 where user_id=11");
        jdbc.update("update sys_employee_salary_current set source_id='source-b' where employee_id=11");
    }); }
    @Test void bindingIsImmutableAndChangedWagesBlockSending()
    {
        service.bind(contract,"source-a");service.bind(contract,"source-a");
        assertThat(jdbc.queryForObject("select count(*) from oa_sign_package_salary_source",Integer.class)).isEqualTo(1);
        tx.executeWithoutResult(status -> service.requireSend(contract));
        changeSalary();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.requireSend(contract))).hasMessageContaining("不能发送旧金额");
        contract.setBaseSalary(new BigDecimal("6000"));contract.setSalaryTotal(new BigDecimal("6000"));
        assertThatThrownBy(() -> service.bind(contract,"source-b")).hasMessageContaining("已关联其他工资版本");
        assertThat(mapper.binding(9L).sourceId).isEqualTo("source-a");
    }
    @Test void sendCheckHoldsCurrentSalaryUntilItsStatusCommits() throws Exception
    {
        service.bind(contract,"source-a");
        var checked=new CountDownLatch(1);var release=new CountDownLatch(1);var writerStarted=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var send=pool.submit(() -> tx.execute(status -> {
                service.requireSend(contract); checked.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test timeout"); }
                catch(InterruptedException e){throw new IllegalStateException(e);}
                jdbc.update("update oa_sign_package set status='PENDING_SIGN' where package_id=9");return true;
            }));
            assertThat(checked.await(10,TimeUnit.SECONDS)).isTrue();
            var writer=pool.submit(() -> {writerStarted.countDown();changeSalary();return true;});
            assertThat(writerStarted.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> writer.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();assertThat(send.get(10,TimeUnit.SECONDS)).isTrue();assertThat(writer.get(10,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select status from oa_sign_package where package_id=9",String.class)).isEqualTo("PENDING_SIGN");
            assertThat(mapper.binding(9L).getSalaryTotal()).isEqualByComparingTo("5000");
            assertThat(mapper.current(11L).getSalaryTotal()).isEqualByComparingTo("6000");
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void historicalPreparedSignatureCanFinishButNewDraftCannotBypassBinding()
    {
        jdbc.update("delete from sys_employee_salary_current");
        jdbc.update("update oa_sign_package set status='PENDING_COMPANY',signing_sequence='SIGNATURE_FIRST',final_confirmation_status='PREPARED_NOT_SENT',initial_signed_time='2020-01-01',final_generated_time='2020-01-02' where package_id=9");
        tx.executeWithoutResult(status -> service.requireSend(contract));
        jdbc.update("update oa_sign_package set final_generated_time='2099-01-01' where package_id=9");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.requireSend(contract))).hasMessageContaining("不能发送旧金额");
        jdbc.update("update oa_sign_package set final_generated_time='2020-01-02',status='DRAFT' where package_id=9");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.requireSend(contract))).hasMessageContaining("不能发送旧金额");
    }
    @Test void oldTransactionSnapshotCannotHideANewUnverifiedSalarySource()
    {
        jdbc.update("delete from sys_employee_salary_current");
        jdbc.update("update oa_sign_package set status='PENDING_COMPANY',signing_sequence='SIGNATURE_FIRST',final_confirmation_status='PREPARED_NOT_SENT',initial_signed_time='2020-01-01',final_generated_time='2020-01-02' where package_id=9");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.queryForObject("select status from oa_sign_package where package_id=9",String.class);
            var independent=new TransactionTemplate(tx.getTransactionManager());
            independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(other -> {
                jdbc.update("update sys_employee_salary_source set verified=0 where source_id='source-a'");
                jdbc.update("insert into sys_employee_salary_current values(11,'source-a')");
            });
            service.requireSend(contract);
        })).hasMessageContaining("不能发送旧金额");
    }
    @Test void salaryChangedDuringGenerationLeavesNoBinding()
    {
        changeSalary();
        assertThatThrownBy(() -> service.bind(contract,"source-a")).hasMessageContaining("生成期间工资已变化");
        assertThat(jdbc.queryForObject("select count(*) from oa_sign_package_salary_source",Integer.class)).isZero();
    }
    private void historicalContract()
    {
        source("historical-a",4000);
        jdbc.update("update sys_employee_salary_source set source_type='HISTORICAL_CONTRACT_EXCEL',batch_id=3,row_id=10,row_version=2,file_sha256=?,effective_date='2020-01-01' where source_id='historical-a'","a".repeat(64));
        jdbc.update("insert into oa_sign_onboard_import_batch values(3,?)","a".repeat(64));
        jdbc.update("insert into oa_sign_onboard_import_row values(10,3,11,4,1,'补签历史合同',?,8,9,2)","{\"contractStartDate\":\"2020-01-01\",\"baseSalary\":4000,\"postSalary\":0,\"fieldAllowance\":0,\"performanceSalary\":0,\"salaryTotal\":4000}");
        jdbc.update("update oa_sign_task set scenario='ONBOARD',source_business_id='10',source_event_version='2',package_id=9 where task_id=8");
        jdbc.update("update oa_sign_package set contract_start_date='2020-01-01',base_salary=4000,post_salary=0,field_allowance=0,performance_salary=0,salary_total=4000 where package_id=9");
        contract.setTaskId(8L);contract.setContractStartDate("2020-01-01");contract.setBaseSalary(new BigDecimal("4000"));contract.setSalaryTotal(new BigDecimal("4000"));
    }
    @Test void historicalBindingAndSendingPreserveLaterPayrollAndAreRepeatable()
    {
        historicalContract();
        service.bind(contract,"historical-a");service.bind(contract,"historical-a");
        changeSalary();
        tx.executeWithoutResult(status -> service.requireSend(contract));
        assertThat(mapper.binding(9L).sourceId).isEqualTo("historical-a");
        assertThat(mapper.current(11L).sourceId).isEqualTo("source-b");
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11",BigDecimal.class)).isEqualByComparingTo("6000");
        assertThat(jdbc.queryForObject("select count(*) from oa_sign_package_salary_source",Integer.class)).isEqualTo(1);
        jdbc.update("update oa_sign_onboard_import_row set historical_supplement=0 where row_id=10");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.requireSend(contract))).hasMessageContaining("历史合同工资来源或绑定已变化");
    }
    @Test void historicalBindingRejectsWrongRowTaskFileDateAndMoney()
    {
        historicalContract();
        String[] corrupt={
            "update oa_sign_onboard_import_row set employee_id=12 where row_id=10",
            "update oa_sign_task set source_business_id='20' where task_id=8",
            "update oa_sign_task set source_event_version='7' where task_id=8",
            "update oa_sign_task set package_id=99 where task_id=8",
            "update oa_sign_onboard_import_row set historical_reason='' where row_id=10",
            "update oa_sign_onboard_import_batch set file_sha256='wrong' where batch_id=3",
            "update oa_sign_package set contract_start_date='2021-01-01' where package_id=9",
            "update oa_sign_onboard_import_row set snapshot_json=json_set(snapshot_json,'$.salaryTotal',9999) where row_id=10",
            "update oa_sign_package set post_salary=1 where package_id=9"};
        for(String sql:corrupt) tx.executeWithoutResult(status -> {
            jdbc.update(sql);
            assertThatThrownBy(() -> service.bind(contract,"historical-a")).hasMessageContaining("生成期间工资已变化");
            assertThat(jdbc.queryForObject("select count(*) from oa_sign_package_salary_source",Integer.class)).isZero();
            status.setRollbackOnly();
        });
        service.bind(contract,"historical-a");
    }
    @Test void boundHistoricalSourceCannotBeReplacedWithAnotherConfirmation()
    {
        historicalContract();service.bind(contract,"historical-a");
        source("historical-b",4000);
        jdbc.update("update sys_employee_salary_source set source_type='HISTORICAL_CONTRACT_EXCEL',batch_id=3,row_id=10,row_version=3,file_sha256=?,effective_date='2020-01-01' where source_id='historical-b'","a".repeat(64));
        assertThatThrownBy(() -> service.bind(contract,"historical-b")).hasMessageContaining("已关联其他工资版本");
        assertThat(mapper.binding(9L).sourceId).isEqualTo("historical-a");
    }

}
