package com.erp.file.drive.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.erp.file.drive.config.DriveProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayName("云盘 MinIO 私有存储")
class MinioDriveStorageProviderTest
{
    private static final String DRIVE_BUCKET = "erp-drive-private";
    private static final String LEGACY_BUCKET = "erp";
    private static final String STORAGE_KEY = "personal/20/2026/07/object.bin";

    private MinioClient client;
    private DriveProperties properties;

    @BeforeEach
    void setUp()
    {
        client = mock(MinioClient.class);
        properties = new DriveProperties();
        properties.setMinioBucket(DRIVE_BUCKET);
    }

    @Test
    @DisplayName("对象操作始终使用云盘专用桶和原始存储键")
    void shouldUseDedicatedBucketAndExactStorageKey() throws Exception
    {
        byte[] bytes = "private-minio-object".getBytes(StandardCharsets.UTF_8);
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn((long) bytes.length);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(new GetObjectResponse(
                new Headers.Builder().build(), DRIVE_BUCKET, null, STORAGE_KEY,
                new ByteArrayInputStream(bytes)));
        MinioDriveStorageProvider provider = provider();

        provider.put(STORAGE_KEY, new ByteArrayInputStream(bytes));
        DriveStoredObject stored = provider.open(STORAGE_KEY);
        assertThat(stored.size()).isEqualTo(bytes.length);
        try (var input = stored.resource().getInputStream())
        {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
        assertThat(provider.exists(STORAGE_KEY)).isTrue();
        provider.delete(STORAGE_KEY);

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        ArgumentCaptor<GetObjectArgs> get = ArgumentCaptor.forClass(GetObjectArgs.class);
        ArgumentCaptor<StatObjectArgs> statArgs = ArgumentCaptor.forClass(StatObjectArgs.class);
        ArgumentCaptor<RemoveObjectArgs> remove = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).putObject(put.capture());
        verify(client).getObject(get.capture());
        verify(client, org.mockito.Mockito.times(2)).statObject(statArgs.capture());
        verify(client).removeObject(remove.capture());

        assertBucketAndKey(put.getValue().bucket(), put.getValue().object());
        assertBucketAndKey(get.getValue().bucket(), get.getValue().object());
        statArgs.getAllValues().forEach(args ->
                assertBucketAndKey(args.bucket(), args.object()));
        assertBucketAndKey(remove.getValue().bucket(), remove.getValue().object());
    }

    @Test
    @DisplayName("删除不存在对象视为成功")
    void shouldTreatMissingObjectDeleteAsSuccess() throws Exception
    {
        doThrow(error("NoSuchKey")).when(client)
                .removeObject(any(RemoveObjectArgs.class));

        assertThatCode(() -> provider().delete(STORAGE_KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("对象不存在时 exists 返回 false")
    void shouldReturnFalseWhenObjectIsMissing() throws Exception
    {
        when(client.statObject(any(StatObjectArgs.class)))
                .thenThrow(error("NoSuchObject"));

        assertThat(provider().exists(STORAGE_KEY)).isFalse();
    }

    @Test
    @DisplayName("stat 阶段发现对象不存在时映射为文件缺失")
    void shouldMapMissingOpenDuringStat() throws Exception
    {
        when(client.statObject(any(StatObjectArgs.class)))
                .thenThrow(error("NoSuchKey"));

        assertThatThrownBy(() -> provider().open(STORAGE_KEY))
                .isInstanceOf(java.nio.file.NoSuchFileException.class)
                .hasMessageContaining("cloud drive object");
    }

    @Test
    @DisplayName("对象在 stat 后消失仍由 open 映射为文件缺失")
    void shouldMapObjectDisappearingAfterStat() throws Exception
    {
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(12L);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        when(client.getObject(any(GetObjectArgs.class)))
                .thenThrow(error("NoSuchKey"));

        assertThatThrownBy(() -> provider().open(STORAGE_KEY))
                .isInstanceOf(java.nio.file.NoSuchFileException.class)
                .hasMessageContaining("cloud drive object");
    }

    @Test
    @DisplayName("云盘桶不得复用旧公开附件桶")
    void shouldRejectLegacyPublicBucketReuse()
    {
        properties.setMinioBucket(LEGACY_BUCKET);
        MinioDriveStorageProvider provider = provider();

        assertThatThrownBy(provider::validate)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("dedicated");
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("匿名读取策略使启动校验失败")
    void shouldRejectAnonymousReadPolicy() throws Exception
    {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(client.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn("""
                {"Statement":[{"Effect":"Allow","Principal":{"AWS":"*"},
                "Action":["s3:GetObject","s3:ListBucket"]}]}
                """);

        assertThatThrownBy(() -> provider().validate())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("anonymous read");
    }

    @Test
    @DisplayName("匿名读取动作的 IAM 通配写法同样被拒绝")
    void shouldRejectAnonymousWildcardReadActions() throws Exception
    {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        for (String action : new String[] {"s3:Get*", "s3:List*", "s3:?etObject"})
        {
            when(client.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn("""
                    {"Statement":{"Effect":"Allow","Principal":"*",
                    "Action":"%s"}}
                    """.formatted(action));

            assertThatThrownBy(() -> provider().validate())
                    .as("action %s", action)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("anonymous read");
        }
    }

    @Test
    @DisplayName("没有桶策略是私有默认并通过校验")
    void shouldAcceptMissingPolicyAsPrivateDefault() throws Exception
    {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(client.getBucketPolicy(any(GetBucketPolicyArgs.class)))
                .thenThrow(error("NoSuchBucketPolicy"));

        assertThatCode(() -> provider().validate()).doesNotThrowAnyException();

        ArgumentCaptor<BucketExistsArgs> exists = ArgumentCaptor.forClass(BucketExistsArgs.class);
        ArgumentCaptor<GetBucketPolicyArgs> policy =
                ArgumentCaptor.forClass(GetBucketPolicyArgs.class);
        verify(client).bucketExists(exists.capture());
        verify(client).getBucketPolicy(policy.capture());
        assertThat(exists.getValue().bucket()).isEqualTo(DRIVE_BUCKET);
        assertThat(policy.getValue().bucket()).isEqualTo(DRIVE_BUCKET);
    }

    @Test
    @DisplayName("桶不存在或策略不可解析时关闭失败")
    void shouldFailClosedForMissingBucketAndMalformedPolicy() throws Exception
    {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        assertThatThrownBy(() -> provider().validate())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not exist");

        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(client.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn("not-json");
        assertThatThrownBy(() -> provider().validate())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("policy");
    }

    @Test
    @DisplayName("合法 JSON 但非法的策略结构同样关闭失败")
    void shouldFailClosedForStructurallyMalformedPolicies() throws Exception
    {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        String[] malformed = {
                "{\"Statement\":[{}]}",
                "{\"Statement\":[\"not-an-object\"]}",
                "{\"Statement\":{\"Effect\":[],\"Principal\":\"*\",\"Action\":\"s3:PutObject\"}}",
                "{\"Statement\":{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":42}}"
        };
        for (String policy : malformed)
        {
            when(client.getBucketPolicy(any(GetBucketPolicyArgs.class))).thenReturn(policy);
            assertThatThrownBy(() -> provider().validate())
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("malformed");
        }
    }

    @Test
    @DisplayName("选择 MinIO 但未启用客户端时给出明确启动配置错误")
    void shouldFailContextClearlyWhenMinioIsDisabled()
    {
        new ApplicationContextRunner()
                .withUserConfiguration(MinioProviderContext.class,
                        MinioDriveStorageProvider.class)
                .withPropertyValues(
                        "drive.storage-type=minio",
                        "drive.minio-bucket=" + DRIVE_BUCKET,
                        "minio.enabled=false",
                        "minio.url=http://127.0.0.1:9000",
                        "minio.accessKey=test-access",
                        "minio.secretKey=test-secret",
                        "minio.bucketName=" + LEGACY_BUCKET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("drive.storage-type=minio requires "
                                    + "minio.enabled=true and non-empty endpoint credentials");
                });
    }

    @Test
    @DisplayName("启用 MinIO 但客户端缺失时拒绝静默回退本地存储")
    void shouldFailContextWhenMinioClientIsUnavailable()
    {
        new ApplicationContextRunner()
                .withUserConfiguration(MinioProviderContext.class,
                        MinioDriveStorageProvider.class)
                .withPropertyValues(
                        "drive.storage-type=minio",
                        "drive.minio-bucket=" + DRIVE_BUCKET,
                        "minio.enabled=true",
                        "minio.url=http://127.0.0.1:9000",
                        "minio.accessKey=test-access",
                        "minio.secretKey=test-secret",
                        "minio.bucketName=" + LEGACY_BUCKET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("drive.storage-type=minio requires a MinioClient");
                });
    }

    @Test
    @DisplayName("配置完整时只装配 MinIO 云盘存储而不装配本地实现")
    void shouldSelectOnlyMinioProviderWhenConfigured()
    {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguredMinioContext.class,
                        MinioDriveStorageProvider.class, LocalDriveStorageProvider.class)
                .withPropertyValues(
                        "drive.storage-type=minio",
                        "drive.minio-bucket=" + DRIVE_BUCKET,
                        "minio.enabled=true",
                        "minio.url=http://127.0.0.1:9000",
                        "minio.accessKey=test-access",
                        "minio.secretKey=test-secret",
                        "minio.bucketName=" + LEGACY_BUCKET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DriveStorageProvider.class)
                            .hasSingleBean(MinioDriveStorageProvider.class)
                            .doesNotHaveBean(LocalDriveStorageProvider.class);
                });
    }

    private MinioDriveStorageProvider provider()
    {
        return new MinioDriveStorageProvider(client, properties, LEGACY_BUCKET,
                new ObjectMapper());
    }

    private static ErrorResponseException error(String code)
    {
        return new ErrorResponseException(new ErrorResponse(code, code, DRIVE_BUCKET,
                STORAGE_KEY, null, null, null), null, null);
    }

    private static void assertBucketAndKey(String bucket, String key)
    {
        assertThat(bucket).isEqualTo(DRIVE_BUCKET).isNotEqualTo(LEGACY_BUCKET);
        assertThat(key).isEqualTo(STORAGE_KEY);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DriveProperties.class)
    static class MinioProviderContext
    {
        @Bean
        ObjectMapper objectMapper()
        {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DriveProperties.class)
    static class ConfiguredMinioContext
    {
        @Bean
        ObjectMapper objectMapper()
        {
            return new ObjectMapper();
        }

        @Bean
        MinioClient minioClient()
        {
            return mock(MinioClient.class);
        }
    }
}
