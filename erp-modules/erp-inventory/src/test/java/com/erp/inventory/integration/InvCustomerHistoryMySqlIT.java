package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
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
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.vo.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.*;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import org.testcontainers.mysql.MySQLContainer;

class InvCustomerHistoryMySqlIT
{
    static MySQLContainer mysql; static JdbcTemplate jdbc; static DataSourceTransactionManager manager;
    InvCustomerServiceCardServiceImpl service; InvCustomerServiceCardMapper mapper;
    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("inventory.mysql.version","5.7.44"); assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("joint_customer_history")
            .withUsername("history_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
            .withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withLabels(Map.of("erp.task","joint-customer-history-20260913"));
        try {
            mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(50)) engine=InnoDB");
            jdbc.execute("create table inv_customer(customer_id bigint primary key,customer_name varchar(100),customer_code varchar(50),contact_person varchar(50),contact_phone varchar(50),shop_dept_id bigint,status char(1),create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime) engine=InnoDB");
            Path cwd=Path.of("").toAbsolutePath();while(cwd!=null&&!Files.exists(cwd.resolve("sql/erp_inventory_customer_service_card_20260713.sql")))cwd=cwd.getParent();
            assertThat(cwd).isNotNull();String sql=Files.readString(cwd.resolve("sql/erp_inventory_customer_service_card_20260713.sql"));
            var creates=Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS inv_customer_service_.*?;(?=\\s|$)").matcher(sql);int tableCount=0;
            while(creates.find()){jdbc.execute(creates.group());tableCount++;}assertThat(tableCount).isEqualTo(3);
            jdbc.update("insert into sys_dept values(10,'store A'),(20,'store B')");
            jdbc.update("insert into inv_customer(customer_id,customer_name,shop_dept_id,status) values(1,'Customer A',10,'0'),(2,'Customer B',20,'0')");
        }catch(Throwable error){mysql.stop();throw error;}
    }
    @AfterAll static void stop(){if(mysql!=null)mysql.stop();}
    @BeforeEach void setup() throws Exception
    {
        jdbc.update("delete from inv_customer_service_record");
        Configuration config=new Configuration(new Environment("history",new SpringManagedTransactionFactory(),manager.getDataSource()));
        String resource="mapper/inventory/InvCustomerServiceCardMapper.xml";
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(InvCustomerServiceCardMapper.class);
        var target=new InvCustomerServiceCardServiceImpl(mapper,mock(InvCustomerMapper.class),null);
        var scope=mock(InvDeptScopeMapper.class);when(scope.selectDeptTypeById(anyLong())).thenReturn("STORE");ReflectionTestUtils.setField(target,"deptScopeMapper",scope);
        ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(InvCustomerServiceCardServiceImpl)factory.getProxy();login();
    }
    @AfterEach void clear(){SecurityContextHolder.remove();}
    @ParameterizedTest @ValueSource(ints={0,20,21,101,1001})
    void allHistoryIsReachableWithTiedDatesAndNoDuplicates(int count)
    {
        seed(count);List<Long> actual=new ArrayList<>();InvCustomerServiceRecordQuery query=new InvCustomerServiceRecordQuery();
        InvCustomerServiceRecordPage page; int pages = 0; Set<Long> seen = new HashSet<>();
        do {assertThat(++pages).isLessThanOrEqualTo(Math.max(1, (count + 19) / 20));page=service.selectRecords(1L,query,10L);assertThat(page.getRecords()).allMatch(row -> seen.add(row.getRecordId()));assertThat(page.getTotal()).isEqualTo((long)count);actual.addAll(page.getRecords().stream().map(InvCustomerServiceRecord::getRecordId).toList());query=next(page);}while(page.isHasMore());
        assertThat(actual).containsExactlyElementsOf(jdbc.queryForList("select record_id from inv_customer_service_record where customer_id=1 order by service_date desc,record_id desc",Long.class));
        assertThat(new HashSet<>(actual)).hasSize(count);
        assertThat(service.selectById(1L,10L).getServiceRecords()).hasSize(Math.min(count,100));
    }
    @Test void committedConcurrentAppendIsExcludedFromOldCutoffAndVisibleAfterRefresh() throws Exception
    {
        seed(41);var first=service.selectRecords(1L,new InvCustomerServiceRecordQuery(),10L);var pool=Executors.newSingleThreadExecutor();
        try{pool.submit(()->{insert("2025-01-01 10:00:00","backdated-new");insert("2026-09-13 10:00:00","current-new");}).get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        var query=next(first);List<Long> ids=new ArrayList<>(first.getRecords().stream().map(InvCustomerServiceRecord::getRecordId).toList());
        while(true){var page=service.selectRecords(1L,query,10L);assertThat(page.getTotal()).isEqualTo(41L);ids.addAll(page.getRecords().stream().map(InvCustomerServiceRecord::getRecordId).toList());if(!page.isHasMore())break;query=next(page);}
        assertThat(ids).hasSize(41);assertThat(ids).allMatch(id->id<=first.getSnapshotMaxRecordId());
        assertThat(service.selectRecords(1L,new InvCustomerServiceRecordQuery(),10L).getTotal()).isEqualTo(43L);
    }
    @Test void literalKeywordAndDateFiltersShareScopeAndCount()
    {
        insert("2026-09-13 10:00:00","tea 100% fresh");insert("2026-09-13 11:00:00","tea 1000 fresh");insert("2026-09-12 10:00:00","tea 100% older");
        var q=new InvCustomerServiceRecordQuery();q.setKeyword("100%");q.setDateFrom("2026-09-13");q.setDateTo("2026-09-13");q.setCustomerId(2L);q.setShopDeptId(20L);
        var page=service.selectRecords(1L,q,10L);assertThat(page.getTotal()).isEqualTo(1L);assertThat(page.getRecords()).hasSize(1);assertThat(page.getRecords().get(0).getServiceNote()).isEqualTo("tea 100% fresh");
        assertThatThrownBy(()->service.selectRecords(2L,new InvCustomerServiceRecordQuery(),10L)).isInstanceOf(ServiceException.class).hasMessageContaining("无权");
    }
    @Test void invalidLimitsDatesAndIncompleteCursorsAreRejected()
    {
        var q=new InvCustomerServiceRecordQuery();q.setPageSize(51);assertThatThrownBy(()->service.selectRecords(1L,q,10L)).isInstanceOf(ServiceException.class);
        q.setPageSize(20);q.setDateFrom("2026-02-30");assertThatThrownBy(()->service.selectRecords(1L,q,10L)).isInstanceOf(ServiceException.class);
        q.setDateFrom(null);q.setBeforeRecordId(1L);assertThatThrownBy(()->service.selectRecords(1L,q,10L)).isInstanceOf(ServiceException.class);
    }
    void seed(int count){new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(tx->{for(int i=0;i<count;i++)insert("2026-09-"+String.format("%02d",1+i%7)+" 10:00:00","service-"+i);});}
    static void insert(String date,String note){jdbc.update("insert into inv_customer_service_record(customer_id,service_date,service_user_id,service_user_name,service_note,shop_dept_id,request_key) values(1,?,7,'Employee',?,10,?)",date,note,UUID.randomUUID().toString());}
    static InvCustomerServiceRecordQuery next(InvCustomerServiceRecordPage page){var q=new InvCustomerServiceRecordQuery();q.setSnapshotMaxRecordId(page.getSnapshotMaxRecordId());q.setBeforeRecordId(page.getNextRecordId());q.setBeforeServiceDate(page.getNextServiceDate());return q;}
    static void login(){SecurityContextHolder.setUserId("1");SecurityContextHolder.setUserName("history-it");SysUser user=new SysUser();user.setUserId(1L);user.setDeptId(10L);LoginUser login=new LoginUser();login.setUserid(1L);login.setUsername("history-it");login.setSysUser(user);SecurityContextHolder.set(SecurityConstants.LOGIN_USER,login);}
}
