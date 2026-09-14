package com.erp.oa.service.impl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
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
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.*;
import com.erp.oa.domain.dto.OaFixedAssetConfigBatchRequest;
import com.erp.oa.domain.vo.OaFixedAssetConfigSnapshot;
import com.erp.oa.mapper.*;
import org.testcontainers.mysql.MySQLContainer;

/** Real configuration, range/command, annual/month quota mapper SQL and actual Spring transactions; no production connections. */
class OaFixedAssetConfigBatchMySqlIT {
    @ParameterizedTest @ValueSource(strings={"mysql:5.7.44","mysql:8.0.36"})
    void atomicSnapshotCommandsSerializeEmptyStoresAndRollbackAllQuotaEffects(String image) throws Exception {
        try(var mysql=new MySQLContainer(image).withDatabaseName("asset_batch_it").withUsername("asset_it").withPassword(UUID.randomUUID().toString()).withReuse(false).withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M")) {
            mysql.start(); assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()); var jdbc=new JdbcTemplate(source); var manager=new DataSourceTransactionManager(source);
            Path root=Path.of("").toAbsolutePath(); while(root!=null&&!Files.exists(root.resolve("sql/erp_oa_fixed_asset_config_batch_20260913.sql")))root=root.getParent();assertThat(root).isNotNull();
            var pattern=Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS oa_fixed_asset.*?;(?=\\s|$)");
            for(String file:List.of("erp_oa_fixed_asset_20260619.sql","erp_oa_fixed_asset_config_batch_20260913.sql")) {
                var matcher=pattern.matcher(Files.readString(root.resolve("sql/"+file)));int count=0;while(matcher.find()){jdbc.execute(matcher.group());count++;}assertThat(count).isGreaterThanOrEqualTo(2);
            }
            // Baseline fixture is the archived product schema; this is the existing OE rename, before the new additive migration.
            jdbc.execute("alter table oa_fixed_asset_config change product_id oe_item_id bigint not null");
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(80)) engine=InnoDB default charset=utf8mb4");
            jdbc.update("insert into sys_dept values(10,'Store A'),(20,'Store B'),(30,'Store C')");
            jdbc.execute("create table inv_oe_item(oe_item_id bigint primary key,oe_item_code varchar(60),oe_item_name varchar(80),item_description varchar(100),order_unit varchar(20),image_url varchar(255),image_urls text,purchase_reference_url varchar(255),purchase_reference_note varchar(200),cost_price decimal(16,2),status char(1),del_flag char(1),create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500)) engine=InnoDB default charset=utf8mb4");
            for(long id:List.of(101L,102L,103L,104L)) jdbc.update("insert into inv_oe_item(oe_item_id,oe_item_code,oe_item_name,item_description,order_unit,image_url,purchase_reference_url,purchase_reference_note,cost_price,status,del_flag) values(?,?,'器皿','规格','个','https://example.com/img.png','https://example.com/item','同款',3,'0','0')",id,"OE"+id);
            var config=new Configuration(new Environment("fixedAsset",new SpringManagedTransactionFactory(),source));config.getTypeAliasRegistry().registerAliases("com.erp.oa.domain");
            for(String name:List.of("OaFixedAssetConfigMapper","OaFixedAssetConfigCommandMapper","OaFixedAssetQuotaMapper","OaFixedAssetQuotaMonthMapper","OaFixedAssetQuotaLedgerMapper")) {
                String resource="mapper/oa/"+name+".xml";try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
            }
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));var commands=session.getMapper(OaFixedAssetConfigCommandMapper.class);var service=service(session,commands,manager);
            var firstRequest=request("first",10L,0L,row(null,101L,2),row(null,102L,1));
            var pool=Executors.newFixedThreadPool(6);
            try {
                var latch=new CountDownLatch(1);var futures=new ArrayList<Future<OaFixedAssetConfigSnapshot>>();
                for(int i=0;i<6;i++)futures.add(pool.submit(()->{login();try{latch.await();return service.saveConfigBatch(request("first",10L,0L,row(null,101L,2),row(null,102L,1)),10L);}finally{SecurityContextHolder.remove();}}));
                latch.countDown();var results=new ArrayList<OaFixedAssetConfigSnapshot>();for(var future:futures)results.add(future.get(30,TimeUnit.SECONDS));
                var ids=results.get(0).getRows().stream().map(OaFixedAssetConfig::getConfigId).sorted().toList();
                assertThat(results).allSatisfy(result->{assertThat(result.getVersion()).isEqualTo("1");assertThat(result.getRows().stream().map(OaFixedAssetConfig::getConfigId).sorted().toList()).isEqualTo(ids);});
                login();assertThat(jdbc.queryForObject("select count(*) from oa_fixed_asset_config",Integer.class)).isEqualTo(2);assertThat(commands.selectVersion(10L)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select asset_total_amount from oa_fixed_asset_quota where shop_dept_id=10",BigDecimal.class)).isEqualByComparingTo("9.00");
                assertThat(jdbc.queryForObject("select annual_quota_amount from oa_fixed_asset_quota where shop_dept_id=10",BigDecimal.class)).isEqualByComparingTo("1.80");
                assertThat(jdbc.queryForObject("select count(*) from oa_fixed_asset_quota_month where shop_dept_id=10",Integer.class)).isPositive();
                var baseline=snapshot(jdbc);var malformed=request("invalid",10L,1L,row(null,103L,1),row(null,104L,0));
                assertThatThrownBy(()->service.saveConfigBatch(malformed,10L)).hasMessageContaining("第2行数量");assertThat(snapshot(jdbc)).isEqualTo(baseline);
                var changed=request("first",10L,0L,row(null,101L,4));assertThatThrownBy(()->service.saveConfigBatch(changed,10L)).hasMessageContaining("不同的配置");assertThat(snapshot(jdbc)).isEqualTo(baseline);
                // Fault at final durable receipt binding, after delete/insert/update/annual quota/month snapshots.
                var failingCommands=mock(OaFixedAssetConfigCommandMapper.class,org.mockito.AdditionalAnswers.delegatesTo(commands));doReturn(0).when(failingCommands).completeCommand(eq(10L),eq(1L),eq("fail-at-end"),anyString(),anyString());
                var failing=service(session,failingCommands,manager);var replacement=request("fail-at-end",10L,1L,row(ids.get(1),102L,5),row(null,103L,2));
                assertThatThrownBy(()->failing.saveConfigBatch(replacement,10L)).hasMessageContaining("回执保存失败");assertThat(snapshot(jdbc)).isEqualTo(baseline);
                assertThat(service.selectConfigCommand("fail-at-end",10L,10L)).isNull();
                var repaired=service.saveConfigBatch(replacement,10L);assertThat(repaired.getVersion()).isEqualTo("2");assertThat(repaired.getRows()).hasSize(2);
                assertThat(jdbc.queryForObject("select asset_total_amount from oa_fixed_asset_quota where shop_dept_id=10",BigDecimal.class)).isEqualByComparingTo("21.00");
                var historical=service.saveConfigBatch(firstRequest,10L);assertThat(historical.getVersion()).isEqualTo("1");assertThat(historical.getCurrentVersion()).isEqualTo("2");
                // Two unrelated initial commands in an empty shop: one wins version zero, the other gets a conflict.
                var start=new CountDownLatch(1);var rivals=new ArrayList<Future<Object>>();
                for(int i=0;i<2;i++){final int n=i;rivals.add(pool.submit(()->{login();try{start.await();return service.saveConfigBatch(request("rival"+n,20L,0L,row(null,101L+n,1)),20L);}catch(Exception error){return error;}finally{SecurityContextHolder.remove();}}));}
                start.countDown();var outcomes=new ArrayList<Object>();for(var future:rivals)outcomes.add(future.get(30,TimeUnit.SECONDS));assertThat(outcomes.stream().filter(OaFixedAssetConfigSnapshot.class::isInstance).count()).isEqualTo(1);assertThat(outcomes.stream().filter(Throwable.class::isInstance).count()).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from oa_fixed_asset_config where shop_dept_id=20",Integer.class)).isEqualTo(1);assertThat(commands.selectVersion(20L)).isEqualTo(1L);
                // An old single-row request and new batch use the same empty-store version gate.
                var race=new CountDownLatch(1);Future<Object> old=pool.submit(()->{login();try{race.await();var value=row(null,101L,1);value.setShopDeptId(30L);value.setExpectedVersion(0L);return service.saveConfig(value,30L);}catch(Exception error){return error;}finally{SecurityContextHolder.remove();}});
                Future<Object> modern=pool.submit(()->{login();try{race.await();return service.saveConfigBatch(request("batch-race",30L,0L,row(null,102L,1)),30L);}catch(Exception error){return error;}finally{SecurityContextHolder.remove();}});
                race.countDown();Object oldResult=old.get(30,TimeUnit.SECONDS),newResult=modern.get(30,TimeUnit.SECONDS);assertThat((oldResult instanceof Throwable)^(newResult instanceof Throwable)).isTrue();assertThat(commands.selectVersion(30L)).isEqualTo(1L);assertThat(jdbc.queryForObject("select count(*) from oa_fixed_asset_config where shop_dept_id=30",Integer.class)).isEqualTo(1);
                login();long originalId=repaired.getRows().get(0).getConfigId();assertThatThrownBy(()->service.deleteConfigById(originalId,10L,1L)).hasMessageContaining("其他操作修改");
                // Empty collection is one transactional delete, including annual/month quota reset and one version step.
                service.saveConfigBatch(request("delete-all",10L,2L),10L);assertThat(jdbc.queryForObject("select count(*) from oa_fixed_asset_config where shop_dept_id=10",Integer.class)).isZero();assertThat(jdbc.queryForObject("select asset_total_amount from oa_fixed_asset_quota where shop_dept_id=10",BigDecimal.class)).isEqualByComparingTo("0.00");assertThat(commands.selectVersion(10L)).isEqualTo(3L);
            } finally {pool.shutdownNow();SecurityContextHolder.remove();}
        }
    }
    private static List<List<Map<String,Object>>> snapshot(JdbcTemplate jdbc){var result=new ArrayList<List<Map<String,Object>>>();for(String table:List.of("oa_fixed_asset_config","oa_fixed_asset_quota","oa_fixed_asset_quota_month","oa_fixed_asset_config_scope","oa_fixed_asset_config_command"))result.add(jdbc.queryForList("select * from "+table+" order by 1"));return result;}
    private static OaFixedAssetConfig row(Long id,Long oe,int qty){var row=new OaFixedAssetConfig();row.setConfigId(id);row.setOeItemId(oe);row.setAssetQuantity(new BigDecimal(qty));row.setAssetUnitPrice(new BigDecimal("3.00"));row.setStatus("0");return row;}
    private static OaFixedAssetConfigBatchRequest request(String id,Long shop,Long version,OaFixedAssetConfig...rows){var value=new OaFixedAssetConfigBatchRequest();value.setRequestId(id);value.setShopDeptId(shop);value.setExpectedVersion(version);value.setAnnualRepairRatio(new BigDecimal("20.00"));value.setRows(Arrays.asList(rows));for(var row:rows)row.setShopDeptId(shop);return value;}
    private static void login(){SecurityContextHolder.setUserId("1");SecurityContextHolder.setUserName("batch-it");}
    private static OaFixedAssetServiceImpl service(SqlSessionTemplate session,OaFixedAssetConfigCommandMapper commands,DataSourceTransactionManager manager){
        var target=new OaFixedAssetServiceImpl();ReflectionTestUtils.setField(target,"configMapper",session.getMapper(OaFixedAssetConfigMapper.class));ReflectionTestUtils.setField(target,"configCommandMapper",commands);ReflectionTestUtils.setField(target,"quotaMapper",session.getMapper(OaFixedAssetQuotaMapper.class));ReflectionTestUtils.setField(target,"monthMapper",session.getMapper(OaFixedAssetQuotaMonthMapper.class));ReflectionTestUtils.setField(target,"ledgerMapper",session.getMapper(OaFixedAssetQuotaLedgerMapper.class));ReflectionTestUtils.setField(target,"shopScopeService",mock(ShopScopeService.class));ReflectionTestUtils.setField(target,"deptScopeMapper",mock(OaDeptScopeMapper.class));var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (OaFixedAssetServiceImpl)factory.getProxy();
    }
}
