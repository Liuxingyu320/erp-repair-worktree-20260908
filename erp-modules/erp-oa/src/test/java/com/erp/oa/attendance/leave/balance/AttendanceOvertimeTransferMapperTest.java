package com.erp.oa.attendance.leave.balance;
import static org.assertj.core.api.Assertions.*;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
class AttendanceOvertimeTransferMapperTest {
 Configuration load(String file)throws Exception{Configuration c=new Configuration();try(InputStream in=getClass().getClassLoader().getResourceAsStream("mapper/oa/"+file+".xml")){new XMLMapperBuilder(in,c,file,c.getSqlFragments()).parse();}return c;}
 String sql(String file,String ns,String name,Map<String,Object> p)throws Exception{return load(file).getMappedStatement(ns+"."+name).getBoundSql(p).getSql().replaceAll("\\s+"," ");}
 @Test void everyNewMapperMethodHasARealBinding()throws Exception{Configuration c=load("AttendanceOvertimeTransferMapper");for(var method:AttendanceOvertimeTransferMapper.class.getMethods())assertThat(c.hasStatement(AttendanceOvertimeTransferMapper.class.getName()+"."+method.getName())).as(method.getName()).isTrue();}
 @Test void historicalBoundaryIncludesSameDayAndReadsNoSnapshots()throws Exception{String s=sql("AttendanceOvertimeTransferMapper",AttendanceOvertimeTransferMapper.class.getName(),"countLaterTransfer",Map.of("userId",11,"sourceDate","2026-09-10"));assertThat(s).contains("effective_date>=?","effective_date IS NULL","business_status='CONFIRMED'","TRANSFER_CONFIRMED").doesNotContain("snapshot","salary","id_card");}
 @Test void validSourceRequiresPublishedSettledAndFrozenBusinessIdentity()throws Exception{String s=sql("AttendanceOvertimeTransferMapper",AttendanceOvertimeTransferMapper.class.getName(),"countValidSource",Map.of("id",1));assertThat(s).contains("d.row_version=t.source_version","d.user_id=t.user_id","d.shop_id=t.shop_id","d.worked_minutes=t.source_worked_minutes","d.scheduled_minutes=t.source_scheduled_minutes","s.status='PUBLISHED'","r.action='REVERSE'");}
 @Test void oldOffsetChecksBothSourceUsesAndLocksPayrollDays()throws Exception{String ns="com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper";String s=sql("AttendanceTimeCreditMapper",ns,"selectSourceCandidates",Map.of("shopId",10,"userId",11,"monthStart","2026-09-01","targetDate","2026-09-12"));assertThat(s).contains("transferredMinutes","coalesce(ot.transferred_minutes,0)","COALESCE(ot.invalid_count,0)=0");assertThat(sql("AttendanceTimeCreditMapper",ns,"lockMonthDayResults",Map.of("shopId",10,"dateFrom","2026-09-01","dateTo","2026-09-30"))).contains("ORDER BY day_result_id FOR UPDATE");}
 @Test void payrollProjectionSubtractsConversionAndExposesInvalidSource()throws Exception{String s=sql("AttendancePayrollMapper","com.erp.oa.attendance.payroll.AttendancePayrollMapper","selectPublishedDayResultsForPayroll",Map.of("shopId",10,"dateFrom","2026-09-01","dateTo","2026-09-30"));assertThat(s).contains("overtime_transferred_minutes","overtime_transfer_invalid","- coalesce(ot.transferred_minutes,0) net_overtime_minutes","NOT(d.row_version <=> t.source_version)");}
}
