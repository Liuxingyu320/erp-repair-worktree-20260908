package com.erp.oa.domain.dto;

import jakarta.validation.constraints.Pattern;

public class OaInvoiceRecognitionRequest
{
    @Pattern(regexp = "(?i)auto|cloud|local",
            message = "识别引擎只能是auto、cloud或local")
    private String engine = "auto";

    public String getEngine()
    {
        return engine;
    }

    public void setEngine(String engine)
    {
        this.engine = engine;
    }
}
