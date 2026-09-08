package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.oa.domain.OaSalaryEmployee;

/** Actual mapper on an isolated MySQL schema, using synthetic employee identities. */
@Testcontainers(disabledWithoutDocker = false)
class OaSalaryEmployeeHistoryMySqlIT
{
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:5.7.44")
            .withDatabaseName("payroll_history_it").withUsername("payroll_it")
            .withPassword("payroll_it_test_only").withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw"))
            .withCommand("--innodb-buffer-pool-size=64M", "--innodb-log-file-size=16M");
    private JdbcTemplate jdbc;
    private OaSalaryEmployeeMapper mapper;

    @BeforeEach
    void setup() throws Exception
    {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source);
        for (String table : List.of("sys_user", "sys_user_profile", "sys_user_shop",
                "oa_attendance_schedule", "oa_salary_record"))
            jdbc.execute("drop table if exists " + table);
        jdbc.execute("create table sys_user (user_id bigint primary key, user_name varchar(64), "
                + "dept_id bigint, status char(1), del_flag char(1))");
        jdbc.execute("create table sys_user_profile (user_id bigint primary key, "
                + "base_salary decimal(16,2), post_salary decimal(16,2), field_allowance decimal(16,2), "
                + "performance_salary decimal(16,2), salary_total decimal(16,2), entry_date date, leave_date date)");
        jdbc.execute("create table sys_user_shop (user_id bigint, dept_id bigint)");
        jdbc.execute("create table oa_attendance_schedule (user_id bigint, shop_id bigint, "
                + "business_date date, status varchar(32))");
        jdbc.execute("create table oa_salary_record (user_id bigint, shop_dept_id bigint, salary_month varchar(7))");
        jdbc.update("insert into sys_user values (11,'history-fixture',201,'0','0')");
        jdbc.update("insert into sys_user_profile values (11,3000,0,0,0,3000,'2026-01-01',null)");
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setTypeAliases(OaSalaryEmployee.class);
        factory.setMapperLocations(new ClassPathResource("mapper/oa/OaSalaryEmployeeMapper.xml"));
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(OaSalaryEmployeeMapper.class);
    }

    @Test
    void includesDisabledAndSoftDeletedEmployeesWithPublishedAttendance()
    {
        jdbc.update("insert into oa_attendance_schedule values (11,201,'2026-08-31','PUBLISHED')");
        jdbc.update("update sys_user set status='1', del_flag='2'");
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-08"))
                .extracting(OaSalaryEmployee::getUserId).containsExactly(11L);
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-09")).isEmpty();
    }

    @Test
    void keepsOriginalStoreAfterTransferAndBoundsMonthAndPublicationState()
    {
        jdbc.update("update sys_user set dept_id=202");
        jdbc.update("insert into oa_attendance_schedule values (11,201,'2026-08-01','PUBLISHED'),"
                + "(11,201,'2026-08-31','PUBLISHED'),(11,201,'2026-09-01','DRAFT')");
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-08"))
                .extracting(OaSalaryEmployee::getUserId).containsExactly(11L);
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-09")).isEmpty();
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(203L, "2026-08")).isEmpty();
    }

    @Test
    void retainsExistingSalaryEmployeeForPreflightEvenIfScheduleIsMissing()
    {
        jdbc.update("update sys_user set status='1', dept_id=202");
        jdbc.update("insert into oa_salary_record values (11,201,'2026-08')");
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-08"))
                .extracting(OaSalaryEmployee::getUserId).containsExactly(11L);
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(202L, "2026-08")).isEmpty();
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-09")).isEmpty();
    }

    @Test
    void missingSalaryProfileRemainsVisibleForBlockingValidation()
    {
        jdbc.update("delete from sys_user_profile");
        jdbc.update("update sys_user set status='1'");
        jdbc.update("insert into oa_attendance_schedule values (11,201,'2026-08-01','PUBLISHED')");
        var employee = mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-08").get(0);
        assertThat(employee.getUserId()).isEqualTo(11L);
        assertThat(employee.validationError()).isNotNull();
    }

    @Test
    void keepsActiveUnscheduledEmployeesButExcludesLaterHiresAndEarlierLeavers()
    {
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-08")).hasSize(1);
        jdbc.update("update sys_user_profile set entry_date='2026-09-01'");
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-08")).isEmpty();
        jdbc.update("update sys_user_profile set entry_date='2026-01-01', leave_date='2026-07-31'");
        assertThat(mapper.selectSalaryEmployeesByShopDeptId(201L, "2026-08")).isEmpty();
    }
}
