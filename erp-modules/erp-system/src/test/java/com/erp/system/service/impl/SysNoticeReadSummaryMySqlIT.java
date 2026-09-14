package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
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
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.erp.system.domain.vo.SysNoticeReadPage;
import com.erp.system.domain.vo.SysNoticeReadUserVo;
import com.erp.system.mapper.SysNoticeReadMapper;
import com.erp.system.service.support.NoticeHtmlSanitizer;

/** Actual mapper, PageHelper and Spring RR service transaction. All DB writes target new disposable containers. */
class SysNoticeReadSummaryMySqlIT
{
    @ParameterizedTest @ValueSource(strings={"mysql:5.7.44","mysql:8.0.36"})
    void nameSearchAndPagingNeverChangeWholeNoticeSummaryAndShareOneSnapshot(String image) throws Exception
    {
        try(var mysql=new MySQLContainer(image).withDatabaseName("notice_summary_it")
                .withUsername("notice_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
                .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M"))
        {
            mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            var jdbc=new JdbcTemplate(source);var manager=new DataSourceTransactionManager(source);
            jdbc.execute("create table sys_user(user_id bigint primary key,user_name varchar(64),nick_name varchar(64),dept_id bigint,del_flag char(1)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(64)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table sys_notice_recipient(notice_id bigint not null,user_id bigint not null,primary key(notice_id,user_id)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table sys_notice_read(read_id bigint auto_increment primary key,notice_id bigint not null,user_id bigint not null,read_time datetime,unique key uk_notice_user(notice_id,user_id)) engine=InnoDB default charset=utf8mb4");
            jdbc.update("insert into sys_dept values(8,'财务部')");
            jdbc.update("insert into sys_user values(1,'zhang_one','张甲',8,'0'),(2,'zhang_two','张乙',8,'0'),(3,'li_three','李丙',8,'0'),(4,'deleted','张已删除',8,'2'),(5,'unread','未读人',8,'0'),(6,'outsider','非接收人',8,'0')");
            jdbc.update("insert into sys_notice_recipient values(17,1),(17,2),(17,3),(17,4),(17,5),(18,6)");
            jdbc.update("insert into sys_notice_read(notice_id,user_id,read_time) values(17,1,'2026-09-13 10:00:00'),(17,2,'2026-09-13 11:00:00'),(17,3,'2026-09-13 12:00:00'),(17,4,'2026-09-13 13:00:00'),(17,6,'2026-09-13 14:00:00'),(18,6,'2026-09-13 15:00:00')");
            var config=new Configuration(new Environment("noticeSummary",new SpringManagedTransactionFactory(),source));
            config.getTypeAliasRegistry().registerAliases("com.erp.system.domain");
            var pagination=new PageInterceptor();var props=new Properties();props.setProperty("helperDialect","mysql");pagination.setProperties(props);config.addInterceptor(pagination);
            String resource="mapper/system/SysNoticeReadMapper.xml";
            try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));var mapper=session.getMapper(SysNoticeReadMapper.class);
            var service=service(mapper,manager);
            PageHelper.startPage(1,1);var first=service.selectReadUsersPage(17L,"张");check(first,1,2,3,5);
            long firstId=((SysNoticeReadUserVo)first.getRows().get(0)).getUserId();
            PageHelper.startPage(2,1);var second=service.selectReadUsersPage(17L,"张");check(second,1,2,3,5);
            assertThat(((SysNoticeReadUserVo)second.getRows().get(0)).getUserId()).isNotEqualTo(firstId);
            PageHelper.startPage(1,1);check(service.selectReadUsersPage(17L,"找不到姓名"),0,0,3,5);
            PageHelper.startPage(1,1);check(service.selectReadUsersPage(17L,"zhang_two"),1,1,3,5);
            PageHelper.startPage(1,2);check(service.selectReadUsersPage(17L,""),2,3,3,5);
            PageHelper.startPage(1,1);check(service.selectReadUsersPage(18L,""),1,1,1,1);
            assertThat(PageHelper.getLocalPage()).isNull();
            // A read committed by another connection after page SQL must not leak into the same service call's summary.
            var hooked=mock(SysNoticeReadMapper.class,org.mockito.AdditionalAnswers.delegatesTo(mapper));
            var pool=Executors.newSingleThreadExecutor();
            try {
                doAnswer(call->{var rows=mapper.selectReadUsersByNoticeId(17L,"张");pool.submit(()->jdbc.update("insert into sys_notice_read(notice_id,user_id,read_time) values(17,5,now())")).get(10,TimeUnit.SECONDS);return rows;})
                        .when(hooked).selectReadUsersByNoticeId(17L,"张");
                PageHelper.startPage(1,1);check(service(hooked,manager).selectReadUsersPage(17L,"张"),1,2,3,5);
                PageHelper.startPage(1,1);check(service.selectReadUsersPage(17L,"张"),1,2,4,5);
            } finally {pool.shutdownNow();PageHelper.clearPage();}
        } finally {PageHelper.clearPage();}
    }
    private static void check(SysNoticeReadPage page,int size,long filtered,long read,long recipients)
    {assertThat(page.getRows()).hasSize(size);assertThat(page.getTotal()).isEqualTo(filtered);assertThat(page.getSummary().getReadCount()).isEqualTo(read);assertThat(page.getSummary().getRecipientCount()).isEqualTo(recipients);assertThat(PageHelper.getLocalPage()).isNull();}
    private static SysNoticeReadServiceImpl service(SysNoticeReadMapper mapper,DataSourceTransactionManager manager)
    {var target=new SysNoticeReadServiceImpl(mapper,mock(NoticeHtmlSanitizer.class));var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (SysNoticeReadServiceImpl)factory.getProxy();}
}
