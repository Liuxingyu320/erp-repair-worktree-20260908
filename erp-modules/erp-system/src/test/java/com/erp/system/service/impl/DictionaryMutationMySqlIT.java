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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.system.api.domain.SysDictType;
import com.erp.system.api.domain.SysDictData;
import com.erp.system.mapper.SysDictTypeMapper;
import com.erp.system.mapper.SysDictDataMapper;
import com.erp.system.service.support.DictCacheCoordinator;

/** Actual service/mapper transaction and concurrency evidence; Redis is covered separately. */
class DictionaryMutationMySqlIT
{
    @ParameterizedTest @ValueSource(strings={"mysql:5.7.44","mysql:8.0.36"})
    void batchDeletionAndRenameStayAtomicAndRejectStaleParentWriters(String image) throws Exception
    {
        try(var mysql=new MySQLContainer(image).withDatabaseName("dict_mutation_it")
                .withUsername("dict_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
                .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M"))
        {
            mysql.start();var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            var jdbc=new JdbcTemplate(source);
            jdbc.execute("create table sys_dict_type(dict_id bigint primary key auto_increment,dict_name varchar(100),dict_type varchar(100) not null unique,status char(1) default '0',create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table sys_dict_data(dict_code bigint primary key auto_increment,dict_sort int default 0,dict_label varchar(100),dict_value varchar(100),dict_type varchar(100) not null,css_class varchar(100),list_class varchar(100),is_default char(1),status char(1) default '0',create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500),key idx_type(dict_type)) engine=InnoDB default charset=utf8mb4");
            var config=new Configuration(new Environment("dict",new SpringManagedTransactionFactory(),source));
            config.getTypeAliasRegistry().registerAlias("SysDictType",SysDictType.class);config.getTypeAliasRegistry().registerAlias("SysDictData",SysDictData.class);
            for(String name:List.of("SysDictTypeMapper","SysDictDataMapper"))
            {String resource="mapper/system/"+name+".xml";try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}}
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            var types=session.getMapper(SysDictTypeMapper.class);var data=session.getMapper(SysDictDataMapper.class);
            var manager=new DataSourceTransactionManager(source);var tx=new TransactionTemplate(manager);
            var cache=mock(DictCacheCoordinator.class);var service=typeService(types,data,cache,manager);
            var items=itemService(types,data,cache,manager);
            jdbc.update("insert into sys_dict_type(dict_id,dict_name,dict_type) values(1,'Old','old'),(2,'Used','used'),(3,'Ambient','ambient'),(4,'First','first'),(5,'Last','last')");
            jdbc.update("insert into sys_dict_data(dict_type,dict_label,dict_value) values('used','Used item','1')");
            assertThatThrownBy(()->service.deleteDictTypeByIds(new Long[]{1L,2L})).hasMessageContaining("整批");
            assertThat(types.selectDictTypeById(1L)).isNotNull();verify(cache,never()).afterCommit(any());
            var brokenTypes=mock(SysDictTypeMapper.class,org.mockito.AdditionalAnswers.delegatesTo(types));
            doReturn(0).when(brokenTypes).deleteDictTypeById(5L);
            assertThatThrownBy(()->typeService(brokenTypes,data,cache,manager).deleteDictTypeByIds(new Long[]{5L,4L})).hasMessageContaining("整批");
            assertThat(types.selectDictTypeById(4L)).isNotNull();assertThat(types.selectDictTypeById(5L)).isNotNull();
            verify(cache,never()).afterCommit(any());

            var pool=Executors.newFixedThreadPool(2);
            try
            {
                // Under REPEATABLE READ a caller may already have a snapshot. Parent locking must still see new children.
                assertThatThrownBy(()->tx.executeWithoutResult(status->{
                    assertThat(jdbc.queryForObject("select count(*) from sys_dict_data where dict_type='ambient'",Integer.class)).isZero();
                    await(pool.submit(()->items.insertDictData(item("ambient","newly committed"))));
                    service.deleteDictTypeByIds(new Long[]{3L});
                })).hasMessageContaining("整批");
                assertThat(types.selectDictTypeById(3L)).isNotNull();assertThat(data.countDictDataByType("ambient")).isEqualTo(1);

                // A writer that resolved the old name before rename must fail after acquiring the renamed parent.
                CountDownLatch atLock=new CountDownLatch(1);
                var observedTypes=mock(SysDictTypeMapper.class,org.mockito.AdditionalAnswers.delegatesTo(types));
                doAnswer(call->{atLock.countDown();return types.lockDictTypeById(call.getArgument(0));}).when(observedTypes).lockDictTypeById(1L);
                var oldWriter=itemService(observedTypes,data,cache,manager);
                var future=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
                tx.executeWithoutResult(status->{
                    types.lockDictTypeById(1L);
                    future.set(pool.submit(()->oldWriter.insertDictData(item("old","late item"))));
                    try {assertThat(atLock.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException error){throw new IllegalStateException(error);}
                    service.updateDictType(type(1L,"renamed"));
                });
                assertThatThrownBy(()->await(future.get())).hasRootCauseMessage("字典类型已改名或删除，请刷新后重试");
                assertThat(types.selectDictTypeByType("old")).isNull();assertThat(types.selectDictTypeByType("renamed")).isNotNull();
                assertThat(data.countDictDataByType("old")).isZero();assertThat(data.countDictDataByType("renamed")).isZero();
                items.insertDictData(item("renamed","current item"));assertThat(data.countDictDataByType("renamed")).isEqualTo(1);

                // A failure after child names changed must restore both child and parent names.
                doReturn(0).when(brokenTypes).updateDictType(any());
                assertThatThrownBy(()->typeService(brokenTypes,data,cache,manager).updateDictType(type(1L,"failed-rename"))).hasMessageContaining("未保存");
                assertThat(data.countDictDataByType("renamed")).isEqualTo(1);assertThat(data.countDictDataByType("failed-rename")).isZero();
                assertThat(types.selectDictTypeById(1L).getDictType()).isEqualTo("renamed");
            } finally {pool.shutdownNow();}
        }
    }
    private static SysDictTypeServiceImpl typeService(SysDictTypeMapper types,SysDictDataMapper data,DictCacheCoordinator cache,DataSourceTransactionManager manager)
    {var value=new SysDictTypeServiceImpl();wire(value,types,data,cache);return (SysDictTypeServiceImpl)proxy(value,manager);}
    private static SysDictDataServiceImpl itemService(SysDictTypeMapper types,SysDictDataMapper data,DictCacheCoordinator cache,DataSourceTransactionManager manager)
    {var value=new SysDictDataServiceImpl();wire(value,types,data,cache);return (SysDictDataServiceImpl)proxy(value,manager);}
    private static void wire(Object target,SysDictTypeMapper types,SysDictDataMapper data,DictCacheCoordinator cache)
    {ReflectionTestUtils.setField(target,"dictTypeMapper",types);ReflectionTestUtils.setField(target,"dictDataMapper",data);ReflectionTestUtils.setField(target,"dictCache",cache);}
    private static Object proxy(Object target,DataSourceTransactionManager manager)
    {var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return proxy.getProxy();}
    private static int await(Future<Integer> future)
    {try{return future.get(10,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException(error);}}
    private static SysDictType type(Long id,String name){var type=new SysDictType();type.setDictId(id);type.setDictName(name);type.setDictType(name);return type;}
    private static SysDictData item(String type,String label){var value=new SysDictData();value.setDictType(type);value.setDictLabel(label);value.setDictValue(label);value.setStatus("0");return value;}
}
