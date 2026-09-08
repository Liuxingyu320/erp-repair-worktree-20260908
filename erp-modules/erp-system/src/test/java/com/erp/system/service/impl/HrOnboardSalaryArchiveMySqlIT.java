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
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw"))
            .withCommand("--innodb-buffer-pool-size=64M", "--innodb-log-file-size=16M");
    private JdbcTemplate jdbc;
    private HrOnboardSalaryArchiveService service;
    private DataSource dataSource;

    @BeforeEach void setup() throws Exception
    {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        for (String table : List.of("sys_employee_salary_import_audit", "sys_user_profile",
                "oa_sign_onboard_import_row", "oa_sign_onboard_import_batch", "oa_sign_task"))
            jdbc.execute("drop table if exists " + table);
        jdbc.execute("create table sys_user_profile (user_id bigint primary key, id_number varchar(32), "
                + "base_salary decimal(16,2), post_salary decimal(16,2), field_allowance decimal(16,2), "
                + "performance_salary decimal(16,2), salary_total decimal(16,2), update_by varchar(64), update_time datetime) engine=InnoDB");
        jdbc.execute("create table oa_sign_onboard_import_batch (batch_id bigint primary key, shop_dept_id bigint, "
                + "file_sha256 varchar(64), created_by_user_id bigint) engine=InnoDB");
        jdbc.execute("create table oa_sign_onboard_import_row (row_id bigint primary key, batch_id bigint, "
                + "employee_id bigint, version bigint, status varchar(32), match_type varchar(32), "
                + "snapshot_json json, task_id bigint, source_event_version bigint) engine=InnoDB");
        jdbc.execute("create table oa_sign_task (task_id bigint primary key, employee_id bigint, assigned_hr_user_id bigint, "
                + "scenario varchar(32), source_type varchar(64), source_business_id varchar(64), source_event_version varchar(64)) engine=InnoDB");
        migrate();
        jdbc.update("insert into oa_sign_onboard_import_batch values (3, 201, ?, 9)", "a".repeat(64));
        seed(10, 11, 5000);
        SecurityContextHolder.setUserId("9"); SecurityContextHolder.setUserName("test_hr");

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/system/HrOnboardSalaryArchiveMapper.xml"));
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
        HrEmployeeAccessService access = mock(HrEmployeeAccessService.class);
        when(access.lockActiveScoped(any())).thenAnswer(call -> {
            var query = (com.erp.system.domain.vo.HrEmployeeQuery) call.getArgument(0);
            SysUser user = new SysUser(); user.setUserId(query.getUserId());
            user.setPhonenumber("138000000" + query.getUserId()); return user;
        });
        ProxyFactory proxy = new ProxyFactory(new HrOnboardSalaryArchiveService(mapper, profiles, access, new ObjectMapper()));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        service = (HrOnboardSalaryArchiveService) proxy.getProxy();
    }
    @AfterEach void clear() { SecurityContextHolder.remove(); }
    private void migrate() { new ResourceDatabasePopulator(new ClassPathResource("db/migration/erp_employee_salary_import_20260908.sql")).execute(dataSource); }
    private void seed(long row, long employee, int total)
    {
        jdbc.update("insert into sys_user_profile(user_id,id_number,salary_total) values (?, ?, 9000)", employee, "TEST_ID_" + employee);
        String json = "{\"phone\":\"138000000" + employee + "\",\"idNumber\":\"TEST_ID_" + employee
                + "\",\"baseSalary\":3000,\"postSalary\":1000,\"fieldAllowance\":500,\"performanceSalary\":500,\"salaryTotal\":" + total + "}";
        jdbc.update("insert into oa_sign_onboard_import_row values (?,3,?,2,'READY_TO_GENERATE','ID_NUMBER',?,null,null)", row, employee, json);
    }
    private HrOnboardSalaryArchiveRequest request(long... rows)
    { return new HrOnboardSalaryArchiveRequest(3L, java.util.Arrays.stream(rows).mapToObj(row -> new HrOnboardSalaryArchiveRequest.Row(row, 2L)).toList()); }

    @Test void archivesAndReplaysWithoutRewritingOrDuplicatingAudit()
    {
        assertThat(service.archive(request(10)).updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11", java.math.BigDecimal.class)).isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("select base_salary from sys_user_profile where user_id=11", java.math.BigDecimal.class)).isEqualByComparingTo("3000");
        assertThat(jdbc.queryForObject("select before_json from sys_employee_salary_import_audit", String.class)).contains("9000");
        assertThat(service.archive(request(10)).reused()).isEqualTo(1);
        migrate();
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit", Integer.class)).isEqualTo(1);
    }

    @Test void oneInvalidRowRollsBackAllProfileAndAuditWrites()
    {
        seed(20, 12, 7777);
        assertThatThrownBy(() -> service.archive(request(10, 20))).hasMessageContaining("合计");
        assertThat(jdbc.queryForObject("select salary_total from sys_user_profile where user_id=11", java.math.BigDecimal.class)).isEqualByComparingTo("9000");
        assertThat(jdbc.queryForObject("select count(*) from sys_employee_salary_import_audit", Integer.class)).isZero();
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

    @Test void cutoverRequiresNewSchemaDeletesOldPermissionsAndCanBeReplayed() throws Exception
    {
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
}
