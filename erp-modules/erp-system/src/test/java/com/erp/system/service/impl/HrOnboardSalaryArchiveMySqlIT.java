package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.dto.HrOnboardSalaryArchiveRequest;
import com.erp.system.mapper.HrOnboardSalaryArchiveMapper;
import com.erp.system.mapper.SysUserProfileMapper;

/** Real MySQL mapper, JSON/decimal round trip and annotation-driven rollback; synthetic identities only. */
@Testcontainers(disabledWithoutDocker = false)
class HrOnboardSalaryArchiveMySqlIT
{
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:5.7.44")
            .withDatabaseName("salary_archive_it").withUsername("salary_it")
            .withPassword("salary_it_test_only").withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw", "/tmp", "rw", "/var/run/mysqld", "rw"))
            .withCommand("--innodb-buffer-pool-size=64M", "--innodb-log-file-size=16M", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    private JdbcTemplate jdbc;
    private HrOnboardSalaryArchiveService service;
    private DataSource dataSource;
    private HrSalarySourceService salarySources;
    private final java.util.Map<String,HrOnboardSalaryArchiveRequest> requests=new java.util.HashMap<>();

    @BeforeEach void setup() throws Exception
    {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        for (String table : List.of("sys_employee_salary_current", "sys_employee_salary_source", "oa_sign_package_salary_source", "sys_config", "sys_employee_salary_import_audit", "sys_user_profile",
                "oa_sign_onboard_import_row", "oa_sign_onboard_import_batch", "oa_sign_task"))
            jdbc.execute("drop table if exists " + table);
        jdbc.execute("create table sys_user_profile (user_id bigint primary key, id_number varchar(32), "
                + "base_salary decimal(16,2), post_salary decimal(16,2), field_allowance decimal(16,2), "
                + "performance_salary decimal(16,2), salary_total decimal(16,2), update_by varchar(64), update_time datetime) engine=InnoDB");
        jdbc.execute("create table oa_sign_onboard_import_batch (batch_id bigint primary key, shop_dept_id bigint, "
                + "file_sha256 varchar(64), created_by_user_id bigint) engine=InnoDB");
        jdbc.execute("create table oa_sign_onboard_import_row (row_id bigint primary key, batch_id bigint, "
                + "employee_id bigint, version bigint, status varchar(32), match_type varchar(32), "
                + "snapshot_json json, task_id bigint, source_event_version bigint, historical_supplement boolean default false) engine=InnoDB");
        jdbc.execute("create table oa_sign_task (task_id bigint primary key, employee_id bigint, assigned_hr_user_id bigint, "
                + "scenario varchar(32), source_type varchar(64), source_business_id varchar(64), source_event_version varchar(64)) engine=InnoDB");
        jdbc.execute("create table sys_config(config_name varchar(100),config_key varchar(100),config_value varchar(100),config_type char(1),create_by varchar(64),create_time datetime,remark varchar(255))");
        requests.clear();
        migrate();
        jdbc.update("insert into oa_sign_onboard_import_batch values (3, 201, ?, 9)", "a".repeat(64));
        seed(10, 11, 5000);
        SecurityContextHolder.setUserId("9"); SecurityContextHolder.setUserName("test_hr");

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/system/HrOnboardSalaryArchiveMapper.xml"), new ClassPathResource("mapper/system/HrSalarySourceMapper.xml"));
        HrOnboardSalaryArchiveMapper mapper = new SqlSessionTemplate(factory.getObject()).getMapper(HrOnboardSalaryArchiveMapper.class);
        SysUserProfileMapper profiles = mock(SysUserProfileMapper.class);
        when(profiles.lockSigningProfileByUserId(anyLong())).thenAnswer(call ->
                jdbc.queryForObject("select user_id from sys_user_profile where user_id=? for update", Long.class, (Object) call.getArgument(0, Long.class)));
        when(profiles.selectUserProfileByUserId(anyLong())).thenAnswer(call -> jdbc.queryForObject(
                "select * from sys_user_profile where user_id=?", (rs, index) -> {
                    SysUserProfile profile = new SysUserProfile();
                    profile.setUserId(rs.getLong("user_id")); profile.setIdNumber(rs.getString("id_number"));
                    profile.setBaseSalary(rs.getBigDecimal("base_salary")); profile.setPostSalary(rs.getBigDecimal("post_salary"));
                    profile.setFieldAllowance(rs.getBigDecimal("field_allowance"));
                    profile.setPerformanceSalary(rs.getBigDecimal("performance_salary"));
                    profile.setSalaryTotal(rs.getBigDecimal("salary_total")); return profile;
                }, (Object) call.getArgument(0, Long.class)));
        when(profiles.selectSalaryProfileForUpdate(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0, Long.class);
            jdbc.queryForObject("select user_id from sys_user_profile where user_id=? for update", Long.class, id);
            return jdbc.queryForObject("select * from sys_user_profile where user_id=? for update", (rs, index) -> {
                SysUserProfile profile = new SysUserProfile(); profile.setUserId(id); profile.setIdNumber(rs.getString("id_number"));
                profile.setBaseSalary(rs.getBigDecimal("base_salary")); profile.setPostSalary(rs.getBigDecimal("post_salary"));
                profile.setFieldAllowance(rs.getBigDecimal("field_allowance")); profile.setPerformanceSalary(rs.getBigDecimal("performance_salary"));
                profile.setSalaryTotal(rs.getBigDecimal("salary_total")); return profile;
            }, id);
        });
        HrEmployeeAccessService access = mock(HrEmployeeAccessService.class);
        org.mockito.stubbing.Answer<SysUser> scoped = call -> {
            var query = (com.erp.system.domain.vo.HrEmployeeQuery) call.getArgument(0);
            SysUser user = new SysUser(); user.setUserId(query.getUserId());
            user.setPhonenumber("138000000" + query.getUserId()); return user;
        };
        when(access.lockActiveScoped(any())).thenAnswer(scoped);
        when(access.findActiveScoped(any())).thenAnswer(scoped);
        var json = new ObjectMapper().findAndRegisterModules();
        var sources = new HrSalarySourceService(new SqlSessionTemplate(factory.getObject()).getMapper(com.erp.system.mapper.HrSalarySourceMapper.class),
                mock(com.erp.system.mapper.SysConfigMapper.class), profiles, json);
        ProxyFactory sourceProxy = new ProxyFactory(sources);
        sourceProxy.setProxyTargetClass(true);
        sourceProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        salarySources = (HrSalarySourceService) sourceProxy.getProxy();
        ProxyFactory proxy = new ProxyFactory(new HrOnboardSalaryArchiveService(mapper, profiles, access, json,
                salarySources));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        service = (HrOnboardSalaryArchiveService) proxy.getProxy();
    }
    @AfterEach void clear() { SecurityContextHolder.remove(); }
    private void migrate() { new ResourceDatabasePopulator(new ClassPathResource("db/migration/erp_employee_salary_import_20260908.sql"), new ClassPathResource("db/migration/erp_employee_salary_source_20260911.sql")).execute(dataSource); }
    private void seed(long row, long employee, int total)
    {
        jdbc.update("insert into sys_user_profile(user_id,id_number,salary_total) values (?, ?, 9000)", employee, "TEST_ID_" + employee);
        String json = "{\"phone\":\"138000000" + employee + "\",\"idNumber\":\"TEST_ID_" + employee
                + "\",\"baseSalary\":3000,\"postSalary\":1000,\"fieldAllowance\":500,\"performanceSalary\":500,\"salaryTotal\":" + total + "}";
        jdbc.update("insert into oa_sign_onboard_import_row(row_id,batch_id,employee_id,version,status,match_type,snapshot_json,task_id,source_event_version) values (?,3,?,2,'READY_TO_GENERATE','ID_NUMBER',?,null,null)", row, employee, json);
    }
    private HrOnboardSalaryArchiveRequest request(long... rows)
    {
        String key=java.util.Arrays.toString(rows);
        return requests.computeIfAbsent(key, ignored -> {
            var preview = new HrOnboardSalaryArchiveRequest(3L, java.util.Arrays.stream(rows).mapToObj(row -> new HrOnboardSalaryArchiveRequest.Row(row, 2L)).toList());
            var selected = service.preview(preview).stream().map(row -> new HrOnboardSalaryArchiveRequest.Row(row.rowId(), row.version(), row.expectedSourceId(), row.expectedProfileHash())).toList();
            return new HrOnboardSalaryArchiveRequest(3L, selected, "mysql_"+java.util.UUID.randomUUID().toString().replace("-", ""),
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")), "测试合同工资确认", true);
        });
    }

    @Test void archivesAndReplaysWithoutRewritingOrDuplicatingAudit()
    {
        assertThat(service.archive(request(10)).updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11", java.math.BigDecimal.class)).isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("select base_salary from sys_user_profile where user_id=11", java.math.BigDecimal.class)).isEqualByComparingTo("3000");
        assertThat(jdbc.queryForObject("select before_json from sys_employee_salary_import_audit", String.class)).contains("9000");
        assertThat(service.archive(request(10)).reused()).isEqualTo(1);
        migrate();
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_source", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_current", Integer.class)).isEqualTo(1);
        jdbc.update("update oa_sign_onboard_import_row set snapshot_json='{}',version=8 where row_id=10");
        assertThat(service.status(request(10)).status()).isEqualTo("CONFIRMED");
        assertThat(service.archive(request(10)).reused()).isEqualTo(1);
    }

    @Test void oneInvalidRowRollsBackAllProfileAndAuditWrites()
    {
        seed(20, 12, 5000);
        var selected = request(10, 20);
        jdbc.update("update oa_sign_onboard_import_row set snapshot_json=json_set(snapshot_json,'$.salaryTotal',7777) where row_id=20");
        assertThatThrownBy(() -> service.archive(selected)).hasMessageContaining("合计");
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11", java.math.BigDecimal.class)).isEqualByComparingTo("9000");
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_source", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_current", Integer.class)).isZero();
    }

    @Test void exactUnboundTaskAssignmentOverridesOriginalBatchCreator()
    {
        jdbc.update("update oa_sign_onboard_import_row set source_event_version=1 where row_id=10");
        jdbc.update("insert into oa_sign_task values (88,11,8,'ONBOARD','MANUAL_SIGN_EXCEL_IMPORT','10','1')");
        assertThatThrownBy(() -> service.archive(request(10))).hasMessageContaining("经办人");
        SecurityContextHolder.setUserId("8");
        assertThat(service.archive(request(10)).updated()).isEqualTo(1);
    }

    @Test void unrelatedBoundTaskCannotAuthorizeSalaryWrites()
    {
        jdbc.update("update oa_sign_onboard_import_row set task_id=88,source_event_version=1 where row_id=10");
        jdbc.update("insert into oa_sign_task values (88,99,9,'ONBOARD','MANUAL_SIGN_EXCEL_IMPORT','10','1')");
        assertThatThrownBy(() -> service.archive(request(10))).hasMessageContaining("来源不规范");
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit", Integer.class)).isZero();
    }

    @Test void concurrentConfirmationsCannotOverwriteTheWinner() throws Exception
    {
        var first = request(10);
        var second = new HrOnboardSalaryArchiveRequest(first.batchId(), first.rows(), "mysql_competing_request", first.effectiveDate(), first.reason(), true);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
            for (var attempt : List.of(first, second)) results.add(pool.submit(() -> {
                SecurityContextHolder.setUserId("9"); SecurityContextHolder.setUserName("test_hr");
                try { start.await(); service.archive(attempt); return true; }
                catch (com.erp.common.core.exception.ServiceException conflict) { assertThat(conflict.getMessage()).contains("已变化"); return false; }
                finally { SecurityContextHolder.remove(); }
            }));
            start.countDown();
            int success = 0;
            for (var result : results) if (result.get(15, java.util.concurrent.TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_source", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_current", Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void formalSalaryChangeWinsOverAStaleImportPreview() throws Exception
    {
        service.archive(request(10));
        requests.clear();
        var stale = request(10);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var change = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
                jdbc.queryForObject("select user_id from sys_user_profile where user_id=11 for update", Long.class);
                var before = salarySources.currentLocked(11L);
                var after = new ObjectMapper().findAndRegisterModules().convertValue(before, com.erp.system.api.domain.EmployeeSalarySource.class);
                after.setBaseSalary(new java.math.BigDecimal("4000")); after.setSalaryTotal(new java.math.BigDecimal("6000"));
                locked.countDown();
                try { if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test synchronization timed out"); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
                jdbc.update("update sys_user_profile set base_salary=4000,salary_total=6000 where user_id=11");
                salarySources.recordChange(11L,"TRANSFER",77L,before,after,stale.effectiveDate(),9L,"test_hr"); return true;
            }));
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var confirmation = pool.submit(() -> {
                SecurityContextHolder.setUserId("9"); SecurityContextHolder.setUserName("test_hr");
                try { service.archive(stale); return "unexpected success"; }
                catch (com.erp.common.core.exception.ServiceException conflict) { return conflict.getMessage(); }
                finally { SecurityContextHolder.remove(); }
            });
            release.countDown();
            assertThat(change.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(confirmation.get(15, java.util.concurrent.TimeUnit.SECONDS)).contains("已变化");
            assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11", java.math.BigDecimal.class)).isEqualByComparingTo("6000");
            assertThat(salarySources.current(11L).sourceType).isEqualTo("TRANSFER");
            assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_source", Integer.class)).isEqualTo(2);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void cutoverRequiresNewSchemaDeletesOldPermissionsAndCanBeReplayed() throws Exception
    {
        jdbc.execute("drop table sys_config");
        jdbc.execute("create table sys_config(config_key varchar(100),config_value varchar(20),update_by varchar(64),update_time datetime,remark varchar(255))");
        jdbc.execute("create table sys_menu(menu_id bigint primary key,perms varchar(100),status char(1))");
        jdbc.execute("create table sys_role_menu(role_id bigint,menu_id bigint)");
        jdbc.update("insert into sys_config(config_key,config_value) values ('feature.oa.attendance.v2.enabled','false')");
        jdbc.update("insert into sys_menu values (1,'oa:attendance:list','1'),(2,'oa:attendance:center:list','0')");
        jdbc.update("insert into sys_role_menu values (9,1),(9,2)");
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("../../sql/erp_attendance_v2_only_20260908.sql"));
        var missing = MYSQL.execInContainer("mysql", "-u" + MYSQL.getUsername(), "-p" + MYSQL.getPassword(), MYSQL.getDatabaseName(), "-e", sql);
        assertThat(missing.getExitCode()).isNotZero();
        assertThat(jdbc.queryForObject("select config_value from sys_config", String.class)).isEqualTo("false");
        for (String table : List.of(
                "oa_attendance_shift",
                "oa_attendance_shift_segment",
                "oa_attendance_site",
                "oa_attendance_schedule",
                "oa_attendance_schedule_segment_snapshot",
                "oa_attendance_punch_challenge",
                "oa_attendance_punch_event",
                "oa_attendance_evidence",
                "oa_attendance_day_result",
                "oa_attendance_leave_type",
                "oa_attendance_leave_request",
                "oa_attendance_leave_segment",
                "oa_attendance_leave_attachment",
                "oa_attendance_leave_approval_start_outbox",
                "oa_attendance_correction_request",
                "oa_attendance_correction_approval_start_outbox",
                "oa_attendance_remaining_work_confirmation",
                "oa_attendance_remaining_work_attachment",
                "oa_attendance_time_credit_period_lock",
                "oa_attendance_time_credit_adjustment"))
            jdbc.execute("create table " + table + "(id bigint)");
        for (int retry = 0; retry < 2; retry++)
        {
            var result = MYSQL.execInContainer("mysql", "-u" + MYSQL.getUsername(), "-p" + MYSQL.getPassword(), MYSQL.getDatabaseName(), "-e", sql);
            assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        }
        assertThat(jdbc.queryForObject("select config_value from sys_config", String.class)).isEqualTo("true");
        assertThat(jdbc.queryForObject("select count(*) from sys_menu where menu_id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_role_menu where menu_id=2", Integer.class)).isEqualTo(1);
    }
    private void pastContract(long rowId)
    {
        jdbc.update("update oa_sign_onboard_import_row set snapshot_json=json_set(snapshot_json,'$.contractStartDate','2020-01-01'),historical_supplement=0 where row_id=?",rowId);
    }
    @Test void historyBeforeConfirmationOnlyPersistsEvidenceAndUnknownResponseReplays()
    {
        pastContract(10);var command=request(10);
        assertThat(service.archive(command).updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11",java.math.BigDecimal.class)).isEqualByComparingTo("9000");
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_current",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select source_type from sys_employee_salary_source",String.class)).isEqualTo("HISTORICAL_CONTRACT_EXCEL");
        assertThat(jdbc.queryForObject("select cast(effective_date as char) from sys_employee_salary_source",String.class)).isEqualTo("2020-01-01");
        jdbc.update("update oa_sign_onboard_import_row set version=8,historical_supplement=1,status='GENERATED' where row_id=10");
        assertThat(service.status(command).status()).isEqualTo("CONFIRMED");
        assertThat(service.archive(command).reused()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_source",Integer.class)).isEqualTo(1);
        var preview=new HrOnboardSalaryArchiveRequest(3L,List.of(new HrOnboardSalaryArchiveRequest.Row(10L,8L)));
        assertThat(service.preview(preview).get(0).confirmed()).isTrue();
    }
    @Test void mixedBatchOnlyAdvancesNormalEmployeesCurrentPayroll()
    {
        seed(20,12,5000);pastContract(20);
        assertThat(service.archive(request(10,20)).updated()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11",java.math.BigDecimal.class)).isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=12",java.math.BigDecimal.class)).isEqualByComparingTo("9000");
        assertThat(jdbc.queryForList("select employee_id from sys_employee_salary_current",Long.class)).containsExactly(11L);
        assertThat(jdbc.queryForList("select employee_id from sys_employee_salary_import_audit",Long.class)).containsExactly(11L);
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_source",Integer.class)).isEqualTo(2);
    }
    @Test void mixedBatchFailureRollsBackEarlierHistoricalEvidence()
    {
        pastContract(10);seed(20,12,5000);var command=request(10,20);
        jdbc.update("update oa_sign_onboard_import_row set snapshot_json=json_set(snapshot_json,'$.salaryTotal',7777) where row_id=20");
        assertThatThrownBy(() -> service.archive(command)).hasMessageContaining("合计");
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_source",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_current",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit",Integer.class)).isZero();
        assertThat(jdbc.queryForList("select salary_total from sys_user_profile order by user_id",java.math.BigDecimal.class))
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("9000"));
    }
    @Test void historicalContractKeepsLaterCurrentSalaryAndSourcePointer()
    {
        service.archive(request(10));
        String current=jdbc.queryForObject("select source_id from sys_employee_salary_current where employee_id=11",String.class);
        pastContract(10);requests.clear();
        jdbc.update("update oa_sign_onboard_import_row set snapshot_json=json_set(snapshot_json,'$.baseSalary',2000,'$.salaryTotal',4000) where row_id=10");
        service.archive(request(10));
        assertThat(jdbc.queryForObject("select source_id from sys_employee_salary_current where employee_id=11",String.class)).isEqualTo(current);
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11",java.math.BigDecimal.class)).isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("select salary_total from sys_employee_salary_source where source_type='HISTORICAL_CONTRACT_EXCEL'",java.math.BigDecimal.class)).isEqualByComparingTo("4000");
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit",Integer.class)).isEqualTo(1);
    }

}
