package com.erp.inventory.mapper;

import org.apache.ibatis.annotations.Param;

public interface InvNumberSequenceMapper
{
    String selectCurrentSequence(@Param("seqName") String seqName, @Param("currentDate") String currentDate);
    int insertOrUpdateSequence(@Param("seqName") String seqName, @Param("currentDate") String currentDate, @Param("currentSeq") int currentSeq, @Param("prefix") String prefix);
    int incrementAndGetSequence(@Param("seqName") String seqName, @Param("currentDate") String currentDate);
    Long selectLastInsertId();
}
