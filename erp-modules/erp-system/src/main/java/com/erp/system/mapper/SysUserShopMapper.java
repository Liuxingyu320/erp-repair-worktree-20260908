package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysUserShop;

/**
 * 用户店铺授权 数据层
 *
 * @author erp
 */
public interface SysUserShopMapper
{
    List<Long> selectShopDeptIdsByUserId(@Param("userId") Long userId);

    List<Long> selectAllShopDeptIdsByUserId(@Param("userId") Long userId);

    /** Serializes previewed scope writes even when the user currently has no bindings. */
    Long lockUserForShopScope(@Param("userId") Long userId);

    List<SysUserShop> selectUserShopsByUserIds(@Param("userIds") List<Long> userIds);

    int countUserShopScope(@Param("userId") Long userId, @Param("deptId") Long deptId);

    int deleteUserShopByUserId(@Param("userId") Long userId);

    int deleteUserShopByUserIdAndDeptIds(@Param("userId") Long userId, @Param("deptIds") List<Long> deptIds);

    int deleteUserShopByUserIds(Long[] userIds);

    int batchUserShop(List<SysUserShop> userShops);

    int insertUserShopIfAbsent(@Param("userId") Long userId, @Param("deptId") Long deptId,
            @Param("operator") String operator);
}
