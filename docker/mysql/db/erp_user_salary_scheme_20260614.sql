-- 员工薪资绑定：用户直接绑定已有薪资方案档位，替代按角色推导工资档位

CREATE TABLE IF NOT EXISTS sys_user_salary_scheme (
    relation_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    user_id bigint(20) NOT NULL COMMENT '用户ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '核算门店/部门ID',
    scheme_id bigint(20) NOT NULL COMMENT '薪资方案ID',
    item_id bigint(20) NOT NULL COMMENT '薪资档位ID',
    priority int(11) DEFAULT 10 COMMENT '优先级',
    effective_date varchar(20) DEFAULT NULL COMMENT '生效日期',
    end_date varchar(20) DEFAULT NULL COMMENT '失效日期',
    status char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (relation_id),
    KEY idx_user_salary_user_shop (user_id, shop_dept_id, status),
    KEY idx_user_salary_scheme (scheme_id),
    KEY idx_user_salary_item (item_id),
    KEY idx_user_salary_effective (effective_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工薪资方案绑定表';
