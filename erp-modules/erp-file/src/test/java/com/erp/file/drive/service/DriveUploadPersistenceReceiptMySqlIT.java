package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
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
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.domain.*;
import com.erp.file.drive.mapper.*;

class DriveUploadPersistenceReceiptMySqlIT
{
    @ParameterizedTest @ValueSource(strings={"mysql:5.7.44","mysql:8.0.36"})
    void actualNodeQuotaReservationAndReceiptCommitOrRollbackTogether(String image) throws Exception
    {
        try(MySQLContainer mysql=new MySQLContainer(image).withDatabaseName("drive_persist_it")
                .withUsername("persist_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw,size=1g"))
                .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M"))
        {
            mysql.start();var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            var jdbc=new JdbcTemplate(source);Path root=Path.of("").toAbsolutePath();
            while(root!=null&&!Files.exists(root.resolve("sql/erp_cloud_drive_upload_operation_20260913.sql")))root=root.getParent();
            assertThat(root).isNotNull();
            for(String filename:List.of("erp_cloud_drive_20260711.sql","erp_cloud_drive_organization_quota_20260713.sql"))
            {
                var tables=Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS (drive_space|drive_node|drive_upload_reservation) \\(.*?;(?=\\s|$)").matcher(Files.readString(root.resolve("sql/"+filename)));
                while(tables.find())jdbc.execute(tables.group());
            }
            jdbc.execute(Files.readString(root.resolve("sql/erp_cloud_drive_upload_operation_20260913.sql")));
            jdbc.update("insert into drive_space(space_id,space_key,space_type,space_name,quota_bytes) values(8,'PERSONAL:30','PERSONAL','Personal',1000)");
            var config=new Configuration(new Environment("persist",new SpringManagedTransactionFactory(),source));
            for(String name:List.of("DriveUploadOperationMapper","DriveUploadReservationMapper","DriveNodeMapper","DriveSpaceMapper"))
            {String resource="mapper/drive/"+name+".xml";try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}}
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            var nodes=session.getMapper(DriveNodeMapper.class);var spaces=session.getMapper(DriveSpaceMapper.class);
            var reservations=session.getMapper(DriveUploadReservationMapper.class);var operationsMapper=session.getMapper(DriveUploadOperationMapper.class);
            var manager=new DataSourceTransactionManager(source);
            var operations=new DriveUploadOperationService(operationsMapper,mock(DriveSpaceService.class),manager);
            var actor=new DriveActor(30L,8L,"Dept","actor",Set.of(),false);
            var capacity=mock(DriveCapacityService.class);when(capacity.lockForAllocationChange()).thenReturn(new DriveCapacityConfig());
            var properties=new DriveProperties();properties.setCapacityReservationEnabled(true);
            var persistence=proxied(nodes,spaces,reservations,capacity,properties,operations,manager);
            String id="upload_"+UUID.randomUUID(),reservation=UUID.randomUUID().toString();
            var claim=operations.claim(id,actor,8L,0L,"first.pdf",10,"a".repeat(64));
            reserve(jdbc,reservation,"first-key");
            var first=persistence.persist(node("first.pdf","first-key"),10,reservation,claim);
            assertThat(operations.receipt(id,actor).nodeId()).isEqualTo(first.getNodeId().toString());
            assertThat(jdbc.queryForObject("select used_bytes from drive_space where space_id=8",Long.class)).isEqualTo(10L);
            assertThat(reservations.selectById(reservation)).isNull();
            assertThatThrownBy(()->persistence.persist(node("duplicate.pdf","another-key"),10,null,claim)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select count(*) from drive_node",Integer.class)).isEqualTo(1);

            String failedId="upload_"+UUID.randomUUID(),failedReservation=UUID.randomUUID().toString();
            var failedClaim=operations.claim(failedId,actor,8L,0L,"failed.pdf",10,"b".repeat(64));
            reserve(jdbc,failedReservation,"failed-key");
            // Failure at the final receipt update must roll back the actual preceding SQL writes.
            var brokenOperations=spy(operations);doThrow(new IllegalStateException("receipt write unavailable")).when(brokenOperations).succeed(eq(failedClaim),anyLong());
            var broken=proxied(nodes,spaces,reservations,capacity,properties,brokenOperations,manager);
            assertThatThrownBy(()->broken.persist(node("failed.pdf","failed-key"),10,failedReservation,failedClaim)).hasMessageContaining("receipt write unavailable");
            assertThat(jdbc.queryForObject("select count(*) from drive_node",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select used_bytes from drive_space where space_id=8",Long.class)).isEqualTo(10L);
            assertThat(reservations.selectById(failedReservation)).isNotNull();
            assertThat(operations.receipt(failedId,actor).status()).isEqualTo("PROCESSING");
            // The same owned transaction can succeed after a definite rollback, without another quota increment.
            var recovered=persistence.persist(node("failed.pdf","failed-key"),10,failedReservation,failedClaim);
            assertThat(operations.receipt(failedId,actor).nodeId()).isEqualTo(recovered.getNodeId().toString());
            assertThat(jdbc.queryForObject("select used_bytes from drive_space where space_id=8",Long.class)).isEqualTo(20L);
            assertThat(reservations.selectById(failedReservation)).isNull();
        }
    }
    private static DriveUploadPersistence proxied(DriveNodeMapper nodes,DriveSpaceMapper spaces,DriveUploadReservationMapper reservations,DriveCapacityService capacity,DriveProperties properties,DriveUploadOperationService operations,DataSourceTransactionManager manager)
    {
        var target=new DriveUploadPersistence(nodes,new DriveQuotaService(spaces),reservations,capacity,properties);target.setUploadOperations(operations);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (DriveUploadPersistence)proxy.getProxy();
    }
    private static void reserve(JdbcTemplate jdbc,String id,String key)
    {jdbc.update("insert into drive_upload_reservation(reservation_id,space_id,storage_key,reserved_bytes,status,expire_time,create_time,update_time) values(?,8,?,10,'RESERVED',date_add(now(),interval 1 hour),now(),now())",id,key);}
    private static DriveNode node(String name,String key)
    {
        var node=new DriveNode();node.setSpaceId(8L);node.setParentId(0L);node.setNodeType("FILE");node.setNodeName(name);node.setNormalizedName(name);node.setStorageKey(key);node.setSizeBytes(10L);node.setStatus("ACTIVE");node.setActiveFlag(1);node.setVersion(0);return node;
    }
}
