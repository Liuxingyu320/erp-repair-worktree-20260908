package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.system.api.model.LoginUser;

/** Real MyBatis read projections against new task-local MySQL only. */
class AttendanceLeaveBalanceOptionsMySqlIT
{
    static MySQLContainer mysql;static JdbcTemplate jdbc;static SqlSessionFactory factory;
    SqlSession session;AttendanceLeaveBalanceOptionsMapper mapper;
    @BeforeAll static void start() throws Exception {
        String version=System.getProperty("b08d.options.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("b08d_options").withUsername("b08d_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
            .withLabels(Map.of("erp.task","b08d-options")).withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withEnv("MYSQL_INITDB_SKIP_TZINFO","1")
            .withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_unicode_ci");
        try {mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE sys_dept(dept_id bigint PRIMARY KEY,dept_name varchar(100),ancestors varchar(200),dept_type varchar(20),legal_entity_id bigint,status char(1),del_flag char(1)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE sys_legal_entity(legal_entity_id bigint PRIMARY KEY,legal_entity_name varchar(100),legal_entity_code varchar(100),status char(1)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE sys_user(user_id bigint PRIMARY KEY,dept_id bigint,user_name varchar(100),nick_name varchar(100),status char(1),del_flag char(1)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE sys_user_profile(user_id bigint PRIMARY KEY,employee_no varchar(100),employee_status varchar(30)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE sys_user_shop(user_id bigint,dept_id bigint,PRIMARY KEY(user_id,dept_id)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE oa_attendance_leave_type(leave_type_id bigint PRIMARY KEY,type_code varchar(64),type_name varchar(100),unit_mode varchar(20),balance_required tinyint(1),sort_no int,status varchar(20)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE oa_attendance_schedule(schedule_id bigint PRIMARY KEY,user_id bigint,shop_id bigint,business_date date,status varchar(20)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE oa_attendance_day_result(day_result_id bigint PRIMARY KEY,schedule_id bigint,row_version bigint,user_id bigint,shop_id bigint,user_name varchar(100),business_date date,worked_minutes int,scheduled_minutes int,settled_at datetime,result_status varchar(40)) ENGINE=InnoDB");
            Configuration config=new Configuration(new Environment("b08d-options",new JdbcTransactionFactory(),ds));
            for(String name:List.of("AttendanceLeaveBalanceOptionsMapper","AttendanceLeaveBalanceMapper")) {String resource="mapper/oa/"+name+".xml";try(InputStream in=AttendanceLeaveBalanceOptionsMySqlIT.class.getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}}
            factory=new SqlSessionFactoryBuilder().build(config);System.out.printf("D options isolated image=%s container=%s port=%s%n",version,mysql.getContainerId(),mysql.getMappedPort(3306));
        }catch(Throwable error){mysql.stop();throw error;}
    }
    @AfterAll static void stop(){if(mysql!=null){String id=mysql.getContainerId();mysql.stop();assertThat(mysql.isRunning()).isFalse();System.out.println("D options cleaned container="+id);}}
    @AfterEach void close(){if(session!=null)session.close();}
    @BeforeEach void seed(){
        for(String table:List.of("sys_dept","sys_legal_entity","sys_user","sys_user_profile","sys_user_shop","oa_attendance_leave_type","oa_attendance_schedule","oa_attendance_day_result"))jdbc.update("delete from "+table);
        jdbc.update("INSERT INTO sys_dept VALUES(1,'集团','0','GROUP',100,'0','0'),(10,'门店','0,1','STORE',101,'0','0'),(11,'子部门','0,1,10','DEPT',null,'0','0'),(20,'另一门店','0,1','STORE',102,'0','0'),(100,'相似编号','0,1','STORE',103,'0','0')");
        jdbc.update("INSERT INTO sys_legal_entity VALUES(100,'集团主体','G','0'),(101,'门店主体','S','0'),(102,'其他主体','O','0'),(103,'相似主体','X','0')");
        user(1,10,"alice","甲","E1","正式");user(2,11,"bob","乙","E2","试用");user(3,20,"other","其他","E3","在职");user(4,100,"prefix","相似","E4","在职");user(Long.MAX_VALUE,11,"last","末尾","E-MAX","待离职");
        jdbc.update("INSERT INTO sys_user_shop VALUES(99,10)");
        jdbc.update("INSERT INTO oa_attendance_leave_type VALUES(1,'A','年假','DAY',1,1,'ENABLED'),(2,'X','停用','HOUR',0,2,'DISABLED'),(?,'M','完整编号','HOUR',0,3,'ENABLED')",Long.MAX_VALUE);
        source(101,1,10,601,1);source(102,2,10,602,2);source(Long.MAX_VALUE,Long.MAX_VALUE,10,Long.MAX_VALUE,Long.MAX_VALUE);
        source(103,3,20,603,3);source(104,4,100,604,4);
        session=factory.openSession(true);mapper=session.getMapper(AttendanceLeaveBalanceOptionsMapper.class);
    }
    static void user(long id,long dept,String account,String name,String number,String status){jdbc.update("INSERT INTO sys_user VALUES(?,?,?,?, '0','0')",id,dept,account,name);jdbc.update("INSERT INTO sys_user_profile VALUES(?,?,?)",id,number,status);}
    static void source(long id,long user,long shop,long schedule,long version){jdbc.update("INSERT INTO oa_attendance_schedule VALUES(?,?,?,'2026-09-12','PUBLISHED')",schedule,user,shop);jdbc.update("INSERT INTO oa_attendance_day_result VALUES(?,?,?,?,?,'source name','2026-09-12',600,480,'2026-09-13 01:00:00','NORMAL')",id,schedule,version,user,shop);}
    @Test void enabledTypesAreMinimalAndPreserveLongIdentifiers(){var rows=mapper.selectTypes();assertThat(rows).extracting(r->r.leaveTypeId).containsExactly("1",String.valueOf(Long.MAX_VALUE));assertThat(rows.get(0).balanceRequired).isTrue();assertThat(rows.get(1).balanceRequired).isFalse();}
    @Test void companiesOnlyIncludeActiveSelfOrAncestorEntities(){assertThat(mapper.selectCompanies(11L)).extracting(r->r.legalEntityId).containsExactlyInAnyOrder("100","101");jdbc.update("UPDATE sys_dept SET status='1' WHERE dept_id=1");session.clearCache();assertThat(mapper.selectCompanies(11L)).extracting(r->r.legalEntityId).containsExactly("101");jdbc.update("UPDATE sys_legal_entity SET status='1' WHERE legal_entity_id=101");session.clearCache();assertThat(mapper.selectCompanies(11L)).isEmpty();assertThat(mapper.selectCompanies(999L)).isEmpty();}
    @Test void employeesUseExactHierarchyStablePaginationAndCurrentActiveStatus(){assertThat(mapper.countEmployees(10L,null)).isEqualTo(3);assertThat(mapper.selectEmployees(10L,null,0,2)).extracting(r->r.userId).containsExactly("1","2");assertThat(mapper.selectEmployees(10L,null,2,2)).extracting(r->r.userId).containsExactly(String.valueOf(Long.MAX_VALUE));jdbc.update("UPDATE sys_user SET status='1' WHERE user_id=1");jdbc.update("UPDATE sys_user_profile SET employee_status='离职' WHERE user_id=2");session.clearCache();assertThat(mapper.countEmployees(10L,null)).isEqualTo(1);jdbc.update("UPDATE sys_dept SET del_flag='2' WHERE dept_id=11");session.clearCache();assertThat(mapper.countEmployees(10L,null)).isZero();}
    @Test void namesAccountsAndEmployeeNumbersUseLiteralSearch(){jdbc.update("UPDATE sys_user_profile SET employee_no=? WHERE user_id=2","%_\\literal");session.clearCache();for(String term:List.of("%","_","\\","literal")){assertThat(mapper.countEmployees(10L,term)).isEqualTo(1);assertThat(mapper.selectEmployees(10L,term,0,20)).extracting(r->r.userId).containsExactly("2");}assertThat(mapper.countEmployees(10L,"甲")).isEqualTo(1);assertThat(mapper.countEmployees(10L,"alice")).isEqualTo(1);assertThat(mapper.countEmployees(10L,"%' OR 1=1 --")).isZero();}
    @Test void sourceReadsPreserveLongsAndFilterCurrentScopeAndDate(){var rows=mapper.selectOvertimeSources(10L,from(),to(),null,0,2);assertThat(mapper.countOvertimeSources(10L,from(),to(),null)).isEqualTo(3);assertThat(rows).extracting(r->r.dayResultId).containsExactly(String.valueOf(Long.MAX_VALUE),"102");var max=rows.get(0);assertThat(max.rowVersion).isEqualTo(String.valueOf(Long.MAX_VALUE));assertThat(max.userId).isEqualTo(String.valueOf(Long.MAX_VALUE));assertThat(max.scheduleId).isEqualTo(String.valueOf(Long.MAX_VALUE));assertThat(max.shopId).isEqualTo("10");assertThat(mapper.selectOvertimeSources(10L,from(),to(),null,2,2)).extracting(r->r.dayResultId).containsExactly("101");assertThat(mapper.countOvertimeSources(10L,from(),from(),null)).isZero();assertThat(mapper.countOvertimeSources(10L,from(),to(),"E2")).isEqualTo(1);}
    @Test void incompleteZeroInvalidVersionAndUnpublishedSourcesAreExcluded(){
        for(String change:List.of("settled_at=NULL","worked_minutes=480","scheduled_minutes=-1","row_version=-1","row_version=NULL","schedule_id=999","shop_id=20")){
            jdbc.update("UPDATE oa_attendance_day_result SET "+change+" WHERE day_result_id=101");session.clearCache();assertThat(mapper.countOvertimeSources(10L,from(),to(),null)).as(change).isEqualTo(2);
            jdbc.update("UPDATE oa_attendance_day_result SET settled_at='2026-09-13 01:00:00',worked_minutes=600,scheduled_minutes=480,row_version=1,schedule_id=601,shop_id=10 WHERE day_result_id=101");}
        jdbc.update("UPDATE oa_attendance_schedule SET status='DRAFT' WHERE schedule_id=601");session.clearCache();assertThat(mapper.countOvertimeSources(10L,from(),to(),null)).isEqualTo(2);
    }
    @Test void actualAccessRejectsOutOfScopeAndSelfOnlyEmployeeSearch(){
        var access=new AttendanceLeaveBalanceAccess(session.getMapper(AttendanceLeaveBalanceMapper.class));var controller=new AttendanceLeaveBalanceOptionsController(mapper,access,mock(ShopScopeService.class),mock(BusinessFeatureGate.class));LoginUser login=new LoginUser();login.setPermissions(Set.of(AttendanceLeaveBalanceAccess.PREFIX+"read"));
        try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getLoginUser).thenReturn(login);security.when(SecurityUtils::getUserId).thenReturn(99L);
            var data=(AttendanceLeaveBalanceOptionsController.OptionsPage)controller.employees(10L,null,1,20).get("data");assertThat(data.total()).isEqualTo(3);
            assertThatThrownBy(()->controller.employees(20L,null,1,20)).isInstanceOf(ServiceException.class).hasMessageContaining("授权组织");
            login.setPermissions(Set.of(AttendanceLeaveBalanceAccess.PREFIX+"self"));assertThatThrownBy(()->controller.employees(10L,null,1,20)).hasMessageContaining("操作权限");
        }
    }
    static LocalDate from(){return LocalDate.of(2026,9,1);}static LocalDate to(){return LocalDate.of(2026,9,30);}
}
