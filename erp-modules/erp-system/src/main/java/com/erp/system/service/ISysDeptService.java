package com.erp.system.service;

import java.util.List;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.dto.SysSortBatchRequest;
import com.erp.system.domain.dto.SysSortChangeRequest;
import com.erp.system.domain.vo.TreeSelect;
import com.erp.system.domain.vo.SysDeptLeaderOptionVo;

/**
 * 部门管理 服务层
 * 
 * @author erp
 */
public interface ISysDeptService
{
    /**
     * 查询部门管理数据
     * 
     * @param dept 部门信息
     * @return 部门信息集合
     */
    public List<SysDept> selectDeptList(SysDept dept);

    /**
     * 查询部门树结构信息
     * 
     * @param dept 部门信息
     * @return 部门树信息集合
     */
    public List<TreeSelect> selectDeptTreeList(SysDept dept);

    /**
     * 构建前端所需要树结构
     * 
     * @param depts 部门列表
     * @return 树结构列表
     */
    public List<SysDept> buildDeptTree(List<SysDept> depts);

    /**
     * 构建前端所需要下拉树结构
     * 
     * @param depts 部门列表
     * @return 下拉树结构列表
     */
    public List<TreeSelect> buildDeptTreeSelect(List<SysDept> depts);

    /**
     * 查询正常仓库列表（供调拨、采购等业务选择仓库目标）
     *
     * @return 仓库列表
     */
    public List<SysDept> selectWarehouseList();

    /**
     * 按业务目的查询正常仓库列表。
     *
     * @param purpose 业务目的：deliverySource、replenishmentSource、returnTarget、currentWarehouse
     * @param scopeDeptId 当前业务组织
     * @return 仓库列表
     */
    public List<SysDept> selectWarehouseList(String purpose, Long scopeDeptId);

    /**
     * 查询当前业务组织范围内可查看库存的正常门店列表。
     *
     * @param scopeDeptId 范围根组织ID
     * @return 门店列表
     */
    public List<SysDept> selectVisibleStoreList(Long scopeDeptId);

    /** 查询当前数据范围内可选的启用在职负责人。 */
    public List<SysDeptLeaderOptionVo> selectLeaderOptions(String keyword);

    /**
     * 根据角色ID查询部门树信息
     * 
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    public List<Long> selectDeptListByRoleId(Long roleId);

    /**
     * 根据部门ID查询信息
     * 
     * @param deptId 部门ID
     * @return 部门信息
     */
    public SysDept selectDeptById(Long deptId);

    /**
     * 根据ID查询所有子部门（正常状态）
     * 
     * @param deptId 部门ID
     * @return 子部门数
     */
    public int selectNormalChildrenDeptById(Long deptId);

    /**
     * 是否存在部门子节点
     * 
     * @param deptId 部门ID
     * @return 结果
     */
    public boolean hasChildByDeptId(Long deptId);

    /**
     * 查询部门是否存在用户
     * 
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    public boolean checkDeptExistUser(Long deptId);

    /**
     * 校验部门名称是否唯一
     * 
     * @param dept 部门信息
     * @return 结果
     */
    public boolean checkDeptNameUnique(SysDept dept);

    /**
     * 校验部门是否有数据权限
     * 
     * @param deptId 部门id
     */
    public void checkDeptDataScope(Long deptId);

    /**
     * 新增保存部门信息
     * 
     * @param dept 部门信息
     * @return 结果
     */
    public int insertDept(SysDept dept);

    /**
     * 修改保存部门信息
     * 
     * @param dept 部门信息
     * @return 结果
     */
    public int updateDept(SysDept dept);

    /**
     * 保存部门排序
     *
     * @param request 带原排序基线的变更集合
     */
    public void updateDeptSort(SysSortChangeRequest request);

    /** 标准 JSON 差量批量排序。 */
    public void updateDeptSort(SysSortBatchRequest request);

    /**
     * 删除部门管理信息
     *
     * @param deptId 部门ID
     * @return 结果
     */
    public int deleteDeptById(Long deptId);

    /**
     * 查询店铺树（供选店页面使用，不走 system:dept:list 权限）
     *
     * @param dept 部门查询条件
     * @return 部门树结构
     */
    public List<SysDept> selectShopTree(SysDept dept);
}
