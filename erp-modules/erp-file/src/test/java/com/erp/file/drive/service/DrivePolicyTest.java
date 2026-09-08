package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.domain.vo.DriveNodeVo;
import com.erp.file.drive.exception.DriveException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("云盘名称与文件安全策略")
class DrivePolicyTest
{
    private final DriveNamePolicy namePolicy = new DriveNamePolicy();
    private final DriveFilePolicy filePolicy = new DriveFilePolicy(new DriveProperties());

    @Test
    @DisplayName("名称使用 NFKC、稳定小写键和 LIKE 转义")
    void shouldNormalizeNamesAndSearchPatterns()
    {
        assertThat(namePolicy.normalize("  月度报表  ")).isEqualTo("月度报表");
        assertThat(namePolicy.normalizedKey("ＡＢＣ.PDF")).isEqualTo("abc.pdf");
        assertThat(namePolicy.searchPattern("  预算%_!  ")).isEqualTo("预算!%!_!!");
    }

    @Test
    @DisplayName("危险、空白、控制符和超长名称被拒绝")
    void shouldRejectUnsafeNames()
    {
        assertThatThrownBy(() -> namePolicy.normalize("../工资.xlsx"))
                .isInstanceOf(DriveException.class);
        assertThatThrownBy(() -> namePolicy.normalize("."))
                .isInstanceOf(DriveException.class);
        assertThatThrownBy(() -> namePolicy.normalize("报告\\最终版"))
                .isInstanceOf(DriveException.class);
        assertThatThrownBy(() -> namePolicy.normalize("报告\u0000.txt"))
                .isInstanceOf(DriveException.class);
        assertThatThrownBy(() -> namePolicy.normalize("文".repeat(201)))
                .isInstanceOf(DriveException.class);
        assertThatThrownBy(() -> namePolicy.searchPattern("文".repeat(101)))
                .isInstanceOf(DriveException.class);
    }

    @ParameterizedTest(name = "允许 {0} / {1}")
    @MethodSource("allowedFiles")
    @DisplayName("白名单扩展名必须匹配显式 MIME 家族")
    void shouldAllowMatchedFileFamilies(String fileName, String contentType)
    {
        filePolicy.validate(fileName, contentType, 10L);
    }

    @ParameterizedTest(name = "拒绝 {0} / {1}")
    @MethodSource("rejectedFiles")
    @DisplayName("伪装、活动内容和可执行文件被拒绝")
    void shouldRejectMismatchedOrActiveContent(String fileName, String contentType)
    {
        assertThatThrownBy(() -> filePolicy.validate(fileName, contentType, 10L))
                .isInstanceOf(DriveException.class);
    }

    @Test
    @DisplayName("预览仅允许安全图片、PDF 和文本且严格执行 100MiB")
    void shouldLimitPreviewAndFileSize()
    {
        assertThat(filePolicy.isPreviewable("pdf", "application/pdf")).isTrue();
        assertThat(filePolicy.isPreviewable("png", "image/png")).isTrue();
        assertThat(filePolicy.isPreviewable("txt", "text/plain")).isTrue();
        assertThat(filePolicy.isPreviewable("docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isFalse();
        assertThat(filePolicy.isPreviewable("heic", "image/heic")).isFalse();
        assertThatThrownBy(() -> filePolicy.validate(
                "大文件.pdf", "application/pdf", 104857601L))
                .isInstanceOf(DriveException.class);
    }

    @Test
    @DisplayName("节点响应绝不定义物理存储键或摘要字段")
    void shouldKeepPhysicalMetadataOutOfNodeVo()
    {
        assertThat(Arrays.stream(DriveNodeVo.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("storageKey", "sha256")
                .contains("nodeId", "logicalPath", "breadcrumbs", "canPreview");
    }

    private static Stream<Arguments> allowedFiles()
    {
        return Stream.of(
                Arguments.of("合同.doc", "application/msword"),
                Arguments.of("合同.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                Arguments.of("台账.xls", "application/vnd.ms-excel"),
                Arguments.of("台账.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                Arguments.of("方案.ppt", "application/vnd.ms-powerpoint"),
                Arguments.of("方案.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                Arguments.of("制度.pdf", "application/pdf"),
                Arguments.of("说明.txt", "text/plain; charset=UTF-8"),
                Arguments.of("数据.csv", "text/csv"),
                Arguments.of("照片.jpg", "image/jpeg"),
                Arguments.of("照片.jpeg", "image/jpeg"),
                Arguments.of("截图.png", "image/png"),
                Arguments.of("动图.gif", "image/gif"),
                Arguments.of("宣传.webp", "image/webp"),
                Arguments.of("实拍.heic", "image/heic"),
                Arguments.of("实拍.heif", "image/heif"),
                Arguments.of("资料.zip", "application/zip"),
                Arguments.of("资料.rar", "application/vnd.rar"),
                Arguments.of("资料.7z", "application/x-7z-compressed"),
                Arguments.of("离线合同.docx", ""),
                Arguments.of("离线资料.zip", "application/octet-stream"));
    }

    private static Stream<Arguments> rejectedFiles()
    {
        return Stream.of(
                Arguments.of("脚本.html", "text/html"),
                Arguments.of("伪装.pdf", "text/html"),
                Arguments.of("伪装.png", "image/svg+xml"),
                Arguments.of("程序.exe", "application/octet-stream"),
                Arguments.of("伪装.jpg", "image/png"),
                Arguments.of("伪装.docx", "application/pdf"),
                Arguments.of("伪装.txt", "application/octet-stream"),
                Arguments.of("伪装.heic", "image/jpeg"),
                Arguments.of("伪装.zip", "application/pdf"),
                Arguments.of("无扩展名", "application/pdf"));
    }
}
