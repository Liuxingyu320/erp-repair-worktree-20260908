#!/usr/bin/env python3
from __future__ import annotations

import argparse
import dataclasses
import hashlib
import os
import re
import subprocess
import sys
import time
from collections import defaultdict
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple


DEFAULT_EXCEL_PATH = (
    "/Users/liuxingyu/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_el0e9x2hyy9022_b7f6/temp/RWTemp/2026-06/"
    "559f94eccc121c4784e667d4eef125da/"
    "北京金英灵韵茶业有限公司在职员工20260619(3).xls"
)

MYSQL_DEFAULT_DB = "BossERP_NEW"
IMPORT_BY = "employee_xls_import"
DEFAULT_PASSWORD_HASH = "$2a$10$VrrYtqOtePcuChoQC2RFJOdra/YSr8qL5nxxUX9AC2bw29qyEFIvC"
DEFAULT_POST_NAME = "茶艺师"

REGION_SHEETS = ["北京区域", "浙江一区", "浙江二三区", "江苏区域", "上海区域"]
SHEET1_ALIASES = ["Sheet1", "解释表", "拆分一些组合酒店解释表", "有一些表中会有组合酒店拆分一些组合酒店解释表"]
SHEET2_ALIASES = ["Sheet2", "职位角色表"]
OPTIONAL_MAIN_SHEETS = ["行政后勤"]
SHEET1_NON_PROJECT_LABELS = {"运营管理"}
MAIN_DEPT_RANK_OFFICIAL = 5
MAIN_DEPT_RANK_DETAIL_STORE = 10
MAIN_DEPT_RANK_MAIN_STORE = 20
MAIN_DEPT_RANK_CHILD_SCOPE = 40
MAIN_DEPT_RANK_AGGREGATE = 50
BASE_DEPT_PATHS = {
    ("金英灵韵",),
    ("杭州名田",),
    ("行政部门",),
    ("仓库后勤部门",),
}
FORCED_ACTIVE_PHONES = {"15853019762"}
OMITTED_USER_PHONES = {
    "18322779602",  # 王路苑
    "15713306605",  # 郑苏韩
    "18306017257",  # 田秋宁
    "15202983374",  # 陈远静
    "15360045776",  # 隆欣怡
}
COMBINED_PROJECT_SCOPES = {
    ("北京康莱德&丽思卡尔顿", "张丽婷"): ["北京康莱德", "北京丽思卡尔顿"],
    ("北京康莱德&丽思卡尔顿", "杨慧媛"): ["北京康莱德"],
    ("北京康莱德&丽思卡尔顿", "聂伟佳"): ["北京丽思卡尔顿"],
    ("北京康莱德&丽思卡尔顿", "郭彤彤"): ["北京康莱德"],
    ("北京瑞吉&索菲特", "李双"): ["北京瑞吉", "北京索菲特"],
    ("北京瑞吉&索菲特", "刘心雨"): ["北京瑞吉"],
    ("北京瑞吉&索菲特", "武寒影"): ["北京瑞吉"],
    ("北京瑞吉&索菲特", "赫亚茹"): ["北京索菲特"],
    ("苏州狮山悦榕庄&成都宴", "李希霞"): ["苏州狮山悦榕庄", "成都宴"],
    ("苏州狮山悦榕庄&成都宴", "潘蝶"): ["苏州狮山悦榕庄"],
    ("苏州狮山悦榕庄&成都宴", "王美芳"): ["成都宴"],
    ("重庆丽思瑞&解放碑凯悦", "王红梅"): ["重庆丽思瑞", "解放碑凯悦"],
    ("重庆丽思瑞&解放碑凯悦", "冉慧文"): ["重庆丽思瑞"],
    ("重庆丽思瑞&解放碑凯悦", "冉晓敏"): ["解放碑凯悦"],
    ("上海瑞吉&麒麟餐厅", "侯元元"): ["上海瑞吉", "麒麟餐厅"],
}
COMBINED_PROJECT_DEFAULT_SCOPES = {
    "通州建国&皇冠": ["通州皇冠"],
}
COMBINED_PROJECT_PATH_SCOPES = {
    ("重庆丽思瑞&解放碑凯悦", "王红梅"): [
        ("金英灵韵", "浙江区域", "浙江二区", "浙江二区D", "重庆丽思瑞"),
        ("金英灵韵", "浙江区域", "浙江二区", "浙江二区D", "解放碑凯悦"),
    ],
    ("重庆丽思瑞&解放碑凯悦", "冉慧文"): [
        ("金英灵韵", "浙江区域", "浙江二区", "浙江二区D", "重庆丽思瑞"),
    ],
    ("重庆丽思瑞&解放碑凯悦", "冉晓敏"): [
        ("金英灵韵", "浙江区域", "浙江二区", "浙江二区D", "解放碑凯悦"),
    ],
}
IGNORED_MAIN_DEPT_PATHS = {
    ("金英灵韵", "浙江区域", "小春山"),
    ("金英灵韵", "浙江区域", "牛头馆"),
}

MITIAN_PROJECT_PARENT_PATHS = {
    "杭州JW万豪": ("名田一区", "杭州区域"),
    "钱塘皇冠假日": ("名田一区", "杭州区域"),
    "杭州远洋凯宾斯基": ("名田一区", "杭州区域"),
    "青山湖万丽": ("名田一区", "杭州区域"),
    "西溪喜来登": ("名田一区", "杭州区域"),
    "滨江艾美": ("名田一区", "杭州区域"),
    "凡云未央餐厅": ("名田一区", "杭州区域"),
    "张家港万豪": ("名田二区", "江苏区域"),
    "常州星河万丽": ("名田二区", "江苏区域"),
    "南京希尔顿": ("名田二区", "江苏区域"),
    "昆山喜来登": ("名田二区", "江苏区域"),
    "扬州凯宾斯基": ("名田二区", "江苏区域"),
    "无锡锦园": ("名田二区", "江苏区域"),
    "太湖喜来登": ("名田二区", "江苏区域"),
    "揭阳万豪": ("名田二区", "广东区域"),
    "安吉索菲特": ("名田二区", "浙江周边"),
    "南浔万豪": ("名田二区", "浙江周边"),
    "德清万豪": ("名田二区", "浙江周边"),
    "小青山": ("名田三区", "富阳区域"),
    "牛头馆": ("名田三区", "富阳区域"),
}
CANONICAL_DEPT_PATHS = {
    ("杭州名田", *parent_path, project_name)
    for project_name, parent_path in MITIAN_PROJECT_PARENT_PATHS.items()
}


POST_ALIASES = {
    "店助": "店长助理",
}

POST_CODES = {
    "实习生": "sxs",
    "茶艺师": "cys",
    "店长助理": "dzzy",
    "预备店长": "ybdz",
    "店长": "dz",
    "AM驻店店长": "amzddz",
    "AM驻店经理": "amzdjl",
    "驻店经理": "zdjl",
    "初级运营经理": "cjyyjl",
    "运营经理": "yyjl",
    "区域运营经理": "qyyyjl",
    "区域运营总监": "qyyyzzj",
    "运营总监": "yyzj",
    "GM": "gm",
    "总经理": "zjl",
    "行政助理": "xzzy",
    "行政经理": "xzjl",
    "产品经理": "cpjl",
    "产品专员": "cpzy",
    "物料采购": "wlcg",
    "运营助理": "yyzl",
}

ROLE_CODES = {
    "实习生": "sxs",
    "茶艺师": "cys",
    "店长助理": "dzzy",
    "店长": "dz",
    "驻店经理": "zdjl",
    "运营经理": "yyjl",
    "区域运营总监": "qyyyzzj",
    "运营总监": "yyzj",
}

DEFAULT_SHEET2_ROWS = [
    ["", "", "", "实习生", ""],
    ["", "", "", "茶艺师", ""],
    ["", "", "", "店长助理", ""],
    ["", "", "预备店长", "店长", ""],
    ["", "", "AM驻店店长", "驻店经理", ""],
    ["", "", "初级运营经理", "运营经理", "区域运营经理"],
    ["", "", "", "区域运营总监", ""],
    ["", "", "", "运营总监", "GM"],
]

SHEET2_ROLE_COLUMN_INDEX = 3
SUPPLEMENTAL_ROLE_BY_POST = {
    "AM驻店经理": "驻店经理",
    "总经理": "运营总监",
}
STORE_LEVEL_ROLE_NAMES = {"实习生", "茶艺师", "店长助理", "店长", "驻店经理"}


@dataclasses.dataclass
class ImportUser:
    phone: str
    name: str
    main_dept_path: Tuple[str, ...]
    project_paths: Set[Tuple[str, ...]]
    post_name: str
    status: str
    sex: str = "2"
    employee_type: str = ""
    entry_date: str = ""
    external_user_id: str = ""
    source_sheets: Set[str] = dataclasses.field(default_factory=set)
    scope_path_order: List[Tuple[str, ...]] = dataclasses.field(default_factory=list)
    main_dept_rank: int = MAIN_DEPT_RANK_AGGREGATE


@dataclasses.dataclass
class ImportPlan:
    users_by_phone: Dict[str, ImportUser]
    skipped_candidates: List[dict]
    post_sort_by_name: Dict[str, int] = dataclasses.field(default_factory=dict)
    role_sort_by_name: Dict[str, int] = dataclasses.field(default_factory=dict)
    role_name_by_post: Dict[str, str] = dataclasses.field(default_factory=dict)

    @property
    def dept_paths(self) -> Set[Tuple[str, ...]]:
        paths: Set[Tuple[str, ...]] = set(BASE_DEPT_PATHS) | set(CANONICAL_DEPT_PATHS)
        for user in self.users_by_phone.values():
            for path in [user.main_dept_path, *user.project_paths]:
                for index in range(1, len(path) + 1):
                    paths.add(path[:index])
        return {path for path in paths if path}

    @property
    def post_names(self) -> Set[str]:
        names = {user.post_name for user in self.users_by_phone.values() if user.post_name}
        names.update(self.post_sort_by_name)
        return names

    def role_name_for_post(self, post_name: str) -> str:
        return self.role_name_by_post.get(normalize_post(post_name), "普通角色")

    def post_sort_for_post(self, post_name: str) -> Optional[int]:
        post_name = normalize_post(post_name)
        if post_name in self.post_sort_by_name:
            return self.post_sort_by_name[post_name]
        role_name = self.role_name_for_post(post_name)
        return self.role_sort_by_name.get(role_name)


def clean(value) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    return re.sub(r"\s+", "", text) if text in {"\n", "\r"} else text


def clean_name(value) -> str:
    return re.sub(r"\s+", "", clean(value))


def phone(value) -> str:
    return re.sub(r"\s+", "", clean(value))


def valid_phone(value: str) -> bool:
    return re.fullmatch(r"1\d{10}", value or "") is not None


def normalize_dept_name(value: str) -> str:
    value = clean(value).replace("（金英）", "").replace("（灵韵）", "")
    value = value.replace("\n", "").strip()
    aliases = {
        "金英灵韵运营部": "总部运营",
        "行政办公室": "行政部门",
        "浙江三区任佳萌": "浙江三区",
        "苏州区域运营": "江苏区域运营",
        "未央": "凡云未央餐厅",
        "味央餐厅": "凡云未央餐厅",
        "钟無宴": "钟无宴",
        "JW万豪": "杭州JW万豪",
        "远洋凯宾斯基": "杭州远洋凯宾斯基",
        "小春山": "牛头馆",
        "舍丽雅": "上海舍丽雅",
        "阿纳迪": "上海阿纳迪",
    }
    return aliases.get(value, value)


def normalize_project_name(value: str) -> str:
    value = normalize_dept_name(value)
    value = value.replace("（", "").replace("）", "")
    value = value.replace("(", "").replace(")", "")
    return value[:30]


def normalize_post(value: str) -> str:
    value = clean(value)
    return POST_ALIASES.get(value, value)


def sex_code(value: str) -> str:
    value = clean(value)
    if value == "男":
        return "0"
    if value == "女":
        return "1"
    return "2"


def account_status(value: str, row_phone: str = "") -> str:
    if row_phone in FORCED_ACTIVE_PHONES:
        return "0"
    return "1" if clean(value) == "待离职" else "0"


def should_import_phone(row_phone: str) -> bool:
    return row_phone not in OMITTED_USER_PHONES


def non_empty_path(*parts: str) -> Tuple[str, ...]:
    cleaned = [normalize_project_name(part) for part in parts]
    result: List[str] = []
    for part in cleaned:
        if part and (not result or result[-1] != part):
            result.append(part)
    return tuple(result)


def zhejiang_level_parts(level3: str) -> List[str]:
    level3 = normalize_project_name(level3)
    if not level3:
        return []
    if level3.startswith("浙江二区") and level3 != "浙江二区":
        return ["浙江二区", level3]
    if level3.startswith("浙江三区"):
        return ["浙江三区"]
    return [level3]


def normalized_jinying_path(level2: str, level3: str = "", project: str = "") -> Tuple[str, ...]:
    level2 = normalize_project_name(level2)
    level3 = normalize_project_name(level3)
    project = normalize_project_name(project)

    if level2 == "西安区域":
        return non_empty_path("金英灵韵", "北京区域", "西安区域运营", project)
    if level3 == "西安区域运营":
        return non_empty_path("金英灵韵", "北京区域", level3, project)
    if level3 == "上海区域运营":
        return non_empty_path("金英灵韵", "上海区域", level3, project)
    if level2 == "上海区域":
        return non_empty_path("金英灵韵", "上海区域", level3 or "上海区域运营", project)
    if level2 == "浙江区域":
        return non_empty_path("金英灵韵", "浙江区域", *zhejiang_level_parts(level3), project)
    if level2 in {"北京区域", "江苏区域"}:
        return non_empty_path("金英灵韵", level2, level3, project)
    return non_empty_path("金英灵韵", level2, level3, project)


def normalized_support_path(level2: str = "", project: str = "") -> Tuple[str, ...]:
    return non_empty_path("仓库后勤部门", level2, project)


def normalized_headquarters_path(level2: str = "", level3: str = "", project: str = "") -> Tuple[str, ...]:
    level2 = normalize_project_name(level2)
    level3 = normalize_project_name(level3)
    project = normalize_project_name(project)
    if not level2 or level2 == "行政部门":
        return ("行政部门",)
    if level2 in {"物料采购群", "食品类以及礼盒采购"}:
        return normalized_support_path(level2, project)
    return non_empty_path("行政部门", level2, level3, project)


def split_project_names(row: dict, project: str) -> List[str]:
    project = normalize_project_name(project)
    if not project:
        return [""]
    name = clean_name(row.get("姓名", ""))
    if (project, name) in COMBINED_PROJECT_SCOPES:
        return COMBINED_PROJECT_SCOPES[(project, name)]
    if project in COMBINED_PROJECT_DEFAULT_SCOPES:
        return COMBINED_PROJECT_DEFAULT_SCOPES[project]
    return [project]


def main_paths(row: dict) -> List[Tuple[str, ...]]:
    level1 = normalize_project_name(row.get("1级部门", ""))
    level2 = normalize_project_name(row.get("2级部门", ""))
    level3 = normalize_project_name(row.get("3级部门", ""))
    project = normalize_project_name(row.get("4级部门", ""))
    name = clean_name(row.get("姓名", ""))
    if (project, name) in COMBINED_PROJECT_PATH_SCOPES:
        return COMBINED_PROJECT_PATH_SCOPES[(project, name)]
    if level1 == "总部":
        return [normalized_headquarters_path(level2, level3, item) for item in split_project_names(row, project)]
    if level1 == "行政部门":
        return [("行政部门",)]
    if level2 in {"物料采购群", "食品类以及礼盒采购"}:
        return [normalized_support_path(level2, project)]
    if level1 == "总部运营":
        return [normalized_jinying_path(level2, level3, item) for item in split_project_names(row, project)]
    return [non_empty_path("金英灵韵", level1, level2, level3, item) for item in split_project_names(row, project)]


def main_path(row: dict) -> Tuple[str, ...]:
    return main_paths(row)[0]


def region_project_paths(row: dict, sheet_name: str = "") -> List[Tuple[str, ...]]:
    level2 = row.get("2级部门", "")
    level3 = row.get("3级部门", "")
    project = row.get("4级部门", "")
    if not normalize_project_name(project):
        return []
    if sheet_name == "上海区域" and not level3:
        level3 = "上海区域运营"
    if sheet_name == "北京区域" and not level3:
        level3 = "北京区域运营"
    if project == level3:
        return [normalized_jinying_path(level2, level3)]
    return [normalized_jinying_path(level2, level3, item) for item in split_project_names(row, project)]


def region_project_path(row: dict, sheet_name: str = "") -> Tuple[str, ...]:
    return region_project_paths(row, sheet_name)[0]


def mitian_store_project_name(row: dict, sheet1_project_by_name: Dict[str, str]) -> str:
    name = clean_name(row.get("姓名", ""))
    level_values = {
        normalize_project_name(row.get("1级部门", "")),
        normalize_project_name(row.get("2级部门", "")),
        normalize_project_name(row.get("3级部门", "")),
    }
    for key in ("4级部门",):
        project = normalize_project_name(row.get(key, ""))
        if project:
            return project
    project = normalize_project_name(sheet1_project_by_name.get(name, ""))
    if project:
        return project
    for key in ("col4", "邮箱"):
        project = normalize_project_name(row.get(key, ""))
        if project and project not in level_values:
            return project
    return ""


def mitian_project_name(row: dict, sheet1_project_by_name: Dict[str, str]) -> str:
    return (
        mitian_store_project_name(row, sheet1_project_by_name)
        or row.get("3级部门", "")
        or row.get("2级部门", "")
        or row.get("1级部门", "")
    )


def mitian_has_store_project(row: dict, sheet1_project_by_name: Dict[str, str]) -> bool:
    return bool(mitian_store_project_name(row, sheet1_project_by_name))


def mitian_project_path(row: dict, sheet1_project_by_name: Dict[str, str]) -> Tuple[str, ...]:
    project = normalize_project_name(mitian_store_project_name(row, sheet1_project_by_name))
    level1 = normalize_project_name(row.get("1级部门", ""))
    level2 = normalize_project_name(row.get("2级部门", ""))
    level3 = normalize_project_name(row.get("3级部门", ""))
    if level1 == "长沙茗记运营部":
        return non_empty_path("长沙茗记", level3 or level2, project)
    if project and level2 and level3:
        return non_empty_path("杭州名田", level2, level3, project)
    parent_path = MITIAN_PROJECT_PARENT_PATHS.get(project)
    if parent_path is not None:
        return non_empty_path("杭州名田", *parent_path, project)
    return non_empty_path("杭州名田", level2, level3, project)


def sheet1_project_map(sheet1_rows: Sequence[dict]) -> Dict[str, str]:
    result: Dict[str, str] = {}
    for row in sheet1_rows:
        name = clean_name(row.get("姓名", ""))
        project = normalize_project_name(row.get("项目", ""))
        if name and project and "人" not in project and project not in SHEET1_NON_PROJECT_LABELS:
            result.setdefault(name, project)
    return result


def sheet2_values(row) -> List[str]:
    if isinstance(row, dict):
        values = [row.get(f"col{index}", "") for index in range(1, 6)]
        if any(values):
            return [clean(value) for value in values]
        keys = sorted(key for key in row if not key.startswith("_"))
        return [clean(row.get(key, "")) for key in keys]
    return [clean(value) for value in row]


def sheet2_role_config(sheet2_rows: Sequence) -> Tuple[Dict[str, int], Dict[str, int], Dict[str, str]]:
    rows = sheet2_rows or DEFAULT_SHEET2_ROWS
    post_sort_by_name: Dict[str, int] = {}
    role_sort_by_name: Dict[str, int] = {}
    role_name_by_post: Dict[str, str] = {}
    parsed_rows: List[Tuple[List[str], str]] = []

    for row in rows:
        values = sheet2_values(row)
        if len(values) <= SHEET2_ROLE_COLUMN_INDEX:
            continue
        role_name = normalize_post(values[SHEET2_ROLE_COLUMN_INDEX])
        if not role_name:
            continue
        parsed_rows.append((values, role_name))

    total_levels = len(parsed_rows)
    for level_index, (values, role_name) in enumerate(parsed_rows):
        position_sort = total_levels - level_index
        if role_name not in role_sort_by_name or position_sort < role_sort_by_name[role_name]:
            role_sort_by_name[role_name] = position_sort
        for value in values:
            post_name = normalize_post(value)
            if not post_name:
                continue
            if post_name not in post_sort_by_name or position_sort < post_sort_by_name[post_name]:
                post_sort_by_name[post_name] = position_sort
                role_name_by_post[post_name] = role_name

    for post_name, role_name in SUPPLEMENTAL_ROLE_BY_POST.items():
        normalized_post = normalize_post(post_name)
        normalized_role = normalize_post(role_name)
        if normalized_role in role_sort_by_name:
            role_name_by_post.setdefault(normalized_post, normalized_role)

    return post_sort_by_name, role_sort_by_name, role_name_by_post


def merge_user(
    existing: Optional[ImportUser],
    row: dict,
    path: Tuple[str, ...],
    source: str,
    main_rank: int = MAIN_DEPT_RANK_AGGREGATE,
) -> ImportUser:
    row_phone = phone(row.get("手机号", ""))
    name = clean_name(row.get("姓名", ""))[:30]
    post_name = normalize_post(row.get("职位", ""))
    if not post_name:
        post_name = DEFAULT_POST_NAME
    if existing is None:
        existing = ImportUser(
            phone=row_phone,
            name=name,
            main_dept_path=path,
            project_paths={path},
            post_name=post_name,
            status=account_status(row.get("员工状态", ""), row_phone),
            sex=sex_code(row.get("性别", "")),
            employee_type=clean(row.get("员工类型", "")),
            entry_date=clean(row.get("入职时间", "")),
            external_user_id=clean(row.get("员工UserID", "")),
            source_sheets={source},
            scope_path_order=[path],
            main_dept_rank=main_rank,
        )
        return existing

    existing.source_sheets.add(source)
    if name and existing.name != name:
        existing.name = existing.name or name
    if existing.post_name == DEFAULT_POST_NAME and post_name and post_name != DEFAULT_POST_NAME:
        existing.post_name = post_name
    if existing.status != "1" or row_phone in FORCED_ACTIVE_PHONES:
        existing.status = account_status(row.get("员工状态", ""), row_phone)
    if existing.sex == "2":
        existing.sex = sex_code(row.get("性别", ""))
    if not existing.employee_type:
        existing.employee_type = clean(row.get("员工类型", ""))
    if not existing.entry_date:
        existing.entry_date = clean(row.get("入职时间", ""))
    if not existing.external_user_id:
        existing.external_user_id = clean(row.get("员工UserID", ""))
    return existing


def add_project_paths(user: ImportUser, paths: Sequence[Tuple[str, ...]]) -> None:
    for path in paths:
        if path not in user.project_paths:
            user.scope_path_order.append(path)
        user.project_paths.add(path)


def prefer_main_dept(user: ImportUser, path: Tuple[str, ...], rank: int) -> None:
    if rank >= user.main_dept_rank:
        return
    if path not in user.project_paths:
        user.scope_path_order.append(path)
        user.project_paths.add(path)
    user.main_dept_path = path
    user.main_dept_rank = rank


def is_descendant_path(parent: Tuple[str, ...], child: Tuple[str, ...]) -> bool:
    return len(child) > len(parent) and child[: len(parent)] == parent


def store_level_role_name(user: ImportUser, role_name_by_post: Dict[str, str]) -> str:
    post_name = normalize_post(user.post_name)
    return role_name_by_post.get(post_name, post_name)


def prefer_concrete_store_primary_departments(users: Dict[str, ImportUser], role_name_by_post: Dict[str, str]) -> None:
    for user in users.values():
        if store_level_role_name(user, role_name_by_post) not in STORE_LEVEL_ROLE_NAMES:
            continue
        child_scopes = [
            path
            for path in user.scope_path_order
            if path in user.project_paths and is_descendant_path(user.main_dept_path, path)
        ]
        if not child_scopes:
            continue
        prefer_main_dept(user, child_scopes[0], MAIN_DEPT_RANK_CHILD_SCOPE)


def build_import_plan(
    main_rows: Sequence[dict],
    region_rows_by_sheet: Dict[str, Sequence[dict]],
    mitian_rows: Sequence[dict],
    sheet1_rows: Sequence[dict],
    sheet2_rows: Sequence = (),
) -> ImportPlan:
    users: Dict[str, ImportUser] = {}
    skipped: List[dict] = []
    sheet1_projects = sheet1_project_map(sheet1_rows)
    post_sort_by_name, role_sort_by_name, role_name_by_post = sheet2_role_config(sheet2_rows)
    name_to_phone: Dict[str, str] = {}

    for row in main_rows:
        row_phone = phone(row.get("手机号", ""))
        if not valid_phone(row_phone):
            if clean_name(row.get("姓名", "")):
                skipped.append(row)
            continue
        if not should_import_phone(row_phone):
            continue
        row_paths = [path for path in main_paths(row) if path not in IGNORED_MAIN_DEPT_PATHS]
        if not row_paths:
            continue
        main_rank = MAIN_DEPT_RANK_OFFICIAL
        user = merge_user(None, row, row_paths[0], clean(row.get("_sheet", "")) or "员工数据", main_rank)
        add_project_paths(user, row_paths)
        users[row_phone] = user
        name_to_phone.setdefault(user.name, row_phone)

    for sheet, rows in region_rows_by_sheet.items():
        for row in rows:
            row_phone = phone(row.get("手机号", ""))
            row_name = clean_name(row.get("姓名", ""))
            if not valid_phone(row_phone):
                if row_name:
                    skipped.append(row)
                continue
            if not should_import_phone(row_phone):
                continue
            paths = region_project_paths(row, sheet)
            if not paths:
                continue
            path = paths[0]
            user = users.get(row_phone)
            if user is None:
                user = merge_user(None, row, path, sheet, MAIN_DEPT_RANK_DETAIL_STORE)
                users[row_phone] = user
            else:
                merge_user(user, row, user.main_dept_path, sheet)
            add_project_paths(user, paths)
            prefer_main_dept(user, path, MAIN_DEPT_RANK_DETAIL_STORE)
            if row_name:
                name_to_phone.setdefault(row_name, row_phone)

    for row in mitian_rows:
        row_phone = phone(row.get("手机号", ""))
        row_name = clean_name(row.get("姓名", ""))
        if not valid_phone(row_phone):
            if row_name:
                skipped.append(row)
            continue
        if not should_import_phone(row_phone):
            continue
        path = mitian_project_path(row, sheet1_projects)
        main_rank = MAIN_DEPT_RANK_DETAIL_STORE if mitian_has_store_project(row, sheet1_projects) else MAIN_DEPT_RANK_AGGREGATE
        user = users.get(row_phone)
        if user is None:
            user = merge_user(None, row, path, "名田", main_rank)
            users[row_phone] = user
        else:
            merge_user(user, row, user.main_dept_path, "名田")
        add_project_paths(user, [path])
        prefer_main_dept(user, path, main_rank)
        if row_name:
            name_to_phone.setdefault(row_name, row_phone)

    prefer_concrete_store_primary_departments(users, role_name_by_post)

    return ImportPlan(
        users_by_phone=users,
        skipped_candidates=skipped,
        post_sort_by_name=post_sort_by_name,
        role_sort_by_name=role_sort_by_name,
        role_name_by_post=role_name_by_post,
    )


def load_xlrd():
    try:
        import xlrd  # type: ignore
        return xlrd
    except ModuleNotFoundError:
        vendor_path = "/tmp/codex_xlrd"
        if os.path.isdir(vendor_path):
            sys.path.insert(0, vendor_path)
        import xlrd  # type: ignore
        return xlrd


def workbook_cell(xlrd, book, sheet, row: int, col: int) -> str:
    value = sheet.cell_value(row, col)
    if sheet.cell_type(row, col) == xlrd.XL_CELL_DATE:
        try:
            return xlrd.xldate_as_datetime(value, book.datemode).strftime("%Y-%m-%d")
        except Exception:
            return clean(value)
    if isinstance(value, float):
        if value.is_integer():
            return str(int(value))
        return str(value)
    return clean(value)


def merged_cell_values(xlrd, book, sheet) -> Dict[Tuple[int, int], str]:
    result: Dict[Tuple[int, int], str] = {}
    for row_start, row_end, col_start, col_end in getattr(sheet, "merged_cells", []) or []:
        value = workbook_cell(xlrd, book, sheet, row_start, col_start)
        if not value:
            continue
        for row in range(row_start, row_end):
            for col in range(col_start, col_end):
                result[(row, col)] = value
    return result


def canonical_header(value: str, col_index: int) -> str:
    header = re.sub(r"\s+", "", value).replace("（金英）", "").replace("（灵韵）", "")
    if not header:
        return f"col{col_index + 1}"
    if header.startswith("1级部门"):
        return "1级部门"
    if header.startswith("2级部门"):
        return "2级部门"
    if header.startswith("3级部门"):
        return "3级部门负责人" if "负责人" in header else "3级部门"
    if header.startswith("4级门店") or header.startswith("4级部门"):
        return "4级部门负责人" if "负责人" in header else "4级部门"
    return header


def put_parsed_cell_value(row: dict, header: str, value: str) -> None:
    if header not in row:
        row[header] = value
        return
    if not row[header] and value:
        row[header] = value
        return
    if row[header] and value:
        duplicate_key = f"{header}#dup"
        if duplicate_key not in row or not row[duplicate_key]:
            row[duplicate_key] = value
            return
        index = 2
        while row.get(f"{duplicate_key}{index}"):
            index += 1
        row[f"{duplicate_key}{index}"] = value


def parse_sheet(book, sheet_name: str, fill_down_departments: bool = False) -> List[dict]:
    xlrd = load_xlrd()
    sheet = book.sheet_by_name(sheet_name)
    merged_values = merged_cell_values(xlrd, book, sheet)
    headers = [canonical_header(workbook_cell(xlrd, book, sheet, 0, col), col) for col in range(sheet.ncols)]
    rows: List[dict] = []
    last_department_values: Dict[str, str] = {}
    for row_index in range(1, sheet.nrows):
        row = {"_sheet": sheet_name, "_row": str(row_index + 1)}
        for col, header in enumerate(headers):
            value = workbook_cell(xlrd, book, sheet, row_index, col)
            if not value:
                value = merged_values.get((row_index, col), "")
            if fill_down_departments and header in {"1级部门", "2级部门", "3级部门"}:
                if value:
                    last_department_values[header] = value
                else:
                    value = last_department_values.get(header, "")
            put_parsed_cell_value(row, header, value)
        if any(value for key, value in row.items() if not key.startswith("_")):
            rows.append(row)
    return rows


def first_existing_sheet_name(book, candidate_names: Sequence[str]) -> str:
    available = set(book.sheet_names())
    for sheet_name in candidate_names:
        if sheet_name in available:
            return sheet_name
    raise ValueError("Missing expected sheet; tried: " + ", ".join(candidate_names))


def parse_sheet1(book, sheet_name: Optional[str] = None) -> List[dict]:
    xlrd = load_xlrd()
    sheet_name = sheet_name or first_existing_sheet_name(book, SHEET1_ALIASES)
    sheet = book.sheet_by_name(sheet_name)
    rows: List[dict] = []
    for row_index in range(sheet.nrows):
        values = [workbook_cell(xlrd, book, sheet, row_index, col) for col in range(sheet.ncols)]
        if not any(values):
            continue
        rows.append(
            {
                "_sheet": sheet_name,
                "_row": str(row_index + 1),
                "序号": values[0] if len(values) > 0 else "",
                "备用姓名": values[1] if len(values) > 1 else "",
                "姓名": values[2] if len(values) > 2 else "",
                "职位": values[3] if len(values) > 3 else "",
                "项目": values[4] if len(values) > 4 else "",
            }
        )
    return rows


def parse_sheet2(book, sheet_name: Optional[str] = None) -> List[List[str]]:
    xlrd = load_xlrd()
    sheet_name = sheet_name or first_existing_sheet_name(book, SHEET2_ALIASES)
    sheet = book.sheet_by_name(sheet_name)
    rows: List[List[str]] = []
    for row_index in range(sheet.nrows):
        values = [workbook_cell(xlrd, book, sheet, row_index, col) for col in range(sheet.ncols)]
        if any(values):
            rows.append(values)
    return rows


def build_plan_from_workbook(path: str) -> ImportPlan:
    xlrd = load_xlrd()
    book = xlrd.open_workbook(path, formatting_info=True)
    main_rows = parse_sheet(book, "员工数据")
    available = set(book.sheet_names())
    for sheet_name in OPTIONAL_MAIN_SHEETS:
        if sheet_name in available:
            main_rows.extend(parse_sheet(book, sheet_name))
    return build_import_plan(
        main_rows=main_rows,
        region_rows_by_sheet={sheet: parse_sheet(book, sheet, fill_down_departments=True) for sheet in REGION_SHEETS},
        mitian_rows=parse_sheet(book, "名田", fill_down_departments=True),
        sheet1_rows=parse_sheet1(book),
        sheet2_rows=parse_sheet2(book),
    )


def sql_quote(value: str) -> str:
    return "'" + (value or "").replace("\\", "\\\\").replace("'", "''") + "'"


class MysqlClient:
    def __init__(self, host: str, port: str, user: str, database: str, password: str = ""):
        self.base_cmd = ["mysql", "--protocol=tcp", f"-h{host}", f"-P{port}", f"-u{user}"]
        if password:
            self.base_cmd.append(f"-p{password}")
        self.base_cmd.append(database)

    def query(self, sql: str) -> List[List[str]]:
        cmd = self.base_cmd + ["--batch", "--raw", "--skip-column-names", "-e", sql]
        output = subprocess.check_output(cmd, universal_newlines=True)
        rows: List[List[str]] = []
        for line in output.splitlines():
            rows.append(line.split("\t"))
        return rows

    def execute(self, sql: str) -> None:
        subprocess.check_call(self.base_cmd + ["-e", sql])


class DatabaseImporter:
    def __init__(self, mysql: MysqlClient):
        self.mysql = mysql
        self.dept_cache: Dict[Tuple[int, str], Tuple[int, str]] = {}
        self.post_cache: Dict[str, int] = {}
        self.role_cache: Dict[str, int] = {}

    def scalar(self, sql: str) -> Optional[str]:
        rows = self.mysql.query(sql)
        return rows[0][0] if rows and rows[0] else None

    def load_roles(self) -> None:
        self.role_cache = {}
        for role_id, role_name in self.mysql.query("select role_id, role_name from sys_role where status='0' and del_flag='0'"):
            self.role_cache[role_name] = int(role_id)

    def dept_info(self, dept_id: int) -> Tuple[int, str]:
        rows = self.mysql.query(
            "select dept_id, ancestors from sys_dept "
            f"where dept_id={dept_id} and del_flag='0' limit 1"
        )
        if not rows:
            raise RuntimeError(f"Missing dept_id {dept_id}")
        return int(rows[0][0]), rows[0][1]

    def prepare_root_depts(self) -> None:
        self.mysql.execute(
            "update sys_dept set parent_id=0, ancestors='0', dept_name='金英灵韵集团', "
            f"order_num=0, dept_type='GROUP', status='0', del_flag='0', update_by={sql_quote(IMPORT_BY)}, update_time=now() "
            "where dept_id=100"
        )
        self.mysql.execute(
            "update sys_dept set parent_id=100, ancestors='0,100', dept_name='金英灵韵', "
            f"order_num=1, dept_type='COMPANY', status='0', del_flag='0', update_by={sql_quote(IMPORT_BY)}, update_time=now() "
            "where dept_id=101"
        )
        self.mysql.execute(
            "update sys_dept set parent_id=100, ancestors='0,100', dept_name='杭州名田', "
            f"order_num=2, dept_type='COMPANY', status='0', del_flag='0', update_by={sql_quote(IMPORT_BY)}, update_time=now() "
            "where dept_id=200"
        )
        self.dept_cache = {}

    def find_root(self, name: str) -> Tuple[int, str]:
        if name == "金英灵韵":
            return self.dept_info(101)
        if name == "杭州名田":
            return self.dept_info(200)
        rows = self.mysql.query(
            "select dept_id, ancestors from sys_dept "
            f"where parent_id=100 and dept_name={sql_quote(name)} and del_flag='0' limit 1"
        )
        if not rows:
            return self.get_or_create_dept(100, "0", name, "COMPANY", 10)
        return int(rows[0][0]), rows[0][1]

    def get_or_create_dept(self, parent_id: int, parent_ancestors: str, name: str, dept_type: str, order: int) -> Tuple[int, str]:
        key = (parent_id, name)
        if key in self.dept_cache:
            if dept_type == "COMPANY":
                self.mysql.execute(
                    "update sys_dept set "
                    f"dept_type='COMPANY', update_by={sql_quote(IMPORT_BY)}, update_time=now() "
                    f"where dept_id={self.dept_cache[key][0]} and dept_type <> 'COMPANY'"
                )
            return self.dept_cache[key]
        rows = self.mysql.query(
            "select dept_id, ancestors, dept_type from sys_dept "
            f"where parent_id={parent_id} and dept_name={sql_quote(name)} and del_flag='0' limit 1"
        )
        if rows:
            dept_id = int(rows[0][0])
            if rows[0][2] != dept_type and dept_type == "COMPANY":
                self.mysql.execute(
                    "update sys_dept set "
                    f"dept_type={sql_quote(dept_type)}, update_by={sql_quote(IMPORT_BY)}, update_time=now() "
                    f"where dept_id={dept_id}"
                )
            result = (dept_id, rows[0][1])
            self.dept_cache[key] = result
            return result
        ancestors = f"{parent_ancestors},{parent_id}" if parent_ancestors else str(parent_id)
        self.mysql.execute(
            "insert into sys_dept(parent_id, ancestors, dept_name, order_num, leader, status, dept_type, del_flag, create_by, create_time) "
            f"values({parent_id}, {sql_quote(ancestors)}, {sql_quote(name)}, {order}, '', '0', {sql_quote(dept_type)}, '0', {sql_quote(IMPORT_BY)}, now())"
        )
        rows = self.mysql.query(
            "select dept_id, ancestors from sys_dept "
            f"where parent_id={parent_id} and dept_name={sql_quote(name)} and del_flag='0' order by dept_id desc limit 1"
        )
        result = (int(rows[0][0]), rows[0][1])
        self.dept_cache[key] = result
        return result

    def ensure_path(self, path: Tuple[str, ...]) -> int:
        if len(path) < 1:
            raise RuntimeError(f"Invalid dept path {path}")
        parent_id, ancestors = self.find_root(path[0])
        if len(path) == 1:
            return parent_id
        for index, name in enumerate(path[1:], start=1):
            dept_type = "STORE" if index == len(path) - 1 else "COMPANY"
            parent_id, ancestors = self.get_or_create_dept(parent_id, ancestors, name, dept_type, index)
        return parent_id

    def get_or_create_post(self, post_name: str, post_sort: Optional[int] = None) -> int:
        post_name = normalize_post(post_name) or DEFAULT_POST_NAME
        if post_name in self.post_cache:
            return self.post_cache[post_name]
        code = POST_CODES.get(post_name)
        if not code:
            code = "imp_" + hashlib.md5(post_name.encode("utf-8")).hexdigest()[:10]
        rows = self.mysql.query(
            "select post_id from sys_post "
            f"where post_name={sql_quote(post_name)} limit 1"
        )
        if not rows and post_name == "店长助理":
            rows = self.mysql.query("select post_id from sys_post where post_name='店助' limit 1")
            if rows:
                post_id = int(rows[0][0])
                sort_sql = f", post_sort={post_sort}" if post_sort is not None else ""
                self.mysql.execute(
                    "update sys_post set "
                    f"post_name={sql_quote(post_name)}, post_code={sql_quote(code)}, status='0'{sort_sql}, "
                    f"update_by={sql_quote(IMPORT_BY)}, update_time=now() where post_id={post_id}"
                )
        if rows:
            post_id = int(rows[0][0])
            sort_sql = f", post_sort={post_sort}" if post_sort is not None else ""
            self.mysql.execute(
                "update sys_post set "
                f"post_code={sql_quote(code)}, status='0'{sort_sql}, "
                f"update_by={sql_quote(IMPORT_BY)}, update_time=now() where post_id={post_id}"
            )
            self.post_cache[post_name] = post_id
            return post_id
        if post_sort is None:
            post_sort = int(self.scalar("select coalesce(max(post_sort), 0) from sys_post") or "0") + 1
        self.mysql.execute(
            "insert into sys_post(post_code, post_name, post_sort, status, create_by, create_time) "
            f"values({sql_quote(code)}, {sql_quote(post_name)}, {post_sort}, '0', {sql_quote(IMPORT_BY)}, now())"
        )
        post_id = int(self.scalar(f"select post_id from sys_post where post_name={sql_quote(post_name)} order by post_id desc limit 1") or "0")
        self.post_cache[post_name] = post_id
        return post_id

    def ensure_roles(self, plan: ImportPlan) -> None:
        if not plan.role_sort_by_name:
            return
        top_sort = min(plan.role_sort_by_name.values())
        for role_name, role_sort in sorted(plan.role_sort_by_name.items(), key=lambda item: item[1]):
            role_id = 99 + role_sort
            role_key = ROLE_CODES.get(role_name)
            if not role_key:
                role_key = "imp_" + hashlib.md5(role_name.encode("utf-8")).hexdigest()[:10]
            data_scope = "1" if role_sort == top_sort else "4"
            rows = self.mysql.query(
                "select role_id from sys_role "
                f"where role_name={sql_quote(role_name)} and del_flag='0' order by role_id limit 1"
            )
            if rows:
                existing_role_id = int(rows[0][0])
                self.mysql.execute(
                    "update sys_role set "
                    f"role_key={sql_quote(role_key)}, role_sort={role_sort}, "
                    f"data_scope={sql_quote(data_scope)}, status='0', del_flag='0', "
                    f"update_by={sql_quote(IMPORT_BY)}, update_time=now(), remark='标准门店角色' "
                    f"where role_id={existing_role_id}"
                )
                continue
            rows = self.mysql.query(f"select role_id from sys_role where role_id={role_id} limit 1")
            if rows:
                self.mysql.execute(
                    "insert into sys_role(role_name, role_key, role_sort, data_scope, menu_check_strictly, "
                    "dept_check_strictly, status, del_flag, create_by, create_time, remark) values("
                    f"{sql_quote(role_name)}, {sql_quote(role_key)}, {role_sort}, {sql_quote(data_scope)}, "
                    f"1, 1, '0', '0', {sql_quote(IMPORT_BY)}, now(), '标准门店角色')"
                )
                continue
            self.mysql.execute(
                "insert into sys_role(role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, "
                "dept_check_strictly, status, del_flag, create_by, create_time, remark) values("
                f"{role_id}, {sql_quote(role_name)}, {sql_quote(role_key)}, {role_sort}, {sql_quote(data_scope)}, "
                f"1, 1, '0', '0', {sql_quote(IMPORT_BY)}, now(), '标准门店角色')"
            )
        self.load_roles()

    def role_id_for_name(self, role_name: str) -> int:
        if not self.role_cache:
            self.load_roles()
        return self.role_cache.get(role_name, self.role_cache.get("普通角色", 2))

    def get_or_create_user(self, user: ImportUser, dept_id: int) -> int:
        rows = self.mysql.query(
            "select user_id from sys_user "
            f"where del_flag='0' and (user_name={sql_quote(user.phone)} or phonenumber={sql_quote(user.phone)}) "
            "order by user_id limit 1"
        )
        if not rows:
            rows = self.mysql.query(
                "select user_id from sys_user "
                f"where del_flag='0' and user_id > 2 and nick_name={sql_quote(user.name)} "
                "and (phonenumber is null or phonenumber='') order by user_id limit 1"
            )
        remark = (
            f"Excel导入; 外部UserID:{user.external_user_id or '-'}; "
            f"员工类型:{user.employee_type or '-'}; 入职:{user.entry_date or '-'}; "
            f"来源:{','.join(sorted(user.source_sheets))}"
        )[:500]
        if rows:
            user_id = int(rows[0][0])
            self.mysql.execute(
                "update sys_user set "
                f"dept_id={dept_id}, nick_name={sql_quote(user.name)}, phonenumber={sql_quote(user.phone)}, "
                f"sex={sql_quote(user.sex)}, status={sql_quote(user.status)}, update_by={sql_quote(IMPORT_BY)}, "
                f"update_time=now(), remark={sql_quote(remark)} where user_id={user_id}"
            )
            return user_id
        self.mysql.execute(
            "insert into sys_user(dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, "
            "status, del_flag, create_by, create_time, remark) values("
            f"{dept_id}, {sql_quote(user.phone)}, {sql_quote(user.name)}, '00', '', {sql_quote(user.phone)}, "
            f"{sql_quote(user.sex)}, '', {sql_quote(DEFAULT_PASSWORD_HASH)}, {sql_quote(user.status)}, '0', "
            f"{sql_quote(IMPORT_BY)}, now(), {sql_quote(remark)})"
        )
        return int(self.scalar(f"select user_id from sys_user where user_name={sql_quote(user.phone)} order by user_id desc limit 1") or "0")

    def cleanup_stale_import_users(self, active_phones: Set[str]) -> int:
        if not active_phones:
            return 0
        phone_list = ",".join(sql_quote(item) for item in sorted(active_phones))
        rows = self.mysql.query(
            "select user_id from sys_user "
            f"where del_flag='0' and user_id > 2 and create_by={sql_quote(IMPORT_BY)} "
            f"and coalesce(nullif(phonenumber, ''), user_name, '') not in ({phone_list})"
        )
        if not rows:
            return 0
        user_ids = ",".join(row[0] for row in rows)
        self.mysql.execute(f"delete from sys_user_shop where user_id in ({user_ids})")
        self.mysql.execute(f"delete from sys_user_role where user_id in ({user_ids})")
        self.mysql.execute(f"delete from sys_user_post where user_id in ({user_ids})")
        self.mysql.execute(
            "update sys_user "
            f"set status='1', del_flag='2', update_by={sql_quote(IMPORT_BY)}, update_time=now() "
            f"where user_id in ({user_ids})"
        )
        return len(rows)

    def cleanup_stale_import_depts(self, active_dept_ids: Set[int]) -> int:
        if not active_dept_ids:
            return 0
        active_ids = ",".join(str(item) for item in sorted(active_dept_ids))
        rows = self.mysql.query(
            "select dept_id from sys_dept stale "
            f"where stale.del_flag='0' and stale.create_by={sql_quote(IMPORT_BY)} "
            f"and stale.dept_id not in ({active_ids}) "
            "and not exists (select 1 from sys_user u where u.del_flag='0' and u.dept_id=stale.dept_id) "
            "and not exists (select 1 from sys_user_shop us join sys_user u on u.user_id=us.user_id "
            "where u.del_flag='0' and us.dept_id=stale.dept_id) "
            "and not exists (select 1 from sys_dept child where child.del_flag='0' and child.parent_id=stale.dept_id)"
        )
        if not rows:
            return 0
        dept_ids = ",".join(row[0] for row in rows)
        self.mysql.execute(
            "update sys_dept set status='1', del_flag='2', "
            f"update_by={sql_quote(IMPORT_BY)}, update_time=now() "
            f"where dept_id in ({dept_ids})"
        )
        return len(rows)

    def import_plan(self, plan: ImportPlan) -> Dict[str, int]:
        created_counts = defaultdict(int)
        self.prepare_root_depts()
        self.ensure_roles(plan)
        created_counts["stale_users_removed"] = self.cleanup_stale_import_users(set(plan.users_by_phone))
        path_ids: Dict[Tuple[str, ...], int] = {}
        for path in sorted(plan.dept_paths, key=lambda item: (item[0], len(item), item)):
            path_ids[path] = self.ensure_path(path)
        for post in sorted(plan.post_names, key=lambda item: (plan.post_sort_for_post(item) or 9999, item)):
            self.get_or_create_post(post, plan.post_sort_for_post(post))

        for user in plan.users_by_phone.values():
            main_dept_id = path_ids[user.main_dept_path]
            user_id = self.get_or_create_user(user, main_dept_id)
            post_id = self.get_or_create_post(user.post_name, plan.post_sort_for_post(user.post_name))
            role_id = self.role_id_for_name(plan.role_name_for_post(user.post_name))
            self.mysql.execute(f"delete from sys_user_post where user_id={user_id}")
            self.mysql.execute(f"delete from sys_user_role where user_id={user_id}")
            self.mysql.execute(f"delete from sys_user_shop where user_id={user_id}")
            self.mysql.execute(f"insert into sys_user_post(user_id, post_id) values({user_id}, {post_id})")
            self.mysql.execute(f"insert into sys_user_role(user_id, role_id) values({user_id}, {role_id})")
            default_done = False
            for path in sorted(user.project_paths, key=lambda item: (item != user.main_dept_path, item)):
                dept_id = path_ids[path]
                is_default = "Y" if not default_done and path == user.main_dept_path else "N"
                if is_default == "Y":
                    default_done = True
                self.mysql.execute(
                    "insert into sys_user_shop(user_id, dept_id, is_default, create_by, create_time) "
                    f"values({user_id}, {dept_id}, {sql_quote(is_default)}, {sql_quote(IMPORT_BY)}, now()) "
                    "on duplicate key update is_default=values(is_default)"
                )
            created_counts["users"] += 1
        created_counts["stale_depts_removed"] = self.cleanup_stale_import_depts(set(path_ids.values()))
        created_counts["dept_paths"] = len(path_ids)
        created_counts["posts"] = len(plan.post_names)
        created_counts["roles"] = len(plan.role_sort_by_name)
        created_counts["skipped"] = len(plan.skipped_candidates)
        return dict(created_counts)

    def backup_tables(self) -> List[str]:
        stamp = time.strftime("%Y%m%d_%H%M%S")
        tables = ["sys_dept", "sys_post", "sys_role", "sys_user", "sys_user_post", "sys_user_role", "sys_user_shop"]
        backup_names = []
        for table in tables:
            backup = f"backup_employee_import_{table}_{stamp}"
            self.mysql.execute(f"create table {backup} as select * from {table}")
            backup_names.append(backup)
        return backup_names


def summarize_plan(plan: ImportPlan) -> str:
    active = sum(1 for user in plan.users_by_phone.values() if user.status == "0")
    disabled = sum(1 for user in plan.users_by_phone.values() if user.status == "1")
    projects = sum(len(user.project_paths) for user in plan.users_by_phone.values())
    return (
        f"users={len(plan.users_by_phone)} active={active} disabled={disabled} "
        f"dept_paths={len(plan.dept_paths)} project_links={projects} "
        f"posts={len(plan.post_names)} skipped_no_phone={len(plan.skipped_candidates)}"
    )


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", default=DEFAULT_EXCEL_PATH)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="3306")
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default=os.environ.get("MYSQL_PASSWORD", ""))
    parser.add_argument("--database", default=MYSQL_DEFAULT_DB)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args(argv)

    plan = build_plan_from_workbook(args.excel)
    print(summarize_plan(plan))
    if not args.apply:
        for item in plan.skipped_candidates[:20]:
            print(f"skip_no_phone sheet={item.get('_sheet')} row={item.get('_row')} name={item.get('姓名')} post={item.get('职位')}")
        return 0

    mysql = MysqlClient(args.host, args.port, args.user, args.database, args.password)
    importer = DatabaseImporter(mysql)
    backups = importer.backup_tables()
    print("backups=" + ",".join(backups))
    counts = importer.import_plan(plan)
    print("imported=" + " ".join(f"{key}={value}" for key, value in sorted(counts.items())))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
