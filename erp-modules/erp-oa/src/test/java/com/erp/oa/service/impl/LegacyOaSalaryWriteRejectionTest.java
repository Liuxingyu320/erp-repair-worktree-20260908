package com.erp.oa.service.impl;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class LegacyOaSalaryWriteRejectionTest
{
    @Test void oldSalaryAndContractWritesRejectBeforeAnyMapperOrDocumentCall()
    {
        var salary=new OaSalaryServiceImpl();var contract=new OaLaborContractServiceImpl();
        java.util.List<Runnable> writes=java.util.List.of(
            () -> salary.saveConfig(null,null), () -> salary.calculateSalary(null,null,null),
            () -> contract.saveTemplate(null), () -> contract.saveSealConfig(null),
            () -> contract.saveContract(null,null), () -> contract.sendContract(null,null));
        for (Runnable write:writes) assertThatThrownBy(write::run).hasMessageContaining("此功能已停用");
    }
}
