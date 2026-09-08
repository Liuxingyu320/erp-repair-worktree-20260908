#!/usr/bin/env python3

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class SystemManagementBrowserContractTest(unittest.TestCase):
    def test_browser_gate_is_cli_pinned_loopback_and_container_free(self):
        source = (ROOT / "scripts/verify-system-management-browser.sh").read_text(encoding="utf-8")
        self.assertIn('PLAYWRIGHT_CLI_VERSION="0.1.17"', source)
        self.assertIn("playwright-cli", source)
        self.assertIn("pw snapshot", source)
        self.assertIn("extract_ref", source)
        self.assertIn("127\\.0\\.0\\.1|localhost", source)
        self.assertNotIn("@playwright/test", source)
        self.assertNotIn("testcontainers", source.lower())
        self.assertNotIn("docker ", source.lower())

    def test_gate_covers_release_and_sensitive_export_flows(self):
        source = (ROOT / "scripts/verify-system-management-browser.sh").read_text(encoding="utf-8")
        for text in ("版本信息", "system 版本", "导出完整个人信息", "这是高敏感数据导出", "确认导出"):
            self.assertIn(text, source)
        self.assertIn("[disabled]", source)

    def test_gate_covers_system_management_ux_v2_at_required_desktop_widths(self):
        source = (ROOT / "scripts/verify-system-management-browser.sh").read_text(encoding="utf-8")
        for viewport in ("1018 768", "1366 768", "1440 900", "1920 1080"):
            self.assertIn(viewport, source)
        for text in (
            "全部账号", "高级筛选", "列预设", "调整组织授权面板宽度",
            "仅显示已选", "直接授权", "上级继承", "确认组织授权变更",
            "保存时会再次校验授权版本", "组织授权保存成功",
        ):
            self.assertIn(text, source)
        self.assertIn("lr.right < xr.left", source)

    def test_gate_covers_role_wizard_and_concurrency_safe_sorting(self):
        source = (ROOT / "scripts/verify-system-management-browser.sh").read_text(encoding="utf-8")
        for text in (
            "新增角色向导", "权限字符会被接口和代码长期引用", "本部门及以下",
            "目录 1", "菜单 1", "按钮 1", "角色创建完成", "去添加成员",
            "保存排序（已修改 1 项）", "搜索将丢失尚未保存的部门排序",
            "排序保存成功", "SORT_CONFLICT", "服务器排序基线已变化",
        ):
            self.assertIn(text, source)
        self.assertIn("conflictIds", source)
        self.assertIn("localValue === 8", source)
        self.assertIn("arm_message_capture", source)
        self.assertIn("pw dialog-accept", source)

    def test_gate_covers_notice_workflow_and_accessible_keyboard_flow(self):
        source = (ROOT / "scripts/verify-system-management-browser.sh").read_text(encoding="utf-8")
        for text in (
            'BROWSER_PHASE="${ERP_SYSTEM_BROWSER_PHASE:-full}"',
            "noticeWorkflow",
            "system:notice:publish",
            "新建公告草稿",
            "保存只会生成草稿，不会向任何人广播",
            "预计接收 3 人（已跨规则去重）",
            "发布后将固化接收人快照",
            "立即发布",
            "计划发布",
            "公告发布成功",
            "公告内容工具栏",
            "desktop-login-username",
            "停用角色：门店查看员",
        ):
            self.assertIn(text, source)
        self.assertIn("pw press Escape", source)
        self.assertIn("document.activeElement", source)

    def test_dist_server_is_loopback_history_fallback_and_no_store(self):
        source = (ROOT / "erp-ui/scripts/serve-dist.cjs").read_text(encoding="utf-8")
        self.assertIn('const host = "127.0.0.1"', source)
        self.assertIn('path.resolve(root, "index.html")', source)
        self.assertIn('"Cache-Control": "no-store"', source)


if __name__ == "__main__":
    unittest.main()
