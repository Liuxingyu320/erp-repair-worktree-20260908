package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("排序行锁SQL契约")
class SysSortMapperSourceTest
{
    @Test
    @DisplayName("部门与菜单排序均按ID稳定排序后加行锁")
    void sortQueriesShouldLockRowsInStableIdOrder() throws Exception
    {
        String deptXml = read("mapper/system/SysDeptMapper.xml");
        String menuXml = read("mapper/system/SysMenuMapper.xml");

        assertThat(deptXml).contains("selectdeptordersforupdate", "order by dept_id", "for update");
        assertThat(menuXml).contains("selectmenuordersforupdate", "order by menu_id", "for update");
    }

    private static String read(String path) throws Exception
    {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .toLowerCase();
    }
}
