package com.erp.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import com.erp.job.domain.SysJob;
import com.erp.job.mapper.SysJobMapper;
import com.erp.job.mapper.SysJobDeleteMapper;
import com.erp.job.util.ScheduleUtils;
import com.erp.common.core.exception.ServiceException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker=false)
class SysJobDeletionMySqlIT
{
    @ParameterizedTest
    @ValueSource(strings={"mysql:5.7.44","mysql:8.0.36"})
    void deletionIsAtomicDurableRevisionBoundAndRecoverableAcrossTwoRamSchedulers(String image) throws Exception
    {
        try (MySQLContainer mysql=new MySQLContainer(image).withDatabaseName("job_delete_it")
                .withUsername("job_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(java.util.Map.of("/var/lib/mysql","rw,size=1g"))
                .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M"))
        {
            mysql.start();
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            var jdbc=new JdbcTemplate(source);
            jdbc.execute("create table sys_job(job_id bigint primary key auto_increment,job_name varchar(64),job_group varchar(64),invoke_target varchar(500),cron_expression varchar(255),misfire_policy varchar(20),concurrent varchar(1),status char(1),create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500))");
            Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
            while(root!=null&&!java.nio.file.Files.isRegularFile(root.resolve("sql/erp_job_durable_deletion_20260913.sql")))root=root.getParent();
            if(root==null)throw new IllegalStateException("actual migration missing");
            var migration=new ResourceDatabasePopulator(new FileSystemResource(root.resolve("sql/erp_job_durable_deletion_20260913.sql")));
            migration.execute(source);migration.execute(source);
            Configuration config=new Configuration(new Environment("job",new SpringManagedTransactionFactory(),source));
            config.getTypeAliasRegistry().registerAlias("SysJob",SysJob.class);
            for(String name:List.of("SysJobMapper","SysJobDeleteMapper")){
                String resource="mapper/job/"+name+".xml";
                try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)) {new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
            }
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            var jobs=session.getMapper(SysJobMapper.class);var deletes=session.getMapper(SysJobDeleteMapper.class);
            var manager=new DataSourceTransactionManager(source);var tx=new TransactionTemplate(manager);
            Scheduler first=scheduler(),second=scheduler();
            try
            {
                var sync1=new SysJobSchedulerReconciler(first,jobs,deletes,manager);
                var sync2=new SysJobSchedulerReconciler(second,jobs,deletes,manager);
                var deferred=new SysJobDeletionService(jobs,deletes,manager,mock(SysJobSchedulerReconciler.class));
                SysJob a=insert(jobs,tx,"a"),b=insert(jobs,tx,"b");
                sync1.synchronizeDefinition(a.getJobId());sync1.synchronizeDefinition(b.getJobId());
                sync2.synchronizeDefinition(a.getJobId());
                // Last target missing must roll back the entire batch and retain both schedulers.
                String bad="delete_"+UUID.randomUUID();
                assertThatThrownBy(()->deferred.delete(new SysJobDeletionService.Request(bad,List.of(target(a),new SysJobDeletionService.Target(999L,"missing"))),30L)).isInstanceOf(ServiceException.class);
                assertThat(jobs.selectJobById(a.getJobId())).isNotNull();assertThat(deletes.selectBatch(bad)).isNull();
                assertThat(first.checkExists(ScheduleUtils.getJobKey(a.getJobId(),a.getJobGroup()))).isTrue();
                String stale="delete_"+UUID.randomUUID();
                assertThatThrownBy(()->deferred.delete(new SysJobDeletionService.Request(stale,List.of(new SysJobDeletionService.Target(a.getJobId(),"old"))),30L)).isInstanceOf(ServiceException.class);
                // Crash after commit: definitions deleted but triggers still present; execution guard rejects both.
                String id="delete_"+UUID.randomUUID();
                var request=new SysJobDeletionService.Request(id,List.of(target(b),target(a),target(a)));
                assertThat(deferred.delete(request,30L).status()).isEqualTo("PENDING");
                assertThat(jobs.selectJobById(a.getJobId())).isNull();
                assertThat(first.checkExists(ScheduleUtils.getJobKey(a.getJobId(),a.getJobGroup()))).isTrue();
                assertThat(new SysJobExecutionGuard(jobs).isCurrent(a,false)).isFalse();
                assertThat(deferred.delete(request,30L).items()).hasSize(2);
                assertThatThrownBy(()->deferred.receipt(id,31L)).isInstanceOf(ServiceException.class);
                assertThatThrownBy(()->deferred.delete(new SysJobDeletionService.Request(id,List.of(target(a))),30L)).isInstanceOf(ServiceException.class);
                sync1.tick();sync2.tick();
                assertThat(deferred.receipt(id,30L).status()).isEqualTo("COMPLETED");
                assertThat(first.checkExists(ScheduleUtils.getJobKey(a.getJobId(),a.getJobGroup()))).isFalse();
                assertThat(second.checkExists(ScheduleUtils.getJobKey(a.getJobId(),a.getJobGroup()))).isFalse();
                // A scheduler exception persists a retry; next scan completes it without recreating the definition.
                SysJob retryJob=insert(jobs,tx,"retry");sync1.synchronizeDefinition(retryJob.getJobId());
                String retryId="delete_"+UUID.randomUUID();deferred.delete(new SysJobDeletionService.Request(retryId,List.of(target(retryJob))),30L);
                Scheduler failing=spy(first);doThrow(new SchedulerException("unavailable")).when(failing).deleteJob(ScheduleUtils.getJobKey(retryJob.getJobId(),retryJob.getJobGroup()));
                new SysJobSchedulerReconciler(failing,jobs,deletes,manager).synchronizeBatch(retryId);
                assertThat(deferred.receipt(retryId,30L).status()).isEqualTo("RETRYING");
                sync1.synchronizeBatch(retryId);assertThat(deferred.receipt(retryId,30L).status()).isEqualTo("COMPLETED");
                // Restored/new generation at the same numeric ID must survive an older deletion intent.
                SysJob old=insert(jobs,tx,"old");sync1.synchronizeDefinition(old.getJobId());
                String oldId="delete_"+UUID.randomUUID();deferred.delete(new SysJobDeletionService.Request(oldId,List.of(target(old))),30L);
                SysJob replacement=definition("replacement");replacement.setJobId(old.getJobId());tx.executeWithoutResult(status->jobs.insertJob(replacement));
                sync1.synchronizeDefinition(replacement.getJobId());sync1.synchronizeBatch(oldId);
                assertThat(deferred.receipt(oldId,30L).items().get(0).status()).isEqualTo("SUPERSEDED");
                assertThat(first.checkExists(ScheduleUtils.getJobKey(old.getJobId(),old.getJobGroup()))).isTrue();
                SysJob scheduled=SysJobSchedulerReconciler.ownedDefinition(first.getJobDetail(ScheduleUtils.getJobKey(old.getJobId(),old.getJobGroup())),ScheduleUtils.getJobKey(old.getJobId(),old.getJobGroup()));
                assertThat(scheduled.getRevision()).isEqualTo(replacement.getRevision());
                // Deletion called inside a larger transaction must not touch Quartz until that transaction commits.
                SysJob outer=insert(jobs,tx,"outer");sync1.synchronizeDefinition(outer.getJobId());
                var watched=spy(sync1);var nested=new SysJobDeletionService(jobs,deletes,manager,watched);
                String outerId="delete_"+UUID.randomUUID();
                tx.executeWithoutResult(status->{
                    nested.delete(new SysJobDeletionService.Request(outerId,List.of(target(outer))),30L);
                    verify(watched,never()).synchronizeBatch(outerId);status.setRollbackOnly();
                });
                verify(watched,never()).synchronizeBatch(outerId);
                assertThat(jobs.selectJobById(outer.getJobId())).isNotNull();assertThat(deletes.selectBatch(outerId)).isNull();
                assertThat(first.checkExists(ScheduleUtils.getJobKey(outer.getJobId(),outer.getJobGroup()))).isTrue();
                tx.executeWithoutResult(status->{
                    nested.delete(new SysJobDeletionService.Request(outerId,List.of(target(outer))),30L);
                    verify(watched,never()).synchronizeBatch(outerId);
                });
                verify(watched).synchronizeBatch(outerId);assertThat(jobs.selectJobById(outer.getJobId())).isNull();
                // Replay inside a transaction whose old snapshot predates the committed command.
                SysJob snapshotJob=insert(jobs,tx,"old-snapshot");
                String snapshotId="delete_"+UUID.randomUUID();
                var snapshotRequest=new SysJobDeletionService.Request(snapshotId,List.of(target(snapshotJob)));
                var writer=java.util.concurrent.Executors.newSingleThreadExecutor();
                try
                {
                    tx.executeWithoutResult(status->{
                        assertThat(jdbc.queryForObject("select count(*) from sys_job_delete_batch where batch_id=?",Integer.class,snapshotId)).isZero();
                        try {assertThat(writer.submit(()->deferred.delete(snapshotRequest,30L)).get(10,java.util.concurrent.TimeUnit.SECONDS).items()).hasSize(1);}
                        catch(Exception error){throw new IllegalStateException(error);}
                        var replay=deferred.delete(snapshotRequest,30L);
                        assertThat(replay.status()).isEqualTo("PENDING");
                        assertThat(replay.items()).hasSize(1);
                        assertThat(replay.items().get(0).jobId()).isEqualTo(snapshotJob.getJobId().toString());
                    });
                    assertThat(deletes.selectBatchIntents(snapshotId)).hasSize(1);
                }
                finally {writer.shutdownNow();assertThat(writer.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
                // New independent batches must not acquire empty intent-range locks before inserting.
                SysJob independentA=insert(jobs,tx,"parallel-a"),independentB=insert(jobs,tx,"parallel-b");
                var independent=java.util.concurrent.Executors.newFixedThreadPool(2);
                try
                {
                    var start=new java.util.concurrent.CountDownLatch(1);
                    var parallel=new java.util.ArrayList<java.util.concurrent.Future<ReceiptHolder>>();
                    for(SysJob job:List.of(independentA,independentB))
                        parallel.add(independent.submit(()->{
                            start.await();
                            var answer=deferred.delete(new SysJobDeletionService.Request("delete_"+UUID.randomUUID(),List.of(target(job))),30L);
                            return new ReceiptHolder(answer.items().size(),answer.status());
                        }));
                    start.countDown();
                    for(var future:parallel){var answer=future.get(10,java.util.concurrent.TimeUnit.SECONDS);assertThat(answer.size()).isEqualTo(1);assertThat(answer.status()).isEqualTo("PENDING");}
                }
                finally {independent.shutdownNow();assertThat(independent.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
            }
            finally {first.shutdown(true);second.shutdown(true);}
        }
    }
    private static SysJobDeletionService.Target target(SysJob job){return new SysJobDeletionService.Target(job.getJobId(),job.getRevision());}
    private record ReceiptHolder(int size,String status){}
    private static SysJob insert(SysJobMapper mapper,TransactionTemplate tx,String name){SysJob job=definition(name);tx.executeWithoutResult(status->mapper.insertJob(job));return job;}
    private static SysJob definition(String name){SysJob job=new SysJob();job.setJobName(name);job.setJobGroup("DEFAULT");job.setRevision(UUID.randomUUID().toString());job.setInvokeTarget("ryTask.ryNoParams");job.setCronExpression("0 0 0 1 1 ? 2099");job.setStatus("1");job.setConcurrent("1");return job;}
    private static Scheduler scheduler() throws Exception{
        Properties properties=new Properties();properties.setProperty("org.quartz.scheduler.instanceName","job-it-"+UUID.randomUUID());properties.setProperty("org.quartz.threadPool.threadCount","1");properties.setProperty("org.quartz.jobStore.class","org.quartz.simpl.RAMJobStore");return new StdSchedulerFactory(properties).getScheduler();
    }
}
