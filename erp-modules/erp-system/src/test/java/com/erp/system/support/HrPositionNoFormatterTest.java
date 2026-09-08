package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

class HrPositionNoFormatterTest
{
    @Test
    void combinesCanonicalPostCodeWithStableEmployeeNumber()
    {
        assertThat(HrPositionNoFormatter.format("e00042", "cys"))
                .isEqualTo("CYS-E00042");
    }

    @Test
    void rejectsImportedPlaceholderPostCodes()
    {
        assertThatThrownBy(() -> HrPositionNoFormatter.format("E00042", "imp_bad"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("岗位编码");
    }
}
