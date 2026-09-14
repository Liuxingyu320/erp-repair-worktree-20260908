package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.*;

/** Executes production dynamic SQL, including the _parameter OGNL receiver. No database mock. */
class InvCatalogOptionalFieldMapperBindingTest
{
    static final Map<String,String> OE=Map.of("oeTypeName","oe_type_name","itemDescription","item_description","orderUnit","order_unit","supplierName","supplier_name","remark","remark");
    static final Map<String,String> GIFT=Map.of("grade","grade","spec","spec","productDescription","product_description","replenishmentUnit","replenishment_unit","supplierName","supplier_name","remark","remark");
    static Configuration configuration;
    static final ObjectMapper JSON=new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
    @BeforeAll static void load() throws Exception
    {
        configuration=new Configuration();configuration.getTypeAliasRegistry().registerAlias("InvOeItem",InvOeItem.class);configuration.getTypeAliasRegistry().registerAlias("InvGiftBox",InvGiftBox.class);
        for(String name:List.of("InvOeMapper","InvGiftMapper")){String resource="mapper/inventory/"+name+".xml";try(InputStream in=InvCatalogOptionalFieldMapperBindingTest.class.getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,configuration,resource,configuration.getSqlFragments()).parse();}}
    }
    static Stream<Arguments> fields(){return Stream.of("oe","gift").flatMap(type->(type.equals("oe")?OE:GIFT).entrySet().stream().flatMap(e->Stream.of("null","\"\"","\"  \"").map(raw->Arguments.of(type,e.getKey(),e.getValue(),raw))));}
    @ParameterizedTest @MethodSource("fields")
    void explicitlyBoundEmptyValuesRemainWritableAfterJavaNormalization(String type,String field,String column,String raw) throws Exception
    {
        Object dto=JSON.readValue("{\""+field+"\":"+raw+"}",requestClass(type));
        assertThat(dto.getClass().getMethod("wasJsonFieldProvided",String.class).invoke(dto,field)).isEqualTo(true);
        // The production normalizer uses ordinary setters, which must preserve JSON provenance.
        dto.getClass().getMethod("set"+Character.toUpperCase(field.charAt(0))+field.substring(1),String.class).invoke(dto,(Object)null);
        assertThat(sql(type,dto)).contains(column+" = ?");
        if(type.equals("oe")&&field.equals("supplierName"))assertThat(sql(type,dto)).contains("supplier_phone = ?");
    }
    @Test void omittedAndForgedFlagsDoNotAccidentallyExpandUpdateColumns() throws Exception
    {
        for(String type:List.of("oe","gift")){
            Object dto=JSON.readValue("{\"jsonProvidedFields\":[\"remark\"],\"params\":{\"providedFields\":[\"remark\"]}}",requestClass(type));
            for(String column:(type.equals("oe")?OE:GIFT).values())assertThat(sql(type,dto)).doesNotContain(column+" = ?");
        }
    }
    @Test void plainDomainAndOrdinarySettersKeepLegacyOmittedNullBehavior()
    {
        var oe=new InvOeItem();oe.setItemDescription(null);var gift=new InvGiftBox();gift.setSpec(null);
        assertThat(sql("oe",oe)).doesNotContain("item_description = ?");assertThat(sql("gift",gift)).doesNotContain("spec = ?");
    }
    static Class<?> requestClass(String type){return type.equals("oe")?InvOeEditRequest.class:InvGiftEditRequest.class;}
    static String sql(String type,Object value){return configuration.getMappedStatement("com.erp.inventory.mapper."+(type.equals("oe")?"InvOeMapper.updateInvOe":"InvGiftMapper.updateInvGift")).getBoundSql(value).getSql().replaceAll("\\s+"," ");}
}
