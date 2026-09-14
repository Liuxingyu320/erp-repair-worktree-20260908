package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Explicit confirmation of the exact publication preview shown to the HR operator. */
public record OaSignPlanPublishRequest(
        @NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String previewToken,
        Long restoreVersionId) {}
