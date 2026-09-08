package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotBlank;

public class OaLaborContractVerifyRequest
{
    @NotBlank(message = "文件哈希不能为空")
    private String hash;

    public String getHash()
    {
        return hash;
    }

    public void setHash(String hash)
    {
        this.hash = hash;
    }
}
