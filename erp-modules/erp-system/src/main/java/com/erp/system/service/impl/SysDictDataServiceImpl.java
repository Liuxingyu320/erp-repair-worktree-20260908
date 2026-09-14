package com.erp.system.service.impl;

import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDictType;
import com.erp.system.mapper.SysDictTypeMapper;
import com.erp.system.service.support.DictCacheCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.security.utils.DictUtils;
import com.erp.system.api.domain.SysDictData;
import com.erp.system.mapper.SysDictDataMapper;
import com.erp.system.service.ISysDictDataService;

/**
 * 字典 业务层处理
 * 
 * @author erp
 */
@Service
public class SysDictDataServiceImpl implements ISysDictDataService
{
    private ServiceException dictionaryConflict(String message) { return new ServiceException(message, 409); }
    @Autowired
    private SysDictDataMapper dictDataMapper;
    @Autowired private SysDictTypeMapper dictTypeMapper;
    @Autowired private DictCacheCoordinator dictCache;

    private void lockParents(Collection<String> types)
    {
        Map<Long, String> parents = new TreeMap<>();
        for (String type : new HashSet<>(types)) {
            if (type == null || type.isBlank()) throw dictionaryConflict("字典类型不能为空");
            SysDictType parent = dictTypeMapper.selectDictTypeByType(type);
            if (parent == null) throw dictionaryConflict("字典类型 " + type + " 已不存在，请刷新后重试");
            parents.put(parent.getDictId(), type);
        }
        for (Map.Entry<Long, String> parent : parents.entrySet()) {
            SysDictType current = dictTypeMapper.lockDictTypeById(parent.getKey());
            if (current == null || !Objects.equals(parent.getValue(), current.getDictType()))
                throw dictionaryConflict("字典类型已改名或删除，请刷新后重试");
        }
    }
    private SysDictData requiredData(Long id)
    {
        SysDictData value = dictDataMapper.selectDictDataById(id);
        if (value == null) throw dictionaryConflict("字典项 " + id + " 已不存在");
        return value;
    }
    private SysDictData lockData(Long id, String expectedType)
    {
        SysDictData value = dictDataMapper.lockDictDataById(id);
        if (value == null || !Objects.equals(value.getDictType(), expectedType))
            throw dictionaryConflict("字典项已变化，请刷新后重试");
        return value;
    }

    /**
     * 根据条件分页查询字典数据
     * 
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictData> selectDictDataList(SysDictData dictData)
    {
        return dictDataMapper.selectDictDataList(dictData);
    }

    /**
     * 根据字典类型和字典键值查询字典数据信息
     * 
     * @param dictType 字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    @Override
    public String selectDictLabel(String dictType, String dictValue)
    {
        return dictDataMapper.selectDictLabel(dictType, dictValue);
    }

    /**
     * 根据字典数据ID查询信息
     * 
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    @Override
    public SysDictData selectDictDataById(Long dictCode)
    {
        return dictDataMapper.selectDictDataById(dictCode);
    }

    /**
     * 批量删除字典数据信息
     * 
     * @param dictCodes 需要删除的字典数据ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictDataByIds(Long[] dictCodes)
    {
        dictCache.beginMutation();
        if (dictCodes == null || dictCodes.length == 0 || dictCodes.length > 200 || Arrays.stream(dictCodes).anyMatch(id -> id == null || id <= 0))
            throw dictionaryConflict("请选择1至200个有效字典项编号");
        List<SysDictData> snapshot = Arrays.stream(dictCodes).distinct().sorted().map(this::requiredData).toList();
        List<String> types = snapshot.stream().map(SysDictData::getDictType).distinct().toList();
        lockParents(types);
        for (SysDictData row : snapshot) lockData(row.getDictCode(), row.getDictType());
        for (SysDictData row : snapshot)
            if (dictDataMapper.deleteDictDataById(row.getDictCode()) != 1) throw dictionaryConflict("字典项已变化，整批删除已回滚");
        dictCache.afterCommit(types);
    }

    /**
     * 新增保存字典数据信息
     * 
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDictData(SysDictData data)
    {
        dictCache.beginMutation(); lockParents(List.of(data.getDictType()));
        int row = dictDataMapper.insertDictData(data);
        if (row != 1) throw dictionaryConflict("字典项保存失败");
        dictCache.afterCommit(List.of(data.getDictType()));
        return row;
    }

    /**
     * 修改保存字典数据信息
     * 
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDictData(SysDictData data)
    {
        dictCache.beginMutation();
        SysDictData old = requiredData(data.getDictCode());
        List<String> types = List.of(old.getDictType(), data.getDictType());
        lockParents(types); lockData(data.getDictCode(), old.getDictType());
        int row = dictDataMapper.updateDictData(data);
        if (row != 1) throw dictionaryConflict("字典项已变化，修改未保存");
        dictCache.afterCommit(types);
        return row;
    }
}
