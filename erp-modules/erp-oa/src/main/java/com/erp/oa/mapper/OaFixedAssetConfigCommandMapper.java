package com.erp.oa.mapper;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaFixedAssetConfigCommand;
public interface OaFixedAssetConfigCommandMapper {
    int ensureScope(@Param("shopId") Long shopId);
    Long selectVersion(@Param("shopId") Long shopId);
    Long lockScope(@Param("shopId") Long shopId);
    int advanceVersion(@Param("shopId") Long shopId, @Param("version") Long version);
    int claimCommand(@Param("shopId") Long shopId, @Param("actorId") Long actorId, @Param("requestId") String requestId, @Param("hash") String hash);
    java.util.List<com.erp.oa.domain.OaFixedAssetConfig> lockConfigRows(@Param("shopId") Long shopId);
    OaFixedAssetConfigCommand lockCommand(@Param("shopId") Long shopId, @Param("actorId") Long actorId, @Param("requestId") String requestId);
    OaFixedAssetConfigCommand selectCommand(@Param("shopId") Long shopId, @Param("actorId") Long actorId, @Param("requestId") String requestId);
    int completeCommand(@Param("shopId") Long shopId, @Param("actorId") Long actorId, @Param("requestId") String requestId, @Param("hash") String hash, @Param("result") String result);
}
