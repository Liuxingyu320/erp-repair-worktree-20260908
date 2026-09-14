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
import org.junit.jupiter.api.AfterEach;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.timecredit.*;
import com.erp.oa.attendance.payroll.AttendancePayrollMapper;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferModels.Transfer;
import org.springframework.transaction.support.TransactionTemplate;

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
class AttendanceOvertimeTransferMySqlIT
{
    @Container static final MySQLContainer MYSQL = new MySQLContainer(System.getProperty("overtime.mysql.image","mysql:5.7.44"))
            .withDatabaseName("b08b_overtime_it").withUsername("b08a_fixture").withPassword("synthetic_b08a_only")
            .withEnv("MYSQL_INITDB_SKIP_TZINFO","1").withTmpFs(Map.of("/var/lib/mysql","rw"))
            .withCommand("--innodb-buffer-pool-size=64M","--innodb-log-file-size=16M","--log-bin-trust-function-creators=1");
    DriverManagerDataSource source; JdbcTemplate jdbc; AttendanceLeaveBalanceMapper mapper; AttendanceLeaveBalanceService service;
    AttendanceLeaveBalanceAccess access; ObjectMapper json=new ObjectMapper().findAndRegisterModules(); Long ruleId;

    AttendanceOvertimeTransferMapper transfers;AttendanceTimeCreditMapper credits;AttendancePayrollMapper payroll;AttendanceV2Mapper days;
    AttendanceOvertimeTransferService sourceService;AttendanceTimeCreditService offsetService;TransactionTemplate tx;
    @BeforeEach void setup() throws Exception
    {
        source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());jdbc=new JdbcTemplate(source);
        for(String table:List.of("oa_attendance_overtime_transfer","oa_attendance_time_credit_adjustment","oa_attendance_time_credit_period_lock","oa_attendance_day_result","oa_attendance_schedule","oa_salary_record","sys_hr_lifecycle_action","oa_attendance_leave_balance_command","oa_attendance_leave_balance_ledger","oa_attendance_leave_balance_bucket","oa_attendance_leave_balance_account","oa_attendance_leave_balance_tier","oa_attendance_leave_balance_rule","oa_attendance_leave_work_location","sys_user_profile","sys_user","sys_user_shop","sys_dept","sys_legal_entity","oa_attendance_leave_type","sys_menu","sys_role_menu","sys_role"))jdbc.execute("DROP TABLE IF EXISTS "+table);
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
        jdbc.update("INSERT INTO sys_dept VALUES(10,0,'0','STORE',20,'0','0'),(12,0,'0','COMPANY',30,'0','0'),(13,10,'0,10','STORE',20,'0','0')");
        jdbc.update("INSERT INTO sys_user_shop VALUES(99,10)");jdbc.update("INSERT INTO sys_legal_entity VALUES(20,'0'),(30,'0')");jdbc.update("INSERT INTO oa_attendance_leave_type VALUES(1,'ENABLED')");
        jdbc.execute("CREATE TABLE sys_hr_lifecycle_action(action_id bigint primary key auto_increment,employee_id bigint,action_type varchar(32),business_status varchar(32),effective_date date) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE oa_salary_record(salary_id bigint primary key auto_increment,user_id bigint,shop_dept_id bigint,salary_month varchar(7),KEY ix_salary(user_id,salary_month)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE oa_attendance_schedule(schedule_id bigint primary key,user_id bigint,user_name varchar(64),shop_id bigint,business_date date,shift_id bigint,standard_minutes_snapshot int,status varchar(16),KEY ix_schedule(shop_id,business_date)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE oa_attendance_day_result(day_result_id bigint primary key,schedule_id bigint unique,user_id bigint,user_name varchar(64),shop_id bigint,business_date date,shift_id bigint,scheduled_minutes int,worked_minutes int,paid_leave_minutes int,unpaid_leave_minutes int,absence_minutes int,late_minutes int,early_leave_minutes int,result_status varchar(32),exception_codes varchar(255),settled_at datetime,row_version bigint,first_in_event_id bigint,last_out_event_id bigint,first_in_correction_request_id bigint,last_out_correction_request_id bigint,calculation_version int,calculated_at datetime,create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500),KEY ix_day_period(shop_id,business_date)) ENGINE=InnoDB");
        jdbc.update("INSERT INTO oa_attendance_schedule VALUES(21,11,'fixture',10,'2026-09-10',1,480,'PUBLISHED'),(22,11,'fixture',10,'2026-09-11',1,480,'PUBLISHED')");
        jdbc.update("INSERT INTO oa_attendance_day_result(day_result_id,schedule_id,user_id,user_name,shop_id,business_date,shift_id,scheduled_minutes,worked_minutes,paid_leave_minutes,unpaid_leave_minutes,absence_minutes,late_minutes,early_leave_minutes,result_status,settled_at,row_version) VALUES(1,21,11,'fixture',10,'2026-09-10',1,480,600,0,0,0,0,0,'NORMAL','2026-09-12 00:00:00',1),(2,22,11,'fixture',10,'2026-09-11',1,480,480,0,0,0,0,120,'EARLY','2026-09-12 00:00:00',1)");
        migrate();migrateExtra("erp_oa_attendance_overtime_transfer_20260912.sql",false);migrateExtra("erp_oa_attendance_time_credit_20260823.sql",true);

        var factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setMapperLocations(new ClassPathResource("mapper/oa/AttendanceLeaveBalanceMapper.xml"),new ClassPathResource("mapper/oa/AttendanceOvertimeTransferMapper.xml"),new ClassPathResource("mapper/oa/AttendanceTimeCreditMapper.xml"),new ClassPathResource("mapper/oa/AttendancePayrollMapper.xml"),new ClassPathResource("mapper/oa/AttendanceV2Mapper.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(AttendanceLeaveBalanceMapper.class);
        transfers=new SqlSessionTemplate(factory.getObject()).getMapper(AttendanceOvertimeTransferMapper.class);
        credits=new SqlSessionTemplate(factory.getObject()).getMapper(AttendanceTimeCreditMapper.class);
        payroll=new SqlSessionTemplate(factory.getObject()).getMapper(AttendancePayrollMapper.class);
        days=new SqlSessionTemplate(factory.getObject()).getMapper(AttendanceV2Mapper.class);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        access=mock(AttendanceLeaveBalanceAccess.class);when(access.actor()).thenReturn(99L);
        var target=new AttendanceLeaveBalanceService(mapper,access,new AttendanceLeaveBalanceCalculator(),mock(BusinessFeatureGate.class),json,new AttendanceOvertimeTransferSourceGuard(transfers),Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"),ZoneOffset.UTC));
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));service=(AttendanceLeaveBalanceService)proxy.getProxy();
        LocationMapping mapping=new LocationMapping();mapping.ownerDeptId=10L;mapping.legalEntityId=20L;mapping.workLocation="合成工作地";mapping.locationCode="TEST_LOCATION";mapping.reason="合成映射";service.saveLocation(null,mapping);
        RuleDraft draft=json.convertValue(AttendanceLeaveBalanceCalculatorTest.rule(),RuleDraft.class);draft.config.leaveCategory="COMPENSATORY";draft.config.calculation="SOURCE_ONLY";draft.config.unit="MINUTES";draft.config.amount=BigDecimal.ZERO;draft.config.carryLimit=BigDecimal.ZERO;Rule saved=service.saveRule(null,draft);ruleId=saved.ruleId;service.publishRule(ruleId,0L);
        ShopScopeService scope=mock(ShopScopeService.class);when(scope.resolveRequiredShopDept(any())).thenAnswer(i->i.getArgument(0));
        sourceService=proxy(new AttendanceOvertimeTransferService(transfers,credits,service,mapper,access,scope,mock(BusinessFeatureGate.class),json));
        offsetService=proxy(new AttendanceTimeCreditService(credits,scope,mock(BusinessFeatureGate.class)));login();
    }
    void migrate() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.isDirectory(root.resolve("sql")))root=root.getParent();assertThat(root).isNotNull();
        try(Connection connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("sql/erp_oa_attendance_leave_balance_20260912.sql")));}
    }
    @SuppressWarnings("unchecked") <T>T proxy(T target) {
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy();
    }
    void migrateExtra(String file,boolean tablesOnly)throws Exception{
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.isDirectory(root.resolve("sql")))root=root.getParent();
        String sql=Files.readString(root.resolve("sql").resolve(file));if(tablesOnly)sql=sql.substring(0,sql.indexOf("SET @attendance_time_credit_parent"));
        try(Connection connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    }
    void login(){SecurityContextHolder.setUserId("99");SecurityContextHolder.setUserName("synthetic-manager");}
    @AfterEach void logout(){SecurityContextHolder.remove();}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    AttendanceOvertimeTransferRequests.Apply input(String key,int minutes){var a=new AttendanceOvertimeTransferRequests.Apply();a.sourceDayResultId=1L;a.sourceVersion=jdbc.queryForObject("SELECT row_version FROM oa_attendance_day_result WHERE day_result_id=1",Long.class);a.leaveTypeId=1L;a.transferMinutes=minutes;a.clientRequestId=key;a.reason="合成主管核定";return a;}
    Transfer grant(String key,int minutes){return sourceService.apply(input(key,minutes),10L);}
    AttendanceOvertimeTransferRequests.Reverse reversal(String key){var r=new AttendanceOvertimeTransferRequests.Reverse();r.clientRequestId=key;r.reason="合成审计撤销";return r;}
    com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Apply offset(String key,int minutes){var a=new com.erp.oa.attendance.timecredit.AttendanceTimeCreditRequests.Apply();a.sourceDayResultId=1L;a.targetDayResultId=2L;a.clientRequestId=key;a.adjustmentMinutes=minutes;a.reason="合成早退抵扣";return a;}
    <T>T asManager(Callable<T> body)throws Exception{login();try{return body.call();}finally{logout();}}
    long granted(){Long n=jdbc.queryForObject("SELECT COALESCE(SUM(granted_units),0) FROM oa_attendance_leave_balance_bucket",Long.class);return n;}
    List<DayResult> payroll(){return payroll.selectPublishedDayResultsForPayroll(10L,java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,30));}

    @Test void grantCreatesOneExactSourceBucketAndReplayDoesNotRepeat() {
        var request=input("grant-repeat-001",80);Transfer first=sourceService.apply(request,10L);assertThat(sourceService.apply(request,10L).transferId).isEqualTo(first.transferId);
        assertThat(granted()).isEqualTo(80_000_000L);assertThat(count("oa_attendance_overtime_transfer")).isEqualTo(1);assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(1);
        assertThat(service.employee(11L,1L).availableUnits).isEqualTo(80_000_000L);request.transferMinutes=81;assertThatThrownBy(()->sourceService.apply(request,10L)).hasMessageContaining("内容不同");
    }
    @Test void twoConfirmationsCompeteForOneSourceWithoutDoubleGrant()throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);int ok=0,failed=0;try{
            List<Future<Transfer>> tasks=List.of(pool.submit(()->asManager(()->grant("double-001",80))),pool.submit(()->asManager(()->grant("double-002",80))));
            for(Future<Transfer>f:tasks)try{f.get(15,TimeUnit.SECONDS);ok++;}catch(ExecutionException ex){assertThat(ex.getCause()).hasMessageContaining("可用来源");failed++;}
        }finally{pool.shutdownNow();}assertThat(ok).isEqualTo(1);assertThat(failed).isEqualTo(1);assertThat(granted()).isEqualTo(80_000_000L);assertThat(credits.selectNetSourceTransferred(1L)).isEqualTo(80);
    }
    @Test void conversionAndExistingOffsetCannotSpendSameMinutesConcurrently()throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);int ok=0,failed=0;try{
            List<Future<?>> tasks=List.of(pool.submit(()->asManager(()->grant("compete-transfer-001",80))),pool.submit(()->asManager(()->offsetService.apply(offset("compete-offset-001",80),10L))));
            for(Future<?>f:tasks)try{f.get(15,TimeUnit.SECONDS);ok++;}catch(ExecutionException ex){assertThat(ex.getCause()).isInstanceOf(com.erp.common.core.exception.ServiceException.class);failed++;}
        }finally{pool.shutdownNow();}assertThat(ok).isEqualTo(1);assertThat(failed).isEqualTo(1);assertThat(credits.selectNetSourceTransferred(1L)+credits.selectNetSourceUsed(1L)).isEqualTo(80);
        assertThat(payroll().get(0).netOvertimeMinutes).isEqualTo(40);
    }
    @Test void oldCandidateAndAllPayrollDailyProjectionsExcludeConvertedMinutes(){
        grant("projection-001",80);var candidates=credits.selectSourceCandidates(10L,11L,java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,11));assertThat(candidates).singleElement().satisfies(x->{assertThat(x.transferredMinutes).isEqualTo(80);assertThat(x.availableMinutes).isEqualTo(40);});
        for(DayResult row:List.of(payroll().get(0),days.selectDayResultsByUserAndRange(11L,java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,30)).get(0),days.selectDayResultsByShopAndRange(10L,java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,30),null).get(0))){assertThat(row.overtimeTransferredMinutes).isEqualTo(80);assertThat(row.netOvertimeMinutes).isEqualTo(40);assertThat(row.overtimeTransferInvalid).isZero();}
    }
    @Test void salaryCommittedWhileSourceWaitsForSameMonthLockBlocksConversion()throws Exception{
        credits.ensurePeriodLock(10L,"2026-09");ExecutorService pool=Executors.newSingleThreadExecutor();try(Connection c=source.getConnection()){
            c.setAutoCommit(false);c.createStatement().executeQuery("SELECT 1 FROM oa_attendance_time_credit_period_lock WHERE shop_id=10 AND salary_month='2026-09' FOR UPDATE");
            Future<Transfer>f=pool.submit(()->asManager(()->grant("salary-wait-001",80)));assertThatThrownBy(()->f.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            c.createStatement().executeUpdate("INSERT INTO oa_salary_record(user_id,shop_dept_id,salary_month) VALUES(11,10,'2026-09')");c.commit();
            assertThatThrownBy(()->f.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(com.erp.common.core.exception.ServiceException.class);assertThat(granted()).isZero();assertThat(count("oa_attendance_overtime_transfer")).isZero();
        }finally{pool.shutdownNow();}
    }
    @Test void payrollPeriodReadWaitsForTransferAndReadsNetMinutesAfterCommit()throws Exception{
        credits.ensurePeriodLock(10L,"2026-09");ExecutorService pool=Executors.newSingleThreadExecutor();java.util.concurrent.atomic.AtomicReference<Future<Integer>> waiting=new java.util.concurrent.atomic.AtomicReference<>();
        try{tx.execute(status->{grant("salary-after-001",80);waiting.set(pool.submit(()->tx.execute(other->{credits.ensurePeriodLock(10L,"2026-09");credits.lockPeriod(10L,"2026-09");credits.lockMonthDayResults(10L,java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,30));return payroll().get(0).netOvertimeMinutes;})));
            assertThatThrownBy(()->waiting.get().get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);return null;});assertThat(waiting.get().get(15,TimeUnit.SECONDS)).isEqualTo(40);
        }finally{pool.shutdownNow();}
    }
    @Test void sourceRecalculationCommittedDuringDayLockWaitRejectsOldPreview()throws Exception{
        var input=input("recalculate-wait-001",80);ExecutorService pool=Executors.newSingleThreadExecutor();try(Connection c=source.getConnection()){
            c.setAutoCommit(false);c.createStatement().executeUpdate("UPDATE oa_attendance_day_result SET worked_minutes=500,row_version=2,settled_at=NULL WHERE day_result_id=1");
            Future<Transfer>f=pool.submit(()->asManager(()->sourceService.apply(input,10L)));assertThatThrownBy(()->f.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);c.commit();
            assertThatThrownBy(()->f.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(com.erp.common.core.exception.ServiceException.class);assertThat(granted()).isZero();
        }finally{pool.shutdownNow();}
    }
    @Test void versionInvalidationKeepsOriginalLedgerAndBlocksBalanceOffsetAndPayroll(){
        Transfer t=grant("invalidate-001",80);jdbc.update("UPDATE oa_attendance_day_result SET row_version=2 WHERE day_result_id=1");
        assertThat(service.employee(11L,1L).availableUnits).isNull();assertThat(service.employee(11L,1L).status).isEqualTo("OVERTIME_SOURCE_REVIEW");assertThat(payroll().get(0).overtimeTransferInvalid).isEqualTo(1);
        assertThatThrownBy(()->offsetService.apply(offset("invalid-offset-001",10),10L)).hasMessageContaining("转休来源已失效");assertThatThrownBy(()->grant("invalid-new-transfer-001",10)).hasMessageContaining("已核定版本失效");
        assertThat(granted()).isEqualTo(80_000_000L);assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(1);
        sourceService.reverse(t.transferId,reversal("invalid-reverse-001"),10L);assertThat(granted()).isZero();assertThat(credits.selectNetSourceTransferred(1L)).isZero();assertThat(service.employee(11L,1L).availableUnits).isZero();
    }
    @Test void employeeTransferOrSameDayHistoryCannotMoveOldStoreSourceToNewCompany(){
        jdbc.update("UPDATE sys_user SET dept_id=12 WHERE user_id=11");jdbc.update("UPDATE sys_user_profile SET legal_entity_id=30 WHERE user_id=11");
        assertThatThrownBy(()->grant("other-company-001",10)).hasMessageContaining("原门店及法人");jdbc.update("UPDATE sys_user SET dept_id=10 WHERE user_id=11");jdbc.update("UPDATE sys_user_profile SET legal_entity_id=20 WHERE user_id=11");
        jdbc.update("INSERT INTO sys_hr_lifecycle_action(employee_id,action_type,business_status,effective_date) VALUES(11,'TRANSFER_CONFIRMED','CONFIRMED','2026-09-10')");
        assertThatThrownBy(()->grant("same-day-transfer-001",10)).hasMessageContaining("来源当日");assertThat(granted()).isZero();
    }
    @Test void transferInsertFailureRollsBackBalanceLedgerAndCommand(){
        jdbc.execute("CREATE TRIGGER b08b_fail_transfer BEFORE INSERT ON oa_attendance_overtime_transfer FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic transfer failure'");
        assertThatThrownBy(()->grant("rollback-transfer-001",80)).hasMessageContaining("synthetic transfer failure");assertThat(granted()).isZero();assertThat(count("oa_attendance_leave_balance_ledger")).isZero();assertThat(count("oa_attendance_leave_balance_command")).isZero();
    }
    @Test void ledgerInsertFailureDoesNotLeaveConfirmedSource(){
        jdbc.execute("CREATE TRIGGER b08b_fail_ledger BEFORE INSERT ON oa_attendance_leave_balance_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic balance ledger failure'");
        assertThatThrownBy(()->grant("rollback-ledger-001",80)).hasMessageContaining("synthetic balance ledger failure");assertThat(granted()).isZero();assertThat(count("oa_attendance_overtime_transfer")).isZero();
    }
    @Test void reversalReleasesOnceAndRestoresPayrollSource(){
        Transfer t=grant("reversible-001",80);Transfer r=sourceService.reverse(t.transferId,reversal("reverse-once-001"),10L);assertThat(sourceService.reverse(t.transferId,reversal("reverse-once-001"),10L).transferId).isEqualTo(r.transferId);
        assertThatThrownBy(()->sourceService.reverse(t.transferId,reversal("reverse-again-001"),10L)).hasMessageContaining("已撤销");assertThat(granted()).isZero();assertThat(credits.selectNetSourceTransferred(1L)).isZero();assertThat(payroll().get(0).netOvertimeMinutes).isEqualTo(120);assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(2);
    }
    @Test void occupiedConsumedExpiredOrCarriedSourceCannotBeReleasedAgain(){
        Transfer t=grant("used-source-001",80);for(String col:List.of("reserved_units","consumed_units","expired_units","carried_units")){
            jdbc.update("UPDATE oa_attendance_leave_balance_bucket SET "+col+"=1000000");assertThatThrownBy(()->sourceService.reverse(t.transferId,reversal("no-release-"+col),10L)).hasMessageContaining("已占用、消耗、到期或结转");jdbc.update("UPDATE oa_attendance_leave_balance_bucket SET "+col+"=0");
        }assertThat(granted()).isEqualTo(80_000_000L);assertThat(count("oa_attendance_overtime_transfer")).isEqualTo(1);
    }
    @Test void reversalInsertFailureRestoresOriginalBalanceAndHistory(){
        Transfer t=grant("reverse-rollback-source",80);jdbc.execute("CREATE TRIGGER b08b_fail_reverse BEFORE INSERT ON oa_attendance_overtime_transfer FOR EACH ROW BEGIN IF NEW.action='REVERSE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic reverse failure'; END IF; END");
        assertThatThrownBy(()->sourceService.reverse(t.transferId,reversal("reverse-rollback-001"),10L)).hasMessageContaining("synthetic reverse failure");assertThat(granted()).isEqualTo(80_000_000L);assertThat(count("oa_attendance_overtime_transfer")).isEqualTo(1);assertThat(count("oa_attendance_leave_balance_ledger")).isEqualTo(1);
    }
    @Test void migrationRepeatsWithoutChangingExistingRoleAssignments()throws Exception{
        long before=count("sys_role_menu");grant("migration-existing-001",80);migrateExtra("erp_oa_attendance_overtime_transfer_20260912.sql",false);assertThat(count("sys_role_menu")).isEqualTo(before);assertThat(count("oa_attendance_overtime_transfer")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms='oa:attendance:leave:balance:convert'",Long.class)).isEqualTo(1);
    }
    @Test void existingOffsetWaitsThenSeesCommittedConversionInsteadOfItsOldPreview()throws Exception{
        ExecutorService pool=Executors.newSingleThreadExecutor();java.util.concurrent.atomic.AtomicReference<Future<?>> waiting=new java.util.concurrent.atomic.AtomicReference<>();
        try{tx.execute(status->{grant("offset-sees-commit-001",80);waiting.set(pool.submit(()->asManager(()->offsetService.apply(offset("offset-after-transfer-001",80),10L))));
            assertThatThrownBy(()->waiting.get().get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);return null;});
            assertThatThrownBy(()->waiting.get().get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(com.erp.common.core.exception.ServiceException.class);
            assertThat(credits.selectNetSourceUsed(1L)).isZero();assertThat(credits.selectNetSourceTransferred(1L)).isEqualTo(80);
        }finally{pool.shutdownNow();}
    }
    @Test void unexpectedOuterRepeatableReadCannotSilentlyDefeatWriteIsolation(){
        TransactionTemplate old=new TransactionTemplate(new DataSourceTransactionManager(source));old.setIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
        assertThatThrownBy(()->old.execute(status->{jdbc.queryForObject("SELECT COUNT(*) FROM oa_attendance_day_result",Long.class);return grant("outer-rr-source-001",80);})).hasMessageContaining("不能加入已有旧快照");
        assertThatThrownBy(()->old.execute(status->offsetService.apply(offset("outer-rr-offset-001",80),10L))).hasMessageContaining("不能加入已有旧快照");
        assertThat(granted()).isZero();assertThat(count("oa_attendance_overtime_transfer")).isZero();assertThat(count("oa_attendance_time_credit_adjustment")).isZero();
    }
    @Test void sourceLongVersionIsNotRoundedByConfirmationOrJson(){
        jdbc.update("UPDATE oa_attendance_day_result SET row_version=9007199254740993 WHERE day_result_id=1");Transfer t=grant("long-version-001",80);assertThat(t.sourceVersion).isEqualTo(9007199254740993L);assertThat(json.valueToTree(t).get("sourceVersion").asText()).isEqualTo("9007199254740993");
    }
}
