package com.erp.oa.attendance.leave.balance;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.log.annotation.Log;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
class AttendanceOvertimeTransferControllerTest {
 @Test void allSourceActionsRequireDedicatedPermissionAndIndependentRoute(){assertThat(AttendanceOvertimeTransferController.class.getAnnotation(RequiresPermissions.class).value()).containsExactly("oa:attendance:leave:balance:convert");assertThat(AttendanceOvertimeTransferController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/attendance-v2/leave/balance/overtime-transfers");}
 @Test void writesAreAuditedWithoutRequestOrResponsePayload(){int writes=0;for(var m:AttendanceOvertimeTransferController.class.getDeclaredMethods())if(m.isAnnotationPresent(PostMapping.class)){writes++;assertThat(m.getAnnotation(Log.class).isSaveRequestData()).isFalse();assertThat(m.getAnnotation(Log.class).isSaveResponseData()).isFalse();}assertThat(writes).isEqualTo(2);}
 @Test void sourceVersionAndIdentityRemainDecimalStringsAcrossJson()throws Exception{DayResult d=new DayResult();d.dayResultId=9007199254740993L;d.rowVersion=9007199254740995L;var j=new ObjectMapper().valueToTree(AttendanceOvertimeTransferModels.Source.from(d));assertThat(j.get("dayResultId").asText()).isEqualTo("9007199254740993");assertThat(j.get("rowVersion").isTextual()).isTrue();assertThat(j.get("rowVersion").asText()).isEqualTo("9007199254740995");}
}
