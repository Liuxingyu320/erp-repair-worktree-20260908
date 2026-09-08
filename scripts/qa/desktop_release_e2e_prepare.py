#!/usr/bin/env python3
"""Prepare a synthetic, local-only desktop release E2E fixture."""

from __future__ import annotations

import hashlib
import json
import secrets
import string
import sys
from datetime import datetime, timezone

import bcrypt

from desktop_release_e2e_common import (
    ACTORS,
    APPROVAL_CANDIDATE,
    APPROVAL_FIXTURE_ROUND,
    APPROVAL_INSTANCE,
    APPROVAL_NODE,
    APPROVAL_RULE,
    APPROVAL_TASK,
    APPROVAL_VERSION,
    AUTO_INCREMENT_TABLES,
    CONTRACT_A,
    CONTRACT_B,
    CREDENTIAL_PATH,
    DATABASE,
    DEPT_COMPANY,
    DEPT_STORE_A,
    DEPT_STORE_B,
    DEPT_WAREHOUSE_A,
    ID_MAX,
    ID_MIN,
    JOB_ID,
    MANIFEST_PATH,
    PREFIX,
    PRODUCT_A,
    PRODUCT_B,
    PROFILE_EMPLOYEE,
    PROFILE_MANAGER,
    PROFILE_OTHER_A,
    PROFILE_OTHER_B,
    PROFILE_PRIVILEGED,
    QaSafetyError,
    ROLE_EMPLOYEE,
    ROLE_MANAGER,
    ROLE_PRIVILEGED,
    RUN_ID,
    STOCK_A,
    STOCK_B,
    TRANSFER_APPROVAL,
    TRANSFER_DETAIL_APPROVAL,
    TRANSFER_DETAIL_DRAFT,
    TRANSFER_DRAFT,
    USER_EMPLOYEE,
    USER_MANAGER,
    USER_OTHER_A,
    USER_OTHER_B,
    USER_PRIVILEGED,
    WRITE_APPROVAL_ENV,
    WRITE_APPROVAL_VALUE,
    capture_auto_increment,
    high_id_hits,
    mysql,
    owner_only_json,
    prefix_hits,
    require_write_approval,
    sql_quote,
    synthetic_id_card,
    table_exists,
    validate_loopback_base_url,
    verify_database_identity,
    verify_local_services,
)


EMPLOYEE_MENUS = (
    3000,
    3001,
    4000,
    4040,
    4041,
    4100,
    4101,
    4102,
    4103,
    4104,
)

MANAGER_MENUS = tuple(
    sorted(
        set(EMPLOYEE_MENUS)
        | {
            3300,
            3301,
            3302,
            4000,
            4040,
            4041,
            4100,
            4101,
            4102,
            4103,
            4104,
            4109,
            4470,
            4471,
            9605,
            4601,
            9606,
            4613,
            9700,
            9704,
            9705,
        }
    )
)


def random_password() -> str:
    alphabet = string.ascii_letters + string.digits + "!@#$%^&*_-+="
    while True:
        value = "".join(secrets.choice(alphabet) for _ in range(20))
        if (
            any(ch.islower() for ch in value)
            and any(ch.isupper() for ch in value)
            and any(ch.isdigit() for ch in value)
            and any(ch in "!@#$%^&*_-+=" for ch in value)
        ):
            return value


def password_hash(value: str) -> str:
    return bcrypt.hashpw(value.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode(
        "ascii"
    )


def values(rows: list[tuple[object, ...]]) -> str:
    return ",\n".join(
        "(" + ",".join(sql_quote(item) for item in row) + ")" for row in rows
    )


def build_prepare_sql(hashes: dict[str, str], other_hash: str) -> str:
    marker = PREFIX + "FIXTURE"
    employee_name = PREFIX + "EMP"
    manager_name = PREFIX + "MGR"
    manager_leader = ACTORS["manager"]["username"]
    privileged_name = PREFIX + "ADM"
    other_a_name = PREFIX + "OTHER_A"
    other_b_name = PREFIX + "OTHER_B"
    id_card = synthetic_id_card()
    phone_a = "19900000001"
    phone_b = "19900000002"
    phone_m = "19900000003"
    phone_p = "19900000004"
    phone_o = "19900000005"
    now = "CURRENT_TIMESTAMP"

    rule_snapshot = {
        "qaRunId": RUN_ID,
        "nodes": [
            {
                "nodeId": APPROVAL_NODE,
                "strategyType": "FIXED_USERS",
                "userIds": [USER_MANAGER],
            }
        ],
    }
    rule_snapshot_text = json.dumps(
        rule_snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    )
    rule_checksum = hashlib.sha256(rule_snapshot_text.encode("utf-8")).hexdigest()
    business_snapshot = json.dumps(
        {"qaRunId": RUN_ID, "totalQuantity": 1, "amount": "10.00"},
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    business_digest = hashlib.sha256(
        business_snapshot.encode("utf-8")
    ).hexdigest()
    route_snapshot = json.dumps(
        {
            "qaRunId": RUN_ID,
            "route": {
                "desktopPath": "/inventory/transfer",
                "transferId": str(TRANSFER_APPROVAL),
            },
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )

    employee_menu_csv = ",".join(str(value) for value in EMPLOYEE_MENUS)
    manager_menu_csv = ",".join(str(value) for value in MANAGER_MENUS)

    return f"""
SET SESSION sql_safe_updates=1;
START TRANSACTION;

INSERT INTO sys_dept
  (dept_id,parent_id,ancestors,dept_name,order_num,leader,leader_user_id,
   phone,email,status,dept_type,del_flag,create_by,create_time)
VALUES
  ({DEPT_COMPANY},0,'0',{sql_quote(PREFIX + 'COMPANY')},900,
   {sql_quote(manager_leader)},{USER_MANAGER},NULL,NULL,'0','COMPANY','0',
   {sql_quote(marker)},{now}),
  ({DEPT_STORE_A},{DEPT_COMPANY},{sql_quote('0,' + str(DEPT_COMPANY))},
   {sql_quote(PREFIX + 'STORE_A')},901,{sql_quote(manager_leader)},{USER_MANAGER},
   NULL,NULL,'0','STORE','0',{sql_quote(marker)},{now}),
  ({DEPT_STORE_B},{DEPT_COMPANY},{sql_quote('0,' + str(DEPT_COMPANY))},
   {sql_quote(PREFIX + 'STORE_B')},902,NULL,NULL,NULL,NULL,'0','STORE','0',
   {sql_quote(marker)},{now}),
  ({DEPT_WAREHOUSE_A},{DEPT_COMPANY},{sql_quote('0,' + str(DEPT_COMPANY))},
   {sql_quote(PREFIX + 'WAREHOUSE_A')},903,{sql_quote(manager_leader)},{USER_MANAGER},
   NULL,NULL,'0','WAREHOUSE','0',{sql_quote(marker)},{now});

INSERT INTO sys_role
  (role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,
   dept_check_strictly,status,del_flag,create_by,create_time,remark)
VALUES
  ({ROLE_EMPLOYEE},{sql_quote(PREFIX + 'EMP_ROLE')},
   {sql_quote(PREFIX + 'EMPLOYEE')},900,'3',1,1,'0','0',
   {sql_quote(marker)},{now},{sql_quote(marker)}),
  ({ROLE_MANAGER},{sql_quote(PREFIX + 'MGR_ROLE')},
   {sql_quote(PREFIX + 'MANAGER')},901,'3',1,1,'0','0',
   {sql_quote(marker)},{now},{sql_quote(marker)}),
  ({ROLE_PRIVILEGED},{sql_quote(PREFIX + 'ADM_ROLE')},
   {sql_quote(PREFIX + 'PRIVILEGED')},902,'1',1,1,'0','0',
   {sql_quote(marker)},{now},
   {sql_quote(PREFIX + 'NOT_BUILT_IN_SUPER_ADMIN')});

INSERT INTO sys_user
  (user_id,dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,
   password,status,del_flag,credential_state,create_by,create_time,remark)
VALUES
  ({USER_EMPLOYEE},{DEPT_STORE_A},{sql_quote(ACTORS['employee']['username'])},
   {sql_quote(employee_name)},'00','qa-employee@example.invalid',
   {sql_quote(phone_a)},'2','',{sql_quote(hashes['employee'])},'0','0','ACTIVE',
   {sql_quote(marker)},{now},{sql_quote(marker)}),
  ({USER_MANAGER},{DEPT_STORE_A},{sql_quote(ACTORS['manager']['username'])},
   {sql_quote(manager_name)},'00','qa-manager@example.invalid',
   {sql_quote(phone_m)},'2','',{sql_quote(hashes['manager'])},'0','0','ACTIVE',
   {sql_quote(marker)},{now},{sql_quote(marker)}),
  ({USER_PRIVILEGED},{DEPT_COMPANY},
   {sql_quote(ACTORS['privilegedAdmin']['username'])},
   {sql_quote(privileged_name)},'00','qa-admin@example.invalid',
   {sql_quote(phone_p)},'2','',{sql_quote(hashes['privilegedAdmin'])},'0','0',
   'ACTIVE',{sql_quote(marker)},{now},{sql_quote(marker)}),
  ({USER_OTHER_A},{DEPT_STORE_A},{sql_quote(PREFIX + 'OA')},
   {sql_quote(other_a_name)},'00','qa-other-a@example.invalid',
   {sql_quote(phone_o)},'2','',{sql_quote(other_hash)},'0','0','ACTIVE',
   {sql_quote(marker)},{now},{sql_quote(marker)}),
  ({USER_OTHER_B},{DEPT_STORE_B},{sql_quote(PREFIX + 'OB')},
   {sql_quote(other_b_name)},'00','qa-other-b@example.invalid',
   {sql_quote(phone_b)},'2','',{sql_quote(other_hash)},'0','0','ACTIVE',
   {sql_quote(marker)},{now},{sql_quote(marker)});

INSERT INTO sys_user_profile
  (profile_id,user_id,employee_name,phone_number,dept_id,employee_no,position_no,
   company_name,store_name,job_grade,employee_status,employee_category,
   entry_date,profile_source,create_by,create_time)
VALUES
  ({PROFILE_EMPLOYEE},{USER_EMPLOYEE},{sql_quote(employee_name)},
   {sql_quote(phone_a)},{DEPT_STORE_A},{sql_quote(PREFIX + 'NO_E')},
   {sql_quote(PREFIX + 'POS_E')},{sql_quote(PREFIX + 'COMPANY')},
   {sql_quote(PREFIX + 'STORE_A')},{sql_quote(PREFIX + 'GRADE_E')},
   '在职','正式员工','2026-07-01',{sql_quote(marker)},{sql_quote(marker)},{now}),
  ({PROFILE_MANAGER},{USER_MANAGER},{sql_quote(manager_name)},
   {sql_quote(phone_m)},{DEPT_STORE_A},{sql_quote(PREFIX + 'NO_M')},
   {sql_quote(PREFIX + 'POS_M')},{sql_quote(PREFIX + 'COMPANY')},
   {sql_quote(PREFIX + 'STORE_A')},{sql_quote(PREFIX + 'GRADE_M')},
   '在职','正式员工','2026-07-01',{sql_quote(marker)},{sql_quote(marker)},{now}),
  ({PROFILE_PRIVILEGED},{USER_PRIVILEGED},{sql_quote(privileged_name)},
   {sql_quote(phone_p)},{DEPT_COMPANY},{sql_quote(PREFIX + 'NO_A')},
   {sql_quote(PREFIX + 'POS_A')},{sql_quote(PREFIX + 'COMPANY')},
   NULL,{sql_quote(PREFIX + 'GRADE_A')},'在职','正式员工','2026-07-01',
   {sql_quote(marker)},{sql_quote(marker)},{now}),
  ({PROFILE_OTHER_A},{USER_OTHER_A},{sql_quote(other_a_name)},
   {sql_quote(phone_o)},{DEPT_STORE_A},{sql_quote(PREFIX + 'NO_OA')},
   {sql_quote(PREFIX + 'POS_OA')},{sql_quote(PREFIX + 'COMPANY')},
   {sql_quote(PREFIX + 'STORE_A')},{sql_quote(PREFIX + 'GRADE_OA')},
   '在职','正式员工','2026-07-01',{sql_quote(marker)},{sql_quote(marker)},{now}),
  ({PROFILE_OTHER_B},{USER_OTHER_B},{sql_quote(other_b_name)},
   {sql_quote(phone_b)},{DEPT_STORE_B},{sql_quote(PREFIX + 'NO_OB')},
   {sql_quote(PREFIX + 'POS_OB')},{sql_quote(PREFIX + 'COMPANY')},
   {sql_quote(PREFIX + 'STORE_B')},{sql_quote(PREFIX + 'GRADE_OB')},
   '在职','正式员工','2026-07-01',{sql_quote(marker)},{sql_quote(marker)},{now});

INSERT INTO sys_user_role (user_id,role_id) VALUES
  ({USER_EMPLOYEE},{ROLE_EMPLOYEE}),({USER_MANAGER},{ROLE_MANAGER}),
  ({USER_PRIVILEGED},{ROLE_PRIVILEGED}),({USER_OTHER_A},{ROLE_EMPLOYEE}),
  ({USER_OTHER_B},{ROLE_EMPLOYEE});

INSERT INTO sys_user_shop (user_id,dept_id,is_default,create_by,create_time)
VALUES
  ({USER_EMPLOYEE},{DEPT_STORE_A},'Y',{sql_quote(marker)},{now}),
  ({USER_MANAGER},{DEPT_STORE_A},'Y',{sql_quote(marker)},{now}),
  ({USER_PRIVILEGED},{DEPT_STORE_A},'Y',{sql_quote(marker)},{now}),
  ({USER_PRIVILEGED},{DEPT_STORE_B},'N',{sql_quote(marker)},{now}),
  ({USER_PRIVILEGED},{DEPT_WAREHOUSE_A},'N',{sql_quote(marker)},{now}),
  ({USER_OTHER_A},{DEPT_STORE_A},'Y',{sql_quote(marker)},{now}),
  ({USER_OTHER_B},{DEPT_STORE_B},'Y',{sql_quote(marker)},{now});

INSERT INTO sys_role_dept (role_id,dept_id) VALUES
  ({ROLE_EMPLOYEE},{DEPT_STORE_A}),({ROLE_MANAGER},{DEPT_STORE_A}),
  ({ROLE_PRIVILEGED},{DEPT_COMPANY}),({ROLE_PRIVILEGED},{DEPT_STORE_A}),
  ({ROLE_PRIVILEGED},{DEPT_STORE_B}),({ROLE_PRIVILEGED},{DEPT_WAREHOUSE_A});

INSERT INTO sys_role_menu (role_id,menu_id)
SELECT {ROLE_EMPLOYEE},menu_id FROM sys_menu
WHERE status='0' AND menu_id IN ({employee_menu_csv});
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT {ROLE_MANAGER},menu_id FROM sys_menu
WHERE status='0' AND menu_id IN ({manager_menu_csv});
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT {ROLE_PRIVILEGED},menu_id FROM sys_menu WHERE status='0';

INSERT INTO inv_product
  (product_id,product_name,product_code,sku,spec,unit,purchase_price,sales_price,
   cost_price,shop_dept_id,status,del_flag,create_by,create_time,remark)
VALUES
  ({PRODUCT_A},{sql_quote(PREFIX + 'PRODUCT_A')},{sql_quote(PREFIX + 'SKU_A')},
   {sql_quote(PREFIX + 'SKU_A')},'QA','件',10.00,12.00,10.00,{DEPT_STORE_A},
   '0','0',{sql_quote(marker)},{now},{sql_quote(marker)}),
  ({PRODUCT_B},{sql_quote(PREFIX + 'PRODUCT_B')},{sql_quote(PREFIX + 'SKU_B')},
   {sql_quote(PREFIX + 'SKU_B')},'QA','件',20.00,24.00,20.00,{DEPT_STORE_B},
   '0','0',{sql_quote(marker)},{now},{sql_quote(marker)});

INSERT INTO inv_stock
  (stock_id,item_type,item_id,product_id,shop_dept_id,warehouse_id,batch_no,
   location_code,location_name,current_quantity,locked_quantity,
   available_quantity,cost_price,total_cost,version,create_by,create_time,remark)
VALUES
  ({STOCK_A},'product',{PRODUCT_A},{PRODUCT_A},{DEPT_STORE_A},{DEPT_STORE_A},
   {sql_quote(PREFIX + 'BATCH_A')},{sql_quote(PREFIX + 'LOC_A')},
   {sql_quote(PREFIX + 'LOCATION_A')},100.00,0.00,100.00,10.00,1000.00,0,
   {sql_quote(marker)},{now},{sql_quote(marker)}),
  ({STOCK_B},'product',{PRODUCT_B},{PRODUCT_B},{DEPT_STORE_B},{DEPT_STORE_B},
   {sql_quote(PREFIX + 'BATCH_B')},{sql_quote(PREFIX + 'LOC_B')},
   {sql_quote(PREFIX + 'LOCATION_B')},200.00,0.00,200.00,20.00,4000.00,0,
   {sql_quote(marker)},{now},{sql_quote(marker)});

INSERT INTO oa_labor_contract
  (contract_id,template_id,employee_id,employee_dept_id,shop_dept_id,
   contract_no,contract_title,employee_name,employee_id_card,employee_phone,
   post_name,social_type,contract_start_date,contract_end_date,base_salary,
   total_salary,status,sign_provider,create_by,create_time,remark)
VALUES
  ({CONTRACT_A},1,{USER_EMPLOYEE},{DEPT_STORE_A},{DEPT_STORE_A},
   {sql_quote(PREFIX + 'CONTRACT_A')},{sql_quote(PREFIX + 'CONTRACT_A_DRAFT')},
   {sql_quote(employee_name)},{sql_quote(id_card)},{sql_quote(phone_a)},
   {sql_quote(PREFIX + 'POST')},'有社保','2026-08-01','2027-07-31',
   1.00,1.00,'draft','internal',{sql_quote(marker)},{now},{sql_quote(marker)}),
  ({CONTRACT_B},1,{USER_OTHER_B},{DEPT_STORE_B},{DEPT_STORE_B},
   {sql_quote(PREFIX + 'CONTRACT_B')},{sql_quote(PREFIX + 'CONTRACT_B_DRAFT')},
   {sql_quote(other_b_name)},{sql_quote(id_card)},{sql_quote(phone_b)},
   {sql_quote(PREFIX + 'POST')},'有社保','2026-08-01','2027-07-31',
   1.00,1.00,'draft','internal',{sql_quote(marker)},{now},{sql_quote(marker)});

INSERT INTO sys_job
  (job_id,job_name,job_group,invoke_target,cron_expression,misfire_policy,
   concurrent,status,create_by,create_time,remark)
VALUES
  ({JOB_ID},{sql_quote(PREFIX + 'NOOP_JOB')},{sql_quote(PREFIX + 'GROUP')},
   'ryTask.ryNoParams','0 0 0 1 1 ?', '3','1','1',
   {sql_quote(marker)},{now},
   {sql_quote(PREFIX + 'PAUSED_NO_EXTERNAL_SIDE_EFFECT')});

INSERT INTO inv_transfer_order
  (transfer_id,order_no,from_dept_id,from_dept_name,from_warehouse_id,
   to_dept_id,to_dept_name,to_warehouse_id,status,approval_instance_id,
   approval_round,approval_engine,total_quantity,total_amount,transfer_type,
   create_by,create_time,update_by,update_time,remark,version,submitted_time)
VALUES
  ({TRANSFER_DRAFT},{sql_quote(PREFIX + 'TRANSFER_DRAFT')},
   {DEPT_WAREHOUSE_A},{sql_quote(PREFIX + 'WAREHOUSE_A')},{DEPT_WAREHOUSE_A},
   {DEPT_STORE_A},{sql_quote(PREFIX + 'STORE_A')},{DEPT_STORE_A},'draft',NULL,
   0,'LEGACY',1.00,10.00,'warehouse',{sql_quote(marker)},{now},
   {sql_quote(marker)},{now},{sql_quote(marker)},0,NULL),
  ({TRANSFER_APPROVAL},{sql_quote(PREFIX + 'TRANSFER_APPROVAL')},
   {DEPT_WAREHOUSE_A},{sql_quote(PREFIX + 'WAREHOUSE_A')},{DEPT_WAREHOUSE_A},
   {DEPT_STORE_A},{sql_quote(PREFIX + 'STORE_A')},{DEPT_STORE_A},'submitted',
   {APPROVAL_INSTANCE},{APPROVAL_FIXTURE_ROUND},'NATIVE',1.00,10.00,'warehouse',
   {sql_quote(marker)},{now},{sql_quote(marker)},{now},{sql_quote(marker)},0,{now});

INSERT INTO inv_transfer_detail
  (detail_id,transfer_id,item_type,item_id,item_code,item_name,product_id,
   product_name,product_code,quantity,delivered_quantity,received_quantity,
   cost_price,amount,unit,spec,grade,sort_order,goods_condition,condition_note)
VALUES
  ({TRANSFER_DETAIL_DRAFT},{TRANSFER_DRAFT},'product',{PRODUCT_A},
   {sql_quote(PREFIX + 'SKU_A')},{sql_quote(PREFIX + 'PRODUCT_A')},{PRODUCT_A},
   {sql_quote(PREFIX + 'PRODUCT_A')},{sql_quote(PREFIX + 'SKU_A')},
   1.00,0.00,0.00,10.00,10.00,'件','QA','QA',0,'NORMAL',
   {sql_quote(marker)}),
  ({TRANSFER_DETAIL_APPROVAL},{TRANSFER_APPROVAL},'product',{PRODUCT_A},
   {sql_quote(PREFIX + 'SKU_A')},{sql_quote(PREFIX + 'PRODUCT_A')},{PRODUCT_A},
   {sql_quote(PREFIX + 'PRODUCT_A')},{sql_quote(PREFIX + 'SKU_A')},
   1.00,0.00,0.00,10.00,10.00,'件','QA','QA',0,'NORMAL',
   {sql_quote(marker)});

INSERT INTO approval_rule
  (rule_id,template_id,rule_code,rule_name,scope_type,scope_id,scope_name,
   business_subtype,rule_status,current_version_id,latest_version_no,
   lock_version,create_by,create_time,remark)
VALUES
  ({APPROVAL_RULE},2,{sql_quote(PREFIX + 'TRANSFER_RULE')},
   {sql_quote(PREFIX + 'TRANSFER_RULE')},'STORE',{DEPT_STORE_A},
   {sql_quote(PREFIX + 'STORE_A')},'warehouse','ACTIVE',{APPROVAL_VERSION},1,0,
   {sql_quote(marker)},{now},{sql_quote(marker)});

INSERT INTO approval_rule_version
  (version_id,rule_id,version_no,version_status,definition_snapshot,
   definition_checksum,published_by_user_id,published_by_name,published_time,
   lock_version,create_by,create_time,remark)
VALUES
  ({APPROVAL_VERSION},{APPROVAL_RULE},1,'PUBLISHED',
   {sql_quote(rule_snapshot_text)},{sql_quote(rule_checksum)},{USER_PRIVILEGED},
   {sql_quote(privileged_name)},{now},0,{sql_quote(marker)},{now},
   {sql_quote(marker)});

INSERT INTO approval_version_node
  (node_id,version_id,node_order,node_code,node_name,strategy_type,
   strategy_code,strategy_config,approval_mode,required_count,missing_policy,
   self_policy,return_allowed,reject_allowed,create_by,create_time,remark)
VALUES
  ({APPROVAL_NODE},{APPROVAL_VERSION},1,
   {sql_quote(PREFIX + 'MANAGER_APPROVAL')},
   {sql_quote(PREFIX + 'MANAGER_APPROVAL')},'FIXED_USERS','FIXED_USERS',
   {sql_quote(json.dumps({'userIds':[USER_MANAGER],'permission':'inv:transfer:approve'}, separators=(',', ':')))},
   'ANY_ONE',1,'BLOCK','BLOCK','1','1',{sql_quote(marker)},{now},
   {sql_quote(marker)});

INSERT INTO approval_instance
  (instance_id,root_instance_id,template_id,business_code,business_source,
   business_id,business_subtype,business_round,idempotency_key,
   applicant_user_id,applicant_name,applicant_dept_id,applicant_dept_name,
   anchor_dept_id,anchor_dept_name,rule_id,rule_version_id,rule_version_no,
   business_snapshot,business_digest,route_snapshot,status,current_node_id,
   current_node_order,callback_status,approved_action_count,started_time,
   lock_version,create_by,create_time,remark)
VALUES
  ({APPROVAL_INSTANCE},{APPROVAL_INSTANCE},2,'INV_TRANSFER','inventory',
   {sql_quote(str(TRANSFER_APPROVAL))},'warehouse',{APPROVAL_FIXTURE_ROUND},
   {sql_quote(PREFIX + 'APPROVAL_INSTANCE_' + str(APPROVAL_FIXTURE_ROUND))},{USER_EMPLOYEE},
   {sql_quote(employee_name)},{DEPT_STORE_A},{sql_quote(PREFIX + 'STORE_A')},
   {DEPT_STORE_A},{sql_quote(PREFIX + 'STORE_A')},{APPROVAL_RULE},
   {APPROVAL_VERSION},1,{sql_quote(business_snapshot)},
   {sql_quote(business_digest)},{sql_quote(route_snapshot)},'RUNNING',
   {APPROVAL_NODE},1,'NONE',0,{now},0,{sql_quote(marker)},{now},
   {sql_quote(marker)});

INSERT INTO approval_task
  (task_id,instance_id,node_id,node_order,node_code,node_name,task_status,
   approval_mode,required_count,completed_count,activated_time,lock_version,
   create_by,create_time,remark)
VALUES
  ({APPROVAL_TASK},{APPROVAL_INSTANCE},{APPROVAL_NODE},1,
   {sql_quote(PREFIX + 'MANAGER_APPROVAL')},
   {sql_quote(PREFIX + 'MANAGER_APPROVAL')},'PENDING','ANY_ONE',1,0,{now},0,
   {sql_quote(marker)},{now},{sql_quote(marker)});

INSERT INTO approval_task_candidate
  (candidate_id,task_id,instance_id,user_id,user_name,dept_id,dept_name,
   candidate_source_type,candidate_source_code,candidate_order,
   candidate_status,lock_version,create_by,create_time,remark)
VALUES
  ({APPROVAL_CANDIDATE},{APPROVAL_TASK},{APPROVAL_INSTANCE},{USER_MANAGER},
   {sql_quote(manager_name)},{DEPT_STORE_A},{sql_quote(PREFIX + 'STORE_A')},
   'FIXED_USERS',{sql_quote(PREFIX + 'MANAGER_APPROVAL')},1,'PENDING',0,
   {sql_quote(marker)},{now},{sql_quote(marker)});

COMMIT;
"""


def verify_expected_counts() -> dict[str, int]:
    checks = {
        "departments": (
            "SELECT COUNT(*) FROM sys_dept "
            f"WHERE dept_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "roles": (
            "SELECT COUNT(*) FROM sys_role "
            f"WHERE role_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "users": (
            "SELECT COUNT(*) FROM sys_user "
            f"WHERE user_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "profiles": (
            "SELECT COUNT(*) FROM sys_user_profile "
            f"WHERE profile_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "products": (
            "SELECT COUNT(*) FROM inv_product "
            f"WHERE product_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "stocks": (
            "SELECT COUNT(*) FROM inv_stock "
            f"WHERE stock_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "contracts": (
            "SELECT COUNT(*) FROM oa_labor_contract "
            f"WHERE contract_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "jobs": (
            "SELECT COUNT(*) FROM sys_job "
            f"WHERE job_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "transfers": (
            "SELECT COUNT(*) FROM inv_transfer_order "
            f"WHERE transfer_id BETWEEN {ID_MIN} AND {ID_MAX}"
        ),
        "approvalInstances": (
            "SELECT COUNT(*) FROM approval_instance "
            f"WHERE instance_id={APPROVAL_INSTANCE}"
        ),
    }
    result: dict[str, int] = {}
    for label, query in checks.items():
        output = mysql(query + ";").strip()
        result[label] = int(output or "0")
    expected = {
        "departments": 4,
        "roles": 3,
        "users": 5,
        "profiles": 5,
        "products": 2,
        "stocks": 2,
        "contracts": 2,
        "jobs": 1,
        "transfers": 2,
        "approvalInstances": 1,
    }
    if result != expected:
        raise QaSafetyError(
            "prepared QA record counts do not match the declared manifest"
        )
    return result


def main() -> int:
    try:
        require_write_approval()
        base_url = validate_loopback_base_url()
        database = verify_database_identity()
        services = verify_local_services()
        before_prefix = prefix_hits()
        before_ids = high_id_hits()
        if before_prefix or before_ids:
            raise QaSafetyError(
                "QA prefix/high-ID range is not empty; run the exact cleanup first"
            )
        auto_increment = capture_auto_increment()
        missing_tables = [
            table
            for table in ("inv_transfer_approval_start_outbox",)
            if not table_exists(table)
        ]

        passwords = {actor: random_password() for actor in ACTORS}
        hashes = {
            actor: password_hash(password)
            for actor, password in passwords.items()
        }
        other_password = random_password()
        mysql(build_prepare_sql(hashes, password_hash(other_password)))
        counts = verify_expected_counts()

        credential_payload = {
            "schemaVersion": 1,
            "runId": RUN_ID,
            "baseUrl": base_url,
            "actors": {
                actor: {
                    "username": metadata["username"],
                    "password": passwords[actor],
                    "userId": metadata["userId"],
                    "roleId": metadata["roleId"],
                    "deptId": metadata["deptId"],
                }
                for actor, metadata in ACTORS.items()
            },
        }
        owner_only_json(CREDENTIAL_PATH, credential_payload)

        manifest = {
            "schemaVersion": 1,
            "runId": RUN_ID,
            "prefix": PREFIX,
            "createdAt": datetime.now(timezone.utc).isoformat(),
            "baseUrl": base_url,
            "database": database,
            "services": services,
            "preflight": {
                "prefixHitsBefore": before_prefix,
                "highIdHitsBefore": before_ids,
                "missingRequiredSchema": missing_tables,
                "profilesVerifiedExternally": [
                    "local",
                    "dev",
                ],
            },
            "autoIncrementBefore": auto_increment,
            "counts": counts,
            "actors": {
                actor: {
                    key: value
                    for key, value in metadata.items()
                    if key != "password"
                }
                for actor, metadata in ACTORS.items()
            },
            "ids": {
                "company": DEPT_COMPANY,
                "storeA": DEPT_STORE_A,
                "storeB": DEPT_STORE_B,
                "warehouseA": DEPT_WAREHOUSE_A,
                "otherUserA": USER_OTHER_A,
                "otherUserB": USER_OTHER_B,
                "productA": PRODUCT_A,
                "productB": PRODUCT_B,
                "stockA": STOCK_A,
                "stockB": STOCK_B,
                "contractA": CONTRACT_A,
                "contractB": CONTRACT_B,
                "job": JOB_ID,
                "transferDraft": TRANSFER_DRAFT,
                "transferApproval": TRANSFER_APPROVAL,
                "approvalInstance": APPROVAL_INSTANCE,
                "approvalTask": APPROVAL_TASK,
            },
            "limitations": [
                "True super-admin behavior is hard-coded to user_id=1; the isolated privileged actor is not equivalent.",
                "inv_transfer_approval_start_outbox is missing while native transfer approval is enabled; real submit must fail closed.",
                "Contract sending/signing is intentionally excluded because no external-sign sandbox is configured.",
            ],
            "credentialPath": str(CREDENTIAL_PATH),
            "cleanupCommand": (
                f"{WRITE_APPROVAL_ENV}={WRITE_APPROVAL_VALUE} "
                "python3 scripts/qa/desktop_release_e2e_cleanup.py "
                f"--confirm {RUN_ID}"
            ),
        }
        owner_only_json(MANIFEST_PATH, manifest)
        print(f"[PASS] isolated QA fixture prepared: {MANIFEST_PATH}")
        print(f"[INFO] owner-only credentials: {CREDENTIAL_PATH}")
        if missing_tables:
            print(
                "[BLOCKED] real transfer submit schema gate: "
                + ",".join(missing_tables)
            )
        return 0
    except (QaSafetyError, OSError, ValueError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
