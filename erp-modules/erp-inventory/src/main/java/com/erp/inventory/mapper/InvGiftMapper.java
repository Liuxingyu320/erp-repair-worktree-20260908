package com.erp.inventory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.inventory.domain.InvGiftBox;

public interface InvGiftMapper
{
    List<InvGiftBox> selectInvGiftList(InvGiftBox gift);
    InvGiftBox selectInvGiftById(Long giftId);
    InvGiftBox selectInvGiftByIdForUpdate(Long giftId);
    InvGiftBox selectInvGiftByCode(@Param("giftCode") String giftCode);
    InvGiftBox selectInvGiftByNaturalKey(@Param("categoryId") Long categoryId,
                                         @Param("giftName") String giftName,
                                         @Param("grade") String grade,
                                         @Param("spec") String spec);
    int countGiftCode(@Param("giftCode") String giftCode, @Param("excludeGiftId") Long excludeGiftId);
    int insertInvGift(InvGiftBox gift);
    int updateInvGift(InvGiftBox gift);
    int deleteInvGiftByIds(Long[] giftIds);
}
