package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
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
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.*;
import com.erp.oa.domain.dto.OaLaborContractSignRequest;
import com.erp.oa.mapper.*;
import com.erp.oa.service.IOaLaborContractService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/** Actual MySQL/mapper/Spring transactions + owned temp files. Rendering/remote identity are isolated fixtures. */
class OaLaborContractTransitionMySqlIT
{
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    static OaLaborContractMapper mapper;
    static OaLaborContractEventMapper events;
    @TempDir Path storage;
    static final List<String> ARCHIVES=List.of("signature.png","archive.docx","archive.pdf","certificate.pdf");

    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("hr.legacy.mysql.version","5.7.44");
        assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("legacy_contract_it")
                .withUsername("legacy_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withEnv("MYSQL_INITDB_SKIP_TZINFO","1");
        try{
            mysql.start();
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc=new JdbcTemplate(source);transactions=new DataSourceTransactionManager(source);
            try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("legacy-contract-concurrency-schema.sql"));}
            Configuration config=new Configuration(new Environment("legacy-it",new SpringManagedTransactionFactory(),source));
            config.getTypeAliasRegistry().registerAlias("OaLaborContract",OaLaborContract.class);
            config.getTypeAliasRegistry().registerAlias("OaLaborContractEvent",OaLaborContractEvent.class);
            for(String name:List.of("OaLaborContractMapper","OaLaborContractEventMapper")){
                String resource="mapper/oa/"+name+".xml";
                try(InputStream in=OaLaborContractTransitionMySqlIT.class.getClassLoader().getResourceAsStream(resource)){
                    new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();
                }
            }
            var sessions=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            mapper=sessions.getMapper(OaLaborContractMapper.class);events=sessions.getMapper(OaLaborContractEventMapper.class);
            assertThat(jdbc.queryForObject("select version()",String.class)).startsWith(version);
            System.out.printf("HR legacy isolated image=%s container=%s port=%s%n",version,mysql.getContainerId(),mysql.getMappedPort(3306));
        }catch(Throwable error){mysql.stop();throw error;}
    }
    @AfterAll static void stop(){if(mysql!=null){mysql.stop();assertThat(mysql.isRunning()).isFalse();}}
    @BeforeEach void fixture() throws Exception
    {
        jdbc.update("delete from oa_labor_contract_event");jdbc.update("delete from oa_labor_contract");
        jdbc.update("insert into oa_labor_contract(contract_id,template_id,employee_id,shop_dept_id,employee_name,employee_id_card,employee_phone,post_name,social_type,status,document_version,preview_file_hash,contract_file_hash) values(100,10,88,201,'fixture','fictional-id','fictional-phone','fixture','fixture','pending_sign','frozen-v1','preview-sha','preview-sha')");
        Files.createDirectories(dir());
        for(String name:List.of("preview.docx","preview.pdf","template.docx","seal.png"))Files.writeString(dir().resolve(name),"frozen-"+name);
        login(88L);
    }
    @AfterEach void clearIdentity(){SecurityContextHolder.remove();}
    Path dir(){return storage.resolve("labor-contract/100");}
    static void login(long id){SecurityContextHolder.remove();var user=new SysUser();user.setUserId(id);user.setUserName("fixture");user.setDeptId(201L);var login=new LoginUser();login.setSysUser(user);if(id==1L)login.setRoles(Set.of("admin"));SecurityContextHolder.setUserId(String.valueOf(id));SecurityContextHolder.setUserName("fixture");SecurityContextHolder.set(SecurityConstants.LOGIN_USER,login);}
    static OaLaborContractSignRequest request(){var r=new OaLaborContractSignRequest();r.setConfirmed(true);r.setDocumentVersion("frozen-v1");r.setPreviewFileHash("preview-sha");r.setSignatureDataUrl("fixture-signature");r.setSignConfirmText("本人确认签署");return r;}
    IOaLaborContractService service(OaLaborContractMapper contracts,OaLaborContractEventMapper audit,boolean generationFailure)
    {
        var target=new OaLaborContractServiceImpl();
        ReflectionTestUtils.setField(target,"contractMapper",contracts);ReflectionTestUtils.setField(target,"eventMapper",audit);
        var templates=mock(OaLaborContractTemplateMapper.class);var template=new OaLaborContractTemplate();template.setTemplateId(10L);when(templates.selectOaLaborContractTemplateById(10L)).thenReturn(template);ReflectionTestUtils.setField(target,"templateMapper",templates);
        var scope=mock(OaShopScopeService.class);when(scope.resolveRequiredShopDept(any())).thenReturn(201L);ReflectionTestUtils.setField(target,"shopScopeService",scope);
        var departments=mock(OaDeptScopeMapper.class);when(departments.countDeptInScope(201L,201L)).thenReturn(1);ReflectionTestUtils.setField(target,"deptScopeMapper",departments);
        var documents=new OaLaborContractDocumentService(){
            @Override public String calculateStoredFileSha256(Long id,String kind){return "preview-sha";}
            @Override public OaLaborContractTemplate loadFrozenTemplateSnapshot(OaLaborContract c,OaLaborContractTemplate t){return t;}
            @Override public OaCompanySealConfig loadFrozenSealSnapshot(OaLaborContract c){return new OaCompanySealConfig();}
            @Override public GeneratedContractFile generateSignedArchive(OaLaborContract c,OaLaborContractTemplate t,OaCompanySealConfig seal,OaLaborContractSignRequest r){
                try{for(String name:ARCHIVES){Files.writeString(dir().resolve(name),"generated-"+name);if(generationFailure)throw new ServiceException("injected archive failure");}}
                catch(java.io.IOException e){throw new RuntimeException(e);}
                return new GeneratedContractFile("archive-url","archive-pdf-url","archive-sha","signature-url","certificate-url","certificate-sha");
            }
        };
        ReflectionTestUtils.setField(documents,"localFilePath",storage.toString());ReflectionTestUtils.setField(target,"documentService",documents);
        var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (IOaLaborContractService)proxy.getProxy();
    }
    void frozenFilesRemain() throws Exception {for(String name:List.of("preview.docx","preview.pdf","template.docx","seal.png"))assertThat(Files.readString(dir().resolve(name))).isEqualTo("frozen-"+name);}
    void noArchiveFiles(){for(String name:ARCHIVES)assertThat(dir().resolve(name)).doesNotExist();}

    @Test void normalFirstSignPreservesExistingPreviewTemplateAndSeal() throws Exception
    {
        assertThat(service(mapper,events,false).signContract(100L,request()).getStatus()).isEqualTo("signed");
        frozenFilesRemain();for(String name:ARCHIVES)assertThat(dir().resolve(name)).isRegularFile();
        assertThat(jdbc.queryForList("select event_type from oa_labor_contract_event",String.class)).containsExactly("sign");
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void signAndVoidSerializeOnSameRowWithEitherWinner(boolean signFirst) throws Exception
    {
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1), competitorStarted=new CountDownLatch(1);AtomicBoolean first=new AtomicBoolean(true);
        var gated=spy(mapper);doAnswer(call->{var result=mapper.selectOaLaborContractByIdForUpdate(100L);if(first.compareAndSet(true,false)){locked.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}return result;}).when(gated).selectOaLaborContractByIdForUpdate(100L);
        var service=service(gated,events,false);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<?> winner=pool.submit(()->{try{login(signFirst?88L:1L);return signFirst?service.signContract(100L,request()):service.voidContract(100L,201L);}finally{SecurityContextHolder.remove();}});
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            Future<?> loser=pool.submit(()->{try{login(signFirst?1L:88L);competitorStarted.countDown();return signFirst?service.voidContract(100L,201L):service.signContract(100L,request());}finally{SecurityContextHolder.remove();}});
            assertThat(competitorStarted.await(5,TimeUnit.SECONDS)).isTrue();release.countDown();winner.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->loser.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ServiceException.class);
            var row=mapper.selectOaLaborContractById(100L);assertThat(row.getStatus()).isEqualTo(signFirst?"signed":"voided");
            assertThat(jdbc.queryForList("select event_type from oa_labor_contract_event",String.class)).containsExactly(signFirst?"sign":"void");
            if(signFirst){assertThat(row.getSignedTime()).isNotNull();assertThat(row.getVoidedTime()).isNull();}else{assertThat(row.getSignedTime()).isNull();noArchiveFiles();}
            frozenFilesRemain();
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest @ValueSource(strings={"generation","cas-zero","event-error","event-zero"})
    void failureRollsBackStateEventsAndOwnedFiles(String stage) throws Exception
    {
        var actual=spy(mapper);var audit=spy(events);
        if(stage.equals("cas-zero"))doReturn(0).when(actual).markSigned(any(),anyString(),anyString());
        if(stage.equals("event-error"))doThrow(new ServiceException("injected event failure")).when(audit).insertOaLaborContractEvent(any());
        if(stage.equals("event-zero"))doReturn(0).when(audit).insertOaLaborContractEvent(any());
        assertThatThrownBy(()->service(actual,audit,stage.equals("generation")).signContract(100L,request())).isInstanceOf(ServiceException.class);
        assertThat(mapper.selectOaLaborContractById(100L).getStatus()).isEqualTo("pending_sign");
        assertThat(mapper.selectOaLaborContractById(100L).getSignedTime()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from oa_labor_contract_event",Integer.class)).isZero();noArchiveFiles();frozenFilesRemain();
    }

    @Test void preexistingArchiveIsRetainedAndDoesNotCauseFalseSignedState() throws Exception
    {
        Files.writeString(dir().resolve("archive.pdf"),"prior unknown evidence");
        assertThatThrownBy(()->service(mapper,events,false).signContract(100L,request())).hasMessageContaining("既有归档");
        assertThat(Files.readString(dir().resolve("archive.pdf"))).isEqualTo("prior unknown evidence");
        assertThat(dir().resolve("signature.png")).doesNotExist();assertThat(dir().resolve("archive.docx")).doesNotExist();
        assertThat(mapper.selectOaLaborContractById(100L).getStatus()).isEqualTo("pending_sign");frozenFilesRemain();
    }

    @Test void identityAndFrozenVersionFailuresCannotGenerateArchives()
    {
        var service=service(mapper,events,false);login(89L);
        assertThatThrownBy(()->service.signContract(100L,request())).hasMessageContaining("本人");
        login(88L);var stale=request();stale.setDocumentVersion("stale");assertThatThrownBy(()->service.signContract(100L,stale)).hasMessageContaining("文件已更新");
        jdbc.update("update oa_labor_contract set shop_dept_id=301 where contract_id=100");
        assertThatThrownBy(()->service.voidContract(100L,201L)).hasMessageContaining("无权");noArchiveFiles();
        assertThat(jdbc.queryForObject("select count(*) from oa_labor_contract_event",Integer.class)).isZero();
    }
}
