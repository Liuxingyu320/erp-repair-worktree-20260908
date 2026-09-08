-- Generated from 供应链平台产品20260711更新版.xlsx
-- Source SHA-256: 4716cc71bca2319880a83d8dfdd942673a9003388173bb124bc61ca7547263bf
-- Source sheet: 供应链平台产品2026版; visible products: 158; hidden products soft-disabled: 9
-- This migration preserves product_id, product_code, stock, stock logs, and all historical business references.
SET NAMES utf8mb4;
USE `BossERP_NEW`;

DROP TEMPORARY TABLE IF EXISTS tmp_product_master_20260711;
CREATE TEMPORARY TABLE tmp_product_master_20260711 (
    source_row int NOT NULL PRIMARY KEY,
    category_name varchar(128) NOT NULL,
    product_name varchar(128) NOT NULL,
    grade varchar(32) NULL,
    spec varchar(256) NULL,
    product_description text NULL,
    unit varchar(32) NULL,
    reference_cost decimal(16,2) NULL,
    supplier_name varchar(128) NULL,
    supplier_phone varchar(32) NULL,
    retail_price decimal(16,2) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO tmp_product_master_20260711 VALUES
(4, '乌龙茶', '铁观音', '一级', '250g/罐', '精选原产于福建泉州市安溪县西坪镇，铁观音茶介于绿茶和红茶之间，属于半发酵茶类，铁观音独具“观音韵”，清香雅韵，冲泡后有天然的兰花香，滋味纯浓,香气馥郁持久，有“七泡有余香之誉 ”。', NULL, 29.30, '今世缘', '13950190015', 75.00),
(5, '乌龙茶', '大红袍', '一级', '125g/罐', '武夷山核心产区，大红袍生长在武夷山的丹霞地貌中，茶条索紧结壮实，色泽乌润油亮；冲泡后汤色橙黄明亮，香气馥郁持久，带有兰花香；滋味醇厚回甘，岩韵显著。', NULL, 33.30, '大齐茶业', '13305995232', 85.00),
(6, '乌龙茶', '大红袍', '一级', '125g/罐', '外形条索紧结，色泽绿褐鲜润，冲泡后汤色橙黄明亮，叶片红绿相间。有兰花香，香高而持久，“岩韵”明显。大红袍生长在武夷山脉的茶叶独领山川灵气，山间岩缝。', NULL, 92.80, '大齐茶业', '13305995232', 232.00),
(7, '乌龙茶', '大红袍', '一级', '125g/罐', '武夷山核心产区，岩韵独特，与优异的自然环境是分不开的。外形条索紧结，色泽绿褐鲜润，汤色橙黄明亮，叶片红绿相间，品质最突出之处是香气馥郁有兰花香，香高而持久，明显。', NULL, 272.80, '大齐茶业', '13305995232', 682.00),
(8, '乌龙茶', '水仙', '一级', '125g/罐', '闽北高山水仙茶，又名武夷水仙茶，现主产区为建瓯、建阳两县。成茶条索紧结沉重，叶端扭曲，色泽油润暗沙绿，呈"蜻蜓头，青蛙腿"状;香气浓郁，具兰花清香，滋味醇厚回甘，汤色清澈橙黄，叶底厚软黄亮，叶缘朱砂红边或红点，即"三红七青"。', NULL, 82.80, '大齐茶业', '13305995232', 210.00),
(9, '乌龙茶', '莲花峰老枞水仙', '一级', '125g/罐', '产地武夷山莲花峰，独特的自然气候优势，使茶叶内含物质更丰富。外形条索紧结，色泽绿褐鲜润，冲泡后汤色橙黄明亮，叶片红绿相间。香气馥郁有兰花香，香高而持久，"岩韵"明显。很耐冲泡，冲泡七、八次仍有香味。', NULL, 277.80, '大齐茶业', '13305995232', 695.00),
(10, '乌龙茶', '肉桂', '一级', '125g/罐', '肉桂的桂皮香明显，香气久泡犹存；入口醇厚而鲜爽，汤色澄黄清澈，叶底黄亮，条索紧结卷曲，色泽褐绿，油润有光。', NULL, 92.80, '大齐茶业', '13305995232', 232.00),
(11, '乌龙茶', '狮子峰肉桂', '一级', '125g/罐', '产于武夷山狮子峰，外形条索紧实，色泽乌润明亮，口感醇厚。由于当地独特的气候地理环境，形成了别具一格的岩韵品质特点，', NULL, 277.80, '大齐茶业', '13305995232', 695.00),
(12, '乌龙茶', '鸭屎香凤凰单丛', '一级', '125g/罐', '凤凰单枞“鸭屎香”已是闻名遐迩，它属于乌龙茶类，其特点是，成茶索硕大紧沉重，色乌褐油润，汤色金黄，自然花香气浓郁，味道甘醇，韵味独特，回甘力强，极耐冲泡。', NULL, 52.80, '凤凰单枞', '18948499239', 132.00),
(13, '红茶', '正山小种(花果香型)', '一级', '200g/罐', '正山小种红茶的鼻祖，干茶壮实紧结，色泽乌黑油润，泡水后汤色红浓，香气高长带花果香浓厚，滋味醇爽，桂圆味水底香明显。', NULL, 98.80, '武夷山探春茶业', '13305096066', 248.00),
(14, '红茶', '正山小种(甜香型)', '一级', '200g/罐', '外形条索肥实，色泽乌润，泡水后汤色红浓，香气高长带薯甜香，滋味醇厚，带有桂圆味。加入牛奶，茶香味不减，形成糖浆状奶茶，液色更为绚丽，备受青睐。', NULL, 57.20, '大齐茶业', '13305995232', 145.00),
(15, '红茶', '金骏眉(薯香型)', '一级', '250g/罐', '一年一采，且选用武夷山产区原料。外观上，干茶色泽独特均匀、金黄黑相间、乌中透黄、油润鲜活、有光泽、条索壮实紧结、夹杂着花果味，口感清甜顺滑。', NULL, 82.80, '武夷山探春茶业', '13305096066', 210.00),
(16, '红茶', '金骏眉(花果香型)', '一级', '250g/罐', '原料选用当地的菜茶芽头，经过多重工序制成。它具有汤色金黄、汤中带甘、甘里透香的特点。干茶香气纯正，有花果香，香气清新优雅、细腻。', NULL, 232.80, '武夷山探春茶业', '13305096066', 580.00),
(17, '红茶', '滇红工夫红茶', '一级', '200g/罐', '云南滇红茶的品质优良，色泽红润透亮，汤色橙黄明亮。冲泡后的茶汤香气四溢，带有明显的果香、花香或蜜香，有时还能闻到一丝丝木香或草本香。入口时，滇红茶的滋味醇厚且甘甜，回甘持久。', NULL, 30.80, '龙泉茶业', '13759450887', 78.00),
(18, '红茶', '祁红三级', '三级', '125g/袋', '安徽省黄山市祁门县一带。它以其独特的“祁门香”闻名于世，具有悠久的生产历史。传统祁门红茶外形紧细，色泽乌润，香气高雅，带有蜜糖香和花果香，汤色红艳明亮，滋味鲜爽甘醇，叶底嫩匀。在国际市场上被誉为“红茶皇后”', NULL, 13.20, '黄山毛峰', '18855932128', 35.00),
(19, '红茶', '桂花九曲红梅', '一级', '125g/罐', '选用杭州产区桂花及九曲红梅，进行窨制。不仅具有桂花的清幽香气，还兼具茶叶的醇和、甘醇。茶汤呈明亮通透的浅橙色，细看之下还带着丝丝白毫，刚入口便有一丝甘甜，桂花的清香，随着冲泡次数的增加，汤色变得愈发浓郁。', NULL, 55.00, '张胜杰13600548858', '13600548858', 138.00),
(20, '红茶', '功夫红茶（滇红古树茶）', '(特级)', '200g/袋', '选用古树茶园云南大叶种原料，保持着原始森林的生态环境，茶树主要以百年的古茶树为主。条索肥壮紧结，茶汤一入口就可以出甜味，山野味明显，甜味来得直接、来得迅速，并且持久。', NULL, 141.20, '三宁茶业', '15087844639', 360.00),
(21, '普洱茶', '普洱熟茶（散茶）', '四级', '200g/袋', '精选云南高山海拔大叶种原料，是由晒青毛茶（绿茶类）再经特殊工艺加工而成，滋味醇厚，独具陈香，红茶滋味甘醇、鲜爽，甜香馥郁。', NULL, 25.20, '三宁茶业', '15087844639', 65.00),
(22, '普洱茶', '普洱茶(熟茶)散茶', '一级', '200g/袋', '干茶外形条索紧结、清晰，色泽呈褐红；叶底发酵度较轻者呈红棕色，重发酵者呈深褐色或黑色居多；茶汤颜色呈深红色或褐红色，滋味浓厚醇和；具有特殊的陈香味。', NULL, 45.20, '六大茶山', '15288251909', 115.00),
(23, '普洱茶', '普洱茶(熟茶)散茶', '一级', '200g/袋', '产自云南省西双版纳勐海县，这里地处热带雨林气候区，山峦起伏，云雾缭绕，为茶树的生长提供了得天独厚的条件。勐海古树熟茶色泽呈红棕色，油润光泽，香气独有，具有陈香、荷香、蜜香。层次丰富令人陶醉。口感醇厚，入口润回味悠长。茶顺如丝般细腻，', NULL, 69.20, NULL, NULL, 175.00),
(24, '普洱茶', '贺开古树晒青茶', '(芽叶型)', '125g/袋', '选用云南勐海古树茶园原料，其特点汤色清亮、透彻，入口甘甜、清爽，回甘快且持久，香气独特浓郁，挂杯留香持久。此茶耐泡度高，冲至第十道，茶汤依然清甜可口。', NULL, 89.70, NULL, NULL, 225.00),
(25, '普洱茶', '云南大叶种晒青茶', '（二级）', '125g/袋', '由传统加工工艺制成的散茶。其特点是色泽墨绿偏黄，汤色清澈黄亮，闻之有清香味，入口后先苦涩而后回甘生津，喉头留有清凉之感。普洱生茶的这些味道特点主要来自于茶叶中的茶多酚、咖啡碱、儿茶素等成分。', NULL, 19.70, NULL, NULL, 49.00),
(26, '普洱茶', '云南古树普洱生茶', '三级', '6g*42片/罐（罐子单独送，不作销售单位）', '产至云南临沧凤庆县，分布海拔在1800-2200米古树茶园，树龄100年以上。口感特点香扬水柔，细腻甘甜，回甘绵长。', NULL, 138.88, '三宁茶业', '15087844639', 348.00),
(27, '普洱茶', '云南古树普洱熟茶', '三级', '6g*42片/罐（罐子单独送，不作销售单位）', '产至云南临沧凤庆县，分布海拔在1800-2200米古树茶园，树龄100年以上。口感特点甜润香高，汤感粘稠，耐泡持久回甘足。', NULL, 138.88, '三宁茶业', '15087844639', 348.00),
(28, '绿茶', '黄山毛峰（一级揉捻）', '一级', '125g/袋', '每年清明谷雨，选摘良种茶树“黄山种”、“黄山大叶种”等的初展肥壮嫩芽，手工炒制，该茶外形微卷，状似雀舌，绿中泛黄。入杯冲泡雾气结顶，汤色清碧微黄，叶底黄绿有活力，滋味醇甘，香气如兰，韵味深长。制成的毛峰茶外形细扁微曲，状如雀舌，香如白兰，味醇回甘。', NULL, 16.60, '黄山毛峰', '18855932128', 45.00),
(29, '绿茶', '西湖龙井', '特级', '125g/罐', '核心产区，带标', NULL, 372.20, '张胜杰', '13600548858', 950.00),
(30, '绿茶', '西湖龙井', '一级', '125g/罐', '核心产区，带标', NULL, 77.20, '张胜杰', '13600548858', 195.00),
(31, '绿茶', '明前龙井', '特级', '125g/罐', '选用浙江钱塘产区明前龙井43原料，精选芽头原料,外形扁平光滑，口感具有香气清高鲜爽，滋味甘甜，茶汤黄绿透亮，鲜爽度高。滋味带花香回甘持久生津。叶底柔嫩匀称。', NULL, 157.20, '张胜杰', '13600548858', 390.00),
(32, '绿茶', '明前龙井', '一级', '125g/罐', '选用浙江钱塘产区明前龙井43原料，精选芽头原料,外形扁平光滑，口感具有香气清高鲜爽，滋味甘甜，茶汤黄绿透亮，鲜爽度高。滋味带花香回甘持久生津。叶底柔嫩匀称。', NULL, 77.20, '张胜杰', '13600548858', 195.00),
(33, '绿茶', '明前龙井(迎客茶)', '二级', '125g/罐', '浙江钱塘产区，外形扁平光滑，绿中微带黄色，茶香的纯正度香气浓郁，带有类似兰花豆的香味，并夹杂着一丝蜂蜜的甜香。在冲泡时，尤其是续水后，香气依然扑鼻而来。', NULL, 37.20, '张胜杰', '13600548858', 95.00),
(34, '绿茶', '碧螺春', '一级', '250g/罐', '外形条索纤细、卷曲如螺，满身披毫，色泽银白隐翠。冲泡后，茶汤碧绿清澈，香气浓郁，滋味鲜醇甘厚，享有“一嫩（芽叶）三鲜”（色、香、味）之称。', NULL, 122.80, '孟露豪', '15268798518', 310.00),
(35, '绿茶', '碧螺春(普)', '一级', '250g/罐', '外形条索纤细、卷曲如螺，满身披毫，色泽银白隐翠。冲泡后，茶汤碧绿清澈，香气浓郁，滋味鲜醇甘厚，享有“一嫩（芽叶）三鲜”（色、香、味）之称。', NULL, 87.80, '孟露豪', '15268798518', 220.00),
(36, '绿茶', '碧螺春', '特级', '250g/罐', '外形条索纤细、卷曲如螺，满身披毫，色泽银白隐翠。冲泡后，茶汤碧绿清澈，香气浓郁，滋味鲜醇甘厚，享有“一嫩（芽叶）三鲜”（色、香、味）之称。', NULL, 182.80, '孟露豪', '15268798518', 460.00),
(37, '绿茶', '安吉白茶', '特级', '125g*2罐/盒', '安吉白茶外形形似凤羽，色泽翠绿间黄，光亮油润，香气清鲜持久，滋味鲜醇，汤色清澈明亮，叶底芽叶细嫩成朵，叶白脉翠，氨基酸含量高于普通绿茶数倍。', NULL, 317.20, '芳女士', '13521728928', 790.00),
(38, '绿茶', '安吉白茶', '特级', '125g*2罐/袋', '安吉白茶外形形似凤羽，色泽翠绿间黄，光亮油润，香气清鲜持久，滋味鲜醇，汤色清澈明亮，叶底芽叶细嫩成朵，叶白脉翠，氨基酸含量高于普通绿茶数倍。', NULL, 277.20, NULL, NULL, 690.00),
(39, '白茶', '白毫银针', '一级', '125g/袋', '产地位于中国福建省的福鼎市。挺直似针，满披白毫，如银似雪。由于鲜叶原料全部是茶芽，白毫银针制成成品茶后，形状似针，白毫密被，色白如银，因此命名为白毫银针。冲泡后，香气毫香显，清鲜滋味醇和爽口。', NULL, 187.20, '玉芷芽', '18859384008', 468.00),
(40, '白茶', '2017福鼎白茶', '一级', '350g/饼', '产地福建省福鼎市，选用2017年寿眉原料，属于微发酵茶。饼型圆润匀称，口感是稠厚的、柔和的、饱满的，喝一口，能够感觉到茶汤是顺滑的。', NULL, 133.20, '寻桑茶业', '18505931115', 350.00),
(41, '白茶', '2017福鼎白茶', '一级', '5g*50片/罐', '产地福建省福鼎市，选用2017年寿眉原料，属于微发酵茶。饼型圆润匀称，口感是稠厚的、柔和的、饱满的，喝一口，能够感觉到茶汤是顺滑的。', NULL, 97.20, '寻桑茶业', '18505931115', 245.00),
(42, '白茶', '2020寿眉', '一级', '100g/袋', '产地福建省福鼎市，选用2020年白露寿眉原料，一芽三四叶标准采制。外形弯曲如眉，色泽乌绿，干茶包覆有细毫，汤色黄绿明亮，滋味鲜爽甘甜，茶叶持久耐泡。', NULL, 15.20, '玉芷芽', '18859384008', 38.00),
(43, '白茶', '2017春寿眉', '一级', '100g/袋', '产地福建省福鼎市，选用2017年春寿眉原料，经过3年以上陈化，逐渐出现陈香、药香、枣香等独特香气，茶汤转为橙黄色、深橙黄色甚至琥珀色，口感更加绵柔醇厚。', NULL, 45.20, '寻桑茶业', '18505931115', 115.00),
(44, '花草', '小青柑皮普洱茶', '－', '250g/袋', '选用每年7-8月采摘的未完全成熟的新会柑，清香果香与普洱陈香交织，柑味浓郁。茶汤细腻顺滑，耐泡度高。', NULL, 64.16, '鸿柑堡', '13824058271', 160.00),
(45, '花草', '陈皮', '－', '250g/袋', '自然生晒，先烘干不烟熏。全程晒足阳光，保留自然本性和内部营养。陈皮香气足，茶汤金黄通透，滋味陈皮味显，带回甘。', NULL, 93.20, '鸿柑堡', '13824058271', NULL),
(46, '花草', '玫瑰花', '－', '125g/袋', '手选玫瑰花，新鲜采收，植物草本，花瓣饱满内敛，花托翠绿，低温微波烘干，玫瑰花香馥郁持久，口感清爽醇和。', NULL, 36.80, '黄山毛峰', '18855932128', NULL),
(47, '花草', '金丝皇菊', '－', '125g/袋', '高山种植，头采鲜花，大而饱满，每一朵严选饱满厚实，颜色金黄。滋味醇香甘甜。', NULL, 20.50, '黄山毛峰', '18855932128', NULL),
(48, '花草', '胎菊', '－', '125g/袋', '精选初采胎菊，精挑细选，朵大饱满，花托翠绿，花瓣整齐，菊香馥郁，滋味鲜爽回甘。', NULL, 22.80, '黄山毛峰', '18855932128', NULL),
(49, '花草', '枸杞', '－', '250g/袋', '新鲜采收，自然晾晒，颗粒大饱满，枸杞果香味浓，汤色橙亮。滋补营养。', NULL, 32.40, '黄山毛峰', '18855932128', NULL),
(50, '花茶', '茉莉花茶（统芽飘雪）', '特级', '250g/罐', '选自广西横县茉莉花与高山绿茶胚多次窨制，条索细扁卷曲，白毫显露，芽肥壮匀整，汤色清黄透亮，花香馥郁持久，味醇回甘，韵味悠长。', NULL, 139.30, '万山茶业', '15289550715', 348.00),
(51, '花茶', '茉莉花茶（炒花飘雪）', '一级', '250g/罐', '选自广西横县，外形匀整紧秀，白毫显露，茉莉花微黄，汤色黄绿透亮，香气花香高长，味甘持久。', NULL, 44.80, '世纪浩晟', '13807870818', 112.00),
(67, '五谷饮品', '黑米核桃', NULL, '162g/包', '谷物醇香浓郁，口感绵柔顺滑，香糯醇厚、回甘自然，不甜腻', '包', 5.32, '杭州正谦食品有限公司3.8', NULL, 6.84),
(68, '五谷饮品', '奶香玉米', NULL, '130g/包', '口感绵密细腻、香甜醇厚，玉米本香融合浓郁奶香，入口顺滑不腻，清甜自然', '包', 6.72, '杭州正谦食品有限公司4.8', NULL, 8.64);
INSERT INTO tmp_product_master_20260711 VALUES
(69, '五谷饮品', '山药红枣', NULL, '150g/包', '口感绵柔温润、枣香浓郁，清甜不腻，醇厚顺滑，自带天然枣香与山药甘润口感。', '包', 5.32, '杭州正谦食品有限公司3.8', NULL, 6.84),
(70, '五谷饮品', '椰香紫薯', NULL, '145g/包', '色泽柔紫诱人，入口绵密丝滑，紫薯醇香糅合淡雅椰香，香甜醇厚不腻口，口感顺滑软糯，回味悠长。', '包', 7.84, '坤富现榨饮品热饮批发5.6', NULL, 10.08),
(71, '五谷饮品', '马蹄雪莲', NULL, '300g/包', '茶汤清透淡雅，入口清甜脆爽、甘润回甘，马蹄鲜醇配雪莲清润，清冽不寒凉、甘甜不腻喉。', '包', 11.90, '坤富现榨饮品热饮批发8.5', NULL, 15.30),
(72, '五谷饮品', '小米红枣枸杞', NULL, '145g/包', '口感绵柔细腻、枣香浓郁、清甜温润，谷物醇香融合红枣回甘，入口顺滑不腻，温和养胃好吸收。', '包', 7.84, '坤富现榨饮品热饮批发5.6', NULL, 10.08),
(73, '五谷饮品', '黑米红豆', NULL, '145g/包', '口感绵密醇厚、谷物香浓，入口顺滑温润，自带天然谷物清甜，香而不腻，回味悠长。', '包', 7.84, '坤富现榨饮品热饮批发5.6', NULL, 10.08),
(74, '五谷饮品', '芒果汁', NULL, '145g/包', '色泽金黄透亮，果香浓郁醇厚，入口绵密顺滑，天然鲜甜无酸涩，果肉感十足，满口热带风情。', '包', 11.90, '坤富现榨饮品热饮批发8.5', NULL, 15.30),
(75, '五谷饮品', '椰汁桃胶', NULL, '145g/包', '汤色温润奶白，椰香浓郁醇厚，桃胶软糯Q弹，口感丝滑绵润，入口清甜不腻，嚼劲十足。', '包', 11.90, '坤富现榨饮品热饮批发8.5', NULL, 15.30),
(76, '熬煮/冷泡/草本茶', '姜糖五味茶', NULL, '24g*10包/袋*4袋', '入口姜香醇厚、甜而不腻，暖感顺着喉咙直达周身，温润驱寒、暖宫暖胃', '箱', 166.40, '杭州塞纳茶叶有限公司104', NULL, 416.00),
(77, '熬煮/冷泡/草本茶', '雪梨罗汉饮', NULL, '10g*10包/袋*4袋', '茶汤清透温润，入口清甜绵柔、甘润回甘，梨香融合罗汉果香，清润不寒凉、甘甜不腻口。', '箱', 83.20, '杭州塞纳茶叶有限公司52', NULL, 208.00),
(78, '熬煮/冷泡/草本茶', '桂香金桔茶', NULL, '10g*10包/袋*4袋', '茶汤清亮金黄，入口金桔酸爽回甘、桂花香气清雅，果香花香交织，酸甜适口，温润不腻。', '箱', 76.80, '杭州塞纳茶叶有限公司48', NULL, 192.00),
(79, '熬煮/冷泡/草本茶', '京味桂花酸梅汤', NULL, '95g*30包', '慢熬出味，酸香醇厚、清甜回甘，口感正宗不涩、酸甜恰到好处。', '箱', 244.80, '杭州塞纳茶叶有限公司153', NULL, 612.00),
(80, '熬煮/冷泡/草本茶', '金桔玫瑰花茶', NULL, '51g*30包', '茶汤色泽明艳透亮，金桔酸甜清爽，玫瑰花香馥郁，果香与花香完美交融，入口甘润回甘', '箱', 163.20, '杭州塞纳茶叶有限公司102', NULL, 408.00),
(81, '熬煮/冷泡/草本茶', '玫瑰雪梨茶', NULL, '5.3g*10包/袋*4袋', '茶汤通透柔和，梨香清甜淡雅，玫瑰香气温婉沁鼻，花香融合果香，清甜回甘不腻口', '箱', 163.20, '杭州塞纳茶叶有限公司102', NULL, 408.00),
(82, '熬煮/冷泡/草本茶', '苹果肉桂红茶', NULL, '17g*30包', '肉桂温润辛香、红茶醇厚回甘，香气层次丰富，入口柔滑香甜，暖而不腻。', '箱', 195.20, '杭州塞纳茶叶有限公司122', NULL, 488.00),
(83, '熬煮/冷泡/草本茶', '乌梅金桂', NULL, '130g/包*30包', '慢熬出味，酸香醇厚、清甜回甘，口感正宗不涩、酸甜恰到好处。', '箱', 241.92, '杭州塞纳茶叶有限公司151.2', NULL, 604.80),
(84, '熬煮/冷泡/草本茶', '洛神紫桑茶', NULL, '10g*10包/袋*4袋', '酸甜清爽，玫瑰香气馥郁，入口顺滑，回甘生津。', '箱', 60.80, '杭州塞纳茶叶有限公司38', NULL, 152.00),
(85, '熬煮/冷泡/草本茶', '抹茶黑豆玄米茶', NULL, '5g*10包/袋*4袋', '茶汤色泽温润，入口米香醇厚、抹茶清鲜，口感绵柔顺滑，微苦回甘，清爽不厚重。', '箱', 60.80, '杭州塞纳茶叶有限公司38', NULL, 152.00),
(86, '熬煮/冷泡/草本茶', '桂花乌龙茶', NULL, '3g*10包/袋*4袋', '茶汤金黄透亮，入口乌龙醇厚绵滑，桂香清雅沁鼻，花香茶香层层交融，甘润顺滑，耐泡留香。', '箱', 76.80, '杭州塞纳茶叶有限公司48', NULL, 192.00),
(87, '熬煮/冷泡/草本茶', '百合杏仁银耳', NULL, '75g*30包', '茶汤温润柔和，口感绵润清甜，自然果香淡雅纯粹，清润不腻、温和滋养。', '箱', 276.80, '杭州塞纳茶叶有限公司174', NULL, 692.00),
(88, '熬煮/冷泡/草本茶', '无花果雪梨饮', NULL, '100g*30包', '茶汤清透温润，入口清甜绵柔、甘润回甘', '箱', 336.00, '杭州塞纳茶叶有限公司210', NULL, 840.00),
(89, '熬煮/冷泡/草本茶', '竹蔗茅根', NULL, '95g*30包', '汤色清亮温润，入口清甜甘爽，自带天然草本蔗香，清甜不腻、润而不寒。', '箱', 211.20, '杭州塞纳茶叶有限公司132', NULL, 528.00),
(90, '熬煮配料', '乌梅干', NULL, '500g', '乌梅金桂熬煮原材料', '袋', 16.63, NULL, NULL, NULL),
(91, '熬煮配料', '山楂干', NULL, '500g', '乌梅金桂熬煮原材料', '袋', 16.77, NULL, NULL, NULL),
(92, '熬煮配料', '桑葚干', NULL, '500g', '乌梅金桂熬煮原材料', '袋', 16.77, NULL, NULL, NULL),
(93, '熬煮配料', '甘草片', NULL, '500g', '乌梅金桂熬煮原材料', '袋', 22.34, NULL, NULL, NULL),
(94, '熬煮配料', '红枣干', NULL, '500g', NULL, NULL, NULL, NULL, NULL, NULL),
(98, '蜂蜜/果浆/糖', '蜂蜜', NULL, '1kg/瓶', '特调饮品专用', '瓶', 24.92, '百峰百旗舰店', NULL, NULL),
(99, '蜂蜜/果浆/糖', '百香果原浆瓶装', NULL, '2斤/瓶', '金桔柠檬百香果原料', '瓶', 25.90, '嘉优鲜果园', NULL, NULL),
(100, '蜂蜜/果浆/糖', '鲜活百香果酱罐子装', NULL, '1.2kg/罐', '金桔柠檬百香果原料', '罐', 36.12, '上海珑铭奶茶原料零售批发', NULL, NULL),
(101, '蜂蜜/果浆/糖', '芭乐果酱', NULL, '1.36kg', '特调抹茶二重奏原料', '桶', 47.60, '新仙尼旗舰店', NULL, NULL),
(102, '蜂蜜/果浆/糖', '蔓越莓果酱', NULL, '1.36kg', '特调蔓越莓荔枝原料', '桶', 49.00, '新仙尼旗舰店', NULL, NULL),
(103, '蜂蜜/果浆/糖', '果糖糖浆', NULL, '2.5kg', '特调饮品专用', '桶', 30.80, '广禧食品旗舰店', NULL, NULL),
(104, '蜂蜜/果浆/糖', '蜂蜜柚子酱', NULL, '1.3kg', '蜂蜜柚子气泡水原料', '桶', 42.00, '广禧食品旗舰店', NULL, NULL),
(105, '蜂蜜/果浆/糖', '荔枝糖浆', NULL, '1L', '特调鸡尾酒原料', '桶', 42.00, '广禧食品旗舰店', NULL, NULL),
(106, '蜂蜜/果浆/糖', '龙吟轩杨梅果酱', NULL, '1kg', '青提杨梅气泡水原料', '袋', 21.00, '龙吟轩冲饮专营店', NULL, NULL),
(107, '蜂蜜/果浆/糖', '甘蔗糖浆', NULL, '1kg', '桂花乌龙醉鸡尾酒原料', '瓶', 36.68, '驿茶站', NULL, NULL),
(108, '蜂蜜/果浆/糖', '桂花糖浆', NULL, '1kg', '桂花乌龙醉鸡尾酒原料', '瓶', 30.80, '富达餐饮咖啡奶茶原料批发', NULL, NULL),
(109, '蜂蜜/果浆/糖', '红柚浓缩果汁', NULL, '1.2kg', '柚香茉莉气泡水原料', '瓶', 35.00, '富达餐饮咖啡奶茶原料批发', NULL, NULL),
(110, '蜂蜜/果浆/糖', '青瓜浓缩果汁', NULL, '1.1kg', '龙井青瓜原料', '瓶', 33.60, '顺时针食品专营店', NULL, NULL),
(111, '蜂蜜/果浆/糖', '青梅浓缩汁', NULL, '1.2kg', '青梅气泡水原料', '瓶', 35.00, '蜜粉儿奶茶店', NULL, NULL),
(112, '蜂蜜/果浆/糖', '凤梨糖浆', NULL, '2kg', NULL, '桶', 33.60, '广禧食品旗舰店', NULL, NULL),
(113, '蜂蜜/果浆/糖', '冰糖(9斤免运费)', NULL, '1-9斤', '乌梅金贵熬煮原料', '9斤', 60.00, '黑糖工厂', NULL, NULL),
(114, '饮品', '清酒', NULL, '750ml*2', '栀子荔枝鸡尾酒原料', '瓶', 45.22, '芳芳小酒铺', NULL, NULL),
(115, '饮品', '美汁源青提葡萄汁', NULL, '450ml*12瓶', '特调翠玉兰雾原料', '提', 43.40, '耀珩饮料专营店', NULL, NULL),
(116, '饮品', '蒙牛乳酸菌', NULL, '100ml*20瓶', NULL, '提', 33.60, '蒙牛品牌正品店', NULL, NULL),
(117, '饮品', '纯水乐苏打水', NULL, '410ml*12瓶', NULL, '提', 25.20, '百事可乐品牌正品店铺', NULL, NULL),
(118, '饮品', '椰子水', NULL, '250ml*20', NULL, '盒', 37.66, '云间小饮店', NULL, NULL),
(119, '饮品', '怡泉干姜水', NULL, '330ml*24罐', NULL, '提', 74.20, '中粮可口可乐旗舰店', NULL, NULL),
(120, '饮品', '莓莓桃桃', NULL, '900ml', NULL, '瓶', 59.50, '奇旅食品专营店', NULL, NULL),
(121, '饮品', '屈臣氏青柠汁', NULL, '750ml', NULL, '瓶', 17.92, '屈臣氏', NULL, NULL);
INSERT INTO tmp_product_master_20260711 VALUES
(122, '饮品', '威士忌', NULL, '700ml*2', NULL, '瓶', 40.88, '百瑞卡旗舰店', NULL, NULL),
(123, '罐头', '杨梅罐头', NULL, '300g*6', NULL, '罐', 102.20, '家家红水果罐头官方旗舰店', NULL, NULL),
(124, '罐头', '荔枝罐头', NULL, '300g*6', NULL, '罐', 102.20, '家家红水果罐头官方旗舰店', NULL, NULL),
(125, '花', '干桂花', NULL, '120g', NULL, '袋', 43.82, '沁园春堂', NULL, NULL),
(126, '花', '盐渍樱花', NULL, '100g', '特调春之樱原料', '袋', 24.92, '小哥花茶铺', NULL, NULL),
(127, '花', '清香木', NULL, '100片', NULL, '盒', 19.60, '鲜宝惠西餐食材配送', NULL, NULL),
(129, '水果', '橙子', NULL, NULL, '按照线上指导价格，由店长在当地采购。若价格高于指导价格，请联系李建伟采购', '斤', 3.00, NULL, NULL, NULL),
(130, '水果', '西瓜', NULL, NULL, '按照线上指导价格，由店长在当地采购。若价格高于指导价格，请联系李建伟采购', '个', 2.50, NULL, NULL, NULL),
(131, '水果', '雪梨', NULL, NULL, '按照线上指导价格，由店长在当地采购。若价格高于指导价格，请联系李建伟采购', '斤', 4.00, NULL, NULL, NULL),
(132, '水果', '金桔', NULL, NULL, '按照线上指导价格，由店长在当地采购。若价格高于指导价格，请联系李建伟采购', '斤', 5.80, NULL, NULL, NULL),
(133, '水果', '柠檬/青柠', NULL, NULL, '按照线上指导价格，由店长在当地采购。若价格高于指导价格，请联系李建伟采购', '斤', 6.90, NULL, NULL, NULL),
(134, '水果', '百香果', NULL, '70-80g/个', '按照线上指导价格，由店长在当地采购。若价格高于指导价格，请联系李建伟采购', '3斤', 30.00, '纯香果旗舰店', NULL, NULL),
(137, '糕点/饼干/果干', '绿豆糕', NULL, '500g*2', NULL, '箱', 51.52, '朱小二旗舰店', NULL, NULL),
(138, '糕点/饼干/果干', '桂花糕', NULL, '250g*2', NULL, '箱', 43.40, '古磨坊旗舰店', NULL, NULL),
(139, '糕点/饼干/果干', '西湖四宝糕点', NULL, '4个/盒', NULL, '盒', 15.12, '品土居食品专营店10.8', NULL, NULL),
(140, '糕点/饼干/果干', '知味观茶点', NULL, '180g/盒 *4款', NULL, '盒', 91.00, '知味观官方旗舰店', NULL, NULL),
(141, '糕点/饼干/果干', '蔓越莓饼干', NULL, '400g/箱', NULL, '箱', 13.86, '比比赞旗舰店9.9', NULL, NULL),
(142, '糕点/饼干/果干', '一品蛋酥', NULL, '6盒/箱', NULL, '箱', 180.60, '鲜有道餐饮食材', NULL, NULL),
(143, '糕点/饼干/果干', '桃花酥', NULL, '500g', NULL, '箱', 43.40, '舟朋聚旗舰店30.88', NULL, NULL),
(144, '糕点/饼干/果干', '一口酥', NULL, '500g', NULL, '箱', 38.36, '浩海通源食品专营店27.4', NULL, NULL),
(145, '糕点/饼干/果干', '马大姐小麻花', NULL, '500克', NULL, '包', 24.50, '北京特产京味儿零食17.5', NULL, NULL),
(146, '糕点/饼干/果干', '芒果干', NULL, '500g', NULL, '袋', 28.00, '比比赞旗舰店19.9', NULL, NULL),
(147, '糕点/饼干/果干', '无核红杏干', NULL, '60克*4袋', NULL, '袋', 18.20, '比比赞旗舰店12.9', NULL, NULL),
(148, '糕点/饼干/果干', '香蕉片', NULL, '200g', NULL, '袋', 8.40, '比比赞旗舰店5.9', NULL, NULL),
(149, '糕点/饼干/果干', '苹果干', NULL, '500g', NULL, '袋', 27.86, '沁园春堂', NULL, NULL),
(150, '糕点/饼干/果干', '干橙片', NULL, '500g', NULL, '袋', 26.59, '沁园春堂', NULL, NULL),
(151, '糕点/饼干/果干', '凤梨干', NULL, '500g', NULL, '袋', 44.77, '沁园春堂', NULL, NULL),
(152, '糕点/饼干/果干', '火龙果干', NULL, '500g', NULL, '袋', 29.37, '沁园春堂', NULL, NULL),
(153, '糕点/饼干/果干', '比比赞空心山楂', NULL, '60g约11个', NULL, '袋', 7.00, '比比赞旗舰店4.68', NULL, NULL),
(154, '糕点/饼干/果干', '比比赞苹果干', NULL, '60g', NULL, '袋', 8.40, '比比赞旗舰店5.9', NULL, NULL),
(155, '糕点/饼干/果干', '比比赞杨梅干', NULL, '80g', NULL, '袋', 11.20, '比比赞旗舰店7.9', NULL, NULL),
(156, '糕点/饼干/果干', '比比赞红杏干', NULL, '60g', NULL, '袋', 18.20, '比比赞旗舰店12.9', NULL, NULL),
(157, '糕点/饼干/果干', '比比赞加州西梅', NULL, '80g', NULL, '袋', 21.00, '比比赞旗舰店14.9', NULL, NULL),
(158, '小零食', '混合坚果', NULL, '500g', NULL, '袋', 36.40, '每果时光旗舰店25.9', NULL, NULL),
(159, '小零食', '花生', NULL, '500g', NULL, '袋', 14.00, '比比赞旗舰店9.9', NULL, NULL),
(160, '小零食', '瓜子', NULL, '500g', NULL, '袋', 15.12, '三千粒食品旗舰店', NULL, NULL),
(161, '小零食', '开心果', NULL, '250g', NULL, '袋', 37.52, '比比赞旗舰店', NULL, NULL),
(162, '小零食', '开心果', NULL, '500g', NULL, '袋', 70.00, '比比赞旗舰店', NULL, NULL),
(163, '小零食', '松子仁', NULL, '250g', NULL, '袋', 50.40, '呈雪醇旗舰店35.5', NULL, NULL),
(164, '小零食', '怪味胡豆', NULL, '500g', NULL, '袋', 19.60, '老香农旗舰店13.8', NULL, NULL),
(165, '小零食', '山楂条', NULL, '500g', NULL, '袋', 11.20, '比比赞旗舰店7.9', NULL, NULL),
(166, '小零食', '腰果', NULL, '250g', NULL, '袋', 43.40, '比比赞旗舰店30.9', NULL, NULL),
(169, '茶相关', '点茶粉', NULL, '100g', '宋代点茶(白 红 绿 乌龙茶粉）', '包', 13.16, NULL, NULL, NULL),
(170, '茶相关', '点茶粉', NULL, '250g', NULL, '包', 18.48, NULL, NULL, NULL),
(171, '茶相关', '点茶粉', NULL, '500g', NULL, '包', 32.20, NULL, NULL, NULL),
(172, '茶相关', '茶泡袋', NULL, '9*10CM/200片', '煮茶专业', '包', 18.90, '朋意旗舰店', NULL, NULL),
(173, '清洁类', '洗洁精', NULL, '1kg', '清洗茶具/擦杯', '瓶', 15.26, NULL, NULL, NULL),
(174, '清洁类', '百洁布', NULL, NULL, '清洗茶具/擦杯', '个', 0.81, NULL, NULL, NULL),
(175, '清洁类', '纳米海绵', NULL, '40片/包', '清洗茶具/擦杯', '包', 8.57, NULL, NULL, NULL),
(176, '清洁类', '去渍粉', NULL, '1kg', '清洗浸泡茶具', '桶', 21.84, '翠翠儿清洁用品店', NULL, NULL);
INSERT INTO tmp_product_master_20260711 VALUES
(177, '文具', '标签贴', NULL, '50/100个', '开封/储存标识', '个', 14.00, 'gllabel旗舰店', NULL, NULL),
(178, '文具', '笔记本', NULL, NULL, '记录每天业绩', '本', 1.19, NULL, NULL, NULL),
(179, '文具', '固体胶', NULL, '6支/盒', NULL, '支', 1.19, NULL, NULL, NULL),
(180, '文具', '笔/笔芯', NULL, NULL, NULL, '盒', 0.28, NULL, NULL, NULL),
(181, '其他', '蜡烛', NULL, '100粒/箱', '夏天2小时', '箱', 20.30, '品润酥油灯', NULL, NULL),
(182, '其他', '蜡烛', NULL, '100粒/箱', '冬天4小时', '箱', 27.30, '品润酥油灯', NULL, NULL),
(183, '其他', '吸管', NULL, '100/200支', '原色可降解纸 细8*230', '支', 15.12, NULL, NULL, NULL),
(184, '其他', '吸管', NULL, '100/200支', '原色可降解纸 粗12*200', '支', 17.22, NULL, NULL, NULL);

DROP TEMPORARY TABLE IF EXISTS tmp_product_hidden_20260711;
CREATE TEMPORARY TABLE tmp_product_hidden_20260711 (source_row int NOT NULL PRIMARY KEY);
INSERT INTO tmp_product_hidden_20260711 VALUES
(52),
(53),
(54),
(55),
(59),
(60),
(61),
(62),
(63);

DROP TEMPORARY TABLE IF EXISTS tmp_product_category_20260711;
CREATE TEMPORARY TABLE tmp_product_category_20260711 (category_name varchar(128) NOT NULL PRIMARY KEY)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO tmp_product_category_20260711 VALUES
('乌龙茶'),
('五谷饮品'),
('其他'),
('小零食'),
('文具'),
('普洱茶'),
('水果'),
('清洁类'),
('熬煮/冷泡/草本茶'),
('熬煮配料'),
('白茶'),
('糕点/饼干/果干'),
('红茶'),
('绿茶'),
('罐头'),
('花'),
('花茶'),
('花草'),
('茶相关'),
('蜂蜜/果浆/糖'),
('饮品');

DROP TEMPORARY TABLE IF EXISTS tmp_product_supplier_20260711;
CREATE TEMPORARY TABLE tmp_product_supplier_20260711 (
    supplier_name varchar(128) NOT NULL PRIMARY KEY,
    previous_name varchar(128) NULL,
    contact_phone varchar(32) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO tmp_product_supplier_20260711 VALUES
('gllabel旗舰店', NULL, NULL),
('万山茶业', NULL, '15289550715'),
('三千粒食品旗舰店', NULL, NULL),
('三宁茶业', NULL, '15087844639'),
('上海珑铭奶茶原料零售批发', NULL, NULL),
('世纪浩晟', NULL, '13807870818'),
('中粮可口可乐旗舰店', NULL, NULL),
('云间小饮店', NULL, NULL),
('今世缘', NULL, '13950190015'),
('六大茶山', NULL, '15288251909'),
('凤凰单枞', NULL, '18948499239'),
('北京特产京味儿零食17.5', NULL, NULL),
('古磨坊旗舰店', NULL, NULL),
('呈雪醇旗舰店35.5', NULL, NULL),
('品土居食品专营店10.8', NULL, NULL),
('品润酥油灯', NULL, NULL),
('嘉优鲜果园', NULL, NULL),
('坤富现榨饮品热饮批发5.6', NULL, NULL),
('坤富现榨饮品热饮批发8.5', NULL, NULL),
('大齐茶业', NULL, '13305995232'),
('奇旅食品专营店', NULL, NULL),
('孟露豪', NULL, '15268798518'),
('家家红水果罐头官方旗舰店', NULL, NULL),
('富达餐饮咖啡奶茶原料批发', NULL, NULL),
('寻桑茶业', NULL, '18505931115'),
('小哥花茶铺', NULL, NULL),
('屈臣氏', NULL, NULL),
('广禧食品旗舰店', NULL, NULL),
('张胜杰', NULL, '13600548858'),
('张胜杰13600548858', NULL, '13600548858'),
('新仙尼旗舰店', NULL, NULL),
('朋意旗舰店', NULL, NULL),
('朱小二旗舰店', NULL, NULL),
('杭州塞纳茶叶有限公司102', NULL, NULL),
('杭州塞纳茶叶有限公司104', NULL, NULL),
('杭州塞纳茶叶有限公司122', NULL, NULL),
('杭州塞纳茶叶有限公司132', '杭州塞纳茶叶有限公司32', NULL),
('杭州塞纳茶叶有限公司151.2', NULL, NULL),
('杭州塞纳茶叶有限公司153', NULL, NULL),
('杭州塞纳茶叶有限公司174', NULL, NULL),
('杭州塞纳茶叶有限公司210', NULL, NULL),
('杭州塞纳茶叶有限公司38', NULL, NULL),
('杭州塞纳茶叶有限公司48', NULL, NULL),
('杭州塞纳茶叶有限公司52', NULL, NULL),
('杭州正谦食品有限公司3.8', NULL, NULL),
('杭州正谦食品有限公司4.8', NULL, NULL),
('武夷山探春茶业', NULL, '13305096066'),
('每果时光旗舰店25.9', NULL, NULL),
('比比赞旗舰店', NULL, NULL),
('比比赞旗舰店12.9', NULL, NULL);
INSERT INTO tmp_product_supplier_20260711 VALUES
('比比赞旗舰店14.9', NULL, NULL),
('比比赞旗舰店19.9', NULL, NULL),
('比比赞旗舰店30.9', NULL, NULL),
('比比赞旗舰店4.68', NULL, NULL),
('比比赞旗舰店5.9', NULL, NULL),
('比比赞旗舰店7.9', NULL, NULL),
('比比赞旗舰店9.9', NULL, NULL),
('沁园春堂', NULL, NULL),
('浩海通源食品专营店27.4', NULL, NULL),
('玉芷芽', NULL, '18859384008'),
('百事可乐品牌正品店铺', NULL, NULL),
('百峰百旗舰店', NULL, NULL),
('百瑞卡旗舰店', NULL, NULL),
('知味观官方旗舰店', NULL, NULL),
('纯香果旗舰店', NULL, NULL),
('翠翠儿清洁用品店', NULL, NULL),
('耀珩饮料专营店', NULL, NULL),
('老香农旗舰店13.8', NULL, NULL),
('舟朋聚旗舰店30.88', NULL, NULL),
('芳女士', NULL, '13521728928'),
('芳芳小酒铺', NULL, NULL),
('蒙牛品牌正品店', NULL, NULL),
('蜜粉儿奶茶店', NULL, NULL),
('顺时针食品专营店', NULL, NULL),
('驿茶站', NULL, NULL),
('鲜宝惠西餐食材配送', NULL, NULL),
('鲜有道餐饮食材', NULL, NULL),
('鸿柑堡', NULL, '13824058271'),
('黄山毛峰', NULL, '18855932128'),
('黑糖工厂', NULL, NULL),
('龙吟轩冲饮专营店', NULL, NULL),
('龙泉茶业', NULL, '13759450887');

CREATE TABLE IF NOT EXISTS backup_inv_product_before_20260711_refresh LIKE inv_product;
INSERT INTO backup_inv_product_before_20260711_refresh
SELECT source.* FROM inv_product source
WHERE NOT EXISTS (SELECT 1 FROM backup_inv_product_before_20260711_refresh LIMIT 1);
CREATE TABLE IF NOT EXISTS backup_inv_product_category_before_20260711_refresh LIKE inv_product_category;
INSERT INTO backup_inv_product_category_before_20260711_refresh
SELECT source.* FROM inv_product_category source
WHERE NOT EXISTS (SELECT 1 FROM backup_inv_product_category_before_20260711_refresh LIMIT 1);
CREATE TABLE IF NOT EXISTS backup_inv_supplier_before_20260711_refresh LIKE inv_supplier;
INSERT INTO backup_inv_supplier_before_20260711_refresh
SELECT source.* FROM inv_supplier source
WHERE NOT EXISTS (SELECT 1 FROM backup_inv_supplier_before_20260711_refresh LIMIT 1);

DROP PROCEDURE IF EXISTS apply_product_master_20260711;
DELIMITER $$
CREATE PROCEDURE apply_product_master_20260711()
BEGIN
    DECLARE current_products int DEFAULT 0;
    DECLARE visible_matches int DEFAULT 0;
    DECLARE hidden_matches int DEFAULT 0;
    DECLARE category_matches int DEFAULT 0;
    DECLARE supplier_matches int DEFAULT 0;
    DECLARE mismatch_count int DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT COUNT(*) INTO current_products FROM inv_product WHERE shop_dept_id = 100;
    IF current_products <> 167 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'preflight failed: expected 167 current products';
    END IF;
    IF (SELECT COUNT(*) FROM backup_inv_product_before_20260711_refresh) <> 167
       OR (SELECT COUNT(*) FROM backup_inv_product_category_before_20260711_refresh) <> 23
       OR (SELECT COUNT(*) FROM backup_inv_supplier_before_20260711_refresh) <> 87 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'preflight failed: backup row counts do not match';
    END IF;

    SELECT COUNT(DISTINCT p.product_id) INTO visible_matches
    FROM inv_product p
    JOIN tmp_product_master_20260711 source
      ON CAST(SUBSTRING_INDEX(p.remark, 'row=', -1) AS UNSIGNED) = source.source_row
    WHERE p.shop_dept_id = 100;
    IF visible_matches <> 158 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'preflight failed: visible product row mapping is not one-to-one';
    END IF;

    SELECT COUNT(DISTINCT p.product_id) INTO hidden_matches
    FROM inv_product p
    JOIN tmp_product_hidden_20260711 source
      ON CAST(SUBSTRING_INDEX(p.remark, 'row=', -1) AS UNSIGNED) = source.source_row
    WHERE p.shop_dept_id = 100;
    IF hidden_matches <> 9 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'preflight failed: hidden product row mapping is not one-to-one';
    END IF;

    SELECT COUNT(DISTINCT c.category_id) INTO category_matches
    FROM inv_product_category c
    JOIN tmp_product_category_20260711 source ON source.category_name = c.category_name
    WHERE c.shop_dept_id = 100;
    IF category_matches <> 21 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'preflight failed: category mapping is incomplete';
    END IF;

    SELECT COUNT(DISTINCT s.supplier_id) INTO supplier_matches
    FROM inv_supplier s
    JOIN tmp_product_supplier_20260711 source
      ON s.supplier_name = source.supplier_name OR s.supplier_name = source.previous_name
    WHERE s.shop_dept_id = 100;
    IF supplier_matches <> 82 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'preflight failed: supplier mapping is incomplete';
    END IF;

    START TRANSACTION;

    UPDATE inv_supplier s
    JOIN tmp_product_supplier_20260711 source
      ON s.supplier_name = source.supplier_name OR s.supplier_name = source.previous_name
    SET s.supplier_name = source.supplier_name,
        s.contact_person = '',
        s.contact_phone = COALESCE(source.contact_phone, ''),
        s.status = '0',
        s.cooperation_status = '0',
        s.update_by = 'product_master_20260711',
        s.update_time = NOW(),
        s.remark = '';

    UPDATE inv_supplier s
    LEFT JOIN tmp_product_supplier_20260711 source ON source.supplier_name = s.supplier_name
    SET s.status = '1',
        s.cooperation_status = '2',
        s.update_by = 'product_master_20260711',
        s.update_time = NOW()
    WHERE s.shop_dept_id = 100
      AND s.create_by = 'xlsx_reimport'
      AND source.supplier_name IS NULL;

    UPDATE inv_product_category c
    JOIN tmp_product_category_20260711 source ON source.category_name = c.category_name
    SET c.status = '0',
        c.del_flag = '0',
        c.update_by = 'product_master_20260711',
        c.update_time = NOW()
    WHERE c.shop_dept_id = 100;

    UPDATE inv_product_category c
    LEFT JOIN tmp_product_category_20260711 source ON source.category_name = c.category_name
    SET c.status = '1',
        c.del_flag = '2',
        c.update_by = 'product_master_20260711',
        c.update_time = NOW()
    WHERE c.shop_dept_id = 100
      AND c.create_by = 'xlsx_reimport'
      AND source.category_name IS NULL;

    UPDATE inv_product p
    JOIN tmp_product_master_20260711 source
      ON CAST(SUBSTRING_INDEX(p.remark, 'row=', -1) AS UNSIGNED) = source.source_row
    JOIN inv_product_category c
      ON c.category_name = source.category_name
     AND c.shop_dept_id = 100
    SET p.product_name = source.product_name,
        p.category_id = c.category_id,
        p.grade = source.grade,
        p.sku = NULL,
        p.spec = source.spec,
        p.size = NULL,
        p.unit = source.unit,
        p.purchase_price = source.reference_cost,
        p.sales_price = source.retail_price,
        p.sale_price_250g = NULL,
        p.sale_price_500g = NULL,
        p.cost_price = source.reference_cost,
        p.supplier_name = source.supplier_name,
        p.supplier_phone = source.supplier_phone,
        p.supplier_remark = NULL,
        p.internal_tea_name = NULL,
        p.product_description = source.product_description,
        p.barcode = NULL,
        p.image_url = NULL,
        p.package_image_url = NULL,
        p.dry_tea_image_url = NULL,
        p.tea_soup_image_url = NULL,
        p.leaf_bottom_image_url = NULL,
        p.extra_image_url = NULL,
        p.status = '0',
        p.del_flag = '0',
        p.update_by = 'product_master_20260711',
        p.update_time = NOW(),
        p.remark = CONCAT('Imported from 供应链平台产品20260711更新版.xlsx sheet=供应链平台产品2026版 row=', source.source_row)
    WHERE p.shop_dept_id = 100;

    UPDATE inv_product p
    JOIN tmp_product_hidden_20260711 source
      ON CAST(SUBSTRING_INDEX(p.remark, 'row=', -1) AS UNSIGNED) = source.source_row
    SET p.status = '1',
        p.del_flag = '2',
        p.update_by = 'product_master_20260711',
        p.update_time = NOW(),
        p.remark = CONCAT('Excluded hidden row from 供应链平台产品20260711更新版.xlsx sheet=供应链平台产品2026版 row=', source.source_row)
    WHERE p.shop_dept_id = 100;

    SELECT COUNT(*) INTO mismatch_count
    FROM inv_product p
    JOIN tmp_product_master_20260711 source
      ON CAST(SUBSTRING_INDEX(p.remark, 'row=', -1) AS UNSIGNED) = source.source_row
    JOIN inv_product_category c ON c.category_id = p.category_id
    WHERE NOT (
        p.product_name <=> source.product_name
        AND c.category_name <=> source.category_name
        AND p.grade <=> source.grade
        AND p.spec <=> source.spec
        AND p.product_description <=> source.product_description
        AND p.unit <=> source.unit
        AND p.purchase_price <=> source.reference_cost
        AND p.cost_price <=> source.reference_cost
        AND p.sales_price <=> source.retail_price
        AND p.supplier_name <=> source.supplier_name
        AND p.supplier_phone <=> source.supplier_phone
        AND p.status = '0'
        AND p.del_flag = '0'
    );
    IF mismatch_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'postflight failed: source/product mismatch remains';
    END IF;
    IF (SELECT COUNT(*) FROM inv_product WHERE shop_dept_id = 100 AND del_flag = '0') <> 158 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'postflight failed: active product count is not 158';
    END IF;
    IF (SELECT COUNT(*) FROM inv_product WHERE shop_dept_id = 100 AND del_flag = '2' AND status = '1') <> 9 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'postflight failed: hidden product count is not 9';
    END IF;

    COMMIT;
END$$
DELIMITER ;
CALL apply_product_master_20260711();
DROP PROCEDURE apply_product_master_20260711;

SELECT 'active_products' AS metric, COUNT(*) AS value FROM inv_product WHERE shop_dept_id = 100 AND del_flag = '0'
UNION ALL SELECT 'soft_disabled_hidden_products', COUNT(*) FROM inv_product WHERE shop_dept_id = 100 AND del_flag = '2' AND status = '1'
UNION ALL SELECT 'active_categories', COUNT(*) FROM inv_product_category WHERE shop_dept_id = 100 AND del_flag = '0' AND status = '0'
UNION ALL SELECT 'active_suppliers', COUNT(*) FROM inv_supplier WHERE shop_dept_id = 100 AND status = '0' AND cooperation_status = '0';
