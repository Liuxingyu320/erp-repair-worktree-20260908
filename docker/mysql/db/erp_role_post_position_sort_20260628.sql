-- 角色/岗位按职位高低排序：数值越小越靠前。
-- 超级管理员保留在最前；经营岗位从运营总监到实习生；普通/仓库等非经营层级角色排在后面。

START TRANSACTION;

UPDATE sys_role
SET role_sort = CASE
        WHEN role_key = 'admin' OR role_name = '超级管理员' THEN 0
        WHEN role_key IN ('yyzj', 'zjl') OR role_name IN ('运营总监', '总经理') THEN 1
        WHEN role_key = 'qyyyzzj' OR role_name = '区域运营总监' THEN 2
        WHEN role_key = 'yyjl' OR role_name = '运营经理' THEN 3
        WHEN role_key = 'zdjl' OR role_name = '驻店经理' THEN 4
        WHEN role_key = 'dz' OR role_name = '店长' THEN 5
        WHEN role_key = 'dzzy' OR role_name = '店长助理' THEN 6
        WHEN role_key = 'cys' OR role_name = '茶艺师' THEN 7
        WHEN role_key = 'sxs' OR role_name = '实习生' THEN 8
        WHEN role_key = 'ck' OR role_name = '仓库管理员' THEN 9
        WHEN role_key = 'common' OR role_name = '普通角色' THEN 99
        ELSE role_sort
    END,
    update_by = 'position_sort_20260628',
    update_time = NOW()
WHERE del_flag = '0'
  AND (
      role_key IN ('admin', 'yyzj', 'zjl', 'qyyyzzj', 'yyjl', 'zdjl', 'dz', 'dzzy', 'cys', 'sxs', 'ck', 'common')
      OR role_name IN ('超级管理员', '运营总监', '总经理', '区域运营总监', '运营经理', '驻店经理', '店长', '店长助理', '茶艺师', '实习生', '仓库管理员', '普通角色')
  );

UPDATE sys_post
SET post_sort = CASE
        WHEN post_code IN ('yyzj', 'zjl', 'gm') OR post_name IN ('运营总监', '总经理', 'GM') THEN 1
        WHEN post_code = 'qyyyzzj' OR post_name = '区域运营总监' THEN 2
        WHEN post_code IN ('yyjl', 'cjyyjl', 'qyyyjl') OR post_name IN ('运营经理', '初级运营经理', '区域运营经理') THEN 3
        WHEN post_code IN ('zdjl', 'amzdjl', 'amzddz') OR post_name IN ('驻店经理', 'AM驻店经理', 'AM驻店店长') THEN 4
        WHEN post_code IN ('dz', 'ybdz') OR post_name IN ('店长', '预备店长') THEN 5
        WHEN post_code = 'dzzy' OR post_name IN ('店长助理', '店助') THEN 6
        WHEN post_code = 'cys' OR post_name = '茶艺师' THEN 7
        WHEN post_code = 'sxs' OR post_name = '实习生' THEN 8
        ELSE post_sort
    END,
    update_by = 'position_sort_20260628',
    update_time = NOW()
WHERE post_code IN ('yyzj', 'zjl', 'gm', 'qyyyzzj', 'yyjl', 'cjyyjl', 'qyyyjl', 'zdjl', 'amzdjl', 'amzddz', 'dz', 'ybdz', 'dzzy', 'cys', 'sxs')
   OR post_name IN ('运营总监', '总经理', 'GM', '区域运营总监', '运营经理', '初级运营经理', '区域运营经理', '驻店经理', 'AM驻店经理', 'AM驻店店长', '店长', '预备店长', '店长助理', '店助', '茶艺师', '实习生');

COMMIT;
