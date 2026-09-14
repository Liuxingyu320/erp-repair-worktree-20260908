package com.erp.inventory.service.impl;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.mapper.InvCatalogReferenceMapper;

@Service
public class InvCatalogDeletionService extends InvBaseService
{
    @Autowired private InvCatalogReferenceMapper catalogReferenceMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    public void delete(String itemType, Long[] ids, Long selectedDeptId)
    {
        if (!List.of("oe", "gift").contains(itemType)) throw new ServiceException("不支持的资料类型");
        // A caller's earlier RR snapshot must never be reused for reference checks.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new ServiceException("资料删除不能嵌套在其他业务事务中");
        requireWarehouseContext(selectedDeptId, "请先选择仓库后再维护物料资料");
        if (ids == null || ids.length == 0 || Arrays.stream(ids).anyMatch(id -> id == null || id <= 0))
            throw new ServiceException("请选择有效资料");
        List<Long> ordered = Arrays.stream(ids).distinct().sorted().toList();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> {
            for (Long id : ordered)
                if (catalogReferenceMapper.selectStatusForUpdate(itemType, id) == null)
                    throw new ServiceException("资料不存在或已删除，请刷新后重试");
            for (Long id : ordered)
                if (catalogReferenceMapper.countReferences(itemType, id) > 0)
                    throw new ServiceException("资料已被库存或历史业务引用，不能删除，请改为停用");
            for (Long id : ordered)
                if (catalogReferenceMapper.deleteUnreferenced(itemType, id) != 1)
                    throw new ServiceException("资料已变化，删除未完成，请刷新后重试");
        });
    }
}
