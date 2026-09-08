package com.erp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.erp.common.security.annotation.RequiresPermissions;
import jakarta.servlet.http.HttpServletRequest;

@DisplayName("调拨审批规则接口声明")
class InvTransferApprovalRuleControllerSourceTest
{
    @Test
    @DisplayName("候选人预览按规则和目标门店查询")
    void candidatePreviewShouldUseScopedRuleQueryPermission() throws Exception
    {
        Method method = InvTransferApprovalRuleController.class.getMethod(
                "candidatePreview", Long.class, Long.class, HttpServletRequest.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/rule/{ruleId}/candidate-preview");
        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("inv:transfer:rule:query");

        String source = Files.readString(Path.of(
                "src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java"),
                StandardCharsets.UTF_8);
        assertThat(source).contains(
                "approvalService.previewCandidates(",
                "ruleId, targetDeptId, resolveShopDeptId(request)");
    }

    @Test
    @DisplayName("规则校验接口为只读查询权限")
    void validationShouldUseQueryPermission() throws Exception
    {
        Method method = InvTransferApprovalRuleController.class.getMethod(
                "validate",
                com.erp.inventory.domain.dto.InvTransferApprovalRuleValidationRequest.class,
                HttpServletRequest.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);

        assertThat(mapping.value()).containsExactly("/rule/validate");
        assertThat(permissions.value()).containsExactly("inv:transfer:rule:query");
    }

    @Test
    @DisplayName("删除规则必须携带详情返回的版本")
    void deleteShouldRequireExpectedVersion() throws Exception
    {
        Method method = InvTransferApprovalRuleController.class.getMethod(
                "remove", Long.class, Integer.class, HttpServletRequest.class);
        RequestParam requestParam = method.getParameters()[1].getAnnotation(RequestParam.class);

        assertThat(requestParam).isNotNull();
        assertThat(requestParam.value()).isEqualTo("expectedVersion");
    }
}
