package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

class HrEmployeeStatusCatalogTest
{
    @Test
    void exposesCanonicalValuesAndMapsOnlyTheKnownLegacyValue()
    {
        assertThat(HrEmployeeStatusCatalog.values())
                .containsExactly("待入职", "试用", "正式", "待离职", "离职", "停薪留职");
        assertThat(HrEmployeeStatusCatalog.normalizeForRead("在职")).isEqualTo("正式");
        assertThat(HrEmployeeStatusCatalog.normalizeForWrite(" 在职 ")).isEqualTo("正式");
        assertThatThrownBy(() -> HrEmployeeStatusCatalog.normalizeForWrite("临时状态"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("员工状态不受支持");
    }
}
