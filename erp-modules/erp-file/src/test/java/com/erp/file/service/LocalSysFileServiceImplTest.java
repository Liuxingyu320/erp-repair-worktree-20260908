package com.erp.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class LocalSysFileServiceImplTest
{
    @TempDir
    private Path uploadRoot;

    @Test
    void uploadFileShouldStorePublicFilesUnderPublicDirectory() throws Exception
    {
        LocalSysFileServiceImpl service = new LocalSysFileServiceImpl();
        service.domain = "http://static.test";
        service.localFilePrefix = "/file";
        ReflectionTestUtils.setField(service, "localFilePath", uploadRoot.toString());

        String url = service.uploadFile(new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[] { 1, 2, 3 }));

        assertThat(url).startsWith("http://static.test/file/public/");
        String publicPath = url.substring("http://static.test/file/public/".length());
        assertThat(Files.exists(uploadRoot.resolve("public").resolve(publicPath))).isTrue();
        assertThat(Files.exists(uploadRoot.resolve(publicPath))).isFalse();
    }
}
