package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyLong;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.*;

/** Real Jackson binding -> production service -> production SQL; synthetic database only. */
class InvCatalogOptionalFieldMySqlIT
{
    static final Map<String,String> OE=Map.of("oeTypeName","oe_type_name","itemDescription","item_description","orderUnit","order_unit","supplierName","supplier_name","remark","remark");
    static final Map<String,String> GIFT=Map.of("grade","grade","spec","spec","productDescription","product_description","replenishmentUnit","replenishment_unit","supplierName","supplier_name","remark","remark");
    static MySQLContainer mysql; static JdbcTemplate jdbc; static DataSourceTransactionManager manager;
    static final ObjectMapper JSON=new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
    InvOeServiceImpl oe;InvGiftServiceImpl gift;
    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("inventory.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("joint_optional_fields").withUsername("optional_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
            .withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withLabels(Map.of("erp.task","joint-optional-fields-20260913"));
        try{
            mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
            Path cwd=Path.of("").toAbsolutePath();while(cwd!=null&&!Files.exists(cwd.resolve("sql/erp_inventory_oe_gift_management_20260706.sql")))cwd=cwd.getParent();assertThat(cwd).isNotNull();
            var statements=Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS inv_(?:oe_category|oe_item|gift_category|gift_box) \\(.*?;(?=\\s|$)").matcher(Files.readString(cwd.resolve("sql/erp_inventory_oe_gift_management_20260706.sql")));int count=0;
            while(statements.find()){jdbc.execute(statements.group());count++;}assertThat(count).isEqualTo(4);
            jdbc.execute("alter table inv_oe_item add column image_urls text,add column purchase_reference_url varchar(1000),add column purchase_reference_note varchar(500),add column purchase_reference_updated_by varchar(64),add column purchase_reference_updated_time datetime");
            jdbc.execute("alter table inv_gift_box add column image_urls text");
            jdbc.execute("create table oa_fixed_asset_config(config_id bigint primary key,oe_item_id bigint,status char(1)) engine=InnoDB");
            jdbc.update("insert into inv_oe_category(category_id,category_name,category_code) values(1,'OE category','OE')");
            jdbc.update("insert into inv_gift_category(category_id,category_name,category_code) values(1,'Gift category','GIFT')");
        }catch(Throwable e){mysql.stop();throw e;}
    }
    @AfterAll static void stop(){if(mysql!=null)mysql.stop();}
    @BeforeEach void setup() throws Exception
    {
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            jdbc.update("delete from oa_fixed_asset_config");jdbc.update("delete from inv_oe_item");jdbc.update("delete from inv_gift_box");
            jdbc.update("insert into inv_oe_item(oe_item_id,oe_item_code,category_id,oe_item_name,oe_type_name,item_description,order_unit,supplier_name,supplier_phone,remark,image_url,image_urls,purchase_reference_url,purchase_reference_note) values(1,'OE-1',1,'OE item','old','old','old','old','old phone','old','https://example.test/a.jpg','[\"https://example.test/a.jpg\"]','https://example.test/item','old note')");
            jdbc.update("insert into inv_gift_box(gift_id,gift_code,category_id,gift_name,grade,spec,product_description,replenishment_unit,supplier_name,remark,image_url,image_urls) values(1,'GIFT-1',1,'Gift item','old','old','old','old','old','old','https://example.test/a.jpg','[\"https://example.test/a.jpg\"]')");
        });
        Configuration c=new Configuration(new Environment("optional",new SpringManagedTransactionFactory(),manager.getDataSource()));c.getTypeAliasRegistry().registerAlias("InvOeItem",InvOeItem.class);c.getTypeAliasRegistry().registerAlias("InvGiftBox",InvGiftBox.class);
        for(String xml:List.of("InvOeMapper.xml","InvGiftMapper.xml")){String resource="mapper/inventory/"+xml;try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(input,c,resource,c.getSqlFragments()).parse();}}
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(c));
        var o=new InvOeServiceImpl();ReflectionTestUtils.setField(o,"oeMapper",session.getMapper(InvOeMapper.class));ReflectionTestUtils.setField(o,"supplierMapper",mock(InvSupplierMapper.class));ReflectionTestUtils.setField(o,"purchaseReferenceAllowedHosts","example.test");
        var oc=new InvOeCategory();oc.setCategoryId(1L);oc.setCategoryCode("OE");oc.setStatus("0");var ocm=mock(InvOeCategoryMapper.class);when(ocm.selectInvOeCategoryById(1L)).thenReturn(oc);ReflectionTestUtils.setField(o,"categoryMapper",ocm);
        var g=new InvGiftServiceImpl();ReflectionTestUtils.setField(g,"giftMapper",session.getMapper(InvGiftMapper.class));var gc=new InvGiftCategory();gc.setCategoryId(1L);gc.setCategoryCode("GIFT");gc.setStatus("0");var gcm=mock(InvGiftCategoryMapper.class);when(gcm.selectInvGiftCategoryById(1L)).thenReturn(gc);ReflectionTestUtils.setField(g,"categoryMapper",gcm);
        var scope=mock(InvDeptScopeMapper.class);when(scope.selectDeptTypeById(anyLong())).thenReturn("WAREHOUSE");ReflectionTestUtils.setField(o,"deptScopeMapper",scope);ReflectionTestUtils.setField(g,"deptScopeMapper",scope);
        oe=proxy(o);gift=proxy(g);SecurityContextHolder.setUserId("1");SecurityContextHolder.setUserName("optional-it");
    }
    @AfterEach void clear(){SecurityContextHolder.remove();}
    static Stream<Arguments> clears(){return Stream.of("oe","gift").flatMap(type->(type.equals("oe")?OE:GIFT).keySet().stream().flatMap(field->Stream.of("\"\"","\"  \"","null").map(value->Arguments.of(type,field,value))));}
    @ParameterizedTest @MethodSource("clears")
    void explicitEmptyWhitespaceAndNullPersistNullAfterRealJsonBinding(String type,String field,String raw) throws Exception
    {
        save(type,base(type)+",\""+field+"\":"+raw+"}");
        assertThat(value(type,(type.equals("oe")?OE:GIFT).get(field))).isNull();
        if(type.equals("oe")&&field.equals("supplierName"))assertThat(value(type,"supplier_phone")).isNull();
        assertThat(value(type,"image_url")).isEqualTo("https://example.test/a.jpg");
    }
    @Test void omittedFieldsAndForgedPresenceDoNotClearData() throws Exception
    {
        for(String type:List.of("oe","gift")){
            save(type,base(type)+",\"status\":\"1\",\"jsonProvidedFields\":[\"remark\"],\"params\":{\"providedFields\":[\"remark\"]}}");
            for(String column:(type.equals("oe")?OE:GIFT).values())assertThat(value(type,column)).isEqualTo("old");
            assertThat(value(type,"status")).isEqualTo("1");assertThat(value(type,"image_url")).isEqualTo("https://example.test/a.jpg");
        }
    }
    @Test void normalJavaAndExcelUpdatesDoNotAcquireJsonPresence() throws Exception
    {
        var o=JSON.readValue(base("oe")+",\"oeItemCode\":\"OE-1\",\"itemDescription\":\"  \"}",InvOeItem.class);
        assertThat(o.wasJsonFieldProvided("itemDescription")).isFalse();oe.importOe(List.of(o),true,20L);assertThat(value("oe","item_description")).isEqualTo("old");
        var g=JSON.readValue(base("gift")+",\"giftCode\":\"GIFT-1\",\"spec\":\"  \"}",InvGiftBox.class);
        gift.importGift(List.of(g),true,20L);assertThat(value("gift","spec")).isEqualTo("old");
    }
    @Test void activeFixedAssetRejectsExplicitDescriptionAndUnitClearWithoutAnyPartialWrite() throws Exception
    {
        jdbc.update("insert into oa_fixed_asset_config values(1,1,'0')");
        for(String field:List.of("itemDescription","orderUnit")){
            var request=JSON.readValue(base("oe")+",\""+field+"\":null,\"remark\":null}",InvOeEditRequest.class);
            assertThatThrownBy(()->oe.saveOe(request,20L)).isInstanceOf(ServiceException.class).hasMessageContaining("固定资产");
            assertThat(value("oe",OE.get(field))).isEqualTo("old");assertThat(value("oe","remark")).isEqualTo("old");
        }
    }
    @Test void explicitImageEmptyListStillClearsWhileOptionalClearRollsBackAtomically() throws Exception
    {
        save("gift",base("gift")+",\"imageUrls\":[],\"spec\":null}");
        // Preserve the existing ImageUrlList.cover contract: an explicitly empty gallery has an empty cover.
        assertThat(value("gift","image_url")).isEqualTo("");assertThat(value("gift","image_urls")).isEqualTo("[]");
        assertThat(value("gift","spec")).isNull();
        var request=JSON.readValue(base("oe")+",\"itemDescription\":null,\"remark\":null}",InvOeEditRequest.class);
        assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(tx->{oe.saveOe(request,20L);throw new IllegalStateException("injected after write");})).isInstanceOf(IllegalStateException.class);
        assertThat(value("oe","item_description")).isEqualTo("old");assertThat(value("oe","remark")).isEqualTo("old");
    }
    @Test void incompleteStatusOnlyBodyStillFailsExistingRequiredNameValidation() throws Exception
    {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
            assertThat(factory.getValidator().validate(JSON.readValue("{\"oeItemId\":1,\"status\":\"1\"}",InvOeEditRequest.class))).isNotEmpty();
            assertThat(factory.getValidator().validate(JSON.readValue("{\"giftId\":1,\"status\":\"1\"}",InvGiftEditRequest.class))).isNotEmpty();
        }
        assertThat(value("oe","item_description")).isEqualTo("old");assertThat(value("gift","spec")).isEqualTo("old");
    }
    static String base(String type){return type.equals("oe")?"{\"oeItemId\":1,\"categoryId\":1,\"oeItemName\":\"OE item\"":"{\"giftId\":1,\"categoryId\":1,\"giftName\":\"Gift item\"";}
    void save(String type,String json)throws Exception{if(type.equals("oe"))oe.saveOe(JSON.readValue(json,InvOeEditRequest.class),20L);else gift.saveGift(JSON.readValue(json,InvGiftEditRequest.class),20L);}
    static String value(String type,String column){assertThat(Stream.concat(OE.values().stream(),GIFT.values().stream()).toList().contains(column)||Set.of("supplier_phone","image_url","image_urls","status").contains(column)).isTrue();return jdbc.queryForObject("select "+column+" from "+(type.equals("oe")?"inv_oe_item":"inv_gift_box")+" where "+(type.equals("oe")?"oe_item_id":"gift_id")+"=1",String.class);}
    @SuppressWarnings("unchecked") static <T>T proxy(T target){ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return(T)factory.getProxy();}
}
