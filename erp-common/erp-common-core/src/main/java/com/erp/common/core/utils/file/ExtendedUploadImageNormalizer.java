package com.erp.common.core.utils.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.erp.common.core.exception.ServiceException;

/** Native codecs run outside the application JVM with bounded resources and private temporary files. */
final class ExtendedUploadImageNormalizer
{
    private static final Semaphore SLOTS = new Semaphore(2);
    private ExtendedUploadImageNormalizer() { }

    static byte[] normalize(byte[] source, String extension)
    {
        if (!Set.of("webp", "heic", "heif").contains(extension)) throw new IllegalArgumentException();
        boolean acquired = false;
        Path workspace = null;
        Process process = null;
        try
        {
            acquired = SLOTS.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) throw new ServiceException("图片处理繁忙，请稍后重试");
            workspace = Files.createTempDirectory("erp-image-");
            Path input = workspace.resolve("source." + extension), output = workspace.resolve("result." + extension);
            Path script = workspace.resolve("normalize_image.py");
            try (InputStream resource = ExtendedUploadImageNormalizer.class.getResourceAsStream("/image/normalize_image.py"))
            {
                if (resource == null) throw new IOException("missing codec resource");
                Files.copy(resource, script);
            }
            Files.write(input, source);
            String python = System.getProperty("erp.image.python", System.getenv().getOrDefault("ERP_IMAGE_PYTHON", "python3"));
            ProcessBuilder builder = new ProcessBuilder(python, "-I", script.toString(), "--input", input.toString(),
                    "--output", output.toString(), "--format", extension);
            builder.directory(workspace.toFile());
            builder.environment().put("TMPDIR", workspace.toString());
            builder.environment().put("OMP_NUM_THREADS", "2");
            builder.redirectOutput(workspace.resolve("stdout.log").toFile());
            builder.redirectError(workspace.resolve("stderr.log").toFile());
            process = builder.start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new ServiceException("图片处理超时，请选择较小的图片重试");
            if (process.exitValue() != 0 || !Files.isRegularFile(output))
                throw new ServiceException("图片内容无法解码或图片处理组件不可用，请重试或联系管理员");
            long size = Files.size(output);
            if (size <= 0 || size > source.length) throw new IOException("invalid normalized image size");
            return Files.readAllBytes(output);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("图片处理已中断，请重试");
        }
        catch (IOException exception)
        {
            throw new ServiceException("图片处理组件不可用，请联系管理员");
        }
        finally
        {
            if (process != null && process.isAlive())
            {
                process.destroyForcibly();
                try { process.waitFor(3, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            if (workspace != null)
            {
                try (var files = Files.walk(workspace))
                {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    });
                }
                catch (IOException ignored) { }
            }
            if (acquired) SLOTS.release();
        }
    }
}
