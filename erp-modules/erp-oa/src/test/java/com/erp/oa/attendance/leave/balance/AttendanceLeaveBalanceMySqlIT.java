package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceRequests.*;
import com.erp.oa.service.BusinessFeatureGate;

/** Dedicated disposable MySQL, synthetic records only. Run once for 5.7 and once for 8.0. */
@Testcontainers(disabledWithoutDocker = false)
class AttendanceLeaveBalanceMySqlIT
{
    @Container static final MySQLContainer MYSQL = new MySQLContainer(System.getProperty("balance.mysql.image","mysql:5.7.44"))
            .withDatabaseName("b08a_balance_it").withUsername("b08a_fixture").withPassword("synthetic_b08a_only")
            .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw"))
            .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M","--log-bin-trust-function-creators=1");
    DriverManagerDataSource source; JdbcTemplate jdbc; AttendanceLeaveBalanceMapper mapper; AttendanceLeaveBalanceService service;
    AttendanceLeaveBalanceAccess access; ObjectMapper json=new ObjectMapper().findAndRegisterModules(); Long ruleId;

    @BeforeEach void setup() throws Exception
    {
        source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());jdbc=new JdbcTemplate(source);
        for(String table:List.of("oa_attendance_leave_balance_command","oa_attendance_leave_balance_ledger","oa_attendance_leave_balance_bucket","oa_attendance_leave_balance_account","oa_attendance_leave_balance_tier","oa_attendance_leave_balance_rule","oa_attendance_leave_work_location","sys_user_profile","sys_user","sys_user_shop","sys_dept","sys_legal_entity","oa_attendance_leave_type","sys_menu","sys_role_menu","sys_role"))jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("CREATE TABLE sys_user(user_id bigint primary key,dept_id bigint,status char(1),del_flag char(1)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_user_profile(profile_id bigint primary key,user_id bigint unique,legal_entity_id bigint,work_location varchar(160),work_start_date date,entry_date date,employee_status varchar(16),update_time datetime) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE sys_dept(dept_id bigint primary key,parent_id bigint,ancestors varchar(64),dept_type varchar(16),legal_entity_id bigint,status char(1),del_flag char(1)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_user_shop(user_id bigint,dept_id bigint,primary key(user_id,dept_id)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_legal_entity(legal_entity_id bigint primary key,status char(1)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE oa_attendance_leave_type(leave_type_id bigint primary key,status varchar(16)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE sys_menu(menu_id bigint primary key auto_increment,menu_name varchar(128),parent_id bigint,order_num int,path varchar(255),component varchar(255),query varchar(255),route_name varchar(128),is_frame int,is_cache int,menu_type char(1),visible char(1),status char(1),perms varchar(128),icon varchar(64),create_by varchar(64),create_time datetime,remark varchar(500)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE sys_role(role_id bigint primary key)");jdbc.execute("CREATE TABLE sys_role_menu(role_id bigint,menu_id bigint,primary key(role_id,menu_id))");
        jdbc.update("INSERT INTO sys_menu(menu_id,perms) VALUES(1,'oa:attendance:center:list')");jdbc.update("INSERT INTO sys_role VALUES(7)");jdbc.update("INSERT INTO sys_role_menu VALUES(7,1)");
        jdbc.update("INSERT INTO sys_user VALUES(11,10,'0','0'),(99,10,'0','0')");
        jdbc.update("INSERT INTO sys_user_profile VALUES(101,11,20,'合成工作地','2021-09-12','2024-01-01','正式','2026-09-01 00:00:00')");
        jdbc.update("INSERT INTO sys_dept VALUES(10,0,'0','COMPANY',20,'0','0'),(12,0,'0','COMPANY',30,'0','0'),(13,10,'0,10','STORE',20,'0','0')");
        jdbc.update("INSERT INTO sys_user_shop VALUES(99,10)");jdbc.update("INSERT INTO sys_legal_entity VALUES(20,'0'),(30,'0')");jdbc.update("INSERT INTO oa_attendance_leave_type VALUES(1,'ENABLED')");
        migrate();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setMapperLocations(new ClassPathResource("mapper/oa/AttendanceLeaveBalanceMapper.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(AttendanceLeaveBalanceMapper.class);
        access=mock(AttendanceLeaveBalanceAccess.class);when(access.actor()).thenReturn(99L);
        var target=new AttendanceLeaveBalanceService(mapper,access,new AttendanceLeaveBalanceCalculator(),mock(BusinessFeatureGate.class),json,new AttendanceOvertimeTransferSourceGuard(mock(AttendanceOvertimeTransferMapper.class)),Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"),ZoneOffset.UTC));
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));service=(AttendanceLeaveBalanceService)proxy.getProxy();
        LocationMapping mapping=new LocationMapping();mapping.ownerDeptId=10L;mapping.legalEntityId=20L;mapping.workLocation="合成工作地";mapping.locationCode="TEST_LOCATION";mapping.reason="合成映射";service.saveLocation(null,mapping);
        RuleDraft draft=json.convertValue(AttendanceLeaveBalanceCalculatorTest.rule(),RuleDraft.class);Rule saved=service.saveRule(null,draft);ruleId=saved.ruleId;service.publishRule(ruleId,0L);
    }
    void migrate() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.isDirectory(root.resolve("sql")))root=root.getParent();assertThat(root).isNotNull();
        try(Connection connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("sql/erp_oa_attendance_leave_balance_20260912.sql")));}
    }
    Balance recalc(){return service.recalculateEmployee(11L,1L);}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    long value(String column){return jdbc.queryForObject("SELECT "+column+" FROM oa_attendance_leave_balance_bucket WHERE source_type='ANNUAL'",Long.class);}
    Adjustment adjustment(String key,String amount){Adjustment a=new Adjustment();a.leaveTypeId=1L;a.bucketId=jdbc.queryForObject("SELECT bucket_id FROM oa_attendance_leave_balance_bucket WHERE source_type='ANNUAL'",Long.class);a.clientRequestId=key;a.amount=new BigDecimal(amount);a.reason="隔离合成调整";Bucket b=mapper.selectBuckets(mapper.selectAccount(11L,1L,false).accountId,false).stream().filter(x->x.bucketId.equals(a.bucketId)).findFirst().orElseThrow();a.bucketVersion=b.rowVersion;a.ruleId=b.ruleId;a.ruleVersion=mapper.selectRule(b.ruleId,false).version;a.displayUnit=b.displayUnit;a.minutesPerDay=b.minutesPerDay;return a;}

    @Test void migrationIsRepeatableAndNeverGrantsExistingRoles() throws Exception {
        recalc();migrate();assertThat(count("sys_role_menu")).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'oa:attendance:leave:balance:%'",Long.class)).isEqualTo(4);
        assertThat(count("oa_attendance_leave_balance_bucket")).isEqualTo(1);assertThat(value("granted_units")).isEqualTo(3_360_000_000L);
    }
    @Test void realMapperAndProxyGrantExactlyOnce() {jdbc.update("UPDATE sys_user_profile SET work_location='  合成工作地  ' WHERE user_id=11");assertThat(recalc().availableUnits).isEqualTo(3_360_000_000L);recalc();assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(1);assertThat(count("oa_attendance_leave_balance_command")).isEqualTo(1);}
    @Test void twoConcurrentAccrualsCannotDoubleGrant() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);try{Future<Balance>a=pool.submit(this::recalc);Future<Balance>b=pool.submit(this::recalc);assertThat(a.get(10,TimeUnit.SECONDS).availableUnits).isEqualTo(3_360_000_000L);assertThat(b.get(10,TimeUnit.SECONDS).availableUnits).isEqualTo(3_360_000_000L);}finally{pool.shutdownNow();}
        assertThat(count("oa_attendance_leave_balance_bucket")).isEqualTo(1);assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(1);
    }
    @Test void ledgerFailureRollsBackBucketAccountAndCommand() {
        jdbc.execute("CREATE TRIGGER b08a_fail_ledger BEFORE INSERT ON oa_attendance_leave_balance_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure'");
        assertThatThrownBy(this::recalc).hasMessageContaining("synthetic ledger failure");assertThat(count("oa_attendance_leave_balance_bucket")).isZero();assertThat(count("oa_attendance_leave_balance_account")).isZero();assertThat(count("oa_attendance_leave_balance_command")).isZero();
    }
    @Test void commandFailureAlsoRollsBackPriorLedgerAndBalance() {
        jdbc.execute("CREATE TRIGGER b08a_fail_command BEFORE INSERT ON oa_attendance_leave_balance_command FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic command failure'");
        assertThatThrownBy(this::recalc).hasMessageContaining("synthetic command failure");assertThat(count("oa_attendance_leave_balance_ledger")).isZero();assertThat(count("oa_attendance_leave_balance_bucket")).isZero();
    }
    @Test void duplicateRequestRejectsDifferentAmountAndRollsBack() {recalc();Adjustment a=adjustment("request-key","1");service.adjust(11L,a);a.amount=new BigDecimal("2");assertThatThrownBy(()->service.adjust(11L,a)).hasMessageContaining("内容不同");assertThat(value("granted_units")).isEqualTo(3_780_000_000L);assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(2);}
    @Test void twoConcurrentNegativeAdjustmentsCannotOverspend() throws Exception {
        recalc();Adjustment a=adjustment("a","-6"),b=adjustment("b","-6");ExecutorService pool=Executors.newFixedThreadPool(2);int success=0,failed=0;
        try{List<Future<Command>> results=List.of(pool.submit(()->service.adjust(11L,a)),pool.submit(()->service.adjust(11L,b)));for(Future<Command>f:results){try{f.get(10,TimeUnit.SECONDS);success++;}catch(ExecutionException invalid){assertThat(invalid.getCause()).hasMessageContaining("调整依据已变化");failed++;}}}finally{pool.shutdownNow();}
        assertThat(success).isEqualTo(1);assertThat(failed).isEqualTo(1);assertThat(value("granted_units")).isEqualTo(840_000_000L);
    }
    @Test void profileTransferCommittedDuringWaitNeverReceivesOldRuleGrant() throws Exception {
        ExecutorService pool=Executors.newSingleThreadExecutor();try(Connection connection=source.getConnection()){
            connection.setAutoCommit(false);connection.createStatement().executeUpdate("UPDATE sys_user SET dept_id=12 WHERE user_id=11");connection.createStatement().executeUpdate("UPDATE sys_user_profile SET legal_entity_id=30,work_location='迁入工作地' WHERE user_id=11");
            Future<Balance> future=pool.submit(this::recalc);assertThatThrownBy(()->future.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);connection.commit();
            assertThat(future.get(10,TimeUnit.SECONDS).status).isEqualTo("MISSING_MAPPING");assertThat(count("oa_attendance_leave_balance_ledger")).isZero();
        }finally{pool.shutdownNow();}
    }
    @Test void mappingChangeIsLockedAndOldRegionCannotBeUsed() throws Exception {
        ExecutorService pool=Executors.newSingleThreadExecutor();try(Connection connection=source.getConnection()){
            connection.setAutoCommit(false);connection.createStatement().executeUpdate("UPDATE oa_attendance_leave_work_location SET location_code='NEW_LOCATION',row_version=row_version+1");
            Future<Balance> future=pool.submit(this::recalc);assertThatThrownBy(()->future.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);connection.commit();
            assertThat(future.get(10,TimeUnit.SECONDS).status).isEqualTo("NO_RULE");assertThat(count("oa_attendance_leave_balance_ledger")).isZero();
        }finally{pool.shutdownNow();}
    }
    @Test void newPublishedVersionUsesDeltaAndOldPublishedVersionCannotBeEdited() {
        recalc();RuleDraft draft=json.convertValue(AttendanceLeaveBalanceCalculatorTest.rule(),RuleDraft.class);draft.previousRuleId=ruleId;draft.config.amount=new BigDecimal("10");Rule next=service.saveRule(null,draft);service.publishRule(next.ruleId,0L);
        assertThat(recalc().availableUnits).isEqualTo(4_200_000_000L);assertThat(count("oa_attendance_leave_balance_bucket")).isEqualTo(1);
        draft.rowVersion=1L;assertThatThrownBy(()->service.saveRule(ruleId,draft)).hasMessageContaining("已发布");
    }
    @Test void SQLScopeBindsOrganizationAndDoesNotTreatEmptyAsGlobal() {
        assertThat(mapper.countDeptPermission(99L,13L)).isEqualTo(1);assertThat(mapper.countDeptPermission(99L,12L)).isZero();assertThat(mapper.countDeptPermission(88L,10L)).isZero();
        jdbc.update("INSERT INTO sys_dept VALUES(14,0,'0','TEAM',20,'0','0')");jdbc.update("INSERT INTO sys_user_shop VALUES(88,14)");
        assertThat(mapper.countDeptPermission(88L,14L)).isZero();
        assertThat(mapper.selectRules(88L,false)).isEmpty();assertThat(mapper.selectRules(99L,false)).hasSize(1);assertThat(mapper.countDeptEntity(10L,30L)).isZero();
    }
    @Test void disabledOrDeletedOrganizationCannotMatchOrReceiveScheduledGrant() {
        EmployeeContext context=mapper.selectContext(11L);
        assertThat(mapper.matchLocations(context,false)).hasSize(1);
        assertThat(mapper.matchRules(context,1L,"TEST_LOCATION",java.time.LocalDate.of(2026,9,12),false)).hasSize(1);
        for(String column:List.of("status","del_flag")) {
            jdbc.update("UPDATE sys_dept SET "+column+"='1' WHERE dept_id=10");
            assertThat(mapper.matchLocations(context,false)).isEmpty();
            assertThat(mapper.matchRules(context,1L,"TEST_LOCATION",java.time.LocalDate.of(2026,9,12),false)).isEmpty();
            assertThat(service.recalculateScheduled(11L,1L).status).isEqualTo("MISSING_MAPPING");
            assertThat(count("oa_attendance_leave_balance_account")).isZero();
            assertThat(count("oa_attendance_leave_balance_ledger")).isZero();
            jdbc.update("UPDATE sys_dept SET "+column+"='0' WHERE dept_id=10");
        }
        assertThat(service.recalculateScheduled(11L,1L).availableUnits).isEqualTo(3_360_000_000L);
    }
    @Test void adjustmentSnapshotCannotBeReinterpretedAfterEquivalentUnitRuleChange() {
        recalc();Adjustment pending=adjustment("pending-old-days","1"),completed=adjustment("completed-old-days","1");
        Command first=service.adjust(11L,completed);
        RuleDraft draft=json.convertValue(AttendanceLeaveBalanceCalculatorTest.rule(),RuleDraft.class);draft.previousRuleId=ruleId;
        draft.config.unit="HOURS";draft.config.amount=new BigDecimal("56");Rule next=service.saveRule(null,draft);service.publishRule(next.ruleId,0L);recalc();
        long before=value("granted_units"),ledgerCount=count("oa_attendance_leave_balance_ledger");
        assertThatThrownBy(()->service.adjust(11L,pending)).hasMessageContaining("调整依据已变化");
        assertThat(service.adjust(11L,completed).commandId).isEqualTo(first.commandId);
        assertThat(value("granted_units")).isEqualTo(before);assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(ledgerCount);
        assertThat(service.employee(11L,1L).buckets.get(0).ruleVersion).isEqualTo(2);
        service.adjust(11L,adjustment("fresh-hours-after-rule","1"));assertThat(value("granted_units")-before).isEqualTo(60_000_000L);
    }
    @Test void expiryDoesNotRemoveOccupiedOriginalYear() {
        recalc();jdbc.update("UPDATE oa_attendance_leave_balance_bucket SET period_year=2025,source_key='ANNUAL|2025',expires_on='2025-12-31',reserved_units=420000000");recalc();
        assertThat(jdbc.queryForObject("SELECT reserved_units FROM oa_attendance_leave_balance_bucket WHERE period_year=2025",Long.class)).isEqualTo(420_000_000L);
        assertThat(jdbc.queryForObject("SELECT expiry_state FROM oa_attendance_leave_balance_bucket WHERE period_year=2025",String.class)).isEqualTo("WAITING_RESERVED");
    }
}
