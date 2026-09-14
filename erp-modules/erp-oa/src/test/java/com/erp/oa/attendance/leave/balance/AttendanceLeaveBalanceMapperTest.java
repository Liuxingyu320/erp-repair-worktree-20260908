package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AttendanceLeaveBalanceMapperTest
{
    Configuration config() throws Exception {
        Configuration c=new Configuration();String resource="mapper/oa/AttendanceLeaveBalanceMapper.xml";
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(in,c,resource,c.getSqlFragments()).parse();}return c;
    }
    String sql(String name,Map<String,Object> params) throws Exception {return config().getMappedStatement(AttendanceLeaveBalanceMapper.class.getName()+"."+name).getBoundSql(params).getSql().replaceAll("\\s+"," ");}
    @Test void everyMapperMethodHasRealXmlBinding() throws Exception {Configuration c=config();for(var method:AttendanceLeaveBalanceMapper.class.getMethods())assertThat(c.hasStatement(AttendanceLeaveBalanceMapper.class.getName()+"."+method.getName())).as(method.getName()).isTrue();}
    @Test void employeeContextSelectHasOnlyNeededFieldsAndLockingIsExplicit() throws Exception {
        String s=sql("selectContext",Map.of("userId",11));assertThat(s).contains("work_start_date","entry_date","legal_entity_id","work_location").doesNotContain("salary","bank","id_card","select *");
        assertThat(sql("lockUser",Map.of("userId",11))).contains("FOR UPDATE");assertThat(sql("lockProfile",Map.of("userId",11))).contains("FOR UPDATE");
    }
    @Test void ruleMatchingBindsSourceScopeDateAndLatestFamilyVersion() throws Exception {
        Map<String,Object> p=new HashMap<>();p.put("context",AttendanceLeaveBalanceCalculatorTest.employee());p.put("leaveTypeId",1L);p.put("locationCode","TEST");p.put("asOf",java.time.LocalDate.of(2026,9,12));p.put("lock",true);
        String s=sql("matchRules",p);assertThat(s).contains("legal_entity_id=?","leave_type_id=?","BINARY r.location_code=BINARY ?","effective_from <= ?","effective_to >= ?","FIND_IN_SET","d.status='0'","d.del_flag='0'","n.family_id=r.family_id","FOR UPDATE");
        assertThat(s).doesNotContain("TEST");
        assertThat(sql("matchLocations",p)).contains("d.status='0'","d.del_flag='0'","FOR UPDATE");
    }
    @Test void nonAdminListAlwaysRequiresOrganizationMembership() throws Exception {
        String s=sql("selectRules",Map.of("actor",99,"admin",false));assertThat(s).contains("sys_user_shop","scope.status='0'","target.status='0'","FIND_IN_SET","scope.dept_type IN");
        assertThat(sql("selectRules",Map.of("actor",99,"admin",true))).doesNotContain("sys_user_shop");
    }
    @Test void immutablePublishedRuleAndBucketVersionWritesAreGuarded() throws Exception {
        Map<String,Object> p=new HashMap<>();
        assertThat(sql("updateRule",p)).contains("row_version=row_version+1","AND row_version=?","AND status='DRAFT'");
        assertThat(sql("updateBucket",p)).contains("rule_granted_units=?","reserved_units=?","consumed_units=?","AND row_version=?");
    }
    @Test void commandFingerprintAndLedgerSnapshotHaveBindings() throws Exception {
        Map<String,Object> p=new HashMap<>();
        assertThat(sql("insertLedger",p)).contains("context_json","event_key");assertThat(sql("insertCommand",p)).contains("fingerprint","result_units");
    }
}
