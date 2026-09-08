#!/usr/bin/env python3
"""Build a production-guarded SQL change for the six missing Beijing accounts.

This builder is intentionally narrower than the reconciliation utility:

* all 27 workbook identities are checked, but only the exact six missing users
  can be written;
* the six accounts are created disabled and no deliverable password exists;
* legal-entity and dictionary master data are never created or changed;
* the 21 existing users and all of their related rows are hashed before and
  after the inserts while the transaction is still open.

The generated SQL contains employee PII and password hashes.  It must be kept
mode 0600, transferred through the sensitive SQL runner, and deleted after use.
"""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import os
import re
import secrets
from pathlib import Path

import bcrypt

from reconcile_beijing_0716_employees import (
    EXPECTED_MAIN_COUNT,
    EXPECTED_NEW_NAMES,
    SourceEmployee,
    load_workbook,
    phone,
    profile_values,
    sql_quote,
    text,
)


EXPECTED_WORKBOOK_SHA256 = "d69e24020cc16a640729809aae2051a785c63f3742786e9ff0d86bb82234c0e5"
EXPECTED_DATABASE = "bosserp_stock_state_75c59ee"
EXPECTED_SEQUENCE = 148
EXPECTED_FINAL_SEQUENCE = EXPECTED_SEQUENCE + len(EXPECTED_NEW_NAMES)
EXPECTED_EXISTING_COUNT = EXPECTED_MAIN_COUNT - len(EXPECTED_NEW_NAMES)
EMPLOYEE_PREFIX = "E"
OPERATOR = "beijing0716_placeholder"
LOCK_NAME = "erp:employee:beijing:0716:create-only"
ORGANIZATION_CONFIRMATION = (
    "FOURTH_LEVEL_STORE;LI_MAN=BEIJING_PARK_HYATT;"
    "LIU_JIE=XIAN_REGION+XIAN_GRAND_HYATT+HUAIYANG_1949;"
    "SU_YUYU=XIAN_REGION_MINIMUM"
)


@dataclasses.dataclass(frozen=True)
class DepartmentPlan:
    dept_id: int
    path: tuple[str, ...]


@dataclasses.dataclass(frozen=True)
class EmployeePlan:
    dept: DepartmentPlan
    scopes: tuple[DepartmentPlan, ...]
    post_id: int
    post_code: str
    role_id: int
    role_key: str


DEPARTMENTS = {
    "BEIJING_PARK_HYATT": DepartmentPlan(
        1176, ("总部", "金英灵韵", "北京区域", "北京区域运营", "北京柏悦")
    ),
    "WANDA_VISTA": DepartmentPlan(
        1171, ("总部", "金英灵韵", "北京区域", "北京区域运营", "万达文华")
    ),
    "XIAN_REGION": DepartmentPlan(
        1157, ("总部", "金英灵韵", "北京区域", "西安区域运营")
    ),
    "HUAIYANG_1949": DepartmentPlan(
        1185, ("总部", "金英灵韵", "北京区域", "西安区域运营", "淮扬1949")
    ),
    "XIAN_GRAND_HYATT": DepartmentPlan(
        1186, ("总部", "金英灵韵", "北京区域", "西安区域运营", "西安君悦")
    ),
}

POSTS = {
    "实习生": (5, "sxs", 100, "sxs"),
    "茶艺师": (6, "cys", 101, "cys"),
    "区域运营总监": (74, "qyyyzzj", 106, "qyyyzzj"),
}


def plan_for(source: SourceEmployee) -> EmployeePlan:
    if source.name in {"李曼", "马毓谦"}:
        dept = DEPARTMENTS["BEIJING_PARK_HYATT"]
        scopes = (dept,)
    elif source.name in {"任艳琪", "何顺琪"}:
        dept = DEPARTMENTS["WANDA_VISTA"]
        scopes = (dept,)
    elif source.name == "刘捷":
        dept = DEPARTMENTS["XIAN_REGION"]
        scopes = (
            dept,
            DEPARTMENTS["HUAIYANG_1949"],
            DEPARTMENTS["XIAN_GRAND_HYATT"],
        )
    elif source.name == "苏余玉":
        # The fourth-level store cell is blank.  The signing sheet says Xi'an;
        # use only the Xi'an region parent, without guessing a store scope.
        dept = DEPARTMENTS["XIAN_REGION"]
        scopes = (dept,)
    else:
        raise ValueError(f"no create-only production plan for {source.name}")

    post_id, post_code, role_id, role_key = POSTS[source.post]
    return EmployeePlan(dept, scopes, post_id, post_code, role_id, role_key)


def fingerprint(source: SourceEmployee) -> str:
    raw = "|".join(
        (source.name, source.phone, text(source.profile.get("id_number")).upper())
    )
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:12]


def guard(label: str, condition: str) -> list[str]:
    safe = re.sub(r"[^a-z0-9_]", "_", label.lower())[:42]
    failure_table = f"erp_assert_{safe}"
    return [
        f"SET @erp_guard_sql=IF(({condition}),'DO 0',"
        f"'SELECT * FROM information_schema.`{failure_table}`');",
        "PREPARE erp_guard FROM @erp_guard_sql;",
        "EXECUTE erp_guard;",
        "DEALLOCATE PREPARE erp_guard;",
    ]


def identity_predicate(source: SourceEmployee) -> str:
    return "(" + " OR ".join(
        (
            f"CAST(nick_name AS BINARY)=CAST({sql_quote(source.name)} AS BINARY)",
            f"CAST(phonenumber AS BINARY)=CAST({sql_quote(source.phone)} AS BINARY)",
            f"CAST(user_name AS BINARY)=CAST({sql_quote(source.phone)} AS BINARY)",
        )
    ) + ")"


def department_guard(plan: DepartmentPlan) -> str:
    aliases = [f"d{i}" for i in range(len(plan.path))]
    clauses = [f"{aliases[0]}.dept_id={plan.dept_id}"]
    for index, name in enumerate(reversed(plan.path)):
        alias = aliases[index]
        clauses.extend(
            (
                f"CAST({alias}.dept_name AS BINARY)=CAST({sql_quote(name)} AS BINARY)",
                f"{alias}.status='0'",
                f"{alias}.del_flag='0'",
            )
        )
    clauses.append(f"{aliases[-1]}.parent_id=0")
    joins = " ".join(
        f"JOIN sys_dept {aliases[index]} ON {aliases[index]}.dept_id={aliases[index - 1]}.parent_id"
        for index in range(1, len(aliases))
    )
    return (
        f"(SELECT COUNT(*) FROM sys_dept {aliases[0]} {joins} "
        f"WHERE {' AND '.join(clauses)})=1"
    )


def random_unissued_bcrypt() -> str:
    raw = secrets.token_urlsafe(48).encode("ascii")
    hashed = bcrypt.hashpw(raw, bcrypt.gensalt(rounds=12)).decode("ascii")
    del raw
    # Production currently stores the Spring-compatible $2a$ marker.  The
    # generated password is ASCII, so the $2a$/$2b$ edge-case difference does
    # not apply; no plaintext credential is retained or delivered.
    if hashed.startswith("$2b$"):
        hashed = "$2a$" + hashed[4:]
    return hashed


PROFILE_OMISSIONS = {
    # These compatibility columns are absent from the running schema.
    "employee_name",
    "phone_number",
    "sex",
    "social_security_type",
    "profile_source",
    "last_profile_update_by",
    "last_profile_update_time",
    # Stable legal-entity master data is not configured in production.  Keep
    # only the workbook's legal_entity text and do not invent an ID/code.
    "legal_entity_code",
}


def insert_profile_values(source: SourceEmployee, plan: EmployeePlan) -> dict[str, object]:
    source.dept_id = plan.dept.dept_id
    source.scope_dept_ids = [item.dept_id for item in plan.scopes]
    source.post_id = plan.post_id
    source.post_code = plan.post_code
    source.role_id = plan.role_id
    source.dept_path = plan.dept.path[1:]
    values = profile_values(source, plan.dept.dept_id)
    for key in PROFILE_OMISSIONS:
        values.pop(key, None)
    values["create_by"] = OPERATOR
    return values


def dynamic_hash(table: str, variable: str) -> list[str]:
    if not re.fullmatch(r"[a-z0-9_]+", table):
        raise ValueError(table)
    if not re.fullmatch(r"@[a-z0-9_]+", variable):
        raise ValueError(variable)
    return [
        "SET @erp_column_expr=NULL;",
        "SELECT GROUP_CONCAT(CONCAT('COALESCE(HEX(`',"
        "REPLACE(column_name,'`','``'),'`),''NULL'')') "
        "ORDER BY ordinal_position SEPARATOR ',') INTO @erp_column_expr "
        "FROM information_schema.columns "
        f"WHERE table_schema=DATABASE() AND table_name={sql_quote(table)};",
        *guard(f"hash_columns_{table}", "@erp_column_expr IS NOT NULL"),
        "SET @erp_hash_sql=CONCAT("
        "'SELECT SHA2(COALESCE(GROUP_CONCAT(CONCAT_WS(''~'',',"
        "@erp_column_expr,"
        "') ORDER BY CONCAT_WS(''~'',',"
        "@erp_column_expr,"
        f"') SEPARATOR ''|''),''''),256) INTO {variable} FROM `{table}` "
        "WHERE user_id IN (SELECT user_id FROM tmp_existing_user_ids)');",
        "PREPARE erp_hash FROM @erp_hash_sql;",
        "EXECUTE erp_hash;",
        "DEALLOCATE PREPARE erp_hash;",
    ]


def schema_guard(table: str, columns: set[str]) -> str:
    values = ",".join(sql_quote(column) for column in sorted(columns))
    return (
        "(SELECT COUNT(DISTINCT column_name) FROM information_schema.columns "
        f"WHERE table_schema=DATABASE() AND table_name={sql_quote(table)} "
        f"AND column_name IN ({values}))={len(columns)}"
    )


def build_sql(all_employees: list[SourceEmployee], workbook_sha: str) -> str:
    if workbook_sha != EXPECTED_WORKBOOK_SHA256:
        raise ValueError(f"unexpected workbook sha256: {workbook_sha}")
    if len(all_employees) != EXPECTED_MAIN_COUNT:
        raise ValueError(f"unexpected source population: {len(all_employees)}")

    new_employees = [item for item in all_employees if item.name in EXPECTED_NEW_NAMES]
    existing_employees = [item for item in all_employees if item.name not in EXPECTED_NEW_NAMES]
    if {item.name for item in new_employees} != EXPECTED_NEW_NAMES:
        raise ValueError("new employee set differs from the locked production preflight")
    if len(existing_employees) != EXPECTED_EXISTING_COUNT:
        raise ValueError("existing employee set differs from the locked production preflight")

    plans: dict[str, EmployeePlan] = {}
    profile_rows: dict[str, dict[str, object]] = {}
    hashes: dict[str, str] = {}
    for source in new_employees:
        plan = plan_for(source)
        plans[source.name] = plan
        profile_rows[source.name] = insert_profile_values(source, plan)
        hashes[source.name] = random_unissued_bcrypt()

    required_user_columns = {
        "dept_id", "user_name", "nick_name", "user_type", "email", "phonenumber",
        "sex", "avatar", "password", "status", "del_flag", "pwd_update_date",
        "credential_state", "temporary_password_expires_at", "create_by", "create_time",
        "update_by", "update_time", "remark",
    }
    required_profile_columns = {
        "user_id", "employee_no", "position_no", "legal_entity_id",
        "create_time", "update_time", *{
            key for values in profile_rows.values() for key in values
        },
    }
    required_relations = {
        "sys_user_post": {"user_id", "post_id"},
        "sys_user_role": {"user_id", "role_id"},
        "sys_user_shop": {"user_id", "dept_id", "is_default", "create_by", "create_time"},
    }

    lines = [
        "-- Sensitive create-only batch generated from Beijing 0716 workbook.",
        "-- Contains PII and password hashes; keep 0600 and delete after use.",
        "SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;",
        "SET SESSION group_concat_max_len=67108864;",
        *guard(
            "database_exact",
            f"CAST(DATABASE() AS BINARY)=CAST({sql_quote(EXPECTED_DATABASE)} AS BINARY)",
        ),
        *guard("schema_sys_user", schema_guard("sys_user", required_user_columns)),
        *guard("schema_profile", schema_guard("sys_user_profile", required_profile_columns)),
    ]
    for table, columns in required_relations.items():
        lines.extend(guard(f"schema_{table}", schema_guard(table, columns)))
    lines.extend(
        guard(
            "schema_sequence",
            schema_guard(
                "hr_employee_no_sequence",
                {"sequence_key", "current_value", "max_value", "update_time"},
            ),
        )
    )
    lines.extend(
        guard(
            "schema_position_history",
            schema_guard(
                "hr_employee_position_no_history",
                {
                    "user_id", "employee_no", "new_position_no", "new_post_id",
                    "new_post_code", "change_source", "change_by", "changed_time",
                },
            ),
        )
    )
    lines.extend(
        guard(
            "profile_insert_trigger",
            "(SELECT COUNT(*) FROM information_schema.triggers "
            "WHERE trigger_schema=DATABASE() AND event_object_table='sys_user_profile' "
            "AND trigger_name='trg_sys_user_profile_position_no_ai' "
            "AND action_timing='AFTER' AND event_manipulation='INSERT')=1",
        )
    )
    lines.extend(
        [
            f"SET @erp_lock_ok=GET_LOCK({sql_quote(LOCK_NAME)},30);",
            *guard("named_lock", "@erp_lock_ok=1"),
            "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;",
            "START TRANSACTION;",
            "CREATE TEMPORARY TABLE tmp_existing_user_ids(user_id bigint PRIMARY KEY) ENGINE=InnoDB;",
            "CREATE TEMPORARY TABLE tmp_created_user_ids(user_id bigint PRIMARY KEY) ENGINE=InnoDB;",
            "SELECT current_value,max_value INTO @erp_sequence,@erp_max_sequence "
            "FROM hr_employee_no_sequence WHERE sequence_key='GLOBAL' FOR UPDATE;",
            *guard("sequence_exact", f"@erp_sequence={EXPECTED_SEQUENCE}"),
            *guard("sequence_capacity", f"@erp_max_sequence>={EXPECTED_FINAL_SEQUENCE}"),
        ]
    )

    # Lock and resolve all 21 existing workbook identities.  They are read-only
    # participants and become the exact set protected by transactional hashes.
    for source in existing_employees:
        predicate = identity_predicate(source)
        label = f"existing_{fingerprint(source)}"
        lines.extend(
            [
                "INSERT INTO tmp_existing_user_ids(user_id) "
                f"SELECT user_id FROM sys_user WHERE del_flag='0' AND {predicate} FOR UPDATE;",
                *guard(label, "ROW_COUNT()=1"),
            ]
        )
    lines.extend(guard("existing_population", f"(SELECT COUNT(*) FROM tmp_existing_user_ids)={EXPECTED_EXISTING_COUNT}"))

    # Every new identity must remain absent, including deleted rows and source
    # ID numbers.  These are locking reads in the same serializable transaction.
    for source in new_employees:
        predicate = identity_predicate(source)
        source_id = text(source.profile.get("id_number")).upper()
        fp = fingerprint(source)
        lines.extend(
            [
                f"SELECT COUNT(*) INTO @erp_identity_count FROM sys_user WHERE {predicate} FOR UPDATE;",
                *guard(f"new_identity_{fp}", "@erp_identity_count=0"),
                f"SELECT COUNT(*) INTO @erp_deleted_count FROM sys_user WHERE del_flag<>'0' AND {predicate} FOR UPDATE;",
                *guard(f"deleted_identity_{fp}", "@erp_deleted_count=0"),
            ]
        )
        if source_id:
            lines.extend(
                [
                    "SELECT COUNT(*) INTO @erp_id_count FROM sys_user_profile "
                    f"WHERE CAST(id_number AS BINARY)=CAST({sql_quote(source_id)} AS BINARY) FOR UPDATE;",
                    *guard(f"new_id_number_{fp}", "@erp_id_count=0"),
                ]
            )

    # Assert every production department path, post and role by both ID and
    # stable business names/keys.  No master-data writes are permitted here.
    unique_departments = {item.dept_id: item for plan in plans.values() for item in (plan.dept, *plan.scopes)}
    for dept_id, dept in sorted(unique_departments.items()):
        lines.extend(guard(f"department_{dept_id}", department_guard(dept)))
        lines.append(f"SELECT dept_id INTO @erp_locked_dept FROM sys_dept WHERE dept_id={dept_id} FOR UPDATE;")
    unique_posts = {
        (source.post, plan.post_id, plan.post_code, plan.role_id, plan.role_key)
        for source in new_employees for plan in (plans[source.name],)
    }
    for post_name, post_id, post_code, role_id, role_key in sorted(unique_posts):
        lines.extend(
            guard(
                f"post_role_{post_id}_{role_id}",
                "(SELECT COUNT(*) FROM sys_post p JOIN sys_role r ON r.role_id="
                f"{role_id} WHERE p.post_id={post_id} "
                f"AND CAST(p.post_code AS BINARY)=CAST({sql_quote(post_code)} AS BINARY) "
                f"AND CAST(p.post_name AS BINARY)=CAST({sql_quote(post_name)} AS BINARY) "
                "AND p.status='0' "
                f"AND CAST(r.role_key AS BINARY)=CAST({sql_quote(role_key)} AS BINARY) "
                f"AND CAST(r.role_name AS BINARY)=CAST({sql_quote(post_name)} AS BINARY) "
                "AND r.status='0' AND r.del_flag='0' AND LOWER(r.role_key)<>'admin')=1",
            )
        )
        lines.append(f"SELECT post_id INTO @erp_locked_post FROM sys_post WHERE post_id={post_id} FOR UPDATE;")
        lines.append(f"SELECT role_id INTO @erp_locked_role FROM sys_role WHERE role_id={role_id} FOR UPDATE;")

    hash_tables = (
        "sys_user", "sys_user_profile", "sys_user_post", "sys_user_role",
        "sys_user_shop", "hr_employee_position_no_history",
    )
    for table in hash_tables:
        lines.extend(dynamic_hash(table, f"@erp_before_{table}"))

    for source in new_employees:
        plan = plans[source.name]
        profile = profile_rows[source.name]
        password_hash = hashes[source.name]
        sex = text(source.profile.get("sex")) or "2"
        lines.extend(
            [
                "SET @erp_sequence=@erp_sequence+1;",
                f"SET @erp_employee_no=CONCAT({sql_quote(EMPLOYEE_PREFIX)},LPAD(@erp_sequence,5,'0'));",
                "SET @erp_position_no="
                f"CONCAT({sql_quote(plan.post_code.upper() + '-')},@erp_employee_no);",
                "SELECT COUNT(*) INTO @erp_number_count FROM sys_user "
                "WHERE CAST(user_name AS BINARY)=CAST(@erp_employee_no AS BINARY) FOR UPDATE;",
                *guard(f"username_free_{fingerprint(source)}", "@erp_number_count=0"),
                "SELECT COUNT(*) INTO @erp_number_count FROM sys_user_profile "
                "WHERE CAST(employee_no AS BINARY)=CAST(@erp_employee_no AS BINARY) "
                "OR CAST(position_no AS BINARY)=CAST(@erp_position_no AS BINARY) FOR UPDATE;",
                *guard(f"employee_number_free_{fingerprint(source)}", "@erp_number_count=0"),
                "INSERT INTO sys_user("
                "dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,password,"
                "status,del_flag,pwd_update_date,credential_state,temporary_password_expires_at,"
                "create_by,create_time,update_by,update_time,remark) VALUES("
                + ",".join(
                    (
                        str(plan.dept.dept_id), "@erp_employee_no", sql_quote(source.name),
                        sql_quote("00"), sql_quote(""), sql_quote(source.phone), sql_quote(sex),
                        sql_quote(""), sql_quote(password_hash), sql_quote("1"), sql_quote("0"),
                        "NULL", sql_quote("CHANGE_REQUIRED"), "NULL", sql_quote(OPERATOR), "NOW()",
                        sql_quote(OPERATOR), "NOW()",
                        sql_quote(
                            f"北京区域0716禁用占位账号;源行{source.row_number};"
                            f"sha256={EXPECTED_WORKBOOK_SHA256[:16]};未签发凭据"
                        ),
                    )
                )
                + ");",
                *guard(f"user_insert_{fingerprint(source)}", "ROW_COUNT()=1"),
                "SET @erp_user_id=LAST_INSERT_ID();",
                "INSERT INTO tmp_created_user_ids(user_id) VALUES(@erp_user_id);",
            ]
        )

        profile_columns = [
            "user_id", "employee_no", "position_no", "legal_entity_id", *profile.keys(),
            "create_time", "update_time",
        ]
        profile_sql_values = ["@erp_user_id", "@erp_employee_no", "@erp_position_no", "NULL"]
        profile_sql_values.extend(sql_quote(value) for value in profile.values())
        profile_sql_values.extend(("NOW()", "NOW()"))
        lines.extend(
            [
                f"INSERT INTO sys_user_profile({','.join(profile_columns)}) "
                f"VALUES({','.join(profile_sql_values)});",
                *guard(f"profile_insert_{fingerprint(source)}", "ROW_COUNT()=1"),
                f"INSERT INTO sys_user_post(user_id,post_id) VALUES(@erp_user_id,{plan.post_id});",
                *guard(f"post_insert_{fingerprint(source)}", "ROW_COUNT()=1"),
                f"INSERT INTO sys_user_role(user_id,role_id) VALUES(@erp_user_id,{plan.role_id});",
                *guard(f"role_insert_{fingerprint(source)}", "ROW_COUNT()=1"),
            ]
        )
        for scope in plan.scopes:
            is_default = "Y" if scope.dept_id == plan.dept.dept_id else "N"
            lines.extend(
                [
                    "INSERT INTO sys_user_shop(user_id,dept_id,is_default,create_by,create_time) VALUES("
                    f"@erp_user_id,{scope.dept_id},{sql_quote(is_default)},{sql_quote(OPERATOR)},NOW());",
                    *guard(f"scope_{scope.dept_id}_{fingerprint(source)}", "ROW_COUNT()=1"),
                ]
            )

        user_conditions = [
            "u.user_id=@erp_user_id",
            "CAST(u.user_name AS BINARY)=CAST(@erp_employee_no AS BINARY)",
            f"CAST(u.nick_name AS BINARY)=CAST({sql_quote(source.name)} AS BINARY)",
            f"CAST(u.phonenumber AS BINARY)=CAST({sql_quote(source.phone)} AS BINARY)",
            f"u.dept_id={plan.dept.dept_id}",
            "u.status='1'", "u.del_flag='0'", "u.credential_state='CHANGE_REQUIRED'",
            "u.temporary_password_expires_at IS NULL", "u.pwd_update_date IS NULL",
            f"CAST(u.password AS BINARY)=CAST({sql_quote(password_hash)} AS BINARY)",
        ]
        profile_conditions = [
            "p.user_id=@erp_user_id",
            "CAST(p.employee_no AS BINARY)=CAST(@erp_employee_no AS BINARY)",
            "CAST(p.position_no AS BINARY)=CAST(@erp_position_no AS BINARY)",
            "p.legal_entity_id IS NULL",
        ]
        profile_conditions.extend(f"p.{column} <=> {sql_quote(value)}" for column, value in profile.items())
        lines.extend(
            guard(
                f"record_{fingerprint(source)}",
                "(SELECT COUNT(*) FROM sys_user u JOIN sys_user_profile p ON p.user_id=u.user_id "
                f"WHERE {' AND '.join(user_conditions + profile_conditions)})=1",
            )
        )
        lines.extend(
            guard(
                f"relations_{fingerprint(source)}",
                f"(SELECT COUNT(*) FROM sys_user_post WHERE user_id=@erp_user_id) = 1 AND "
                f"(SELECT COUNT(*) FROM sys_user_post WHERE user_id=@erp_user_id AND post_id={plan.post_id}) = 1 AND "
                f"(SELECT COUNT(*) FROM sys_user_role WHERE user_id=@erp_user_id) = 1 AND "
                f"(SELECT COUNT(*) FROM sys_user_role WHERE user_id=@erp_user_id AND role_id={plan.role_id}) = 1 AND "
                f"(SELECT COUNT(*) FROM sys_user_shop WHERE user_id=@erp_user_id) = {len(plan.scopes)} AND "
                "(SELECT COUNT(*) FROM sys_user_shop WHERE user_id=@erp_user_id AND is_default='Y') = 1",
            )
        )
        for scope in plan.scopes:
            is_default = "Y" if scope.dept_id == plan.dept.dept_id else "N"
            lines.extend(
                guard(
                    f"scope_verify_{scope.dept_id}_{fingerprint(source)}",
                    "(SELECT COUNT(*) FROM sys_user_shop WHERE user_id=@erp_user_id "
                    f"AND dept_id={scope.dept_id} AND is_default={sql_quote(is_default)})=1",
                )
            )
        lines.extend(
            guard(
                f"history_{fingerprint(source)}",
                "(SELECT COUNT(*) FROM hr_employee_position_no_history "
                "WHERE user_id=@erp_user_id "
                "AND CAST(employee_no AS BINARY)=CAST(@erp_employee_no AS BINARY) "
                "AND CAST(new_position_no AS BINARY)=CAST(@erp_position_no AS BINARY) "
                f"AND new_post_id={plan.post_id} "
                f"AND CAST(new_post_code AS BINARY)=CAST({sql_quote(plan.post_code.upper())} AS BINARY) "
                "AND change_source='PROFILE_INSERT' "
                f"AND change_by={sql_quote(OPERATOR)})=1",
            )
        )

    lines.extend(
        [
            "UPDATE hr_employee_no_sequence SET current_value=@erp_sequence,update_time=NOW() "
            f"WHERE sequence_key='GLOBAL' AND current_value={EXPECTED_SEQUENCE};",
            *guard("sequence_update", "ROW_COUNT()=1"),
            *guard("created_population", f"(SELECT COUNT(*) FROM tmp_created_user_ids)={len(new_employees)}"),
            *guard("final_sequence", f"@erp_sequence={EXPECTED_FINAL_SEQUENCE}"),
        ]
    )

    for table in hash_tables:
        lines.extend(dynamic_hash(table, f"@erp_after_{table}"))
        lines.extend(
            guard(
                f"unchanged_{table}",
                f"@erp_before_{table} <=> @erp_after_{table}",
            )
        )

    all_identity_conditions = " OR ".join(identity_predicate(source) for source in all_employees)
    lines.extend(
        [
            *guard(
                "final_workbook_population",
                f"(SELECT COUNT(*) FROM sys_user WHERE del_flag='0' AND ({all_identity_conditions}))={EXPECTED_MAIN_COUNT}",
            ),
            "COMMIT;",
            f"DO RELEASE_LOCK({sql_quote(LOCK_NAME)});",
            "SELECT 'CREATE_ONLY_APPLY_OK' AS result,"
            f"{len(new_employees)} AS created_disabled_accounts,"
            f"{EXPECTED_EXISTING_COUNT} AS existing_accounts_unchanged,"
            f"{EXPECTED_FINAL_SEQUENCE} AS final_sequence;",
        ]
    )
    return "\n".join(lines) + "\n"


def write_sensitive(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise FileExistsError(path)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)
    path.chmod(0o600)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--database", default=EXPECTED_DATABASE)
    parser.add_argument("--expected-sequence", type=int, default=EXPECTED_SEQUENCE)
    parser.add_argument("--organization-confirmation", required=True)
    args = parser.parse_args()

    if args.database != EXPECTED_DATABASE:
        raise SystemExit(f"refusing unexpected database: {args.database}")
    if args.expected_sequence != EXPECTED_SEQUENCE:
        raise SystemExit(f"refusing unexpected sequence: {args.expected_sequence}")
    if args.organization_confirmation != ORGANIZATION_CONFIRMATION:
        raise SystemExit("organization confirmation does not match the approved fourth-level-store plan")

    excel = Path(args.excel).expanduser().resolve()
    output = Path(args.output).expanduser().resolve()
    employees, workbook_sha, sign_only = load_workbook(excel)
    if sign_only != ["段继康"]:
        raise SystemExit(f"unexpected signing-only rows: {sign_only}")
    sql = build_sql(employees, workbook_sha)
    write_sensitive(output, sql)
    print(f"workbook_sha256={workbook_sha}")
    print(f"source_rows={len(employees)} existing_read_only={EXPECTED_EXISTING_COUNT} create_disabled={len(EXPECTED_NEW_NAMES)}")
    print(f"employee_numbers={EMPLOYEE_PREFIX}{EXPECTED_SEQUENCE + 1:05d}-{EMPLOYEE_PREFIX}{EXPECTED_FINAL_SEQUENCE:05d}")
    print(f"sensitive_sql={output} mode={oct(output.stat().st_mode & 0o777)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
