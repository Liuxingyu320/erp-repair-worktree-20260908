package com.erp.common.core.utils.file;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ImageUrlListTest {
    @Test void supportsOrderedListAndLegacyRead() {
        assertThat(ImageUrlList.read(null, "/a.png,/b.png")).containsExactly("/a.png", "/b.png");
        String stored = ImageUrlList.validateAndWrite(List.of("/b.png", "/a.png"));
        assertThat(ImageUrlList.cover(stored, "/old.png")).isEqualTo("/b.png");
        assertThat(ImageUrlList.read("[]", "/old.png")).isEmpty();
    }
    @Test void preservesOmittedAndRejectsDestructiveLegacyUpdate() {
        String stored = "[\"/a.png\",\"/b.png\"]";
        assertThat(ImageUrlList.prepare(null, null, stored, "/a.png")).isNull();
        assertThat(ImageUrlList.prepare(null, "/a.png", stored, "/a.png")).isNull();
        assertThatThrownBy(() -> ImageUrlList.prepare(null, "", stored, "/a.png")).hasMessageContaining("多张图片");
        assertThat(ImageUrlList.prepare("[]", "/old.png", stored, "/a.png")).isEqualTo("[]");
        assertThat(ImageUrlList.prepare(null, "", null, "/old.png")).isEqualTo("[]");
    }
    @Test void rejectsUnsafeUrlsAndOverLimit() {
        assertThatThrownBy(() -> ImageUrlList.validateAndWrite(List.of("javascript:alert(1)"))).hasMessageContaining("图片地址");
        assertThatThrownBy(() -> ImageUrlList.validateAndWrite(List.of("/1", "/2", "/3", "/4", "/5", "/6"))).hasMessageContaining("5张");
        assertThatThrownBy(() -> ImageUrlList.validateAndWrite(null)).hasMessageContaining("空列表");
    }
}
