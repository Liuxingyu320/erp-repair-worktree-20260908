package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.*;
import java.io.InputStream;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import com.erp.system.domain.SysUserNotification;
import com.erp.system.domain.dto.SysUserNotificationPageQuery;

class SysUserNotificationPageMapperBindingTest
{
    Configuration config;
    @BeforeEach void load() throws Exception
    {
        config=new Configuration();config.getTypeAliasRegistry().registerAlias("SysUserNotification",SysUserNotification.class);
        String resource="mapper/system/SysUserNotificationMapper.xml";
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
    }
    @Test void allInterfaceMethodsHaveStatements()
    {for(var m:SysUserNotificationMapper.class.getMethods())assertThat(config.hasStatement(SysUserNotificationMapper.class.getName()+"."+m.getName())).as(m.getName()).isTrue();}
    @Test void countAndRowsShareOwnerSnapshotAndEveryFilter()
    {
        var q=new SysUserNotificationPageQuery();q.setKeyword("%_!");q.setRouteType("HR");q.setReadStatus("0");q.setStartDate("2026-09-12");q.setEndDate("2026-09-12");q.validate();
        for(String id:List.of("selectPage","countPage"))
        {
            var sql=config.getMappedStatement(SysUserNotificationMapper.class.getName()+"."+id).getBoundSql(Map.of("userId",42L,"snapshotMaxId",100L,"query",q));
            assertThat(sql.getSql()).contains("user_id = ?","notification_id <= ?","escape '!'","route_type = ?","read_status = ?","create_time >= ?","create_time < ?");
            assertThat(sql.getParameterMappings()).extracting(x->x.getProperty()).contains("userId","snapshotMaxId","query.keywordPattern","query.startTime","query.endTimeExclusive");
            if(id.equals("selectPage"))assertThat(sql.getSql()).contains("order by create_time desc, notification_id desc","limit ?, ?");
        }
    }
    @Test void bulkReadIsOneOwnerAndUpperBoundStatementIndependentOfPage()
    {
        var sql=config.getMappedStatement(SysUserNotificationMapper.class.getName()+".markAllRead").getBoundSql(Map.of("userId",42L,"snapshotMaxId",100L));
        assertThat(sql.getSql()).contains("user_id = ?","notification_id <= ?","read_status = '0'").doesNotContain("limit","route_type","title like");
        assertThat(sql.getParameterMappings()).extracting(x->x.getProperty()).containsExactly("userId","snapshotMaxId");
    }
}
