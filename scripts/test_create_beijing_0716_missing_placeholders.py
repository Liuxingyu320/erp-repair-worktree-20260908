#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import create_beijing_0716_missing_placeholders as target
from reconcile_beijing_0716_employees import load_workbook, sql_quote


DEFAULT_EXCEL = Path(
    "/Users/liuxingyu/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_el0e9x2hyy9022_b7f6/temp/RWTemp/2026-07/"
    "bd517d7a6c153298db1830ef81fbf4c2/北京区域0716-2.xlsx"
)


def workbook_path() -> Path:
    return Path(os.environ.get("BEIJING_0716_EXCEL", DEFAULT_EXCEL)).expanduser().resolve()


def mysql(sql: str, database: str | None = None) -> subprocess.CompletedProcess[str]:
    command = ["mysql", "--batch", "--raw", "--skip-column-names"]
    if database:
        command.append(database)
    return subprocess.run(command, input=sql, text=True, capture_output=True, check=False)


class CreateOnlySqlContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        excel = workbook_path()
        if not excel.is_file():
            raise unittest.SkipTest(f"workbook unavailable: {excel}")
        cls.employees, cls.sha, excluded = load_workbook(excel)
        if excluded != ["段继康"]:
            raise AssertionError(excluded)
        cls.sql = target.build_sql(cls.employees, cls.sha)

    def test_static_create_only_contract(self) -> None:
        sql = self.sql
        self.assertEqual(sql.count("INSERT INTO sys_user("), 6)
        self.assertEqual(sql.count("INSERT INTO sys_user_profile("), 6)
        self.assertEqual(sql.count("INSERT INTO sys_user_post("), 6)
        self.assertEqual(sql.count("INSERT INTO sys_user_role("), 6)
        self.assertEqual(sql.count("INSERT INTO sys_user_shop("), 8)
        self.assertEqual(sql.count("UPDATE hr_employee_no_sequence SET"), 1)
        other_updates = [
            line for line in sql.splitlines()
            if line.startswith("UPDATE ") and not line.startswith("UPDATE hr_employee_no_sequence ")
        ]
        self.assertEqual(other_updates, [])
        self.assertNotRegex(sql, r"(?mi)^DELETE\s")
        self.assertNotRegex(sql, r"(?mi)^(INSERT|UPDATE|DELETE).*sys_legal_entity")
        self.assertNotRegex(sql, r"(?mi)^(INSERT|UPDATE|DELETE).*sys_dict_data")
        self.assertNotRegex(sql, r"(?mi)^(INSERT|UPDATE|DELETE).*sys_security_session_outbox")
        self.assertNotIn("ON DUPLICATE KEY UPDATE", sql)

        hashes = set(re.findall(r"\$2a\$12\$[./A-Za-z0-9]{53}", sql))
        self.assertEqual(len(hashes), 6)
        self.assertNotIn("'TEMPORARY'", sql)
        self.assertEqual(sql.count("'1','0',NULL,'CHANGE_REQUIRED'"), 6)
        self.assertEqual(sql.count("'0','0',NULL,'CHANGE_REQUIRED'"), 0)
        self.assertEqual(sql.count("START TRANSACTION;"), 1)
        self.assertEqual(sql.count("COMMIT;"), 1)
        self.assertEqual(sql.count("GET_LOCK("), 1)
        self.assertEqual(sql.count("RELEASE_LOCK("), 1)

    def test_second_build_uses_new_unissued_hashes(self) -> None:
        other = target.build_sql(*load_workbook(workbook_path())[:2])
        first_hashes = set(re.findall(r"\$2a\$12\$[./A-Za-z0-9]{53}", self.sql))
        other_hashes = set(re.findall(r"\$2a\$12\$[./A-Za-z0-9]{53}", other))
        self.assertEqual(len(first_hashes), 6)
        self.assertEqual(len(other_hashes), 6)
        self.assertTrue(first_hashes.isdisjoint(other_hashes))


class CreateOnlyMysqlIntegrationTest(unittest.TestCase):
    DB = target.EXPECTED_DATABASE

    @classmethod
    def setUpClass(cls) -> None:
        if shutil.which("mysql") is None:
            raise unittest.SkipTest("mysql client unavailable")
        probe = mysql("SELECT 1;")
        if probe.returncode != 0:
            raise unittest.SkipTest("local mysql is not available without interactive credentials")
        exists = mysql(
            f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name={sql_quote(cls.DB)};"
        )
        if exists.returncode != 0 or exists.stdout.strip() != "0":
            raise unittest.SkipTest(f"refusing to replace pre-existing local database {cls.DB}")
        excel = workbook_path()
        if not excel.is_file():
            raise unittest.SkipTest(f"workbook unavailable: {excel}")
        cls.employees, cls.sha, _ = load_workbook(excel)
        cls.apply_sql = target.build_sql(cls.employees, cls.sha)

    @classmethod
    def tearDownClass(cls) -> None:
        exists = mysql(
            f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name={sql_quote(cls.DB)};"
        )
        if exists.returncode == 0 and exists.stdout.strip() == "1":
            mysql(f"DROP DATABASE `{cls.DB}`;")

    def setUp(self) -> None:
        created = mysql(f"CREATE DATABASE `{self.DB}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;")
        self.assertEqual(created.returncode, 0, created.stderr)
        fixture = self.fixture_sql()
        loaded = mysql(fixture, self.DB)
        self.assertEqual(loaded.returncode, 0, loaded.stderr)

    def tearDown(self) -> None:
        mysql(f"DROP DATABASE IF EXISTS `{self.DB}`;")

    def fixture_sql(self) -> str:
        new_employees = [e for e in self.employees if e.name in target.EXPECTED_NEW_NAMES]
        profile_columns = {
            "user_id", "employee_no", "position_no", "legal_entity_id", "create_time", "update_time"
        }
        for source in new_employees:
            profile_columns.update(target.insert_profile_values(source, target.plan_for(source)))
        profile_columns.update({"profile_id"})

        date_columns = {
            column for column in profile_columns
            if column.endswith("_date") or column in {"birth_date", "entry_date"}
        }
        decimal_columns = {
            "base_salary", "post_salary", "field_allowance", "performance_salary", "salary_total"
        }
        bigint_columns = {"profile_id", "user_id", "dept_id", "legal_entity_id"}
        datetime_columns = {"create_time", "update_time"}

        definitions: list[str] = []
        for column in sorted(profile_columns):
            if column == "profile_id":
                definitions.append("profile_id bigint NOT NULL AUTO_INCREMENT")
            elif column == "user_id":
                definitions.append("user_id bigint NOT NULL")
            elif column in bigint_columns:
                definitions.append(f"`{column}` bigint NULL")
            elif column in date_columns:
                definitions.append(f"`{column}` date NULL")
            elif column in decimal_columns:
                definitions.append(f"`{column}` decimal(16,2) NULL")
            elif column in datetime_columns:
                definitions.append(f"`{column}` datetime NULL")
            elif column == "employee_no":
                definitions.append("employee_no varchar(64) NULL")
            elif column == "position_no":
                definitions.append("position_no varchar(96) NULL")
            else:
                definitions.append(f"`{column}` varchar(255) NULL")
        definitions.extend(
            (
                "PRIMARY KEY(profile_id)",
                "UNIQUE KEY uk_profile_user(user_id)",
                "UNIQUE KEY uk_profile_employee(employee_no)",
                "UNIQUE KEY uk_profile_position(position_no)",
            )
        )

        lines = [
            "SET NAMES utf8mb4;",
            "CREATE TABLE sys_user("
            "user_id bigint NOT NULL AUTO_INCREMENT,dept_id bigint,user_name varchar(64),"
            "nick_name varchar(100),user_type varchar(8),email varchar(100),phonenumber varchar(32),"
            "sex char(1),avatar varchar(255),password varchar(255),status char(1),del_flag char(1),"
            "pwd_update_date datetime,credential_state varchar(24),temporary_password_expires_at datetime,"
            "create_by varchar(64),create_time datetime,update_by varchar(64),update_time datetime,"
            "remark varchar(500),PRIMARY KEY(user_id)) ENGINE=InnoDB;",
            f"CREATE TABLE sys_user_profile({','.join(definitions)}) ENGINE=InnoDB;",
            "CREATE TABLE sys_dept(dept_id bigint NOT NULL,parent_id bigint NOT NULL,dept_name varchar(100),"
            "status char(1),del_flag char(1),PRIMARY KEY(dept_id)) ENGINE=InnoDB;",
            "CREATE TABLE sys_post(post_id bigint NOT NULL,post_code varchar(64),post_name varchar(100),"
            "status char(1),PRIMARY KEY(post_id),UNIQUE KEY uk_post_code(post_code)) ENGINE=InnoDB;",
            "CREATE TABLE sys_role(role_id bigint NOT NULL,role_name varchar(100),role_key varchar(100),"
            "data_scope char(1),status char(1),del_flag char(1),PRIMARY KEY(role_id)) ENGINE=InnoDB;",
            "CREATE TABLE sys_user_post(user_id bigint NOT NULL,post_id bigint NOT NULL,"
            "PRIMARY KEY(user_id,post_id)) ENGINE=InnoDB;",
            "CREATE TABLE sys_user_role(user_id bigint NOT NULL,role_id bigint NOT NULL,"
            "PRIMARY KEY(user_id,role_id)) ENGINE=InnoDB;",
            "CREATE TABLE sys_user_shop(user_id bigint NOT NULL,dept_id bigint NOT NULL,is_default char(1),"
            "create_by varchar(64),create_time datetime,PRIMARY KEY(user_id,dept_id)) ENGINE=InnoDB;",
            "CREATE TABLE hr_employee_no_sequence(sequence_key varchar(32) NOT NULL,current_value int unsigned NOT NULL,"
            "max_value int unsigned NOT NULL,create_time datetime,update_time datetime,PRIMARY KEY(sequence_key)) ENGINE=InnoDB;",
            "CREATE TABLE hr_employee_position_no_history("
            "history_id bigint NOT NULL AUTO_INCREMENT,user_id bigint NOT NULL,employee_no varchar(64) NOT NULL,"
            "old_position_no varchar(96),new_position_no varchar(96) NOT NULL,old_post_id bigint,new_post_id bigint,"
            "old_post_code varchar(64),new_post_code varchar(64) NOT NULL,effective_date date,"
            "change_source varchar(32) NOT NULL,change_by varchar(64) NOT NULL,changed_time datetime NOT NULL,"
            "remark varchar(500),PRIMARY KEY(history_id)) ENGINE=InnoDB;",
            "INSERT INTO hr_employee_no_sequence VALUES('GLOBAL',148,99999,NOW(),NOW());",
        ]

        dept_rows: dict[int, tuple[int, str]] = {}
        synthetic_ids: dict[tuple[str, ...], int] = {}
        next_id = 9000
        for department in target.DEPARTMENTS.values():
            parent_id = 0
            prefix: tuple[str, ...] = ()
            for index, name in enumerate(department.path):
                prefix += (name,)
                if prefix == department.path:
                    dept_id = department.dept_id
                else:
                    dept_id = synthetic_ids.get(prefix, next_id)
                    if prefix not in synthetic_ids:
                        synthetic_ids[prefix] = dept_id
                        next_id += 1
                dept_rows[dept_id] = (parent_id, name)
                parent_id = dept_id
        for dept_id, (parent_id, name) in sorted(dept_rows.items()):
            lines.append(
                "INSERT INTO sys_dept VALUES("
                f"{dept_id},{parent_id},{sql_quote(name)},'0','0');"
            )

        for post_name, (post_id, post_code, role_id, role_key) in target.POSTS.items():
            lines.append(
                f"INSERT INTO sys_post VALUES({post_id},{sql_quote(post_code)},{sql_quote(post_name)},'0');"
            )
            lines.append(
                f"INSERT INTO sys_role VALUES({role_id},{sql_quote(post_name)},{sql_quote(role_key)},'4','0','0');"
            )

        existing = [e for e in self.employees if e.name not in target.EXPECTED_NEW_NAMES]
        for index, source in enumerate(existing, start=1):
            employee_no = f"E{index:05d}"
            lines.append(
                "INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,"
                "password,status,del_flag,credential_state,create_by,create_time,update_by,update_time) VALUES("
                + ",".join(
                    (
                        str(index), "1176", sql_quote(f"legacy{index}"), sql_quote(source.name),
                        sql_quote("00"), sql_quote(""), sql_quote(source.phone), sql_quote("2"),
                        sql_quote(""), sql_quote("$2a$12$syntheticExistingHash000000000000000000000000000000"),
                        sql_quote("0"), sql_quote("0"), sql_quote("ACTIVE"), sql_quote("fixture"),
                        "NOW()", sql_quote("fixture"), "NOW()",
                    )
                )
                + ");"
            )
            lines.append(
                "INSERT INTO sys_user_profile(user_id,employee_no,create_time,update_time) VALUES("
                f"{index},{sql_quote(employee_no)},NOW(),NOW());"
            )
            lines.append(f"INSERT INTO sys_user_post VALUES({index},6);")
            lines.append(f"INSERT INTO sys_user_role VALUES({index},101);")
            lines.append(f"INSERT INTO sys_user_shop VALUES({index},1176,'Y','fixture',NOW());")

        lines.extend(
            (
                "DELIMITER $$",
                "CREATE TRIGGER trg_sys_user_profile_position_no_ai AFTER INSERT ON sys_user_profile FOR EACH ROW BEGIN "
                "DECLARE v_post_code varchar(64); DECLARE v_post_id bigint; "
                "IF NULLIF(TRIM(NEW.position_no),'') IS NOT NULL THEN "
                "SET v_post_code=SUBSTRING_INDEX(NEW.position_no,'-',1); "
                "SELECT MIN(post_id) INTO v_post_id FROM sys_post WHERE CAST(UPPER(TRIM(post_code)) AS BINARY)="
                "CAST(UPPER(TRIM(v_post_code)) AS BINARY); "
                "INSERT INTO hr_employee_position_no_history(user_id,employee_no,old_position_no,new_position_no,"
                "old_post_id,new_post_id,old_post_code,new_post_code,effective_date,change_source,change_by,changed_time,remark) "
                "VALUES(NEW.user_id,NEW.employee_no,NULL,NEW.position_no,NULL,v_post_id,NULL,UPPER(TRIM(v_post_code)),"
                "COALESCE(NEW.entry_date,CURRENT_DATE),'PROFILE_INSERT',COALESCE(NULLIF(NEW.create_by,''),'system'),"
                "CURRENT_TIMESTAMP,'fixture'); END IF; END$$",
                "DELIMITER ;",
            )
        )
        return "\n".join(lines) + "\n"

    def counts(self) -> str:
        result = mysql(
            "SELECT CONCAT_WS('/',"
            "(SELECT COUNT(*) FROM sys_user),(SELECT COUNT(*) FROM sys_user_profile),"
            "(SELECT COUNT(*) FROM sys_user_post),(SELECT COUNT(*) FROM sys_user_role),"
            "(SELECT COUNT(*) FROM sys_user_shop),(SELECT COUNT(*) FROM hr_employee_position_no_history),"
            "(SELECT current_value FROM hr_employee_no_sequence WHERE sequence_key='GLOBAL'));",
            self.DB,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def test_failure_rolls_back_then_success_and_replay_is_safe(self) -> None:
        baseline = self.counts()
        trigger = mysql(
            "DELIMITER $$\n"
            "CREATE TRIGGER it_fail_fourth_profile BEFORE INSERT ON sys_user_profile FOR EACH ROW BEGIN "
            "IF NEW.employee_no='E00152' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IT_FAIL_AFTER_THREE'; END IF; END$$\n"
            "DELIMITER ;\n",
            self.DB,
        )
        self.assertEqual(trigger.returncode, 0, trigger.stderr)
        failed = mysql(self.apply_sql, self.DB)
        self.assertNotEqual(failed.returncode, 0)
        self.assertIn("IT_FAIL_AFTER_THREE", failed.stderr)
        self.assertEqual(self.counts(), baseline)
        lock_probe = mysql(
            f"SELECT GET_LOCK({sql_quote(target.LOCK_NAME)},0); DO RELEASE_LOCK({sql_quote(target.LOCK_NAME)});",
            self.DB,
        )
        self.assertEqual(lock_probe.returncode, 0, lock_probe.stderr)
        self.assertEqual(lock_probe.stdout.strip(), "1")

        dropped = mysql("DROP TRIGGER it_fail_fourth_profile;", self.DB)
        self.assertEqual(dropped.returncode, 0, dropped.stderr)
        applied = mysql(self.apply_sql, self.DB)
        self.assertEqual(applied.returncode, 0, applied.stderr)
        self.assertIn("CREATE_ONLY_APPLY_OK", applied.stdout)
        self.assertEqual(self.counts(), "27/27/27/27/29/6/154")

        replay = mysql(self.apply_sql, self.DB)
        self.assertNotEqual(replay.returncode, 0)
        self.assertEqual(self.counts(), "27/27/27/27/29/6/154")


if __name__ == "__main__":
    unittest.main(verbosity=2)
