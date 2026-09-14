package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.system.domain.vo.HrEmployeeQuery;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Native MySQL smoke test for the HR health-certificate migration. */
@EnabledIfEnvironmentVariable(named = "ERP_HR_NATIVE_TEST_URL",
        matches = "jdbc:mysql:.*")
class HrEmployeeHealthCertificateNativeMySqlTest
{
    @Test
    void migratedSchemaExecutesTheCurrentEmployeeMapperQuery() throws Exception
    {
        Configuration configuration=new Configuration();
        HealthCertificateMapperFragments.register(configuration);
        configuration.getTypeAliasRegistry().registerAlias("SysUser",
                com.erp.system.api.domain.SysUser.class);
        configuration.getTypeAliasRegistry().registerAlias("SysDept",
                com.erp.system.api.domain.SysDept.class);
        configuration.getTypeAliasRegistry().registerAlias("SysRole",
                com.erp.system.api.domain.SysRole.class);
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream(
                "mapper/system/SysUserMapper.xml"))
        {
            new XMLMapperBuilder(stream,configuration,
                    "mapper/system/SysUserMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        HrEmployeeQuery query=new HrEmployeeQuery();
        query.getParams().put("dataScope","");
        BoundSql bound=configuration.getMappedStatement(
                "com.erp.system.mapper.SysUserMapper.selectHrEmployeeList")
                .getBoundSql(query);
        assertThat(bound.getParameterMappings()).isNotEmpty();

        String url=System.getenv("ERP_HR_NATIVE_TEST_URL");
        String username=System.getenv().getOrDefault(
                "ERP_HR_NATIVE_TEST_USERNAME","root");
        String password=System.getenv().getOrDefault(
                "ERP_HR_NATIVE_TEST_PASSWORD","");
        try(Connection connection=DriverManager.getConnection(url,username,password))
        {
            HrNativeMySqlTestSupport.requireIsolatedDatabase(connection);
            try(PreparedStatement statement=connection.prepareStatement(bound.getSql()))
            {
                for (int index=0;index<bound.getParameterMappings().size();index++) statement.setDate(index+1,java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))));
                statement.setMaxRows(5);
                try(ResultSet rows=statement.executeQuery())
                {
                    assertThat(rows.next()).isTrue();
                }
            }
            try(PreparedStatement statement=connection.prepareStatement(
                    "select column_name from information_schema.columns "
                    +"where table_schema=database() "
                    +"and table_name='hr_employee_health_certificate'"))
            {
                Set<String> columns=new LinkedHashSet<>();
                try(ResultSet rows=statement.executeQuery())
                {
                    while(rows.next()) columns.add(rows.getString(1));
                }
                assertThat(columns).containsExactlyInAnyOrder(
                        "certificate_id","user_id","dept_id_snapshot",
                        "certificate_no","issued_date","valid_from",
                        "expires_on","issuer_name","attachment_node_id",
                        "review_status","current_flag","reviewed_by_user_id",
                        "reviewed_by_name","reviewed_time","rejection_reason",
                        "version","del_flag","create_by","create_time",
                        "update_by","update_time");
            }
            try(PreparedStatement statement=connection.prepareStatement(
                    "select count(*) from information_schema.statistics "
                    +"where table_schema=database() "
                    +"and table_name='hr_employee_health_certificate' "
                    +"and index_name='uk_hr_health_user_current' "
                    +"and non_unique=0"))
            {
                try(ResultSet rows=statement.executeQuery())
                {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            }
        }
    }
}
