package com.erp.file.drive.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

@DisplayName("云盘启动完整性校验")
class DriveStartupValidatorTest
{
    private DriveProperties properties;
    private DriveStorageProvider storage;
    private DriveSpaceMapper spaceMapper;
    private ApplicationArguments arguments;

    @BeforeEach
    void setUp()
    {
        properties = new DriveProperties();
        storage = mock(DriveStorageProvider.class);
        spaceMapper = mock(DriveSpaceMapper.class);
        arguments = mock(ApplicationArguments.class);
    }

    @Test
    @DisplayName("功能关闭时不探测存储和数据库")
    void shouldSkipAllProbesWhenDisabled() throws Exception
    {
        new DriveStartupValidator(properties, storage, spaceMapper).run(arguments);

        verifyNoInteractions(storage, spaceMapper, arguments);
    }

    @Test
    @DisplayName("功能开启时先校验存储再要求公司空间存在")
    void shouldValidateStorageAndCompanySpaceWhenEnabled() throws Exception
    {
        properties.setEnabled(true);
        DriveSpace company = new DriveSpace();
        company.setSpaceKey(DriveConstants.COMPANY_SPACE_KEY);
        when(spaceMapper.selectByKey(DriveConstants.COMPANY_SPACE_KEY)).thenReturn(company);

        assertThatCode(() -> new DriveStartupValidator(properties, storage, spaceMapper)
                .run(arguments)).doesNotThrowAnyException();

        verify(storage).validate();
        verify(spaceMapper).selectByKey(DriveConstants.COMPANY_SPACE_KEY);
    }

    @Test
    @DisplayName("公司空间未初始化时停止启动")
    void shouldFailStartupWhenCompanySpaceIsMissing()
    {
        properties.setEnabled(true);

        assertThatThrownBy(() -> new DriveStartupValidator(properties, storage, spaceMapper)
                .run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cloud drive schema/company space is not ready");
    }

    @Test
    @DisplayName("存储探测失败时不继续查询数据库")
    void shouldStopBeforeDatabaseWhenStorageValidationFails() throws Exception
    {
        properties.setEnabled(true);
        doThrow(new IOException("unavailable")).when(storage).validate();

        assertThatThrownBy(() -> new DriveStartupValidator(properties, storage, spaceMapper)
                .run(arguments)).isInstanceOf(IOException.class);
        verifyNoInteractions(spaceMapper);
    }
}
