package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = false)
class DriveOrganizationScopeMySqlIT
{
    @ParameterizedTest
    @ValueSource(strings = {"mysql:5.7.44", "mysql:8.0.36"})
    void onlyRolesGrantingTheDriveManagementPermissionContributeTheirScope(String image) throws Exception
    {
        try (MySQLContainer mysql = new MySQLContainer(image).withDatabaseName("drive_scope_it")
                .withUsername("drive_scope_it").withPassword(UUID.randomUUID().toString())
                .withReuse(false).withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=1g"))
                .withCommand("--innodb-buffer-pool-size=64M", "--innodb-log-file-size=16M"))
        {
            mysql.start();
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("create table sys_user(user_id bigint primary key, dept_id bigint, status char(1), del_flag char(1))");
            jdbc.execute("create table sys_dept(dept_id bigint primary key,parent_id bigint,ancestors varchar(100),dept_name varchar(50),dept_type varchar(20),status char(1),del_flag char(1),order_num int)");
            jdbc.execute("create table sys_role(role_id bigint primary key,data_scope char(1),status char(1),del_flag char(1))");
            jdbc.execute("create table sys_user_role(user_id bigint,role_id bigint,primary key(user_id,role_id))");
            jdbc.execute("create table sys_role_dept(role_id bigint,dept_id bigint,primary key(role_id,dept_id))");
            jdbc.execute("create table sys_menu(menu_id bigint primary key,perms varchar(100),status char(1))");
            jdbc.execute("create table sys_role_menu(role_id bigint,menu_id bigint,primary key(role_id,menu_id))");
            jdbc.update("insert into sys_user values(30,8,'0','0')");
            jdbc.update("insert into sys_dept values(1,0,'0','group','GROUP','0','0',1),(8,1,'0,1','own','STORE','0','0',1),(9,8,'0,1,8','child','STORE','0','0',1),(18,1,'0,1,80','outside','STORE','0','0',2),(20,8,'0,1,8','disabled','STORE','1','0',2)");
            jdbc.update("insert into sys_role values(100,'3','0','0'),(200,'1','0','0')");
            jdbc.update("insert into sys_user_role values(30,100),(30,200)");
            jdbc.update("insert into sys_menu values(10,'drive:department:manage','0'),(20,'system:user:list','0')");
            jdbc.update("insert into sys_role_menu values(100,10),(200,20)");
            Configuration config = new Configuration(new Environment("drive-scope", new SpringManagedTransactionFactory(), source));
            String resource = "mapper/drive/DriveOrganizationMapper.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
            SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            DriveOrganizationMapper mapper = session.getMapper(DriveOrganizationMapper.class);
            DriveOrganizationScopeService service = new DriveOrganizationScopeService(mapper);
            DriveActor manager = new DriveActor(30L, 8L, "own", "test", Set.of(DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_DEPARTMENT_MANAGE), false);
            assertThat(mapper.selectRoleScopes(30L)).extracting("roleId").containsExactly(100L);
            assertThat(service.manageableDeptIds(manager)).containsExactly(8L);
            assertThat(service.readableDeptIds(manager)).containsExactly(8L);
            jdbc.update("update sys_role set data_scope='4' where role_id=100");
            assertThat(service.manageableDeptIds(manager)).containsExactly(8L,9L);
            jdbc.update("update sys_role set data_scope='2' where role_id=100");
            jdbc.update("insert into sys_role_dept values(100,9),(100,20)");
            assertThat(service.manageableDeptIds(manager)).containsExactly(9L);
            assertThat(service.readableDeptIds(manager)).containsExactly(8L,9L);
            jdbc.update("update sys_role set data_scope='5' where role_id=100");
            assertThat(service.manageableDeptIds(manager)).containsExactly(8L);
            jdbc.update("update sys_role set status='1' where role_id=100");
            assertThat(service.manageableDeptIds(manager)).isEmpty();
            jdbc.update("update sys_role set status='0',del_flag='2' where role_id=100");
            assertThat(service.manageableDeptIds(manager)).isEmpty();
            jdbc.update("update sys_role set del_flag='0' where role_id=100");
            jdbc.update("update sys_menu set status='1' where menu_id=10");
            assertThat(service.manageableDeptIds(manager)).isEmpty();
            jdbc.update("update sys_menu set status='0' where menu_id=10");
            jdbc.update("delete from sys_role_menu where role_id=100");
            assertThat(service.manageableDeptIds(manager)).isEmpty();
            jdbc.update("insert into sys_role_menu values(200,10)");
            assertThat(service.manageableDeptIds(manager)).containsExactlyInAnyOrder(1L,8L,9L,18L);
            jdbc.update("update sys_role set data_scope='2' where role_id=200");
            jdbc.update("insert into sys_role_dept values(200,18)");
            jdbc.update("insert into sys_role_menu values(100,10)");
            assertThat(service.manageableDeptIds(manager)).containsExactlyInAnyOrder(8L,18L);
            assertThat(service.readableDeptIds(manager)).containsExactlyInAnyOrder(8L,18L);
            DriveActor member = new DriveActor(30L,8L,"own","test",Set.of(DriveConstants.PERMISSION_ACCESS),false);
            assertThat(service.manageableDeptIds(member)).isEmpty();
            assertThat(service.readableDeptIds(member)).containsExactly(8L);
            DriveActor admin = new DriveActor(1L,null,null,"admin",Set.of(),true);
            assertThat(service.manageableDeptIds(admin)).containsExactlyInAnyOrder(1L,8L,9L,18L);
        }
    }
}
