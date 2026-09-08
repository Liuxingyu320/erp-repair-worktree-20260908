package com.erp.oa.attendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.config.AttendanceV2Properties;

class AttendanceLeaveAttachmentStorageTest
{
    @TempDir Path temporary;

    @Test
    void storesValidatedContentUnderPrivateRelativePath()
    {
        AttendanceLeaveAttachmentStorage storage = storage();
        byte[] pdf = "%PDF-1.7\nprivate".getBytes(StandardCharsets.US_ASCII);

        AttendanceLeaveAttachmentStorage.StoredAttachment value =
                storage.store(42L, new MockMultipartFile("file",
                        "certificate.pdf", "image/png", pdf));

        assertThat(value.relativePath()).startsWith("leave/42/")
                .endsWith(".pdf");
        assertThat(value.contentType()).isEqualTo("application/pdf");
        assertThat(value.sha256()).hasSize(64);
        assertThat(storage.resolve(value.relativePath())).isRegularFile()
                .startsWith(temporary.resolve("private"));
    }

    @Test
    void rejectsExtensionSpoofingAndTraversal()
    {
        AttendanceLeaveAttachmentStorage storage = storage();
        assertThatThrownBy(() -> storage.store(42L,
                new MockMultipartFile("file", "fake.pdf",
                        "application/pdf", new byte[] { 1, 2, 3 })))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("LEAVE_ATTACHMENT_CONTENT_INVALID");
        assertThatThrownBy(() -> storage.resolve("../outside.pdf"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("LEAVE_ATTACHMENT_PATH_ESCAPE");
    }

    private AttendanceLeaveAttachmentStorage storage()
    {
        AttendanceV2Properties properties = new AttendanceV2Properties();
        properties.setStorageRoot(temporary.resolve("private").toString());
        properties.setTempRoot(temporary.resolve("temp").toString());
        properties.setMaxPhotoBytes(1024 * 1024);
        return new AttendanceLeaveAttachmentStorage(properties);
    }
}
