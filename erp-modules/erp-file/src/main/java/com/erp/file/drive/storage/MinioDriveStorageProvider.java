package com.erp.file.drive.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.Locale;
import java.util.Objects;
import com.erp.file.drive.config.DriveProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 使用独立私有桶的云盘 MinIO 存储，不生成或返回公开对象地址。
 */
@Service
@ConditionalOnProperty(prefix = "drive", name = "storage-type", havingValue = "minio")
public class MinioDriveStorageProvider implements DriveStorageProvider
{
    private static final long UNKNOWN_SIZE = -1L;
    private static final long MULTIPART_SIZE = 10L * 1024L * 1024L;

    private final MinioClient client;
    private final String bucket;
    private final String legacyBucket;
    private final ObjectMapper objectMapper;

    @Autowired
    public MinioDriveStorageProvider(DriveProperties properties,
            ObjectProvider<MinioClient> clientProvider, Environment environment,
            ObjectMapper objectMapper)
    {
        this(requireConfiguredClient(clientProvider, environment), properties,
                environment.getProperty("minio.bucketName"), objectMapper);
    }

    MinioDriveStorageProvider(MinioClient client, DriveProperties properties,
            String legacyBucket, ObjectMapper objectMapper)
    {
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = trim(properties == null ? null : properties.getMinioBucket());
        this.legacyBucket = trim(legacyBucket);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void put(String storageKey, InputStream input) throws IOException
    {
        String key = requireStorageKey(storageKey);
        Objects.requireNonNull(input, "input");
        try
        {
            client.putObject(PutObjectArgs.builder()
                    .bucket(requireBucket())
                    .object(key)
                    .stream(input, UNKNOWN_SIZE, MULTIPART_SIZE)
                    .build());
        }
        catch (Exception ex)
        {
            throw storageFailure("Unable to write cloud drive object", ex);
        }
    }

    @Override
    public DriveStoredObject open(String storageKey) throws IOException
    {
        String key = requireStorageKey(storageKey);
        try
        {
            String driveBucket = requireBucket();
            long size = client.statObject(StatObjectArgs.builder()
                    .bucket(driveBucket).object(key).build()).size();
            if (size < 0)
            {
                throw new IOException("Cloud drive object size is unavailable");
            }
            InputStream object = client.getObject(GetObjectArgs.builder()
                    .bucket(driveBucket).object(key).build());
            if (object == null)
            {
                throw new IOException("Cloud drive object stream is unavailable");
            }
            return new DriveStoredObject(new InputStreamResource(object), size);
        }
        catch (ErrorResponseException ex)
        {
            if (isMissingObject(ex))
            {
                throw new NoSuchFileException("cloud drive object");
            }
            throw storageFailure("Unable to read cloud drive object", ex);
        }
        catch (IOException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw storageFailure("Unable to read cloud drive object", ex);
        }
    }

    @Override
    public boolean exists(String storageKey)
    {
        String key = requireStorageKeyUnchecked(storageKey);
        try
        {
            client.statObject(StatObjectArgs.builder()
                    .bucket(requireBucketUnchecked()).object(key).build());
            return true;
        }
        catch (ErrorResponseException ex)
        {
            if (isMissingObject(ex))
            {
                return false;
            }
            throw new UncheckedIOException(storageFailure(
                    "Unable to inspect cloud drive object", ex));
        }
        catch (Exception ex)
        {
            throw new UncheckedIOException(storageFailure(
                    "Unable to inspect cloud drive object", ex));
        }
    }

    @Override
    public void delete(String storageKey) throws IOException
    {
        String key = requireStorageKey(storageKey);
        try
        {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(requireBucket()).object(key).build());
        }
        catch (ErrorResponseException ex)
        {
            if (!isMissingObject(ex))
            {
                throw storageFailure("Unable to delete cloud drive object", ex);
            }
        }
        catch (Exception ex)
        {
            throw storageFailure("Unable to delete cloud drive object", ex);
        }
    }

    @Override
    public void validate() throws IOException
    {
        String driveBucket = requireBucket();
        if (!legacyBucket.isEmpty() && driveBucket.equals(legacyBucket))
        {
            throw new IOException("Cloud drive MinIO must use a dedicated private bucket");
        }
        try
        {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(driveBucket).build()))
            {
                throw new IOException("Cloud drive MinIO bucket does not exist");
            }
            validatePolicy(driveBucket);
        }
        catch (IOException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw storageFailure("Unable to validate cloud drive MinIO storage", ex);
        }
    }

    private void validatePolicy(String driveBucket) throws IOException
    {
        final String policy;
        try
        {
            policy = client.getBucketPolicy(GetBucketPolicyArgs.builder()
                    .bucket(driveBucket).build());
        }
        catch (ErrorResponseException ex)
        {
            if (isMissingPolicy(ex))
            {
                return;
            }
            throw storageFailure("Unable to inspect cloud drive MinIO bucket policy", ex);
        }
        catch (Exception ex)
        {
            throw storageFailure("Unable to inspect cloud drive MinIO bucket policy", ex);
        }

        try
        {
            JsonNode root = objectMapper.readTree(policy);
            if (root == null || !root.isObject())
            {
                throw malformedPolicy();
            }
            JsonNode statements = root.get("Statement");
            if (statements == null || (!statements.isArray() && !statements.isObject()))
            {
                throw malformedPolicy();
            }
            if (statements.isArray())
            {
                if (statements.isEmpty())
                {
                    throw malformedPolicy();
                }
                for (JsonNode statement : statements)
                {
                    validateStatement(statement);
                }
            }
            else
            {
                validateStatement(statements);
            }
        }
        catch (JacksonException ex)
        {
            throw new IOException("Cloud drive MinIO bucket policy is malformed", ex);
        }
        catch (IOException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new IOException("Cloud drive MinIO bucket policy is malformed", ex);
        }
    }

    private static void validateStatement(JsonNode statement) throws IOException
    {
        if (statement == null || !statement.isObject())
        {
            throw malformedPolicy();
        }
        JsonNode effect = statement.get("Effect");
        JsonNode principal = statement.get("Principal");
        JsonNode action = statement.get("Action");
        JsonNode notAction = statement.get("NotAction");
        if (effect == null || !effect.isTextual()
                || (!("Allow".equals(effect.textValue()))
                        && !("Deny".equals(effect.textValue())))
                || !isValidStringTree(principal)
                || (action == null) == (notAction == null)
                || !isValidActionList(action == null ? notAction : action))
        {
            throw malformedPolicy();
        }

        if ("Allow".equals(effect.textValue()) && containsWildcard(principal)
                && (notAction != null || containsReadAction(action)))
        {
            throw new IOException("Cloud drive MinIO bucket allows anonymous read access");
        }
    }

    private static boolean isValidStringTree(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return false;
        }
        if (node.isTextual())
        {
            return !node.textValue().isBlank();
        }
        if (!node.isContainer() || node.isEmpty())
        {
            return false;
        }
        for (JsonNode child : node)
        {
            if (!isValidStringTree(child))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidActionList(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return false;
        }
        if (node.isTextual())
        {
            return !node.textValue().isBlank();
        }
        if (!node.isArray() || node.isEmpty())
        {
            return false;
        }
        for (JsonNode child : node)
        {
            if (!child.isTextual() || child.textValue().isBlank())
            {
                return false;
            }
        }
        return true;
    }

    private static boolean containsWildcard(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return false;
        }
        if (node.isTextual())
        {
            return "*".equals(node.textValue().trim());
        }
        if (node.isContainer())
        {
            for (JsonNode child : node)
            {
                if (containsWildcard(child))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsReadAction(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return false;
        }
        if (node.isTextual())
        {
            String action = node.textValue().trim().toLowerCase(Locale.ROOT);
            return action.startsWith("s3:get") || action.startsWith("s3:list")
                    || wildcardMatches(action, "s3:getobject")
                    || wildcardMatches(action, "s3:listbucket");
        }
        if (node.isContainer())
        {
            for (JsonNode child : node)
            {
                if (containsReadAction(child))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean wildcardMatches(String pattern, String value)
    {
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int retryValueIndex = -1;
        while (valueIndex < value.length())
        {
            if (patternIndex < pattern.length()
                    && (pattern.charAt(patternIndex) == '?'
                            || pattern.charAt(patternIndex) == value.charAt(valueIndex)))
            {
                patternIndex++;
                valueIndex++;
            }
            else if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == '*')
            {
                starIndex = patternIndex++;
                retryValueIndex = valueIndex;
            }
            else if (starIndex >= 0)
            {
                patternIndex = starIndex + 1;
                valueIndex = ++retryValueIndex;
            }
            else
            {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*')
        {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    private static IOException malformedPolicy()
    {
        return new IOException("Cloud drive MinIO bucket policy is malformed");
    }

    private String requireBucket() throws IOException
    {
        if (bucket.isEmpty())
        {
            throw new IOException("Cloud drive MinIO bucket must not be blank");
        }
        return bucket;
    }

    private String requireBucketUnchecked()
    {
        try
        {
            return requireBucket();
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    private static String requireStorageKey(String storageKey) throws IOException
    {
        if (storageKey == null || storageKey.isBlank())
        {
            throw new IOException("Cloud drive storage key must not be blank");
        }
        return storageKey;
    }

    private static String requireStorageKeyUnchecked(String storageKey)
    {
        try
        {
            return requireStorageKey(storageKey);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    private static boolean isMissingObject(ErrorResponseException ex)
    {
        String code = errorCode(ex);
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code)
                || "NoSuchVersion".equals(code);
    }

    private static boolean isMissingPolicy(ErrorResponseException ex)
    {
        String code = errorCode(ex);
        return "NoSuchBucketPolicy".equals(code) || "NoSuchPolicy".equals(code);
    }

    private static String errorCode(ErrorResponseException ex)
    {
        return ex != null && ex.errorResponse() != null
                ? ex.errorResponse().code() : null;
    }

    private static IOException storageFailure(String message, Exception cause)
    {
        return new IOException(message, cause);
    }

    private static String trim(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static MinioClient requireConfiguredClient(
            ObjectProvider<MinioClient> clientProvider, Environment environment)
    {
        Objects.requireNonNull(clientProvider, "clientProvider");
        Objects.requireNonNull(environment, "environment");
        boolean enabled = environment.getProperty("minio.enabled", Boolean.class, false);
        if (!enabled || blank(environment.getProperty("minio.url"))
                || blank(environment.getProperty("minio.accessKey"))
                || blank(environment.getProperty("minio.secretKey")))
        {
            throw new IllegalStateException("drive.storage-type=minio requires minio.enabled=true "
                    + "and non-empty endpoint credentials");
        }
        MinioClient configured = clientProvider.getIfAvailable();
        if (configured == null)
        {
            throw new IllegalStateException("drive.storage-type=minio requires a MinioClient");
        }
        return configured;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }
}
