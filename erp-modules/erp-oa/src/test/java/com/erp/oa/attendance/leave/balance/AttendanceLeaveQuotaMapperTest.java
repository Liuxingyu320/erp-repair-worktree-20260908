package com.erp.oa.attendance.leave.balance;
import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.oa.attendance.leave.AttendanceLeaveMapper;
class AttendanceLeaveQuotaMapperTest
{
 Configuration config(String name)throws Exception{Configuration c=new Configuration();String resource="mapper/oa/"+name+".xml";try(InputStream in=System.getProperty("quota.sourceRoot")==null?getClass().getClassLoader().getResourceAsStream(resource):Files.newInputStream(Path.of(System.getProperty("quota.sourceRoot"),"erp-modules/erp-oa/src/main/resources",resource))){new XMLMapperBuilder(in,c,resource,c.getSqlFragments()).parse();}return c;}
 String sql(String name,Map<String,Object> p)throws Exception{return config("AttendanceLeaveQuotaMapper").getMappedStatement(AttendanceLeaveQuotaMapper.class.getName()+"."+name).getBoundSql(p).getSql().replaceAll("\\s+"," ");}
 @Test void everyQuotaMethodHasBoundSql()throws Exception{Configuration c=config("AttendanceLeaveQuotaMapper");for(var m:AttendanceLeaveQuotaMapper.class.getMethods())assertThat(c.hasStatement(AttendanceLeaveQuotaMapper.class.getName()+"."+m.getName())).isTrue();}
 @Test void allocationsLockOnlyRequestedRoundAndEventUsesBinaryIdentity()throws Exception{assertThat(sql("selectAllocations",Map.of("requestId",10,"round",1,"lock",true))).contains("leave_request_id=? AND business_round=?","ORDER BY bucket_id FOR UPDATE");assertThat(sql("selectEvent",Map.of("requestId",10,"round",1,"eventKey","synthetic-key"))).contains("BINARY event_key=BINARY ?").doesNotContain("synthetic-key");}
 @Test void statusTransitionsRemainConditional()throws Exception{assertThat(sql("transitionAllocation",new HashMap<>())).contains("allocation_id=? AND status=?");assertThat(sql("returnFailedSubmission",new HashMap<>())).contains("business_round=?","status='SUBMITTING' AND approval_instance_id IS NULL","row_version=row_version+1");assertThat(sql("updateRequestQuotaStatus",new HashMap<>())).contains("business_round=?","quota_policy_json IS NOT NULL");}
 @Test void sourceAndAffectedDayQueriesBindEmployeeShopAndDates()throws Exception{assertThat(sql("selectOvertimeSourceDays",new HashMap<>())).contains("b.user_id=? AND b.leave_type_id=?","b.source_type='OVERTIME'","b.expiry_state!='REVERSED'","t.action='APPLY'");assertThat(sql("selectAffectedDays",new HashMap<>())).contains("s.user_id=? AND s.shop_id=?","LEFT JOIN oa_attendance_day_result","s.cross_day_snapshot=1","INTERVAL 1 DAY","s.start_time_snapshot) < ?").doesNotContain("business_date BETWEEN");}
 @Test void realLeaveReadsAndWritesContainPolicyAndIndependentDays()throws Exception{Configuration c=config("AttendanceLeaveMapper");String ns=AttendanceLeaveMapper.class.getName()+".";for(String name:List.of("insertLeaveRequest","updateLeaveDraft","selectLeaveRequestById")){String s=c.getMappedStatement(ns+name).getBoundSql(new HashMap<>()).getSql();assertThat(s).contains("requested_days","quota_policy_json","quota_units","quota_status");}assertThat(c.getMappedStatement(ns+"markLeaveSubmitting").getBoundSql(new HashMap<>()).getSql()).contains("quota_status","RESERVED");assertThat(c.getMappedStatement(ns+"selectLeaveTypeForUpdate").getBoundSql(new HashMap<>()).getSql().toUpperCase(java.util.Locale.ROOT)).contains("FOR UPDATE");}
}
