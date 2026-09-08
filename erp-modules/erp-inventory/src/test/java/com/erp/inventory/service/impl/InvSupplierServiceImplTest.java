package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvSupplierMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("供应商服务完整性边界")
class InvSupplierServiceImplTest
{
    private final InvSupplierMapper supplierMapper = mock(InvSupplierMapper.class);
    private final InvProductMapper productMapper = mock(InvProductMapper.class);
    private final InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
    private final InvSupplierServiceImpl service = new InvSupplierServiceImpl();

    @BeforeEach
    void setUp()
    {
        LoginUser admin = new LoginUser();
        admin.setUserid(1L);
        admin.setUsername("admin");
        admin.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, admin);
        ReflectionTestUtils.setField(service, "supplierMapper", supplierMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        when(deptScopeMapper.selectRelatedDeptIds(301L)).thenReturn(List.of(301L, 302L));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("被商品或采购历史引用的供应商不能改名")
    void referencedSupplierCannotBeRenamed()
    {
        InvSupplier current = supplier(8L, "江南茶业", "SUP-JN", 301L);
        InvSupplier update = supplier(8L, "江南茶业新名称", "SUP-JN", null);
        when(supplierMapper.selectInvSupplierById(8L)).thenReturn(current);
        when(supplierMapper.countSupplierReferences(eq(8L), eq("江南茶业"), anyList()))
                .thenReturn(1);

        assertThatThrownBy(() -> service.saveSupplier(update, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被商品或采购历史引用");
        verify(supplierMapper, never()).updateInvSupplier(any(InvSupplier.class));
    }

    @Test
    @DisplayName("删除使用相关组织范围的统一引用检查")
    void deleteUsesRelatedScopeReferenceCheck()
    {
        InvSupplier current = supplier(8L, "江南茶业", "SUP-JN", 301L);
        when(supplierMapper.selectInvSupplierById(8L)).thenReturn(current);
        when(supplierMapper.countSupplierReferences(eq(8L), eq("江南茶业"),
                eq(List.of(301L, 302L)))).thenReturn(1);

        assertThatThrownBy(() -> service.deleteSupplierByIds(new Long[]{8L}, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能删除");
        verify(supplierMapper, never()).deleteInvSupplierByIds(any(Long[].class));
    }

    @Test
    @DisplayName("空相关组织范围读取供应商品时失败关闭为零结果")
    void emptyRelatedScopeReturnsNoProducts()
    {
        InvSupplier current = supplier(8L, "江南茶业", "SUP-JN", 301L);
        when(supplierMapper.selectInvSupplierById(8L)).thenReturn(current);
        when(deptScopeMapper.selectRelatedDeptIds(301L)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.selectSupplierProductList(8L, 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无可用供应商组织范围");
        verify(productMapper, never()).selectInvProductListBySupplier(any(), anyList());
    }

    @Test
    @DisplayName("供应商映射必须持久化备注并包含范围化引用与重复检查")
    void mapperShouldPersistRemarkAndExposeScopedChecks() throws Exception
    {
        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/inventory/InvSupplierMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(mapper).contains("<if test=\"remark != null\">remark = #{remark},</if>");
        assertThat(mapper).contains("countSupplierReferences", "countDuplicateSupplierName",
                "countDuplicateSupplierCode");
        assertThat(mapper).contains("p.shop_dept_id in", "o.shop_dept_id in",
                "r.shop_dept_id in");
    }

    private InvSupplier supplier(Long id, String name, String code, Long shopDeptId)
    {
        InvSupplier supplier = new InvSupplier();
        supplier.setSupplierId(id);
        supplier.setSupplierName(name);
        supplier.setSupplierCode(code);
        supplier.setShopDeptId(shopDeptId);
        supplier.setCooperationStatus("0");
        supplier.setStatus("0");
        return supplier;
    }
}
