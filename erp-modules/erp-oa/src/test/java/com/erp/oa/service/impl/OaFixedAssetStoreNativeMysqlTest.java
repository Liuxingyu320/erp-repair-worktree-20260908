package com.erp.oa.service.impl;

import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.github.pagehelper.Page;
import com.erp.oa.domain.OaFixedAssetConfig;
import com.erp.oa.mapper.OaFixedAssetConfigMapper;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ERP_REPAIR_NATIVE_MYSQL", matches="1")
class OaFixedAssetStoreNativeMysqlTest
{
    @Test void realMapperGroupsAll5001RowsBeforePagingAndKeepsPermissionFilters() throws Exception
    {
        String base="jdbc:mysql://127.0.0.1:3306/", suffix="?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai";
        var admin=new JdbcTemplate(new UnpooledDataSource("com.mysql.cj.jdbc.Driver",base+"mysql"+suffix,"root",""));
        String database="erp_asset_overview_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("create database "+database);
        try {
            var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",base+database+suffix,"root","");var jdbc=new JdbcTemplate(ds);
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(80))");
            jdbc.execute("create table inv_oe_item(oe_item_id bigint primary key,oe_item_code varchar(60),oe_item_name varchar(80),item_description varchar(100),order_unit varchar(20),image_url varchar(255),image_urls text,purchase_reference_url varchar(255),purchase_reference_note varchar(200),del_flag char(1))");
            jdbc.execute("create table oa_fixed_asset_config(config_id bigint primary key,shop_dept_id bigint,oe_item_id bigint,asset_quantity decimal(16,2),asset_unit_price decimal(16,2),asset_amount decimal(16,2),status char(1),create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,remark varchar(500))");
            jdbc.update("insert into sys_dept values(10,'A'),(20,'B'),(30,'Unauthorized')");
            jdbc.update("insert into inv_oe_item(oe_item_id,oe_item_code,oe_item_name,del_flag) values(1,'OE1','Cup','0')");
            var batch=new ArrayList<Object[]>();for(int i=1;i<=5000;i++)batch.add(new Object[]{i,10,1,1,2,2,"0"});
            batch.add(new Object[]{5001,20,1,1,2,2,"1"});batch.add(new Object[]{5002,30,1,1,2,2,"0"});
            jdbc.batchUpdate("insert into oa_fixed_asset_config(config_id,shop_dept_id,oe_item_id,asset_quantity,asset_unit_price,asset_amount,status) values(?,?,?,?,?,?,?)",batch);
            var config=new Configuration(new Environment("native",new SpringManagedTransactionFactory(),ds));config.getTypeAliasRegistry().registerAliases("com.erp.oa.domain");
            var pager=new PageInterceptor();var props=new Properties();props.setProperty("helperDialect","mysql");pager.setProperties(props);config.addInterceptor(pager);
            String resource="mapper/oa/OaFixedAssetConfigMapper.xml";
            try(var in=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
            var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(OaFixedAssetConfigMapper.class);
            var query=new OaFixedAssetConfig();query.getParams().put("scopeDeptIds",List.of(10L,20L));
            PageHelper.startPage(1,1);var first=mapper.selectConfigStoreList(query);
            assertThat(((Page<?>)first).getTotal()).isEqualTo(2);assertThat(first).hasSize(1);assertThat(first.get(0).getDetailCount()).isEqualTo(5000);assertThat(first.get(0).getAssetTotalAmount()).isEqualByComparingTo("10000.00");
            PageHelper.startPage(2,1);var second=mapper.selectConfigStoreList(query);assertThat(second.get(0).getShopDeptId()).isEqualTo(20);assertThat(second.get(0).getDetailCount()).isEqualTo(1);
            query.setStatus("0");assertThat(mapper.selectConfigStoreList(query)).hasSize(1);
            query.setStatus(null);query.setOeItemName("Missing");assertThat(mapper.selectConfigStoreList(query)).isEmpty();
            query.setOeItemName("OE1");query.setShopDeptId(10L);assertThat(mapper.selectConfigList(query)).hasSize(5000);
            query.setShopDeptId(30L);assertThat(mapper.selectConfigStoreList(query)).isEmpty();assertThat(mapper.selectConfigList(query)).isEmpty();
        } finally {PageHelper.clearPage();if(database.matches("erp_asset_overview_[0-9a-f]{32}"))admin.execute("drop database "+database);}
    }
}
