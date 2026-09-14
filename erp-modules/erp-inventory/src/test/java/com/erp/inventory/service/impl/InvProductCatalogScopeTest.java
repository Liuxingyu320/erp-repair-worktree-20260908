package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.redis.service.RedisService;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvProductCategory;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvProductCategoryMapper;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvSupplierMapper;
import com.erp.inventory.domain.InvSupplier;

@DisplayName("商品目录组织作用域")
class InvProductCatalogScopeTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("仓库商品列表可见上级共享目录但不跨兄弟门店")
    void shouldUseRelatedDeptScopeForWarehouseProductCatalog()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper);

        service.selectProductList(new InvProduct(), 104L);

        assertThat(productMapper.lastListQuery.getParams().get("scopeDeptIds"))
                .isEqualTo(List.of(100L, 104L));
    }

    @Test
    @DisplayName("仓库分类树可见上级共享分类但不跨兄弟门店")
    void shouldUseRelatedDeptScopeForWarehouseCategoryTree()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
        InvProductCategoryServiceImpl service = categoryService(categoryMapper);

        service.selectCategoryTree(104L);

        assertThat(categoryMapper.lastListQuery.getParams().get("scopeDeptIds"))
                .isEqualTo(List.of(100L, 104L));
    }

    @Test
    @DisplayName("分类树缓存命中前必须先校验门店授权")
    void shouldValidateScopeBeforeReturningCachedCategoryTree()
    {
        SecurityContextHolder.setUserId("2");
        SecurityContextHolder.setUserName("normal");
        FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        deptScopeMapper.allowed = false;
        FakeRedisService redisService = new FakeRedisService();
        InvProductCategory cachedCategory = new InvProductCategory();
        cachedCategory.setCategoryId(99L);
        cachedCategory.setShopDeptId(999L);
        redisService.cached = List.of(cachedCategory);
        InvProductCategoryServiceImpl service = categoryService(categoryMapper, deptScopeMapper, redisService);

        assertThatThrownBy(() -> service.selectCategoryTree(999L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权选择该店铺");
        assertThat(redisService.getCacheObjectCalls).isZero();
        assertThat(redisService.lastCacheKey).isNull();
        assertThat(categoryMapper.lastListQuery).isNull();
    }

    @Test
    @DisplayName("仓库可打开集团共享商品详情但不能打开兄弟门店商品")
    void shouldAllowSharedProductDetailAndRejectSiblingStoreProduct()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper);

        productMapper.stored = product(9L, 100L);
        assertThat(service.selectProductById(9L, 104L).getShopDeptId()).isEqualTo(100L);

        productMapper.stored = product(3L, 103L);
        assertThatThrownBy(() -> service.selectProductById(3L, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问该店铺商品");
    }

    @Test
    @DisplayName("商品详情缓存命中仍不能绕过实时范围校验")
    void shouldNotReturnCachedProductBeforeScopeCheck()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.stored = product(3L, 103L);
        FakeRedisService redisService = new FakeRedisService();
        redisService.cached = product(3L, 100L);
        InvProductServiceImpl service = productService(productMapper, redisService);

        assertThatThrownBy(() -> service.selectProductById(3L, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问该店铺商品");
        assertThat(productMapper.selectByIdCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("被业务引用的商品不能删除")
    void shouldRejectDeletingReferencedProduct()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.stored = product(3L, 100L);
        productMapper.businessReferenceCount = 1;
        InvProductServiceImpl service = productService(productMapper);

        assertThatThrownBy(() -> service.deleteProductByIds(new Long[]{3L}, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被库存或历史单据引用");
        assertThat(productMapper.deleteCalls).isZero();
    }

    @Test
    @DisplayName("商品导入更新优先按商品编码匹配原商品")
    void shouldUpdateImportedProductByProductCodeWhenNameChanged()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.codeMatch = product(9L, 100L);
        productMapper.codeMatch.setCategoryId(8L);
        productMapper.codeMatch.setProductCode("CS-0001");
        InvProduct imported = new InvProduct();
        imported.setProductCode(" CS-0001 ");
        imported.setProductName("改名后的商品");
        imported.setCategoryId(8L);
        imported.setSupplierName("测试供应商");
        InvProductServiceImpl service = productService(productMapper);

        String result = service.importProduct(List.of(imported), true, 104L);

        assertThat(result).contains("成功导入0条，更新1条，失败0条");
        assertThat(productMapper.lastCodeLookup).isEqualTo("CS-0001");
        assertThat(productMapper.updatedProducts).hasSize(1);
        assertThat(productMapper.updatedProducts.get(0).getProductId()).isEqualTo(9L);
        assertThat(productMapper.updatedProducts.get(0).getProductCode()).isEqualTo("CS-0001");
        assertThat(productMapper.insertCalls).isZero();
    }

    @Test
    @DisplayName("商品新增保留请求状态")
    void shouldKeepRequestedStatusWhenCreatingProduct()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper);
        InvProduct product = new InvProduct();
        product.setProductName("停用新品");
        product.setCategoryId(8L);
        product.setSupplierName("测试供应商");
        product.setStatus("1");

        service.saveProduct(product, 104L);

        assertThat(productMapper.insertedProducts).hasSize(1);
        assertThat(productMapper.insertedProducts.get(0).getStatus()).isEqualTo("1");
    }

    @Test
    @DisplayName("商品新增未传状态时默认启用")
    void shouldDefaultProductStatusWhenCreatingWithoutStatus()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper);
        InvProduct product = new InvProduct();
        product.setProductName("默认启用新品");
        product.setCategoryId(8L);
        product.setSupplierName("测试供应商");

        service.saveProduct(product, 104L);

        assertThat(productMapper.insertedProducts).hasSize(1);
        assertThat(productMapper.insertedProducts.get(0).getStatus()).isEqualTo("0");
    }

    @Test
    @DisplayName("商品维护拒绝负数、库存上下限倒置和非法状态")
    void shouldRejectInvalidOperationalValuesWhenSavingProduct()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper);

        InvProduct negativePrice = maintainedProduct("负价格商品");
        negativePrice.setSalesPrice(new BigDecimal("-0.01"));
        assertThatThrownBy(() -> service.saveProduct(negativePrice, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("价格");

        InvProduct invertedStock = maintainedProduct("库存倒置商品");
        invertedStock.setSafetyStockMin(new BigDecimal("31"));
        invertedStock.setSafetyStockMax(new BigDecimal("30"));
        assertThatThrownBy(() -> service.saveProduct(invertedStock, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("安全库存上限");

        InvProduct invalidStatus = maintainedProduct("非法状态商品");
        invalidStatus.setStatus("2");
        assertThatThrownBy(() -> service.saveProduct(invalidStatus, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("商品状态");

        assertThat(productMapper.insertCalls).isZero();
    }

    @Test
    @DisplayName("商品部分更新按最终持久化值校验安全库存边界")
    void shouldValidateEffectiveSafetyStockBoundsForPartialUpdate()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        productMapper.stored = product(9L, 100L);
        productMapper.stored.setProductCode("CS-0001");
        productMapper.stored.setSafetyStockMin(new BigDecimal("5"));
        productMapper.stored.setSafetyStockMax(new BigDecimal("30"));
        InvProductServiceImpl service = productService(productMapper);

        InvProduct invertedPartialUpdate = maintainedProduct("部分更新商品");
        invertedPartialUpdate.setProductId(9L);
        invertedPartialUpdate.setSafetyStockMin(new BigDecimal("31"));
        assertThatThrownBy(() -> service.saveProduct(invertedPartialUpdate, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("安全库存上限");
        assertThat(productMapper.updatedProducts).isEmpty();

        InvProduct explicitMaximumClear = maintainedProduct("显式清空上限商品");
        explicitMaximumClear.setProductId(9L);
        explicitMaximumClear.setSafetyStockMin(new BigDecimal("31"));
        explicitMaximumClear.setClearSafetyStockMax(true);
        service.saveProduct(explicitMaximumClear, 104L);

        assertThat(productMapper.updatedProducts).singleElement().satisfies(updated -> {
            assertThat(updated.getSafetyStockMin()).isEqualByComparingTo("31");
            assertThat(updated.getClearSafetyStockMax()).isTrue();
        });
    }

    @Test
    @DisplayName("商品导入新增保留请求状态")
    void shouldKeepRequestedStatusWhenImportingNewProduct()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper);
        InvProduct product = new InvProduct();
        product.setProductName("导入停用新品");
        product.setCategoryId(8L);
        product.setSupplierName("测试供应商");
        product.setStatus("1");

        service.importProduct(List.of(product), false, 104L);

        assertThat(productMapper.insertedProducts).hasSize(1);
        assertThat(productMapper.insertedProducts.get(0).getStatus()).isEqualTo("1");
    }

    @Test
    @DisplayName("商品导入缺供应商时允许作为待完善商品导入")
    void shouldImportProductWithoutSupplierAsNeedsCompletion()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper);
        InvProduct product = new InvProduct();
        product.setProductName("待完善商品");
        product.setCategoryId(8L);
        product.setSalesPrice(BigDecimal.TEN);

        String result = service.importProduct(List.of(product), false, 104L);

        assertThat(result).contains("成功导入1条，更新0条，失败0条，待完善1条");
        assertThat(productMapper.insertedProducts).hasSize(1);
        assertThat(productMapper.insertedProducts.get(0).getSupplierName()).isNull();
    }

    @Test
    @DisplayName("商品导入填写无效供应商时仍然失败")
    void shouldRejectInvalidSupplierNameWhenImportingProduct()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeProductMapper productMapper = new FakeProductMapper();
        InvProductServiceImpl service = productService(productMapper, new MissingSupplierMapper());
        InvProduct product = new InvProduct();
        product.setProductName("脏供应商商品");
        product.setCategoryId(8L);
        product.setSupplierName("不存在供应商");
        product.setSalesPrice(BigDecimal.TEN);

        String result = service.importProduct(List.of(product), false, 104L);

        assertThat(result).contains("成功导入0条，更新0条，失败1条，待完善0条")
                .contains("供应商不存在、已停用或非合作中");
        assertThat(productMapper.insertCalls).isZero();
    }

    @Test
    @DisplayName("存在子分类或商品引用时分类不能删除")
    void shouldRejectDeletingReferencedCategory()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
        categoryMapper.childCount = 1;
        InvProductCategoryServiceImpl service = categoryService(categoryMapper);

        assertThatThrownBy(() -> service.deleteCategoryById(8L, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存在子分类");
        assertThat(categoryMapper.deleteCalls).isZero();
    }

    @Test
    @DisplayName("编辑分类时不能通过请求体修改父级和祖级链")
    void shouldIgnoreParentFieldsWhenUpdatingCategory()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
        InvProductCategoryServiceImpl service = categoryService(categoryMapper);

        InvProductCategory update = new InvProductCategory();
        update.setCategoryId(8L);
        update.setCategoryName("更新后的分类");
        update.setParentId(99L);
        update.setAncestors("0,99");

        service.saveCategory(update, 100L);

        assertThat(categoryMapper.updatedCategory).isNotNull();
        assertThat(categoryMapper.updatedCategory.getCategoryName()).isEqualTo("更新后的分类");
        assertThat(categoryMapper.updatedCategory.getParentId()).isNull();
        assertThat(categoryMapper.updatedCategory.getAncestors()).isNull();
    }

    @Test
    @DisplayName("新增分类不能挂到无关门店的父分类下")
    void shouldRejectUnrelatedParentCategoryWhenCreatingCategory()
    {
        SecurityContextHolder.setUserId("2");
        SecurityContextHolder.setUserName("normal");
        FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
        categoryMapper.storedCategoryShopDeptId = 103L;
        InvProductCategoryServiceImpl service = categoryService(categoryMapper);
        InvProductCategory category = new InvProductCategory();
        category.setCategoryName("越权子分类");
        category.setParentId(8L);

        assertThatThrownBy(() -> service.saveCategory(category, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权访问该店铺分类");
        assertThat(categoryMapper.insertCalls).isZero();
    }

    @Test
    @DisplayName("分类维护拒绝负排序、非法状态和负父级")
    void shouldRejectInvalidOperationalValuesWhenSavingCategory()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
        InvProductCategoryServiceImpl service = categoryService(categoryMapper);

        InvProductCategory negativeOrder = new InvProductCategory();
        negativeOrder.setCategoryName("负排序分类");
        negativeOrder.setOrderNum(-1);
        assertThatThrownBy(() -> service.saveCategory(negativeOrder, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("排序");

        InvProductCategory invalidStatus = new InvProductCategory();
        invalidStatus.setCategoryName("非法状态分类");
        invalidStatus.setStatus("2");
        assertThatThrownBy(() -> service.saveCategory(invalidStatus, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("分类状态");

        InvProductCategory negativeParent = new InvProductCategory();
        negativeParent.setCategoryName("负父级分类");
        negativeParent.setParentId(-1L);
        assertThatThrownBy(() -> service.saveCategory(negativeParent, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("上级分类");

        assertThat(categoryMapper.insertCalls).isZero();
    }

    @Test
    @DisplayName("新增分类不能挂到停用父分类下")
    void shouldRejectInactiveParentCategoryWhenCreatingCategory()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
        categoryMapper.storedCategoryStatus = "1";
        InvProductCategoryServiceImpl service = categoryService(categoryMapper);
        InvProductCategory category = new InvProductCategory();
        category.setCategoryName("停用父级下的新分类");
        category.setParentId(8L);

        assertThatThrownBy(() -> service.saveCategory(category, 104L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("上级分类已停用");
        assertThat(categoryMapper.insertCalls).isZero();
    }

    private InvProductServiceImpl productService(FakeProductMapper productMapper)
    {
        return productService(productMapper, new FakeRedisService());
    }

    private InvProductServiceImpl productService(FakeProductMapper productMapper, InvSupplierMapper supplierMapper)
    {
        return productService(productMapper, new FakeRedisService(), supplierMapper);
    }

    private InvProductServiceImpl productService(FakeProductMapper productMapper, FakeRedisService redisService)
    {
        return productService(productMapper, redisService, new FakeSupplierMapper());
    }

    private InvProductServiceImpl productService(FakeProductMapper productMapper, FakeRedisService redisService,
            InvSupplierMapper supplierMapper)
    {
        InvProductServiceImpl service = new InvProductServiceImpl();
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "categoryMapper", new FakeCategoryMapper());
        ReflectionTestUtils.setField(service, "supplierMapper", supplierMapper);
        ReflectionTestUtils.setField(service, "redisService", redisService);
        return service;
    }

    private InvProductCategoryServiceImpl categoryService(FakeCategoryMapper categoryMapper)
    {
        InvProductCategoryServiceImpl service = new InvProductCategoryServiceImpl();
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", new FakeDeptScopeMapper());
        ReflectionTestUtils.setField(service, "redisService", new FakeRedisService());
        return service;
    }

    private InvProductCategoryServiceImpl categoryService(FakeCategoryMapper categoryMapper,
            FakeDeptScopeMapper deptScopeMapper, FakeRedisService redisService)
    {
        InvProductCategoryServiceImpl service = new InvProductCategoryServiceImpl();
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "redisService", redisService);
        return service;
    }

    private static InvProduct product(Long productId, Long shopDeptId)
    {
        InvProduct product = new InvProduct();
        product.setProductId(productId);
        product.setProductName("测试商品");
        product.setShopDeptId(shopDeptId);
        product.setStatus("0");
        return product;
    }

    private static InvProduct maintainedProduct(String name)
    {
        InvProduct product = new InvProduct();
        product.setProductName(name);
        product.setCategoryId(8L);
        product.setSupplierName("测试供应商");
        product.setStatus("0");
        return product;
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private boolean allowed = true;

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            if (deptId != null && deptId.equals(104L))
            {
                return List.of(100L, 104L);
            }
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectActiveRelatedDeptIdsForReplenishment(
                Long deptId)
        {
            return selectRelatedDeptIds(deptId);
        }

        @Override
        public List<Long> selectAncestorDeptIds(Long deptId)
        {
            return Collections.emptyList();
        }

        @Override
        public Long selectRawBusinessRootDeptId(Long deptId)
        {
            return deptId;
        }

        @Override
        public List<Long> selectUserStoreScopeDeptIds(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Long> selectAllStoreDeptIds()
        {
            return Collections.emptyList();
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return scopeDeptId != null && scopeDeptId.equals(targetDeptId) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return allowed ? 1 : 0;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return "WAREHOUSE";
        }
    }

    private static class FakeProductMapper implements InvProductMapper
    {
        private InvProduct lastListQuery;
        private InvProduct stored;
        private InvProduct codeMatch;
        private String lastCodeLookup;
        private final List<InvProduct> updatedProducts = new java.util.ArrayList<>();
        private final List<InvProduct> insertedProducts = new java.util.ArrayList<>();
        private int selectByIdCalls;
        private int businessReferenceCount;
        private int deleteCalls;
        private int insertCalls;

        @Override
        public List<InvProduct> selectInvProductList(InvProduct product)
        {
            this.lastListQuery = product;
            return Collections.emptyList();
        }

        @Override
        public List<InvProduct> selectInvProductListBySupplier(String supplierName, List<Long> scopeDeptIds)
        {
            return Collections.emptyList();
        }

        @Override
        public InvProduct selectInvProductById(Long productId)
        {
            selectByIdCalls++;
            if (stored != null)
            {
                return stored;
            }
            return insertedProducts.stream()
                    .filter(product -> product.getProductId() != null && product.getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public InvProduct selectInvProductByCodeAndShop(String productCode, Long shopDeptId)
        {
            lastCodeLookup = productCode;
            return codeMatch != null && productCode.equals(codeMatch.getProductCode()) ? codeMatch : null;
        }

        @Override
        public InvProduct selectInvProductByNaturalKey(Long categoryId, String productName, String spec, Long shopDeptId)
        {
            return null;
        }

        @Override
        public int countBusinessReferenceByProductId(Long productId)
        {
            return businessReferenceCount;
        }

        @Override
        public int insertInvProduct(InvProduct product)
        {
            insertCalls++;
            product.setProductId(product.getProductId() == null ? 900L + insertCalls : product.getProductId());
            insertedProducts.add(product);
            stored = product;
            return 0;
        }

        @Override
        public int updateInvProduct(InvProduct product)
        {
            updatedProducts.add(product);
            return 0;
        }

        @Override
        public int deleteInvProductByIds(Long[] productIds)
        {
            deleteCalls++;
            return 0;
        }
    }

    private static class FakeCategoryMapper implements InvProductCategoryMapper
    {
        private InvProductCategory lastListQuery;
        private Long storedCategoryShopDeptId = 100L;
        private String storedCategoryStatus = "0";
        private int childCount;
        private int productCount;
        private int deleteCalls;
        private int insertCalls;
        private InvProductCategory updatedCategory;

        @Override
        public List<InvProductCategory> selectInvProductCategoryList(InvProductCategory category)
        {
            this.lastListQuery = category;
            return Collections.emptyList();
        }

        @Override
        public InvProductCategory selectInvProductCategoryById(Long categoryId)
        {
            InvProductCategory category = new InvProductCategory();
            category.setCategoryId(categoryId);
            category.setCategoryName("测试分类");
            category.setCategoryCode("CS");
            category.setShopDeptId(storedCategoryShopDeptId);
            category.setStatus(storedCategoryStatus);
            category.setAncestors("0");
            return category;
        }

        @Override
        public int countCategoryCodeByShop(String categoryCode, Long shopDeptId, Long excludeCategoryId)
        {
            return 0;
        }

        @Override
        public int countChildCategory(Long categoryId)
        {
            return childCount;
        }

        @Override
        public int countProductByCategoryId(Long categoryId)
        {
            return productCount;
        }

        @Override
        public int insertInvProductCategory(InvProductCategory category)
        {
            insertCalls++;
            category.setCategoryId(category.getCategoryId() == null ? 900L + insertCalls : category.getCategoryId());
            return 0;
        }

        @Override
        public int updateInvProductCategory(InvProductCategory category)
        {
            updatedCategory = category;
            return 0;
        }

        @Override
        public int deleteInvProductCategoryById(Long categoryId)
        {
            deleteCalls++;
            return 0;
        }
    }

    private static class FakeSupplierMapper implements InvSupplierMapper
    {
        @Override
        public InvSupplier selectInvSupplierByIdForUpdate(Long supplierId) { return selectInvSupplierById(supplierId); }

        @Override
        public List<Long> selectReferencingOeIdsForUpdate(String supplierName) { return Collections.emptyList(); }

        @Override
        public List<InvSupplier> selectInvSupplierList(InvSupplier supplier)
        {
            return Collections.emptyList();
        }

        @Override
        public InvSupplier selectInvSupplierById(Long supplierId)
        {
            return null;
        }

        @Override
        public InvSupplier selectInvSupplierByNameAndShop(String supplierName, Long shopDeptId)
        {
            return null;
        }

        @Override
        public InvSupplier selectActiveSupplierByNameInDeptChain(String supplierName, Long shopDeptId)
        {
            InvSupplier supplier = new InvSupplier();
            supplier.setSupplierName(supplierName);
            supplier.setContactPhone("13800000000");
            return supplier;
        }

        @Override
        public int countDuplicateSupplierName(String supplierName, Long shopDeptId,
                Long excludeSupplierId)
        {
            return 0;
        }

        @Override
        public int countDuplicateSupplierCode(String supplierCode, Long shopDeptId,
                Long excludeSupplierId)
        {
            return 0;
        }

        @Override
        public int countSupplierReferences(Long supplierId, String supplierName,
                List<Long> scopeDeptIds)
        {
            return 0;
        }

        @Override
        public int insertInvSupplier(InvSupplier supplier)
        {
            return 0;
        }

        @Override
        public int updateInvSupplier(InvSupplier supplier)
        {
            return 0;
        }

        @Override
        public int deleteInvSupplierByIds(Long[] supplierIds)
        {
            return 0;
        }
    }

    private static class MissingSupplierMapper extends FakeSupplierMapper
    {
        @Override
        public InvSupplier selectActiveSupplierByNameInDeptChain(String supplierName, Long shopDeptId)
        {
            return null;
        }
    }

    private static class FakeRedisService extends RedisService
    {
        private Object cached;
        private int getCacheObjectCalls;
        private String lastCacheKey;

        @Override
        public <T> T getCacheObject(String key)
        {
            getCacheObjectCalls++;
            lastCacheKey = key;
            return (T) cached;
        }

        @Override
        public <T> void setCacheObject(String key, T value, Long timeout, TimeUnit timeUnit)
        {
        }

        @Override
        public <T> void setCacheObject(String key, T value)
        {
        }

        @Override
        public Collection<String> keys(String pattern)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean deleteObject(Collection collection)
        {
            return true;
        }

        @Override
        public boolean deleteObject(String key)
        {
            return true;
        }
    }
}
