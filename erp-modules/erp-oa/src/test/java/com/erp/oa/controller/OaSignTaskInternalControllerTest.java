package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.oa.api.RemoteSignTaskService;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.service.impl.OaSignTaskOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("人事签约内部事件Controller")
class OaSignTaskInternalControllerTest
{
    @Test
    @DisplayName("内部事件接口与Feign路径一致并强制InnerAuth")
    void shouldExposeInnerAuthenticatedFeignRoute() throws Exception
    {
        Class<?> type = Class.forName("com.erp.oa.controller.OaSignTaskInternalController");
        assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly("/signTask/inner");

        Method method = type.getMethod("receiveEvent", HrSignBusinessEvent.class);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/events");
        assertThat(method.getAnnotation(InnerAuth.class)).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(R.class);

        Method feignMethod = RemoteSignTaskService.class.getMethod(
                "publishEvent", HrSignBusinessEvent.class, String.class);
        String feignPath = feignMethod.getAnnotation(PostMapping.class).value()[0];
        String controllerPath = type.getAnnotation(RequestMapping.class).value()[0]
                + method.getAnnotation(PostMapping.class).value()[0];
        assertThat(controllerPath).isEqualTo(feignPath).isEqualTo("/signTask/inner/events");
    }

    @Test
    @DisplayName("内部接口只返回taskId且业务输出不含方案或源文件hash")
    void shouldReturnOnlyTaskIdWithoutRawHashes() throws Exception
    {
        OaSignTaskOrchestrator orchestrator = mock(OaSignTaskOrchestrator.class);
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        when(orchestrator.orchestrate(event)).thenReturn(9L);

        R<Long> response = new OaSignTaskInternalController(orchestrator).receiveEvent(event);

        assertThat(response.getData()).isEqualTo(9L);
        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).doesNotContain("versionHash", "sourceFileHash");
    }

    @Test
    @DisplayName("草稿决策模型字段和动作枚举保持固定契约")
    void shouldKeepFixedDraftDecisionContract()
    {
        Set<String> fields = Arrays.stream(OaSignDraftDecision.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
                "action", "planVersionId", "riskLevel", "reasonCodes", "draftPackage");
        assertThat(OaSignDraftDecision.Action.values()).extracting(Enum::name)
                .containsExactly("CREATE_DRAFT", "NEEDS_DATA", "NO_ACTION");
        assertThat(fields).doesNotContain("versionHash", "sourceFileHash");
    }
}
