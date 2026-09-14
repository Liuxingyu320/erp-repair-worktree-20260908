package com.erp.system.service.impl;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class LegacySalaryWriteRejectionTest
{
    @Test void everyLegacySystemSalaryMutationRejectsBeforeReadingOrWriting()
    {
        var service=new SysSalaryConfigServiceImpl();
        java.util.List<Runnable> writes=java.util.List.of(
            () -> service.insertSalaryScheme(null), () -> service.updateSalaryScheme(null),
            () -> service.deleteSalarySchemeByIds(null,null,null,false),
            () -> service.insertSalarySchemeItem(null), () -> service.updateSalarySchemeItem(null),
            () -> service.deleteSalarySchemeItemById(null,null,null,false),
            () -> service.rollbackSalarySchemeRevision(null,null),
            () -> service.saveRoleSalarySchemes(null,null), () -> service.saveUserSalarySchemes(null,null));
        for (Runnable write:writes) assertThatThrownBy(write::run).hasMessageContaining("此功能已停用");
    }
}
