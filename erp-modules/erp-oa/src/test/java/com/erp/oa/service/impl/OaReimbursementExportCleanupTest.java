package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.mapper.OaReimbursementMapper;

class OaReimbursementExportCleanupTest
{
    @TempDir Path root;
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void onlyAnExplicitRollbackCanDeleteAnArchive(int completion) throws Exception
    {
        var properties=new OaReimbursementProperties();properties.setStorageRoot(root.toString());
        var storage=new OaReimbursementFileStorageService(properties);
        var service=new OaReimbursementExportServiceImpl(mock(OaReimbursementMapper.class),mock(ShopScopeService.class),storage,properties);
        Path archive=root.resolve("exports/original.zip");Files.createDirectories(archive.getParent());Files.writeString(archive,"immutable archive");
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            ReflectionTestUtils.invokeMethod(service,"registerRollbackCleanup","exports/original.zip");
            var callbacks=TransactionSynchronizationManager.getSynchronizations();assertThat(callbacks).hasSize(1);
            callbacks.get(0).afterCompletion(completion);
            assertThat(Files.exists(archive)).isEqualTo(completion!=1);
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
}
