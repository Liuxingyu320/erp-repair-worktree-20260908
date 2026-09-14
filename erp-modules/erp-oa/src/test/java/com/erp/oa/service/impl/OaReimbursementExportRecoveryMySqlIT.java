package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.*;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.domain.*;
import com.erp.oa.domain.dto.OaReimbursementExportRequest;
import com.erp.oa.mapper.OaReimbursementMapper;
import com.erp.system.api.model.LoginUser;

/** Actual export service, mapper, migrations, ZIP promotion and Spring transactions on both supported engines. */
class OaReimbursementExportRecoveryMySqlIT
{
    @TempDir Path work;

    @ParameterizedTest @ValueSource(strings={"mysql:5.7.44","mysql:8.0.36"})
    void concurrentReplayAndRollbackPreserveExactlyTheOriginalCommittedArchive(String image) throws Exception
    {
        try (MySQLContainer mysql = new MySQLContainer(image).withDatabaseName("export_recovery_it")
                .withUsername("export_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
                .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M"))
        {
            mysql.start();
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            var jdbc = new JdbcTemplate(source);
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.exists(root.resolve("sql/erp_oa_reimbursement_export_recovery_20260913.sql"))) root=root.getParent();
            assertThat(root).isNotNull();
            var tables=Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS oa_reimbursement.*?;(?=\\s|$)")
                    .matcher(Files.readString(root.resolve("sql/erp_oa_reimbursement_20260730.sql")));
            int created=0; while(tables.find()){jdbc.execute(tables.group());created++;} assertThat(created).isGreaterThanOrEqualTo(7);
            String migration=Files.readString(root.resolve("sql/erp_oa_reimbursement_export_recovery_20260913.sql"));
            jdbc.execute(migration);jdbc.execute(migration);
            jdbc.execute("create table sys_user(user_id bigint primary key,nick_name varchar(50)) engine=InnoDB");
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(50)) engine=InnoDB");
            jdbc.update("insert into sys_user values(7,'Applicant')");jdbc.update("insert into sys_dept values(20,'Store A')");
            jdbc.update("insert into oa_reimbursement(reimbursement_id,reimbursement_no,title,purpose,applicant_id,applicant_name,shop_dept_id,status,total_amount,approved_time) values(17,'BX17','Trip','Taxi',7,'Applicant',20,'approved',12.34,now())");
            jdbc.update("insert into oa_reimbursement_item(reimbursement_id,expense_type,expense_date,description,claimed_amount) values(17,'Taxi','2026-09-13','Trip',12.34)");
            var config=new Configuration(new Environment("export",new SpringManagedTransactionFactory(),source));
            config.getTypeAliasRegistry().registerAliases("com.erp.oa.domain");
            String resource="mapper/oa/OaReimbursementMapper.xml";
            try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource))
            {new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
            OaReimbursementMapper mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(OaReimbursementMapper.class);
            var manager=new DataSourceTransactionManager(source);
            var properties=new OaReimbursementProperties();properties.setStorageRoot(work.resolve("private").toString());properties.setTempRoot(work.resolve("temporary").toString());
            var storage=new OaReimbursementFileStorageService(properties);
            var scope=mock(ShopScopeService.class);when(scope.resolveScopeDeptIds(20L)).thenReturn(List.of(20L));
            var service=transactional(mapper,scope,storage,properties,manager);
            String command="export_"+UUID.randomUUID();
            var pool=Executors.newFixedThreadPool(6);
            List<OaReimbursementExportBatch> results=new ArrayList<>();
            try
            {
                CountDownLatch start=new CountDownLatch(1);
                List<Future<OaReimbursementExportBatch>> futures=new ArrayList<>();
                for(int i=0;i<6;i++) futures.add(pool.submit(()->{login(9L);try{start.await();return service.createExport(request(command),20L);}finally{clear();}}));
                start.countDown();for(var future:futures)results.add(future.get(30,TimeUnit.SECONDS));
            } finally { pool.shutdownNow(); }
            login(9L);
            try
            {
                var original=results.get(0);
                assertThat(results).allSatisfy(result->{assertThat(result.getBatchId()).isEqualTo(original.getBatchId());assertThat(result.getArchivePath()).isEqualTo(original.getArchivePath());});
                assertThat(jdbc.queryForObject("select count(*) from oa_reimbursement_export_batch",Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from oa_reimbursement_export_batch_item",Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select row_version from oa_reimbursement where reimbursement_id=17",Long.class)).isEqualTo(1L);
                assertThat(service.exportByRequestId(command).getBatchId()).isEqualTo(original.getBatchId());
                byte[] originalBytes=Files.readAllBytes(storage.resolve(original.getArchivePath()));
                assertThat(service.createExport(request(command),20L).getArchiveSha256()).isEqualTo(original.getArchiveSha256());
                assertThat(Files.readAllBytes(storage.resolve(original.getArchivePath()))).isEqualTo(originalBytes);
                var changed=request(command);changed.setReimbursementIds(List.of(17L,18L));
                assertThatThrownBy(()->service.createExport(changed,20L)).hasMessageContaining("不同的报销集合或组织");
                login(10L);assertThat(service.exportByRequestId(command)).isNull();
                assertThatThrownBy(()->service.exportContent(original.getBatchId())).hasMessageContaining("本人创建");
                login(9L);
                String failedCommand="rollback_"+UUID.randomUUID();
                // Fail the final durable binding after the ZIP, header, items and exported flag were written.
                var failingMapper=mock(OaReimbursementMapper.class,org.mockito.AdditionalAnswers.delegatesTo(mapper));
                doReturn(0).when(failingMapper).completeExportCommand(eq(9L),eq(failedCommand),anyString(),anyLong());
                var failing=transactional(failingMapper,scope,storage,properties,manager);
                assertThatThrownBy(()->failing.createExport(request(failedCommand),20L)).hasMessageContaining("命令回执失败");
                assertThat(jdbc.queryForObject("select count(*) from oa_reimbursement_export_command",Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from oa_reimbursement_export_batch",Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select row_version from oa_reimbursement where reimbursement_id=17",Long.class)).isEqualTo(1L);
                try(var files=Files.walk(work.resolve("private"))){assertThat(files.filter(p->p.toString().endsWith(".zip")).toList()).containsExactly(storage.resolve(original.getArchivePath()));}
                var recovered=service.createExport(request(failedCommand),20L);
                assertThat(recovered.getBatchId()).isNotEqualTo(original.getBatchId());
                assertThat(jdbc.queryForObject("select count(*) from oa_reimbursement_export_command",Integer.class)).isEqualTo(2);
                assertThat(jdbc.queryForObject("select row_version from oa_reimbursement where reimbursement_id=17",Long.class)).isEqualTo(2L);
                // A missing original file is reported; an original command must never regenerate it.
                Files.delete(storage.resolve(original.getArchivePath()));
                assertThat(service.createExport(request(command),20L).getArchiveStatus()).isEqualTo("UNAVAILABLE");
                assertThat(jdbc.queryForObject("select count(*) from oa_reimbursement_export_batch",Integer.class)).isEqualTo(2);
                assertThat(service.exportHistory(false)).hasSize(2);
            } finally { clear(); }
        }
    }
    private static OaReimbursementExportServiceImpl transactional(OaReimbursementMapper mapper,ShopScopeService scope,OaReimbursementFileStorageService storage,OaReimbursementProperties properties,DataSourceTransactionManager manager)
    {
        var factory=new ProxyFactory(new OaReimbursementExportServiceImpl(mapper,scope,storage,properties));
        factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (OaReimbursementExportServiceImpl)factory.getProxy();
    }
    private static OaReimbursementExportRequest request(String id)
    {var request=new OaReimbursementExportRequest();request.setRequestId(id);request.setReimbursementIds(List.of(17L));return request;}
    private static void login(Long actor)
    {
        var request=new MockHttpServletRequest();request.addHeader(SecurityConstants.AUTHORIZATION_HEADER,"Bearer export-it");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.setUserId(actor.toString());SecurityContextHolder.setUserName("finance-it");
        var login=new LoginUser();login.setUserid(actor);login.setUsername("finance-it");login.setRoles(Set.of());
        login.setPermissions(Set.of(OaReimbursementServiceImpl.PERMISSION_FINANCE_EXPORT));SecurityContextHolder.set(SecurityConstants.LOGIN_USER,login);
    }
    private static void clear(){SecurityContextHolder.remove();RequestContextHolder.resetRequestAttributes();}
}
