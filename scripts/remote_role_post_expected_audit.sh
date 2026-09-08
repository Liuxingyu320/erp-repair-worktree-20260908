#!/usr/bin/env bash
set -euo pipefail
DB="BossERP_NEW"
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
mysql --protocol=tcp -uroot -p"${MYSQL_ROOT_PASS}" "${DB}" --batch --raw <<'SQL'
CREATE TEMPORARY TABLE expected_user_role_post (
  match_type varchar(32) not null,
  match_key varchar(128) not null,
  expected_name varchar(128) not null,
  expected_post varchar(128) not null,
  expected_role varchar(128) not null,
  source_name varchar(128) not null
);
INSERT INTO expected_user_role_post(match_type, match_key, expected_name, expected_post, expected_role, source_name) VALUES
('phone', '13002918964', '郑曼婷', '店长助理', '店长助理', '员工Excel'),
('phone', '13052521165', '林科讯', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13059605813', '赵佳善', '店长助理', '店长助理', '员工Excel'),
('phone', '13146568683', '马旭', '店长助理', '店长助理', '员工Excel'),
('phone', '13151661889', '潘蝶', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13221010205', '李娇', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13230367267', '绳淇月', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13233054794', '谷晨茜', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13262279481', '傅小丽', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13291231812', '徐汉文', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13293192198', '赵海阳', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13294143159', '李昌林', '驻店经理', '驻店经理', '员工Excel'),
('phone', '13295030132', '韩梦瑶', '店长', '店长', '员工Excel'),
('phone', '13419976192', '石春芝', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13521287710', '孙静芳', '初级运营经理', '运营经理', '员工Excel'),
('phone', '13547599314', '周亚玲', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13639351884', '张妍', '店长助理', '店长助理', '员工Excel'),
('phone', '13659326770', '灵韵', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13758806835', '赖晴晴', '茶艺师', '茶艺师', '员工Excel'),
('phone', '13775121564', '马亚丽', '店长', '店长', '员工Excel'),
('phone', '13776013655', '侯元元', '驻店经理', '驻店经理', '员工Excel'),
('phone', '13777492912', '罗心怡', '行政助理', '普通角色', '员工Excel'),
('phone', '13918797386', '赵加龙', '运营助理', '普通角色', '员工Excel'),
('phone', '13940709851', '刘营', 'AM驻店经理', '驻店经理', '员工Excel'),
('phone', '15011279445', '张声潮', '产品经理', '普通角色', '员工Excel'),
('phone', '15025969585', '谢芳芳', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15027004455', '谢艳丽', 'AM驻店经理', '驻店经理', '员工Excel'),
('phone', '15055567977', '李雅倩', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15070541861', '邓楚雪', '店长助理', '店长助理', '员工Excel'),
('phone', '15075405459', '徐盼盼', '驻店经理', '驻店经理', '员工Excel'),
('phone', '15097543090', '李建伟', '物料采购', '普通角色', '员工Excel'),
('phone', '15126636787', '周永猜', '店长助理', '店长助理', '员工Excel'),
('phone', '15223628021', '冉晓敏', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15224623845', '梁源姗', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15267457673', '王宗亚', '运营经理', '运营经理', '员工Excel'),
('phone', '15274437021', '伍秋定', '店长助理', '店长助理', '员工Excel'),
('phone', '15395627572', '林凯丽', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15504655148', '杨雪佳', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15515670298', '刘乐', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15516784616', '李瑶瑶', '店长', '店长', '员工Excel'),
('phone', '15522429601', '王路琳', '店长助理', '店长助理', '员工Excel'),
('phone', '15585879846', '王红梅', '初级运营经理', '运营经理', '员工Excel'),
('phone', '15672636806', '杜洋', '初级运营经理', '运营经理', '员工Excel'),
('phone', '15687181142', '王籼懿', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15713978326', '张蓝心', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15738757721', '赵艺', '店长', '店长', '员工Excel'),
('phone', '15738961489', '左凡凡', '驻店经理', '驻店经理', '员工Excel'),
('phone', '15810180130', '熊彩红', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15810209597', '杜帅', '产品专员', '普通角色', '员工Excel'),
('phone', '15853019762', '刘梦敏', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15868163414', '赵洺妍', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15902975627', '任妮', '店长', '店长', '员工Excel'),
('phone', '15931473118', '李双', '店长', '店长', '员工Excel'),
('phone', '15938965695', '谷佳璐', '茶艺师', '茶艺师', '员工Excel'),
('phone', '15958794906', '周蒙蒙', '店长助理', '店长助理', '员工Excel'),
('phone', '16657049808', '段继康', '行政经理', '普通角色', '员工Excel'),
('phone', '16674241024', '吴婧', '店长助理', '店长助理', '员工Excel'),
('phone', '17140459888', '李希霞', '店长', '店长', '员工Excel'),
('phone', '17320619347', '宗鑫淼', '茶艺师', '茶艺师', '员工Excel'),
('phone', '17330977932', '郭彤彤', '茶艺师', '茶艺师', '员工Excel'),
('phone', '17348383797', '黄济霖', '店长助理', '店长助理', '员工Excel'),
('phone', '17590100701', '李双双', '茶艺师', '茶艺师', '员工Excel'),
('phone', '17633811532', '陶丹丹', '店长', '店长', '员工Excel'),
('phone', '17651806125', '巩盼盼', '茶艺师', '茶艺师', '员工Excel'),
('phone', '17703877570', '李思雨', '茶艺师', '茶艺师', '员工Excel'),
('phone', '17754144063', '宋情诗', '茶艺师', '茶艺师', '员工Excel'),
('phone', '17773493143', '宋祖丽', '店长助理', '店长助理', '员工Excel'),
('phone', '17818820831', '谢暖暖', '茶艺师', '茶艺师', '员工Excel'),
('phone', '17856524374', '周欣晴', '店长助理', '店长助理', '员工Excel'),
('phone', '17887203968', '罗丹', '店长助理', '店长助理', '员工Excel'),
('phone', '17896929661', '郭晴晴', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18008534404', '卢小怡', '店长助理', '店长助理', '员工Excel'),
('phone', '18034311668', '张丽婷', '驻店经理', '驻店经理', '员工Excel'),
('phone', '18035343645', '杨一美', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18082492679', '任佳萌', '店长', '店长', '员工Excel'),
('phone', '18210107086', '金英', '总经理', '运营总监', '员工Excel'),
('phone', '18233557512', '杨慧媛', '店长', '店长', '员工Excel'),
('phone', '18270523702', '王曼瑶', '店长助理', '店长助理', '员工Excel'),
('phone', '18285043096', '岳燕', '店长助理', '店长助理', '员工Excel'),
('phone', '18294516722', '谢芳慧', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18325233828', '冉慧文', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18337623343', '孙文丽', '店长助理', '店长助理', '员工Excel'),
('phone', '18339835431', '吴天然', '店长助理', '店长助理', '员工Excel'),
('phone', '18371300296', '李晴晴', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18378742518', '杨咏斯', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18379805244', '郭诗梅', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18387357652', '袁升琼', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18390281095', '陈姣利', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18403901865', '孙慧君', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18476641706', '陈思恩', '店长助理', '店长助理', '员工Excel'),
('phone', '18500153124', '赫亚茹', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18535975204', '刘彩红', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18579206247', '袁芮婷', '预备店长', '店长', '员工Excel'),
('phone', '18584537689', '陈海燕', '店长', '店长', '员工Excel'),
('phone', '18607199105', '陈苗', 'AM驻店经理', '驻店经理', '员工Excel'),
('phone', '18655197121', '张影影', '店长', '店长', '员工Excel'),
('phone', '18669567559', '刘星宇', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18672473845', '洪立', '店长助理', '店长助理', '员工Excel'),
('phone', '18696070256', '王丽莎', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18777525212', '李燕燕', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18856164358', '王美芳', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18863281928', '李雨洋', '茶艺师', '茶艺师', '员工Excel'),
('phone', '18969576735', '潘舒静', '店长', '店长', '员工Excel'),
('phone', '18996165827', '陶李媛', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19012738035', '王秋敏', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19057930421', '冯小倩', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19096405763', '王波丽', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19167620997', '阳玉姣', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19170660793', '李爱兰', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19179583134', '李爱婷', '店长助理', '店长助理', '员工Excel'),
('phone', '19189397336', '朱文淑', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19193889337', '杨沛', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19198149521', '黎莫', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19231184007', '聂伟佳', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19252141774', '周思思', '店长助理', '店长助理', '员工Excel'),
('phone', '19293130415', '武寒影', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19295149072', '栗柯', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19324804297', '刘心雨', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19356132590', '王思佳', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19356718098', '朱洪梅', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19537158076', '马成秀', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19550171505', '丑稚琼', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19551606976', '张梦茹', '店长助理', '店长助理', '员工Excel'),
('phone', '19573388482', '黎彩红', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19574163931', '熊淑欣', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19834743225', '王欣雨', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19855401274', '刘媛媛', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19858165769', '李意欣', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19884189399', '杨瑶', '预备店长', '店长', '员工Excel'),
('phone', '19939741208', '李好慧', '茶艺师', '茶艺师', '员工Excel'),
('phone', '19944219613', '麻欢欢', '店长助理', '店长助理', '员工Excel'),
('missing_name', '沈延慧', '沈延慧', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '于文乐', '于文乐', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '周腾芳', '周腾芳', '店长', '店长', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '杨沛', '杨沛', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '张丽霞', '张丽霞', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '孔茗倩', '孔茗倩', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '王淑贤', '王淑贤', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '陈向宇', '陈向宇', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '刘嘉祺', '刘嘉祺', '', '', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '胡达', '胡达', '', '', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '周研', '周研', '', '', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '李曼', '李曼', '', '', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '菅子玉', '菅子玉', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '段嘉杏', '段嘉杏', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '周资能', '周资能', '店长助理', '店长助理', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '李佳宁', '李佳宁', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '叶韦芳', '叶韦芳', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '彭丹', '彭丹', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '叶素红', '叶素红', '运营经理', '运营经理', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '何念', '何念', '', '', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '顾芳铭', '顾芳铭', '', '', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '侯梦璃月', '侯梦璃月', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx'),
('missing_name', '胡熠熠', '胡熠熠', '茶艺师', '茶艺师', '无手机号员工明细_含负责人.xlsx');
CREATE TEMPORARY TABLE expected_user_role_post_lookup AS SELECT * FROM expected_user_role_post;

select 'expected_counts' as section;
select count(*) as expected_rows,
       sum(match_type='phone') as expected_phone_rows,
       sum(match_type='missing_name') as expected_missing_name_rows
from expected_user_role_post;

select 'role_post_mismatches' as section;
select e.source_name, e.match_type, e.match_key, e.expected_name,
       coalesce(u.user_id, 0) as user_id,
       coalesce(u.nick_name, '<missing>') as actual_name,
       e.expected_post,
       coalesce(a.actual_posts, '<none>') as actual_posts,
       e.expected_role,
       coalesce(a.actual_roles, '<none>') as actual_roles,
       coalesce(a.post_count, 0) as post_count,
       coalesce(a.role_count, 0) as role_count
from expected_user_role_post e
left join sys_user u
  on u.del_flag='0'
 and (
      (e.match_type='phone' and (u.phonenumber=e.match_key or u.user_name=e.match_key))
      or (e.match_type='missing_name' and u.create_by='missing_phone_reseed' and u.nick_name=e.match_key)
 )
left join (
    select u.user_id,
           group_concat(distinct p.post_name order by p.post_sort, p.post_id separator '|') as actual_posts,
           group_concat(distinct r.role_name order by r.role_sort, r.role_id separator '|') as actual_roles,
           count(distinct p.post_id) as post_count,
           count(distinct r.role_id) as role_count
    from sys_user u
    left join sys_user_post up on up.user_id=u.user_id
    left join sys_post p on p.post_id=up.post_id
    left join sys_user_role ur on ur.user_id=u.user_id
    left join sys_role r on r.role_id=ur.role_id
    where u.del_flag='0'
    group by u.user_id
) a on a.user_id=u.user_id
where u.user_id is null
   or coalesce(a.post_count, 0) <> 1
   or coalesce(a.role_count, 0) <> 1
   or (e.expected_post <> '' and coalesce(a.actual_posts, '') <> e.expected_post)
   or (e.expected_role <> '' and coalesce(a.actual_roles, '') <> e.expected_role)
order by e.source_name, e.match_type, e.expected_name, e.match_key;

select 'active_import_users_not_in_expected' as section;
select u.user_id, u.nick_name, u.user_name, u.phonenumber, u.create_by,
       coalesce(a.actual_posts, '<none>') as actual_posts,
       coalesce(a.actual_roles, '<none>') as actual_roles
from sys_user u
left join (
    select u.user_id,
           group_concat(distinct p.post_name order by p.post_sort, p.post_id separator '|') as actual_posts,
           group_concat(distinct r.role_name order by r.role_sort, r.role_id separator '|') as actual_roles
    from sys_user u
    left join sys_user_post up on up.user_id=u.user_id
    left join sys_post p on p.post_id=up.post_id
    left join sys_user_role ur on ur.user_id=u.user_id
    left join sys_role r on r.role_id=ur.role_id
    where u.del_flag='0'
    group by u.user_id
) a on a.user_id=u.user_id
where u.del_flag='0'
  and u.create_by in ('employee_xls_import','missing_phone_reseed')
  and not exists (
      select 1 from expected_user_role_post_lookup e
      where (e.match_type='phone' and (u.phonenumber=e.match_key or u.user_name=e.match_key))
         or (e.match_type='missing_name' and u.create_by='missing_phone_reseed' and u.nick_name=e.match_key)
  )
order by u.create_by, u.nick_name, u.user_id;
SQL
