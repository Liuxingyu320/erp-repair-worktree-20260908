package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvQualityCommand;
import com.erp.inventory.mapper.InvQualityCommandMapper;

/** Uses only a randomly named local scratch database, never existing business data. */
@EnabledIfEnvironmentVariable(named = "ERP_REPAIR_NATIVE_MYSQL", matches = "1")
class InvQualityCommandNativeMysqlTest
{
    private static String database;
    private static JdbcTemplate admin, jdbc;
    private static TransactionTemplate tx;
    private static InvQualityCommandMapper mapper;
    private static InvQualityCommandExecutor executor;

    @BeforeAll static void createDatabase() throws Exception
    {
        String base = "jdbc:mysql://127.0.0.1:3306/";
        String suffix = "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai";
        admin = new JdbcTemplate(new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + "mysql" + suffix, "root", ""));
        database = "erp_quality_repair_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create database " + database);
        UnpooledDataSource ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + database + suffix, "root", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(Files.readString(Path.of("../../sql/erp_inventory_quality_command_20260909.sql")));
        jdbc.execute(Files.readString(Path.of("../../sql/erp_inventory_quality_command_20260909.sql"))); // A repeated deployment must preserve the command table.
        jdbc.execute("create table inventory_fact (id int primary key, quantity decimal(16,4) not null) engine=InnoDB");
        jdbc.update("insert into inventory_fact values (1,0)");
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        Configuration config = new Configuration(new Environment("native", new SpringManagedTransactionFactory(), ds));
        config.getTypeAliasRegistry().registerAlias("InvQualityCommand", InvQualityCommand.class);
        String resource = "mapper/inventory/InvQualityCommandMapper.xml";
        try (InputStream in = InvQualityCommandNativeMysqlTest.class.getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        mapper = new SqlSessionTemplate(factory).getMapper(InvQualityCommandMapper.class);
        executor = new InvQualityCommandExecutor(mapper, new ObjectMapper());
    }

    @AfterAll static void cleanup()
    {
        if (database != null && database.matches("erp_quality_repair_[0-9a-f]{32}"))
            admin.execute("drop database " + database);
    }

    @BeforeEach void reset()
    {
        jdbc.update("delete from inv_quality_command");
        jdbc.update("update inventory_fact set quantity=0");
    }

    private static boolean command(InvQualityCommandExecutor target, String id, int quantity, boolean fail)
    {
        SecurityContextHolder.setUserId("77");
        SecurityContextHolder.setUserName("qc-test");
        try
        {
            return tx.execute(status -> target.execute(id, "PURCHASE_QUALITY_CHECK", 20L,
                    "purchase:10:batch:5", Map.of("quantity", quantity), Boolean.class, () -> {
                        jdbc.update("update inventory_fact set quantity=quantity+? where id=1", quantity);
                        if (fail) throw new ServiceException("forced rollback");
                        return true;
                    }));
        }
        finally { SecurityContextHolder.remove(); }
    }

    @Test void lostResponseAndExecutorRestartMustReplayWithoutAddingStock()
    {
        assertThat(command(executor, "qc-lost-response", 20, false)).isTrue();
        jdbc.update("update inv_quality_command set completed_time=date_sub(now(), interval 2 day)");
        assertThat(command(new InvQualityCommandExecutor(mapper, new ObjectMapper()), "qc-lost-response", 20, false)).isTrue();
        assertThat(jdbc.queryForObject("select quantity from inventory_fact", java.math.BigDecimal.class)).isEqualByComparingTo("20");
        assertThatThrownBy(() -> command(executor, "qc-lost-response", 40, false))
                .isInstanceOf(ServiceException.class).hasMessageContaining("拒绝覆盖");
    }

    @Test void concurrentIdenticalRequestsCommitOneBusinessFact() throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            Callable<Boolean> action = () -> { start.await(); return command(executor, "qc-concurrent", 20, false); };
            Future<Boolean> a = pool.submit(action), b = pool.submit(action);
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(b.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select quantity from inventory_fact", java.math.BigDecimal.class)).isEqualByComparingTo("20");
            assertThat(jdbc.queryForObject("select count(*) from inv_quality_command", Integer.class)).isEqualTo(1);
        }
        finally { pool.shutdownNow(); }
    }

    @Test void failedTransactionRollsBackBothCommandAndStock()
    {
        assertThatThrownBy(() -> command(executor, "qc-rollback", 20, true)).isInstanceOf(ServiceException.class);
        assertThat(jdbc.queryForObject("select count(*) from inv_quality_command", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select quantity from inventory_fact", java.math.BigDecimal.class)).isEqualByComparingTo("0");
        assertThat(command(executor, "qc-rollback", 20, false)).isTrue();
    }
}
