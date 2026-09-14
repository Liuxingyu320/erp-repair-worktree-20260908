package com.erp.inventory.mapper;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.erp.inventory.domain.InvOeItem;
import com.erp.inventory.domain.InvGiftBox;
import com.erp.common.core.utils.file.ImageUrlList;
import static org.assertj.core.api.Assertions.*;

/** Run only against the separately created empty image-gallery verification database. */
@EnabledIfEnvironmentVariable(named = "IMAGE_GALLERY_TEST_JDBC_URL", matches = ".+codex_image_gallery_.+")
class ImageGalleryMySqlTest
{
    @Test void actualMappersRoundTripReorderClearAndPreserveStateOnlyUpdates() throws Exception
    {
        String url = System.getenv("IMAGE_GALLERY_TEST_JDBC_URL");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "root", "");
        Configuration config = new Configuration(new Environment("gallery-test", new JdbcTransactionFactory(), dataSource));
        config.getTypeAliasRegistry().registerAlias("InvOeItem", InvOeItem.class);
        config.getTypeAliasRegistry().registerAlias("InvGiftBox", InvGiftBox.class);
        for (String file : List.of("mapper/inventory/InvOeMapper.xml", "mapper/inventory/InvGiftMapper.xml"))
        {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(file))
            { new XMLMapperBuilder(input, config, file, config.getSqlFragments()).parse(); }
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(false))
        {
            InvOeMapper oe = session.getMapper(InvOeMapper.class);
            InvGiftMapper gift = session.getMapper(InvGiftMapper.class);
            InvOeItem item = new InvOeItem(); item.setOeItemName("gallery isolated test");
            item.setOeItemCode("GALLERY-TEST-OE"); item.setCategoryId(0L); item.setStatus("0");
            item.setImageUrls(List.of("/second.png", "/first.png"));
            assertThat(oe.insertInvOe(item)).isEqualTo(1);
            assertThat(oe.selectInvOeByIdForUpdate(item.getOeItemId()).getImageUrls()).containsExactly("/second.png", "/first.png");
            InvOeItem state = new InvOeItem(); state.setOeItemId(item.getOeItemId()); state.setStatus("1");
            oe.updateInvOe(state);
            assertThat(oe.selectInvOeById(item.getOeItemId()).getImageUrls()).containsExactly("/second.png", "/first.png");
            item.setImageUrls(List.of()); oe.updateInvOe(item);
            assertThat(oe.selectInvOeById(item.getOeItemId()).getImageUrl()).isEmpty();
            assertThat(oe.selectInvOeById(item.getOeItemId()).getImageUrls()).isEmpty();

            InvGiftBox box = new InvGiftBox(); box.setGiftName("gallery isolated test");
            box.setGiftCode("GALLERY-TEST-GIFT"); box.setCategoryId(0L); box.setStatus("0");
            box.setImageUrls(List.of("/a.png", "/b.png"));
            assertThat(gift.insertInvGift(box)).isEqualTo(1);
            InvGiftBox loaded = gift.selectInvGiftByIdForUpdate(box.getGiftId());
            assertThat(loaded.getImageUrl()).isEqualTo("/a.png");
            assertThatThrownBy(() -> ImageUrlList.prepare(null, "", loaded.getImageUrlsText(), loaded.getRawImageUrl()))
                    .hasMessageContaining("多张图片");
            box.setImageUrls(List.of("/b.png", "/a.png")); gift.updateInvGift(box);
            assertThat(gift.selectInvGiftById(box.getGiftId()).getImageUrls()).containsExactly("/b.png", "/a.png");
            InvGiftBox stateOnly = new InvGiftBox(); stateOnly.setGiftId(box.getGiftId()); stateOnly.setStatus("1");
            gift.updateInvGift(stateOnly);
            assertThat(gift.selectInvGiftById(box.getGiftId()).getImageUrls()).hasSize(2);
            box.setImageUrls(List.of()); gift.updateInvGift(box);
            assertThat(gift.selectInvGiftById(box.getGiftId()).getImageUrls()).isEmpty();
            session.rollback();
        }
    }
}
