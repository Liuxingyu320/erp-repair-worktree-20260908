import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from erp_employee_importer import (
    DatabaseImporter,
    ImportPlan,
    build_import_plan,
    first_existing_sheet_name,
    parse_sheet,
    put_parsed_cell_value,
)


class FakeWorkbook:
    def __init__(self, sheet_names):
        self._sheet_names = sheet_names

    def sheet_names(self):
        return self._sheet_names


class FakeSheet:
    def __init__(self, rows, merged_cells=None):
        self.rows = rows
        self.merged_cells = merged_cells or []
        self.nrows = len(rows)
        self.ncols = max(len(row) for row in rows)

    def cell_value(self, row, col):
        if row >= len(self.rows) or col >= len(self.rows[row]):
            return ""
        return self.rows[row][col]

    def cell_type(self, row, col):
        return 1


class FakeBookWithSheets:
    datemode = 0

    def __init__(self, sheets):
        self.sheets = sheets

    def sheet_by_name(self, name):
        return self.sheets[name]


class FakeMysql:
    def __init__(self, query_rows):
        self.query_rows = query_rows
        self.queries = []
        self.executed = []

    def query(self, sql):
        self.queries.append(sql)
        return self.query_rows

    def execute(self, sql):
        self.executed.append(sql)


class GuardedRoleMysql(FakeMysql):
    def query(self, sql):
        self.queries.append(sql)
        if "from sys_role where role_id=" in sql:
            raise AssertionError("existing imported roles should be matched by role_name before deterministic role_id")
        if "where role_name='运营总监'" in sql:
            return [["107"]]
        return []


class EmployeeImportPlanTest(unittest.TestCase):
    def test_parse_sheet_uses_merged_cell_values_for_store_columns(self):
        book = FakeBookWithSheets(
            {
                "名田": FakeSheet(
                    [
                        ["姓名", "手机号", "1级部门", "2级部门", "3级部门", "4级部门"],
                        ["A", "13800000301", "名田运营部", "名田二区", "3组", "张家港万豪"],
                        ["B", "13800000302", "名田运营部", "", "", ""],
                    ],
                    merged_cells=[
                        (1, 3, 3, 4),
                        (1, 3, 4, 5),
                        (1, 3, 5, 6),
                    ],
                )
            }
        )

        rows = parse_sheet(book, "名田", fill_down_departments=True)

        self.assertEqual("名田二区", rows[1]["2级部门"])
        self.assertEqual("3组", rows[1]["3级部门"])
        self.assertEqual("张家港万豪", rows[1]["4级部门"])

    def test_adjusted_workbook_sheet_aliases_are_supported(self):
        book = FakeWorkbook(["员工数据", "拆分一些组合酒店解释表", "职位角色表"])

        self.assertEqual(
            "拆分一些组合酒店解释表",
            first_existing_sheet_name(book, ["Sheet1", "拆分一些组合酒店解释表"]),
        )
        self.assertEqual(
            "职位角色表",
            first_existing_sheet_name(book, ["Sheet2", "职位角色表"]),
        )

    def test_duplicate_empty_header_cell_does_not_overwrite_existing_value(self):
        row = {}

        put_parsed_cell_value(row, "职位", "系统维护")
        put_parsed_cell_value(row, "职位", "")
        put_parsed_cell_value(row, "职位", "产品经理")

        self.assertEqual("系统维护", row["职位"])
        self.assertEqual("产品经理", row["职位#dup"])

    def test_stale_imported_users_are_removed_without_touching_admin(self):
        mysql = FakeMysql([["42"], ["43"]])
        importer = DatabaseImporter(mysql)

        removed = importer.cleanup_stale_import_users({"13800000131"})

        self.assertEqual(2, removed)
        self.assertIn("user_id > 2", mysql.queries[0])
        self.assertIn("create_by='employee_xls_import'", mysql.queries[0])
        self.assertIn("'13800000131'", mysql.queries[0])
        self.assertEqual(4, len(mysql.executed))
        self.assertIn("delete from sys_user_shop where user_id in (42,43)", mysql.executed)
        self.assertIn("delete from sys_user_role where user_id in (42,43)", mysql.executed)
        self.assertIn("delete from sys_user_post where user_id in (42,43)", mysql.executed)
        self.assertTrue(any("set status='1', del_flag='2'" in sql for sql in mysql.executed))

    def test_stale_imported_departments_are_removed_only_when_unused(self):
        mysql = FakeMysql([["1124"], ["1217"]])
        importer = DatabaseImporter(mysql)

        removed = importer.cleanup_stale_import_depts({101, 200})

        self.assertEqual(2, removed)
        self.assertIn("create_by='employee_xls_import'", mysql.queries[0])
        self.assertIn("dept_id not in (101,200)", mysql.queries[0])
        self.assertIn("not exists (select 1 from sys_user", mysql.queries[0])
        self.assertIn("not exists (select 1 from sys_user_shop", mysql.queries[0])
        self.assertIn("not exists (select 1 from sys_dept child", mysql.queries[0])
        self.assertEqual(1, len(mysql.executed))
        self.assertIn("update sys_dept set status='1', del_flag='2'", mysql.executed[0])
        self.assertIn("where dept_id in (1124,1217)", mysql.executed[0])

    def test_headquarters_admin_rows_stay_under_group_admin_department(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "_sheet": "行政后勤",
                    "姓名": "Pat",
                    "手机号": "13800000131",
                    "1级部门": "总部",
                    "2级部门": "产品中心",
                    "职位": "产品助理",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={},
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual(("行政部门", "产品中心"), plan.users_by_phone["13800000131"].main_dept_path)

    def test_region_detail_scope_does_not_override_official_main_department(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Grace",
                    "手机号": "13800000121",
                    "1级部门": "总部运营",
                    "2级部门": "上海区域",
                    "3级部门": "上海区域运营",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={
                "上海区域": [
                    {
                        "姓名": "Grace",
                        "手机号": "13800000121",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "上海区域",
                        "3级部门": "上海区域运营",
                        "4级部门": "锦庐",
                        "职位": "茶艺师",
                    }
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        user = plan.users_by_phone["13800000121"]
        operation_path = ("金英灵韵", "上海区域", "上海区域运营")
        store_path = ("金英灵韵", "上海区域", "上海区域运营", "锦庐")
        self.assertEqual(operation_path, user.main_dept_path)
        self.assertEqual({operation_path, store_path}, user.project_paths)

    def test_mitian_scope_does_not_override_headquarters_main_department(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "_sheet": "行政后勤",
                    "姓名": "王宗亚",
                    "手机号": "15267457673",
                    "1级部门": "总部",
                    "2级部门": "市场销售部",
                    "职位": "运营经理",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "王宗亚",
                    "手机号": "15267457673",
                    "1级部门": "长沙茗记运营部",
                    "2级部门": "名田一区",
                    "3级部门": "杭州区域",
                    "4级部门": "大连君悦",
                    "职位": "运营经理",
                    "员工状态": "正式",
                }
            ],
            sheet1_rows=[],
        )

        user = plan.users_by_phone["15267457673"]
        market_path = ("行政部门", "市场销售部")
        store_path = ("长沙茗记", "杭州区域", "大连君悦")
        self.assertEqual(market_path, user.main_dept_path)
        self.assertEqual({market_path, store_path}, user.project_paths)

    def test_ignored_zhejiang_xiaochunshan_main_path_uses_mitian_assignment(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Chen",
                    "手机号": "13800000132",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "小春山",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Chen",
                    "手机号": "13800000132",
                    "1级部门": "名田运营部",
                    "2级部门": "名田三区",
                    "3级部门": "富阳区域",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                }
            ],
            sheet1_rows=[],
        )

        self.assertEqual(("杭州名田", "名田三区", "富阳区域"), plan.users_by_phone["13800000132"].main_dept_path)
        self.assertNotIn(("金英灵韵", "浙江区域", "小春山"), plan.dept_paths)

    def test_mitian_duplicate_distribution_cell_does_not_create_store(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Wang",
                    "手机号": "13800000133",
                    "1级部门": "名田运营部",
                    "2级部门": "名田三区",
                    "3级部门": "富阳区域",
                    "4级部门": "",
                    "col4": "名田三区",
                    "职位": "运营经理",
                    "员工状态": "正式",
                }
            ],
            sheet1_rows=[],
        )

        self.assertEqual(("杭州名田", "名田三区", "富阳区域"), plan.users_by_phone["13800000133"].main_dept_path)
        self.assertNotIn(("杭州名田", "名田三区", "富阳区域", "名田三区"), plan.dept_paths)

    def test_region_project_does_not_override_main_management_org(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Alice",
                    "手机号": "13800000001",
                    "1级部门": "总部运营",
                    "2级部门": "江苏区域",
                    "3级部门": "上海区域运营",
                    "4级部门": "",
                    "职位": "运营经理",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={
                "上海区域": [
                    {
                        "姓名": "Alice",
                        "手机号": "13800000001",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "上海区域",
                        "3级部门": "",
                        "4级部门": "锦庐",
                        "职位": "运营经理",
                    }
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        user = plan.users_by_phone["13800000001"]
        self.assertEqual(("金英灵韵", "上海区域", "上海区域运营"), user.main_dept_path)
        self.assertIn(("金英灵韵", "上海区域", "上海区域运营", "锦庐"), user.project_paths)
        self.assertEqual("运营经理", user.post_name)

    def test_user_confirmed_department_aliases_are_normalized(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Liu",
                    "手机号": "13800000207",
                    "1级部门": "总部运营",
                    "2级部门": "江苏区域",
                    "3级部门": "苏州区域运营",
                    "4级部门": "苏州丽思卡尔顿",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={
                "上海区域": [
                    {
                        "姓名": "Shen",
                        "手机号": "13800000208",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "上海区域",
                        "3级部门": "",
                        "4级部门": "舍丽雅",
                        "职位": "茶艺师",
                    },
                    {
                        "姓名": "An",
                        "手机号": "13800000209",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "上海区域",
                        "3级部门": "",
                        "4级部门": "阿纳迪",
                        "职位": "茶艺师",
                    },
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual(
            ("金英灵韵", "江苏区域", "江苏区域运营", "苏州丽思卡尔顿"),
            plan.users_by_phone["13800000207"].main_dept_path,
        )
        self.assertEqual(
            ("金英灵韵", "上海区域", "上海区域运营", "上海舍丽雅"),
            plan.users_by_phone["13800000208"].main_dept_path,
        )
        self.assertEqual(
            ("金英灵韵", "上海区域", "上海区域运营", "上海阿纳迪"),
            plan.users_by_phone["13800000209"].main_dept_path,
        )

    def test_same_phone_in_mitian_adds_project_instead_of_duplicate_user(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Bob",
                    "手机号": "13800000002",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江二区",
                    "4级部门": "宋宴",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Bob",
                    "手机号": "13800000002",
                    "1级部门": "名田运营部",
                    "2级部门": "名田二区",
                    "3级部门": "",
                    "4级部门": "安吉索菲特",
                    "职位": "茶艺师",
                }
            ],
            sheet1_rows=[],
        )

        self.assertEqual(["13800000002"], sorted(plan.users_by_phone))
        user = plan.users_by_phone["13800000002"]
        self.assertEqual(("金英灵韵", "浙江区域", "浙江二区", "宋宴"), user.main_dept_path)
        self.assertIn(("杭州名田", "名田二区", "浙江周边", "安吉索菲特"), user.project_paths)
        self.assertIn(("金英灵韵", "浙江区域", "浙江二区", "宋宴"), user.project_paths)

    def test_mitian_store_is_nested_under_level3_group(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Wu",
                    "手机号": "13800000201",
                    "1级部门": "名田运营部",
                    "2级部门": "名田一区",
                    "3级部门": "杭州区域",
                    "4级部门": "杭州JW万豪",
                    "职位": "店长助理",
                },
                {
                    "姓名": "Chen",
                    "手机号": "13800000202",
                    "1级部门": "名田运营部",
                    "2级部门": "名田一区",
                    "3级部门": "杭州区域",
                    "职位": "茶艺师",
                },
            ],
            sheet1_rows=[],
        )

        self.assertEqual(("杭州名田", "名田一区", "杭州区域", "杭州JW万豪"), plan.users_by_phone["13800000201"].main_dept_path)
        self.assertEqual(("杭州名田", "名田一区", "杭州区域"), plan.users_by_phone["13800000202"].main_dept_path)
        self.assertIn(("杭州名田", "名田一区", "杭州区域"), plan.dept_paths)
        self.assertIn(("杭州名田", "名田一区", "杭州区域", "杭州JW万豪"), plan.dept_paths)

    def test_mitian_store_parent_is_inferred_when_merged_group_cells_are_blank(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Qian",
                    "手机号": "13800000204",
                    "1级部门": "名田运营部",
                    "4级部门": "小青山",
                    "职位": "茶艺师",
                }
            ],
            sheet1_rows=[],
        )

        self.assertEqual(("杭州名田", "名田三区", "富阳区域", "小青山"), plan.users_by_phone["13800000204"].main_dept_path)

    def test_changsha_mingji_mitian_rows_use_changsha_company_path(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Lin",
                    "手机号": "13800000210",
                    "1级部门": "长沙茗记运营部",
                    "2级部门": "名田一区",
                    "3级部门": "杭州区域",
                    "4级部门": "大连君悦",
                    "职位": "茶艺师",
                }
            ],
            sheet1_rows=[],
        )

        self.assertEqual(("长沙茗记", "杭州区域", "大连君悦"), plan.users_by_phone["13800000210"].main_dept_path)

    def test_canonical_mitian_tree_keeps_unused_store_departments(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={},
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertIn(("杭州名田", "名田二区", "浙江周边", "德清万豪"), plan.dept_paths)

    def test_mitian_prefers_sheet_store_over_distribution_alias(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Du",
                    "手机号": "13800000203",
                    "1级部门": "名田运营部",
                    "2级部门": "名田三区",
                    "3级部门": "",
                    "4级部门": "牛头馆",
                    "职位": "初级运营经理",
                }
            ],
            sheet1_rows=[
                {"姓名": "Du", "项目": "小春山"},
            ],
        )

        self.assertEqual(("杭州名田", "名田三区", "富阳区域", "牛头馆"), plan.users_by_phone["13800000203"].main_dept_path)
        self.assertNotIn(("杭州名田", "名田三区", "富阳区域", "小春山"), plan.dept_paths)

    def test_detail_sheet_store_adds_scope_without_overriding_main_department(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Zhang",
                    "手机号": "13800000205",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江二区",
                    "4级部门": "宋宴",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={
                "浙江二三区": [
                    {
                        "姓名": "Zhang",
                        "手机号": "13800000205",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "浙江区域",
                        "3级部门": "浙江二区B",
                        "4级部门": "宋宴钱江店",
                        "职位": "茶艺师",
                    }
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual(("金英灵韵", "浙江区域", "浙江二区", "宋宴"), plan.users_by_phone["13800000205"].main_dept_path)
        self.assertIn(("金英灵韵", "浙江区域", "浙江二区", "宋宴"), plan.users_by_phone["13800000205"].project_paths)
        self.assertIn(
            ("金英灵韵", "浙江区域", "浙江二区", "浙江二区B", "宋宴钱江店"),
            plan.users_by_phone["13800000205"].project_paths,
        )

    def test_multiple_detail_sheet_stores_are_all_scoped_with_first_as_primary(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={
                "浙江二三区": [
                    {
                        "姓名": "Ren",
                        "手机号": "13800000206",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "浙江区域",
                        "3级部门": "浙江三区",
                        "4级部门": "南浔希尔顿",
                        "职位": "区域运营经理",
                    },
                    {
                        "姓名": "Ren",
                        "手机号": "13800000206",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "浙江区域",
                        "3级部门": "浙江三区",
                        "4级部门": "太湖洲际",
                        "职位": "区域运营经理",
                    },
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual(("金英灵韵", "浙江区域", "浙江三区", "南浔希尔顿"), plan.users_by_phone["13800000206"].main_dept_path)
        self.assertEqual(
            {
                ("金英灵韵", "浙江区域", "浙江三区", "南浔希尔顿"),
                ("金英灵韵", "浙江区域", "浙江三区", "太湖洲际"),
            },
            plan.users_by_phone["13800000206"].project_paths,
        )

    def test_sheet1_distribution_does_not_add_management_project(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Alice",
                    "手机号": "13800000003",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江一区",
                    "职位": "运营经理",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={},
            mitian_rows=[],
            sheet1_rows=[
                {"姓名": "Alice", "职位": "运营经理", "项目": "运营管理"},
            ],
        )

        user = plan.users_by_phone["13800000003"]
        self.assertEqual({("金英灵韵", "浙江区域", "浙江一区")}, user.project_paths)

    def test_sheet1_distribution_refines_mitian_composite_project(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={},
            mitian_rows=[
                {
                    "姓名": "Bob",
                    "手机号": "13800000004",
                    "1级部门": "名田运营部",
                    "col4": "凯宾斯基",
                    "邮箱": "凯宾斯基",
                    "职位": "茶艺师",
                }
            ],
            sheet1_rows=[
                {"姓名": "Bob", "项目": "远洋凯宾斯基"},
            ],
        )

        user = plan.users_by_phone["13800000004"]
        self.assertEqual({("杭州名田", "名田一区", "杭州区域", "杭州远洋凯宾斯基")}, user.project_paths)

    def test_department_hierarchy_matches_group_company_region_shape(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Zoe",
                    "手机号": "13800000008",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江一区",
                    "4级部门": "杭州柏悦",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                },
                {
                    "姓名": "Yan",
                    "手机号": "13800000009",
                    "1级部门": "总部运营",
                    "2级部门": "西安区域",
                    "3级部门": "西安区域运营",
                    "4级部门": "",
                    "职位": "店长",
                    "员工状态": "正式",
                },
                {
                    "姓名": "Xia",
                    "手机号": "13800000010",
                    "1级部门": "行政办公室",
                    "职位": "行政助理",
                    "员工状态": "正式",
                },
                {
                    "姓名": "Will",
                    "手机号": "13800000011",
                    "1级部门": "总部运营",
                    "2级部门": "物料采购群",
                    "职位": "物料采购",
                    "员工状态": "正式",
                },
            ],
            region_rows_by_sheet={
                "江苏区域": [
                    {
                        "姓名": "Zoe",
                        "手机号": "13800000008",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "江苏区域",
                        "3级部门": "江苏二区",
                        "4级部门": "南京安达仕",
                        "职位": "茶艺师",
                    }
                ],
                "浙江二三区": [
                    {
                        "姓名": "Yan",
                        "手机号": "13800000009",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "浙江区域",
                        "3级部门": "浙江三区\n任佳萌",
                        "4级部门": "平湖万怡",
                        "职位": "店长",
                    }
                ],
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual(("金英灵韵", "浙江区域", "浙江一区", "杭州柏悦"), plan.users_by_phone["13800000008"].main_dept_path)
        self.assertEqual(("金英灵韵", "北京区域", "西安区域运营"), plan.users_by_phone["13800000009"].main_dept_path)
        self.assertEqual(("行政部门",), plan.users_by_phone["13800000010"].main_dept_path)
        self.assertEqual(("仓库后勤部门", "物料采购群"), plan.users_by_phone["13800000011"].main_dept_path)
        self.assertIn(("金英灵韵", "江苏区域", "江苏二区", "南京安达仕"), plan.users_by_phone["13800000008"].project_paths)
        self.assertIn(("金英灵韵", "浙江区域", "浙江三区", "平湖万怡"), plan.users_by_phone["13800000009"].project_paths)
        self.assertIn(("仓库后勤部门",), plan.dept_paths)

    def test_rows_without_phone_are_kept_as_skipped_candidates(self):
        plan = build_import_plan(
            main_rows=[],
            region_rows_by_sheet={
                "北京区域": [
                    {
                        "姓名": "Charlie",
                        "手机号": "",
                        "工号": "新人",
                        "2级部门": "北京区域",
                        "4级部门": "费尔蒙",
                        "职位": "茶艺师",
                    }
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual({}, plan.users_by_phone)
        self.assertEqual(1, len(plan.skipped_candidates))
        self.assertEqual("Charlie", plan.skipped_candidates[0]["姓名"])

    def test_sheet2_defines_posts_and_canonical_role_levels(self):
        sheet2_rows = [
            ["", "", "", "实习生", ""],
            ["", "", "", "茶艺师", ""],
            ["", "", "", "店长助理", ""],
            ["", "", "预备店长", "店长", ""],
            ["", "", "AM驻店店长", "驻店经理", ""],
            ["", "", "初级运营经理", "运营经理", "区域运营经理"],
            ["", "", "", "区域运营总监", ""],
            ["", "", "", "运营总监", "GM"],
        ]
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Dana",
                    "手机号": "13800000005",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江一区",
                    "职位": "店助",
                    "员工状态": "正式",
                },
                {
                    "姓名": "Evan",
                    "手机号": "13800000006",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江一区",
                    "职位": "AM驻店经理",
                    "员工状态": "正式",
                },
                {
                    "姓名": "Faye",
                    "手机号": "13800000007",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江一区",
                    "职位": "总经理",
                    "员工状态": "正式",
                },
            ],
            region_rows_by_sheet={},
            mitian_rows=[],
            sheet1_rows=[],
            sheet2_rows=sheet2_rows,
        )

        self.assertIn("预备店长", plan.post_names)
        self.assertIn("AM驻店店长", plan.post_names)
        self.assertIn("GM", plan.post_names)
        self.assertNotIn("店助", plan.post_names)
        self.assertEqual("店长助理", plan.users_by_phone["13800000005"].post_name)
        self.assertEqual(
            ["运营总监", "区域运营总监", "运营经理", "驻店经理", "店长", "店长助理", "茶艺师", "实习生"],
            [name for name, _ in sorted(plan.role_sort_by_name.items(), key=lambda item: item[1])],
        )
        self.assertLess(plan.role_sort_by_name["运营总监"], plan.role_sort_by_name["实习生"])
        self.assertLess(plan.post_sort_for_post("GM"), plan.post_sort_for_post("茶艺师"))
        self.assertEqual("店长", plan.role_name_for_post("预备店长"))
        self.assertEqual("驻店经理", plan.role_name_for_post("AM驻店店长"))
        self.assertEqual("驻店经理", plan.role_name_for_post("AM驻店经理"))
        self.assertEqual("运营经理", plan.role_name_for_post("区域运营经理"))
        self.assertEqual("运营总监", plan.role_name_for_post("GM"))
        self.assertEqual("运营总监", plan.role_name_for_post("总经理"))

    def test_existing_standard_roles_keep_identity_when_sort_changes(self):
        mysql = GuardedRoleMysql([])
        importer = DatabaseImporter(mysql)
        importer.ensure_roles(
            ImportPlan(
                users_by_phone={},
                skipped_candidates=[],
                role_sort_by_name={"运营总监": 1},
            )
        )

        self.assertTrue(any("where role_id=107" in sql for sql in mysql.executed))
        self.assertTrue(any("role_sort=1" in sql for sql in mysql.executed))
        self.assertTrue(any("data_scope='1'" in sql for sql in mysql.executed))

    def test_pending_leave_import_exceptions(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "刘梦敏",
                    "手机号": "15853019762",
                    "1级部门": "总部运营",
                    "2级部门": "江苏区域",
                    "3级部门": "苏州区域运营",
                    "4级部门": "苏州丽思卡尔顿",
                    "职位": "茶艺师",
                    "员工状态": "待离职",
                },
                {
                    "姓名": "王路苑",
                    "手机号": "18322779602",
                    "1级部门": "总部运营",
                    "2级部门": "北京区域",
                    "3级部门": "北京区域运营",
                    "4级部门": "万达文华",
                    "职位": "店长助理",
                    "员工状态": "待离职",
                },
            ],
            region_rows_by_sheet={},
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertIn("15853019762", plan.users_by_phone)
        self.assertEqual("0", plan.users_by_phone["15853019762"].status)
        self.assertNotIn("18322779602", plan.users_by_phone)

    def test_combined_shop_scopes_are_split_by_person(self):
        def row(name, phone, level2, level3, project, post="茶艺师"):
            return {
                "姓名": name,
                "手机号": phone,
                "1级部门": "总部运营",
                "2级部门": level2,
                "3级部门": level3,
                "4级部门": project,
                "职位": post,
                "员工状态": "正式",
            }

        plan = build_import_plan(
            main_rows=[
                row("张丽婷", "13800000101", "北京区域", "北京区域运营", "北京康莱德&丽思卡尔顿", "驻店经理"),
                row("聂伟佳", "13800000102", "北京区域", "北京区域运营", "北京康莱德&丽思卡尔顿"),
                row("杨慧媛", "13800000103", "北京区域", "北京区域运营", "北京康莱德&丽思卡尔顿", "店长"),
                row("李双", "13800000104", "北京区域", "北京区域运营", "北京瑞吉&索菲特", "店长"),
                row("宗鑫淼", "13800000105", "北京区域", "北京区域运营", "通州建国&皇冠"),
                row("李希霞", "13800000106", "江苏区域", "苏州区域运营", "苏州狮山悦榕庄&成都宴", "店长"),
                row("王美芳", "13800000107", "江苏区域", "苏州区域运营", "苏州狮山悦榕庄&成都宴"),
                row("王红梅", "13800000108", "浙江区域", "浙江二区", "重庆丽思瑞&解放碑凯悦", "初级运营经理"),
                row("冉晓敏", "13800000109", "浙江区域", "浙江二区", "重庆丽思瑞&解放碑凯悦"),
                row("侯元元", "13800000110", "上海区域", "上海区域运营", "上海瑞吉&麒麟餐厅", "驻店经理"),
            ],
            region_rows_by_sheet={},
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual(
            {
                ("金英灵韵", "北京区域", "北京区域运营", "北京康莱德"),
                ("金英灵韵", "北京区域", "北京区域运营", "北京丽思卡尔顿"),
            },
            plan.users_by_phone["13800000101"].project_paths,
        )
        self.assertEqual(
            {("金英灵韵", "北京区域", "北京区域运营", "北京丽思卡尔顿")},
            plan.users_by_phone["13800000102"].project_paths,
        )
        self.assertEqual(
            {("金英灵韵", "北京区域", "北京区域运营", "北京康莱德")},
            plan.users_by_phone["13800000103"].project_paths,
        )
        self.assertEqual(
            {
                ("金英灵韵", "北京区域", "北京区域运营", "北京瑞吉"),
                ("金英灵韵", "北京区域", "北京区域运营", "北京索菲特"),
            },
            plan.users_by_phone["13800000104"].project_paths,
        )
        self.assertEqual(
            {("金英灵韵", "北京区域", "北京区域运营", "通州皇冠")},
            plan.users_by_phone["13800000105"].project_paths,
        )
        self.assertEqual(
            {
                ("金英灵韵", "江苏区域", "江苏区域运营", "苏州狮山悦榕庄"),
                ("金英灵韵", "江苏区域", "江苏区域运营", "成都宴"),
            },
            plan.users_by_phone["13800000106"].project_paths,
        )
        self.assertEqual(
            {("金英灵韵", "江苏区域", "江苏区域运营", "成都宴")},
            plan.users_by_phone["13800000107"].project_paths,
        )
        self.assertEqual(
            {
                ("金英灵韵", "浙江区域", "浙江二区", "浙江二区D", "重庆丽思瑞"),
                ("金英灵韵", "浙江区域", "浙江二区", "浙江二区D", "解放碑凯悦"),
            },
            plan.users_by_phone["13800000108"].project_paths,
        )
        self.assertEqual(
            {("金英灵韵", "浙江区域", "浙江二区", "浙江二区D", "解放碑凯悦")},
            plan.users_by_phone["13800000109"].project_paths,
        )
        self.assertEqual(
            {
                ("金英灵韵", "上海区域", "上海区域运营", "上海瑞吉"),
                ("金英灵韵", "上海区域", "上海区域运营", "麒麟餐厅"),
            },
            plan.users_by_phone["13800000110"].project_paths,
        )
        self.assertFalse(any("&" in part for path in plan.dept_paths for part in path))

    def test_store_manager_with_multiple_stores_keeps_official_main_and_all_store_scopes(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Helen",
                    "手机号": "13800000122",
                    "1级部门": "总部运营",
                    "2级部门": "上海区域",
                    "3级部门": "上海区域运营",
                    "职位": "驻店经理",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={
                "上海区域": [
                    {
                        "姓名": "Helen",
                        "手机号": "13800000122",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "上海区域",
                        "3级部门": "上海区域运营",
                        "4级部门": "上海瑞吉",
                        "职位": "驻店经理",
                    },
                    {
                        "姓名": "Helen",
                        "手机号": "13800000122",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "上海区域",
                        "3级部门": "上海区域运营",
                        "4级部门": "麒麟餐厅",
                        "职位": "驻店经理",
                    },
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        user = plan.users_by_phone["13800000122"]
        primary_store = ("金英灵韵", "上海区域", "上海区域运营", "上海瑞吉")
        extra_store = ("金英灵韵", "上海区域", "上海区域运营", "麒麟餐厅")
        operation_path = ("金英灵韵", "上海区域", "上海区域运营")
        self.assertEqual(operation_path, user.main_dept_path)
        self.assertEqual({operation_path, primary_store, extra_store}, user.project_paths)

    def test_operations_manager_keeps_operational_node_with_additional_store_scope(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Ivy",
                    "手机号": "13800000123",
                    "1级部门": "总部运营",
                    "2级部门": "上海区域",
                    "3级部门": "上海区域运营",
                    "职位": "运营经理",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={
                "上海区域": [
                    {
                        "姓名": "Ivy",
                        "手机号": "13800000123",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "上海区域",
                        "3级部门": "上海区域运营",
                        "4级部门": "锦庐",
                        "职位": "运营经理",
                    }
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        user = plan.users_by_phone["13800000123"]
        operation_path = ("金英灵韵", "上海区域", "上海区域运营")
        store_path = ("金英灵韵", "上海区域", "上海区域运营", "锦庐")
        self.assertEqual(operation_path, user.main_dept_path)
        self.assertEqual({operation_path, store_path}, user.project_paths)

    def test_region_office_location_does_not_create_department_scope(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Jill",
                    "手机号": "13800000124",
                    "1级部门": "总部运营",
                    "2级部门": "浙江区域",
                    "3级部门": "浙江一区",
                    "4级部门": "杭州柏悦",
                    "职位": "茶艺师",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={
                "浙江一区": [
                    {
                        "姓名": "Jill",
                        "手机号": "13800000124",
                        "1级部门": "金英灵韵运营部",
                        "2级部门": "浙江区域",
                        "3级部门": "浙江一区",
                        "4级部门": "",
                        "办公地点": "宋宴钱江店",
                        "职位": "茶艺师",
                    }
                ]
            },
            mitian_rows=[],
            sheet1_rows=[],
        )

        user = plan.users_by_phone["13800000124"]
        self.assertEqual({("金英灵韵", "浙江区域", "浙江一区", "杭州柏悦")}, user.project_paths)
        self.assertNotIn(("金英灵韵", "浙江区域", "浙江一区", "宋宴钱江店"), plan.dept_paths)

    def test_missing_post_defaults_to_tea_artist_not_common_employee(self):
        plan = build_import_plan(
            main_rows=[
                {
                    "姓名": "Kim",
                    "手机号": "13800000125",
                    "1级部门": "总部运营",
                    "2级部门": "北京区域",
                    "3级部门": "北京区域运营",
                    "4级部门": "北京柏悦",
                    "职位": "",
                    "员工状态": "正式",
                }
            ],
            region_rows_by_sheet={},
            mitian_rows=[],
            sheet1_rows=[],
        )

        self.assertEqual("茶艺师", plan.users_by_phone["13800000125"].post_name)
        self.assertIn("茶艺师", plan.post_names)
        self.assertNotIn("普通员工", plan.post_names)


if __name__ == "__main__":
    unittest.main()
