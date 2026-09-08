package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class HrOnboardingImportMapperBindingTest
{
    private static final String RESOURCE="mapper/system/HrOnboardingImportMapper.xml";
    private static final String NAMESPACE="com.erp.system.mapper.HrOnboardingImportMapper";

    @Test
    void mapperMethodsBindAndCleanupSqlIsChildFirstBoundedAndStatusSafe() throws Exception
    {
        Document document;
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        try(InputStream input=Resources.getResourceAsStream(RESOURCE))
        {document=factory.newDocumentBuilder().parse(input);}
        Set<String> methods=Arrays.stream(HrOnboardingImportMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).collect(Collectors.toSet());
        Set<String> statements=statementIds(document);
        assertThat(document.getDocumentElement().getAttribute("namespace")).isEqualTo(NAMESPACE);
        assertThat(statements).containsExactlyInAnyOrderElementsOf(methods);
        Configuration configuration=new Configuration();configuration.addMapper(HrOnboardingImportMapper.class);
        try(InputStream input=Resources.getResourceAsStream(RESOURCE))
        {new XMLMapperBuilder(input,configuration,RESOURCE,configuration.getSqlFragments()).parse();}
        assertThat(methods).allSatisfy(id->assertThat(configuration.hasStatement(NAMESPACE+"."+id)).isTrue());
        String xml=new String(Resources.getResourceAsStream(RESOURCE).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
                .toLowerCase();
        assertThat(xml).contains("delete r from hr_onboarding_import_row", "limit #{limit}", "status in")
                .contains("create_time&lt;#{cutoff}")
                .doesNotContain("coalesce(expires_time,create_time)", "processing','completed", "status not in");
    }

    @Test
    void everyRowMutationIsBoundToTheKnownBatch() throws Exception
    {
        Configuration configuration=new Configuration();configuration.addMapper(HrOnboardingImportMapper.class);
        try(InputStream input=Resources.getResourceAsStream(RESOURCE))
        {new XMLMapperBuilder(input,configuration,RESOURCE,configuration.getSqlFragments()).parse();}
        Map<String,Object> params=new HashMap<>();params.put("rowId",11L);params.put("batchId",1L);
        params.put("decision","IMPORT");params.put("bindUserId",null);params.put("onboardingId",101L);
        params.put("resultCode","STALE_CONFLICT");params.put("resultMessage","冲突已变化");
        params.put("operatorUserId",7L);params.put("operator","operator");
        for(String id:Arrays.asList("recordRowDecision","markRowSuccess","markRowFailure"))
        {
            org.apache.ibatis.mapping.BoundSql sql=configuration.getMappedStatement(NAMESPACE+"."+id)
                    .getBoundSql(params);
            String normalized=sql.getSql().replaceAll("\\s+"," ").trim().toLowerCase();
            assertThat(normalized).as(id).contains("where row_id=? and batch_id=? and row_status='previewed'");
            assertThat(sql.getParameterMappings()).extracting(org.apache.ibatis.mapping.ParameterMapping::getProperty)
                    .contains("rowId","batchId");
        }
    }

    @Test
    void leaseFenceAndRecoveryCasUseStatusVersionAndFreshUpdateTime() throws Exception
    {
        Configuration configuration=new Configuration();configuration.addMapper(HrOnboardingImportMapper.class);
        try(InputStream input=Resources.getResourceAsStream(RESOURCE))
        {new XMLMapperBuilder(input,configuration,RESOURCE,configuration.getSqlFragments()).parse();}
        Map<String,Object> params=new HashMap<>();params.put("batchId",1L);params.put("version",4);
        params.put("operatorUserId",7L);params.put("operator","operator");params.put("leaseCutoff",new java.util.Date());
        String fence=configuration.getMappedStatement(NAMESPACE+".touchProcessingBatchLease")
                .getBoundSql(params).getSql().replaceAll("\\s+"," ").toLowerCase();
        String recovery=configuration.getMappedStatement(NAMESPACE+".claimStaleProcessingBatch")
                .getBoundSql(params).getSql().replaceAll("\\s+"," ").toLowerCase();
        assertThat(fence).contains("update_time=sysdate()","status='processing'","version=?");
        assertThat(recovery).contains("version=version+1","status='processing'","version=?","update_time<?");
    }

    private Set<String> statementIds(Document document)
    {
        return Arrays.asList("select","insert","update","delete").stream().flatMap(tag->{
            NodeList nodes=document.getElementsByTagName(tag);java.util.List<String> ids=new java.util.ArrayList<>();
            for(int i=0;i<nodes.getLength();i++)ids.add(((Element)nodes.item(i)).getAttribute("id"));return ids.stream();
        }).collect(Collectors.toSet());
    }
}
