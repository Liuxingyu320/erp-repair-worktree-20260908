package com.erp.file.drive.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.exception.DriveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("云盘私有本地存储")
class LocalDriveStorageProviderTest
{
    @TempDir
    private Path uploadRoot;

    private Path privateRoot;
    private LocalDriveStorageProvider provider;

    @BeforeEach
    void setUp() throws Exception
    {
        privateRoot = uploadRoot.resolve("private/drive");
        DriveProperties properties = new DriveProperties();
        properties.setLocalPath(privateRoot.toString());
        provider = new LocalDriveStorageProvider(properties);
        provider.validate();
    }

    @Test
    @DisplayName("put/open/exists/delete 只操作私有根目录")
    void shouldStoreAndDeletePrivateObjects() throws Exception
    {
        String key = "personal/20/report.txt";
        byte[] content = "private-drive".getBytes(StandardCharsets.UTF_8);

        provider.put(key, new ByteArrayInputStream(content));

        assertThat(provider.exists(key)).isTrue();
        DriveStoredObject stored = provider.open(key);
        assertThat(stored.size()).isEqualTo(content.length);
        try (var input = stored.resource().getInputStream())
        {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
        assertThat(privateRoot.resolve(key)).hasBinaryContent(content);
        assertThat(uploadRoot.resolve("public")).doesNotExist();

        provider.delete(key);
        assertThat(provider.exists(key)).isFalse();
    }

    @Test
    @DisplayName("目录穿越键被拒绝且不能写入公开目录")
    void shouldRejectTraversalOutsidePrivateRoot()
    {
        assertThatThrownBy(() -> provider.put("../public/escape.txt",
                new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ACCESS_DENIED);

        assertThat(Files.exists(uploadRoot.resolve("public/escape.txt"))).isFalse();
    }
}
