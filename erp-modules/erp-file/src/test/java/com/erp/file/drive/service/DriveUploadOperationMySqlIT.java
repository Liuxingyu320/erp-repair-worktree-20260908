package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveUploadOperationMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = false)
class DriveUploadOperationMySqlIT
{
    @ParameterizedTest
    @ValueSource(strings = {"mysql:5.7.44", "mysql:8.0.36"})
    void durableClaimsAndReceiptsStayConsistentAcrossConcurrencyRollbackAndLostCommitReply(String image) throws Exception
    {
        try (MySQLContainer mysql = new MySQLContainer(image).withDatabaseName("upload_operation_it")
                .withUsername("upload_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=1g"))
                .withCommand("--innodb-buffer-pool-size=64M", "--innodb-log-file-size=16M"))
        {
            mysql.start();
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            while (root != null && !java.nio.file.Files.isRegularFile(root.resolve("sql/erp_cloud_drive_upload_operation_20260913.sql"))) root = root.getParent();
            if (root == null) throw new IllegalStateException("actual upload operation migration not found");
            var migration = new ResourceDatabasePopulator(new FileSystemResource(root.resolve("sql/erp_cloud_drive_upload_operation_20260913.sql")));
            migration.execute(source);
            migration.execute(source);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("create table probe_node(id bigint primary key, bytes bigint)");
            jdbc.execute("create table probe_quota(id bigint primary key, used_bytes bigint)");
            jdbc.update("insert into probe_quota values(1,0)");
            Configuration config = new Configuration(new Environment("receipt", new SpringManagedTransactionFactory(), source));
            String resource = "mapper/drive/DriveUploadOperationMapper.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
            DriveUploadOperationMapper mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config))
                    .getMapper(DriveUploadOperationMapper.class);
            var manager = new DataSourceTransactionManager(source);
            var service = new DriveUploadOperationService(mapper, mock(DriveSpaceService.class), manager);
            var tx = new TransactionTemplate(manager);
            DriveActor actor = new DriveActor(20L, 8L, "test", "test", Set.of(), false);
            String id = "upload_" + UUID.randomUUID();
            var workers = Executors.newFixedThreadPool(6);
            try
            {
                CountDownLatch go = new CountDownLatch(1);
                var claims = new ArrayList<java.util.concurrent.Future<DriveUploadOperationService.Claim>>();
                for (int i=0;i<6;i++) claims.add(workers.submit(() -> {
                    assertThat(go.await(5,TimeUnit.SECONDS)).isTrue();
                    return service.claim(id, actor, 4L, 0L, "file.pdf", 12, "a".repeat(64));
                }));
                go.countDown();
                var results = new ArrayList<DriveUploadOperationService.Claim>();
                for (var future : claims) results.add(future.get(10,TimeUnit.SECONDS));
                assertThat(results.stream().filter(DriveUploadOperationService.Claim::acquired)).hasSize(1);
                var owner = results.stream().filter(DriveUploadOperationService.Claim::acquired).findFirst().orElseThrow();
                assertThat(service.receipt(id,actor).status()).isEqualTo("PROCESSING");
                assertThatThrownBy(() -> service.claim(id,actor,5L,0L,"file.pdf",12,"a".repeat(64))).isInstanceOf(DriveException.class);
                assertThatThrownBy(() -> service.claim(id,actor,4L,0L,"file.pdf",12,"b".repeat(64))).isInstanceOf(DriveException.class);
                assertThatThrownBy(() -> service.receipt(id,new DriveActor(21L,8L,"test","other",Set.of(),false))).isInstanceOf(DriveException.class);
                assertThat(service.receipt("upload_"+UUID.randomUUID(),actor).status()).isEqualTo("NOT_OBSERVED");
                assertThatThrownBy(() -> service.lockWriter(owner)).isInstanceOf(IllegalStateException.class);
                // Simulated crash before commit rolls node, quota AND receipt back together.
                assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    service.lockWriter(owner);
                    jdbc.update("insert into probe_node values(77,12)");
                    jdbc.update("update probe_quota set used_bytes=used_bytes+12 where id=1");
                    service.succeed(owner,77L);
                    throw new IllegalStateException("fault after all writes");
                })).isInstanceOf(IllegalStateException.class);
                assertThat(jdbc.queryForObject("select count(*) from probe_node",Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select used_bytes from probe_quota",Long.class)).isZero();
                assertThat(service.receipt(id,actor).status()).isEqualTo("PROCESSING");
                // A cleanup attempt waits for a possibly-committing node transaction; it must not delete its object.
                CountDownLatch written = new CountDownLatch(1), allowCommit = new CountDownLatch(1), cleanupStarted = new CountDownLatch(1);
                var commit = workers.submit(() -> tx.executeWithoutResult(status -> {
                    service.lockWriter(owner);
                    jdbc.update("insert into probe_node values(77,12)");
                    jdbc.update("update probe_quota set used_bytes=used_bytes+12 where id=1");
                    service.succeed(owner,77L);
                    written.countDown();
                    try { if (!allowCommit.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("commit timeout"); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                }));
                assertThat(written.await(5,TimeUnit.SECONDS)).isTrue();
                var cleanup = workers.submit(() -> {cleanupStarted.countDown();return service.fenceForCleanup(owner);});
                assertThat(cleanupStarted.await(5,TimeUnit.SECONDS)).isTrue();
                try { assertThatThrownBy(() -> cleanup.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class); }
                finally { allowCommit.countDown(); }
                commit.get(5,TimeUnit.SECONDS);
                assertThat(cleanup.get(5,TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
                assertThat(service.receipt(id,actor).nodeId()).isEqualTo("77");
                assertThat(service.claim(id,actor,4L,0L,"file.pdf",12,"a".repeat(64)).acquired()).isFalse();
                assertThat(jdbc.queryForObject("select used_bytes from probe_quota",Long.class)).isEqualTo(12L);
                // Only a known safe cleanup allows a new owner. Old owners cannot commit after that transition.
                String failedId="upload_"+UUID.randomUUID();
                var failed=service.claim(failedId,actor,4L,0L,"file.pdf",12,"a".repeat(64));
                assertThat(service.fenceForCleanup(failed).status()).isEqualTo("CLEANING");
                assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.lockWriter(failed))).isInstanceOf(DriveException.class);
                service.finishCleanup(failed,true);
                var retry=service.claim(failedId,actor,4L,0L,"file.pdf",12,"a".repeat(64));
                assertThat(retry.acquired()).isTrue();assertThat(retry.owner()).isNotEqualTo(failed.owner());
                assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.lockWriter(failed))).isInstanceOf(DriveException.class);
                service.fenceForCleanup(retry);service.finishCleanup(retry,false);
                assertThat(service.receipt(failedId,actor).status()).isEqualTo("REVIEW_REQUIRED");
                assertThat(service.claim(failedId,actor,4L,0L,"file.pdf",12,"a".repeat(64)).acquired()).isFalse();
                // A process restart does not grant ownership of any in-flight operation.
                var restarted=new DriveUploadOperationService(mapper,mock(DriveSpaceService.class),manager);
                assertThat(restarted.receipt(id,actor).nodeId()).isEqualTo("77");
                assertThat(restarted.claim(failedId,actor,4L,0L,"file.pdf",12,"a".repeat(64)).acquired()).isFalse();
            }
            finally {workers.shutdownNow();}
        }
    }
}
