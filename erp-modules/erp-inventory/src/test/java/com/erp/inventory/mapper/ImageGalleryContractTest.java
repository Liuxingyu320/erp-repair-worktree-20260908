package com.erp.inventory.mapper;
import java.util.List;
import com.erp.inventory.domain.InvGiftBox;
import com.erp.inventory.domain.InvOeItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ImageGalleryContractTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void arrayRoundTripsAndLegacyCoverRemainsSingle() throws Exception {
        InvOeItem item = json.readValue("{\"imageUrls\":[\"/b.png\",\"/a.png\"]}", InvOeItem.class);
        assertThat(item.getImageUrls()).containsExactly("/b.png", "/a.png");
        assertThat(item.getImageUrl()).isEqualTo("/b.png");
        assertThat(json.readTree(json.writeValueAsString(item)).get("imageUrl").asText()).isEqualTo("/b.png");
        assertThat(json.readTree(json.writeValueAsString(item)).has("imageUrlsText")).isFalse();
        assertThat(json.readTree(json.writeValueAsString(item)).has("rawImageUrl")).isFalse();
    }
    @Test void excelExportImportRetainsEveryImageInOrder() throws Exception {
        InvOeItem source = new InvOeItem(); source.setOeItemName("多图测试");
        source.setImageUrls(List.of("/b.png", "/a.png"));
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        new com.erp.common.core.utils.poi.ExcelUtil<>(InvOeItem.class).exportExcel(response, List.of(source), "图库");
        List<InvOeItem> imported = new com.erp.common.core.utils.poi.ExcelUtil<>(InvOeItem.class)
                .importExcel(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        assertThat(imported).hasSize(1);
        assertThat(imported.get(0).getImageUrls()).containsExactly("/b.png", "/a.png");
    }
    @Test void missingAndExplicitEmptyDiffer() throws Exception {
        InvGiftBox missing = json.readValue("{\"giftName\":\"name\"}", InvGiftBox.class);
        InvGiftBox cleared = json.readValue("{\"imageUrl\":\"/old.png\",\"imageUrls\":[]}", InvGiftBox.class);
        assertThat(missing.getImageUrlsText()).isNull();
        assertThat(cleared.getImageUrlsText()).isEqualTo("[]");
        assertThat(cleared.getImageUrl()).isEmpty();
        assertThat(cleared.getImageUrls()).isEmpty();
    }
    @Test void legacyCommaImagesRemainOrderedAndDangerousListIsRejected() throws Exception {
        InvOeItem legacy = new InvOeItem(); legacy.setImageUrl("/a.png,/b.png");
        assertThat(legacy.getImageUrls()).containsExactly("/a.png", "/b.png");
        assertThat(legacy.getImageUrl()).isEqualTo("/a.png");
        assertThatThrownBy(() -> legacy.setImageUrls(List.of("javascript:alert(1)"))).hasMessageContaining("图片地址");
        InvGiftBox ignored = json.readValue("{\"imageUrlsText\":\"[]\"}", InvGiftBox.class);
        assertThat(ignored.getImageUrlsText()).isNull();
    }
}
