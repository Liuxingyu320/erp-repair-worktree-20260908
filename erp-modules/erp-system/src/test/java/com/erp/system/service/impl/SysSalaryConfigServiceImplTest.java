package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.constant.HttpStatus;
import com.erp.system.domain.SysRoleSalaryScheme;
import com.erp.system.domain.SysSalaryScheme;
import com.erp.system.domain.SysSalarySchemeExport;
import com.erp.system.domain.SysSalarySchemeItem;
import com.erp.system.domain.SysUserSalaryScheme;
import com.erp.system.domain.dto.SysSalaryImpactPreviewRequest;
import com.erp.system.domain.vo.SysSalaryImpactPreview;
import com.erp.system.domain.vo.SysSalaryImpactStats;
import com.erp.system.mapper.SysRoleSalarySchemeMapper;
import com.erp.system.mapper.SysSalaryImpactMapper;
import com.erp.system.mapper.SysSalarySchemeItemMapper;
import com.erp.system.mapper.SysSalarySchemeMapper;
import com.erp.system.mapper.SysSalarySchemeRevisionMapper;
import com.erp.system.mapper.SysUserSalarySchemeMapper;

@DisplayName("薪资配置服务")
class SysSalaryConfigServiceImplTest
{
    @Test
    @DisplayName("新增薪资档位时服务端重算合计")
    void insertSalaryItemShouldRecalculateTotalSalary()
    {
        SecurityContextHolder.setUserName("tester");
        try
        {
            AtomicReference<SysSalarySchemeItem> inserted = new AtomicReference<>();
            SysSalaryConfigServiceImpl service = newService(schemeMapper(), itemMapper((method, args) -> {
                if ("insertSalarySchemeItem".equals(method))
                {
                    inserted.set((SysSalarySchemeItem) args[0]);
                    return 1;
                }
                if ("selectSalarySchemeItemsBySchemeId".equals(method))
                {
                    return inserted.get() == null ? Collections.emptyList() : Collections.singletonList(inserted.get());
                }
                throw unexpected(method);
            }), roleSalaryMapper((method, args) -> {
                throw unexpected(method);
            }));
            SysSalarySchemeItem item = item(1000, 100, 50);
            item.setTotalSalary(new BigDecimal("99999"));

            service.insertSalarySchemeItem(item);

            assertThat(inserted.get().getTotalSalary()).isEqualByComparingTo("1150");
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    @DisplayName("薪资档位金额不能为负数")
    void salaryItemAmountsShouldNotBeNegative()
    {
        SysSalaryConfigServiceImpl service = newService(schemeMapper(), itemMapper((method, args) -> {
            throw unexpected(method);
        }), roleSalaryMapper((method, args) -> {
            throw unexpected(method);
        }));
        SysSalarySchemeItem item = item(1000, -1, 50);

        assertThatThrownBy(() -> service.insertSalarySchemeItem(item))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为负数");
    }

    @Test
    @DisplayName("已生效薪资方案不能未经紧急授权直接删除")
    void effectiveSchemeDeleteShouldRequireEmergencyCorrection()
    {
        SysSalarySchemeMapper effectiveSchemeMapper = mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeById".equals(method))
            {
                SysSalaryScheme scheme = new SysSalaryScheme();
                scheme.setSchemeId((Long) args[0]);
                scheme.setSchemeName("已生效方案");
                scheme.setSocialType("有社保");
                scheme.setEffectiveDate("2020-01-01");
                scheme.setStatus("0");
                scheme.setVersion(3);
                return scheme;
            }
            throw unexpected(method);
        });
        SysSalaryConfigServiceImpl service = newService(effectiveSchemeMapper,
                itemMapper((method, args) -> { throw unexpected(method); }),
                roleSalaryMapper((method, args) -> { throw unexpected(method); }));

        assertThatThrownBy(() -> service.deleteSalarySchemeByIds(
                new Long[] { 1L }, 3, "清理错误方案", false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("紧急修正需独立权限");
    }

    @Test
    @DisplayName("服务层拒绝无独立权限的紧急修正请求")
    void emergencyCorrectionShouldRequirePermissionInsideService()
    {
        SysSalarySchemeMapper effectiveSchemeMapper = mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeById".equals(method))
            {
                SysSalaryScheme scheme = new SysSalaryScheme();
                scheme.setSchemeId((Long) args[0]);
                scheme.setEffectiveDate("2020-01-01");
                scheme.setVersion(3);
                return scheme;
            }
            throw unexpected(method);
        });
        SysSalaryConfigServiceImpl service = newService(effectiveSchemeMapper,
                itemMapper((method, args) -> { throw unexpected(method); }),
                roleSalaryMapper((method, args) -> { throw unexpected(method); }));

        assertThatThrownBy(() -> service.deleteSalarySchemeByIds(
                new Long[] { 1L }, 3, "修正错误方案", true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少薪资紧急修正权限")
                .satisfies(error -> assertThat(((ServiceException) error).getCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("删除薪资方案必须携带影响预览时的版本")
    void salarySchemeDeleteShouldRejectStaleVersion()
    {
        SysSalaryConfigServiceImpl service = newService(schemeMapper(),
                itemMapper((method, args) -> { throw unexpected(method); }),
                roleSalaryMapper((method, args) -> { throw unexpected(method); }));

        assertThatThrownBy(() -> service.deleteSalarySchemeByIds(
                new Long[] { 1L }, 99, "删除未来方案", false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("版本已变化");
    }

    @Test
    @DisplayName("删除薪资方案的最终 SQL 必须再次校验版本")
    void salarySchemeDeleteShouldRejectConcurrentWriteAtDeleteTime()
    {
        SysSalarySchemeMapper concurrentMapper = mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeById".equals(method))
            {
                SysSalaryScheme scheme = new SysSalaryScheme();
                scheme.setSchemeId((Long) args[0]);
                scheme.setSchemeName("未来方案");
                scheme.setEffectiveDate("2099-01-01");
                scheme.setVersion(3);
                return scheme;
            }
            if ("deleteSalarySchemeByIdAndVersion".equals(method))
            {
                assertThat(args).containsExactly(1L, 3);
                return 0;
            }
            throw unexpected(method);
        });
        SysSalaryConfigServiceImpl service = newService(concurrentMapper,
                itemMapper((method, args) -> {
                    if ("selectSalarySchemeItemsBySchemeId".equals(method))
                    {
                        return Collections.emptyList();
                    }
                    if ("deleteSalarySchemeItemsBySchemeId".equals(method))
                    {
                        return 0;
                    }
                    throw unexpected(method);
                }),
                roleSalaryMapper((method, args) -> {
                    if ("deleteRoleSalarySchemeBySchemeIds".equals(method))
                    {
                        return 0;
                    }
                    throw unexpected(method);
                }),
                userSalaryMapper((method, args) -> {
                    if ("deleteUserSalarySchemeBySchemeIds".equals(method))
                    {
                        return 0;
                    }
                    throw unexpected(method);
                }));

        assertThatThrownBy(() -> service.deleteSalarySchemeByIds(
                new Long[] { 1L }, 3, "删除未来方案", false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("其他管理员修改");
    }

    @Test
    @DisplayName("已绑定角色的薪资方案不允许整批替换档位")
    void boundSalarySchemeShouldRejectReplacingItems()
    {
        AtomicBoolean deletedItems = new AtomicBoolean(false);
        SysSalaryConfigServiceImpl service = newService(schemeMapper(), itemMapper((method, args) -> {
            if ("deleteSalarySchemeItemsBySchemeId".equals(method))
            {
                deletedItems.set(true);
                return 1;
            }
            throw unexpected(method);
        }), roleSalaryMapper((method, args) -> {
            if ("selectRoleSalarySchemesBySchemeId".equals(method))
            {
                SysRoleSalaryScheme binding = new SysRoleSalaryScheme();
                binding.setRoleId(2L);
                binding.setSchemeId(1L);
                binding.setItemId(10L);
                return Collections.singletonList(binding);
            }
            throw unexpected(method);
        }));
        SysSalaryScheme scheme = new SysSalaryScheme();
        scheme.setSchemeId(1L);
        scheme.setSchemeName("有社保方案");
        scheme.setSocialType("有社保");
        scheme.setEffectiveDate("2026-06-01");
        scheme.setStatus("0");
        scheme.setVersion(1);
        scheme.setItems(Collections.singletonList(item(1000, 100, 50)));

        assertThatThrownBy(() -> service.updateSalaryScheme(scheme))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已绑定角色");
        assertThat(deletedItems).isFalse();
    }

    @Test
    @DisplayName("批量查询角色薪资绑定透传去重后的角色ID")
    void batchRoleSalaryShouldQueryDistinctRoleIds()
    {
        SysSalaryConfigServiceImpl service = newService(schemeMapper(), itemMapper((method, args) -> {
            throw unexpected(method);
        }), roleSalaryMapper((method, args) -> {
            if ("selectRoleSalarySchemesByRoleIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(new Long[] { 2L, 3L });
                SysRoleSalaryScheme binding = new SysRoleSalaryScheme();
                binding.setRoleId(2L);
                binding.setSchemeId(1L);
                binding.setItemId(10L);
                return Collections.singletonList(binding);
            }
            throw unexpected(method);
        }));

        List<SysRoleSalaryScheme> result = service.selectRoleSalarySchemesByRoleIds(new Long[] { 2L, 3L, 2L, null });

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoleId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("保存员工薪资绑定时按用户重建绑定")
    void saveUserSalaryShouldRebuildBindingsByUserId()
    {
        SecurityContextHolder.setUserName("tester");
        try
        {
            AtomicReference<List<SysUserSalaryScheme>> inserted = new AtomicReference<>();
            AtomicReference<Long> deletedUserId = new AtomicReference<>();
            SysSalaryConfigServiceImpl service = newService(schemeMapper(), itemMapper((method, args) -> {
                if ("selectSalarySchemeItemById".equals(method))
                {
                    SysSalarySchemeItem item = item(1000, 100, 50);
                    item.setItemId((Long) args[0]);
                    item.setSchemeId(1L);
                    return item;
                }
                throw unexpected(method);
            }), roleSalaryMapper((method, args) -> {
                throw unexpected(method);
            }), userSalaryMapper((method, args) -> {
                if ("deleteUserSalarySchemeByUserId".equals(method))
                {
                    deletedUserId.set((Long) args[0]);
                    return 1;
                }
                if ("batchUserSalaryScheme".equals(method))
                {
                    inserted.set((List<SysUserSalaryScheme>) args[0]);
                    return 1;
                }
                throw unexpected(method);
            }));

            SysUserSalaryScheme binding = new SysUserSalaryScheme();
            binding.setShopDeptId(201L);
            binding.setSchemeId(1L);
            binding.setItemId(10L);
            binding.setEffectiveDate("2025-12-28");
            binding.setRemark("茶艺师默认档");

            int rows = service.saveUserSalarySchemes(11L, Collections.singletonList(binding));

            assertThat(rows).isEqualTo(1);
            assertThat(deletedUserId.get()).isEqualTo(11L);
            assertThat(inserted.get()).hasSize(1);
            assertThat(inserted.get().get(0).getUserId()).isEqualTo(11L);
            assertThat(inserted.get().get(0).getShopDeptId()).isEqualTo(201L);
            assertThat(inserted.get().get(0).getCreateBy()).isEqualTo("tester");
        }
        finally
        {
            SecurityContextHolder.remove();
        }
    }

    @Test
    @DisplayName("导出薪资方案时展开档位明细")
    void exportSalarySchemesShouldFlattenSchemeItems()
    {
        SysSalaryScheme scheme = new SysSalaryScheme();
        scheme.setSchemeId(1L);
        scheme.setSchemeName("门店薪资方案");
        scheme.setSocialType("有社保");
        scheme.setEffectiveDate("2026-06-01");
        scheme.setStatus("0");
        SysSalaryConfigServiceImpl service = newService(mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeList".equals(method))
            {
                return Collections.singletonList(scheme);
            }
            throw unexpected(method);
        }), itemMapper((method, args) -> {
            if ("selectSalarySchemeItemsBySchemeId".equals(method))
            {
                SysSalarySchemeItem first = item(1000, 100, 50);
                first.setTotalSalary(new BigDecimal("1150"));
                SysSalarySchemeItem second = item(2000, 200, 100);
                second.setTotalSalary(new BigDecimal("2300"));
                return List.of(first, second);
            }
            throw unexpected(method);
        }), roleSalaryMapper((method, args) -> {
            throw unexpected(method);
        }));

        List<SysSalarySchemeExport> rows = service.selectSalarySchemeExportList(new SysSalaryScheme());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getSchemeName()).isEqualTo("门店薪资方案");
        assertThat(rows.get(0).getPostName()).isEqualTo("店长");
        assertThat(rows.get(0).getTotalSalary()).isEqualByComparingTo("1150");
        assertThat(rows.get(1).getTotalSalary()).isEqualByComparingTo("2300");
    }

    @Test
    @DisplayName("影响预览返回直接绑定、角色继承和版本冲突")
    void impactPreviewShouldReturnCountsAndVersionConflict()
    {
        SysSalaryConfigServiceImpl service = newService(mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeById".equals(method))
            {
                SysSalaryScheme scheme = new SysSalaryScheme();
                scheme.setSchemeId(1L);
                scheme.setVersion(3);
                scheme.setEffectiveDate("2099-01-01");
                return scheme;
            }
            throw unexpected(method);
        }), itemMapper((method, args) -> {
            throw unexpected(method);
        }), roleSalaryMapper((method, args) -> {
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "salaryImpactMapper", mapper(SysSalaryImpactMapper.class, (method, args) -> {
            if ("selectSchemeImpact".equals(method))
            {
                SysSalaryImpactStats stats = new SysSalaryImpactStats();
                stats.setAffectedUserCount(12);
                stats.setDirectUserCount(7);
                stats.setRoleInheritedUserCount(8);
                stats.setEffectiveNowCount(9);
                stats.setFutureEffectiveCount(3);
                stats.setConflictUserCount(3);
                return stats;
            }
            throw unexpected(method);
        }));
        SysSalaryImpactPreviewRequest request = new SysSalaryImpactPreviewRequest();
        request.setOperation("SCHEME_UPDATE");
        request.setSchemeId(1L);
        request.setExpectedVersion(2);

        SysSalaryImpactPreview preview = service.previewSalaryImpact(request);

        assertThat(preview.getAffectedUserCount()).isEqualTo(12);
        assertThat(preview.getDirectUserCount()).isEqualTo(7);
        assertThat(preview.getRoleInheritedUserCount()).isEqualTo(8);
        assertThat(preview.getWarnings()).anyMatch(value -> value.contains("同时存在"));
        assertThat(preview.getConflicts()).anyMatch(value -> value.contains("当前为 v3"));
    }

    @Test
    @DisplayName("薪资方案更新受乐观锁保护")
    void updateSchemeShouldRejectConcurrentOverwrite()
    {
        SysSalaryConfigServiceImpl service = newService(mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeById".equals(method))
            {
                SysSalaryScheme existing = new SysSalaryScheme();
                existing.setSchemeId(1L);
                existing.setVersion(2);
                existing.setEffectiveDate("2099-01-01");
                return existing;
            }
            if ("updateSalaryScheme".equals(method))
            {
                return 0;
            }
            throw unexpected(method);
        }), itemMapper((method, args) -> {
            throw unexpected(method);
        }), roleSalaryMapper((method, args) -> {
            throw unexpected(method);
        }));
        SysSalaryScheme update = new SysSalaryScheme();
        update.setSchemeId(1L);
        update.setVersion(2);
        update.setSchemeName("新版");
        update.setSocialType("有社保");
        update.setEffectiveDate("2099-02-01");
        update.setStatus("0");

        assertThatThrownBy(() -> service.updateSalaryScheme(update))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("其他管理员修改");
    }

    @Test
    @DisplayName("已生效方案普通修改必须选择未来生效日期")
    void effectiveSchemeShouldRequireFutureDateOrEmergencyCorrection()
    {
        SysSalaryConfigServiceImpl service = newService(mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeById".equals(method))
            {
                SysSalaryScheme existing = new SysSalaryScheme();
                existing.setSchemeId(1L);
                existing.setVersion(1);
                existing.setEffectiveDate("2020-01-01");
                return existing;
            }
            throw unexpected(method);
        }), itemMapper((method, args) -> {
            throw unexpected(method);
        }), roleSalaryMapper((method, args) -> {
            throw unexpected(method);
        }));
        SysSalaryScheme update = new SysSalaryScheme();
        update.setSchemeId(1L);
        update.setVersion(1);
        update.setEffectiveDate("2020-01-01");

        assertThatThrownBy(() -> service.updateSalaryScheme(update))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未来生效日期");
    }

    private static SysSalarySchemeItem item(int base, int overtime, int reward)
    {
        SysSalarySchemeItem item = new SysSalarySchemeItem();
        item.setSchemeId(1L);
        item.setPostName("店长");
        item.setGradeName("第一档");
        item.setRegionName("默认");
        item.setBaseSalary(new BigDecimal(base));
        item.setOvertimePay(new BigDecimal(overtime));
        item.setRewardAllowance(new BigDecimal(reward));
        item.setFullAttendanceBonus(BigDecimal.ZERO);
        item.setManagementAllowance(BigDecimal.ZERO);
        item.setSocialSubsidy(BigDecimal.ZERO);
        item.setCommuteSubsidy(BigDecimal.ZERO);
        return item;
    }

    private static SysSalarySchemeMapper schemeMapper()
    {
        return mapper(SysSalarySchemeMapper.class, (method, args) -> {
            if ("selectSalarySchemeById".equals(method))
            {
                SysSalaryScheme scheme = new SysSalaryScheme();
                scheme.setSchemeId((Long) args[0]);
                scheme.setVersion(1);
                scheme.setEffectiveDate("2099-01-01");
                return scheme;
            }
            if ("bumpSalarySchemeVersion".equals(method))
            {
                return 1;
            }
            if ("updateSalaryScheme".equals(method))
            {
                return 1;
            }
            throw unexpected(method);
        });
    }

    private static SysSalarySchemeItemMapper itemMapper(BiFunction<String, Object[], Object> handler)
    {
        return mapper(SysSalarySchemeItemMapper.class, handler);
    }

    private static SysRoleSalarySchemeMapper roleSalaryMapper(BiFunction<String, Object[], Object> handler)
    {
        return mapper(SysRoleSalarySchemeMapper.class, handler);
    }

    private static SysUserSalarySchemeMapper userSalaryMapper(BiFunction<String, Object[], Object> handler)
    {
        return mapper(SysUserSalarySchemeMapper.class, handler);
    }

    private static SysSalaryConfigServiceImpl newService(SysSalarySchemeMapper schemeMapper,
            SysSalarySchemeItemMapper itemMapper, SysRoleSalarySchemeMapper roleSalarySchemeMapper)
    {
        return newService(schemeMapper, itemMapper, roleSalarySchemeMapper, userSalaryMapper((method, args) -> {
            throw unexpected(method);
        }));
    }

    private static SysSalaryConfigServiceImpl newService(SysSalarySchemeMapper schemeMapper,
            SysSalarySchemeItemMapper itemMapper, SysRoleSalarySchemeMapper roleSalarySchemeMapper,
            SysUserSalarySchemeMapper userSalarySchemeMapper)
    {
        SysSalaryConfigServiceImpl service = new SysSalaryConfigServiceImpl();
        ReflectionTestUtils.setField(service, "schemeMapper", schemeMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "roleSalarySchemeMapper", roleSalarySchemeMapper);
        ReflectionTestUtils.setField(service, "userSalarySchemeMapper", userSalarySchemeMapper);
        ReflectionTestUtils.setField(service, "salaryImpactMapper", mapper(SysSalaryImpactMapper.class, (method, args) -> {
            if ("selectSchemeImpact".equals(method) || "selectItemImpact".equals(method))
            {
                return new com.erp.system.domain.vo.SysSalaryImpactStats();
            }
            if (method.startsWith("count"))
            {
                return 0;
            }
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "revisionMapper", mapper(SysSalarySchemeRevisionMapper.class, (method, args) -> {
            if ("insertRevision".equals(method))
            {
                return 1;
            }
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return service;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
            {
                return method.invoke(thisProxy(type), args);
            }
            return handler.apply(method.getName(), args == null ? new Object[0] : args);
        });
    }

    private static Object thisProxy(Class<?> type)
    {
        return new Object()
        {
            @Override
            public String toString()
            {
                return type.getSimpleName() + "TestProxy";
            }
        };
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected call: " + method);
    }
}
