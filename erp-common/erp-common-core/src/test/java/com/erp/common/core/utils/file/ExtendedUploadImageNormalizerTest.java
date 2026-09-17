package com.erp.common.core.utils.file;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.common.core.exception.ServiceException;

/** Native codec release gate: run with ERP_IMAGE_PYTHON pointing to the prepared venv. */
@EnabledIfEnvironmentVariable(named = "ERP_IMAGE_PYTHON", matches = ".+")
class ExtendedUploadImageNormalizerTest
{
    @TempDir Path work;

    @Test void preservesAllThreeFormatsAndReportsFinalBytes() throws Exception
    {
        String code = "from PIL import Image; import pillow_heif,sys; pillow_heif.register_heif_opener(); "
                + "im=Image.effect_noise((160,120),100).convert('RGB'); "
                + "im.save(sys.argv[1],format=sys.argv[2],quality=100)";
        for (String extension : new String[] { "webp", "heic", "heif" })
        {
            Path source = work.resolve("source." + extension);
            Process process = new ProcessBuilder(System.getenv("ERP_IMAGE_PYTHON"), "-I", "-c", code,
                    source.toString(), "webp".equals(extension) ? "WEBP" : "HEIF").start();
            assertThat(process.waitFor()).isZero();
            byte[] bytes = Files.readAllBytes(source);
            var original = new MockMultipartFile("file", "original." + extension, "image/" + extension, bytes);
            var normalized = UploadImageNormalizer.normalize(original);
            assertThat(normalized.getOriginalFilename()).isEqualTo(original.getOriginalFilename());
            assertThat(normalized.getContentType()).isEqualTo(original.getContentType());
            assertThat(normalized.getSize()).isPositive().isLessThanOrEqualTo(bytes.length);
            assertThat(normalized.getBytes()).hasSize((int) normalized.getSize());
        }
    }

    @Test void rejectsDisguisedExtendedImage() throws Exception
    {
        for (String extension : new String[] { "webp", "heic", "heif" })
            assertThatThrownBy(() -> UploadImageNormalizer.normalize("not a photo".getBytes(), "fake." + extension))
                    .isInstanceOf(ServiceException.class).hasMessageContaining("无法解码");
    }
}
