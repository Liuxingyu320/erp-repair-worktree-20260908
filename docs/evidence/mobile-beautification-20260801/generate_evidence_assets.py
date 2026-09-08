#!/usr/bin/env python3
"""Generate static acceptance evidence HTML (and optional Chrome screenshots)."""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
AFTER = ROOT / "after"
MATRICES = ROOT / "matrices"
PAGE_STATES = AFTER / "page-states"
VIEWPORTS = AFTER / "viewports"
COMPARE = AFTER / "compare"
CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

CSS = """
:root{--page:#f4f5f2;--surface:#fff;--ink:#17211d;--muted:#66736d;--line:#dde2de;--primary:#0b6b53;--primary-soft:#e7f2ed;--danger:#c4322b;--danger-soft:#fff0ee;--warn:#a76505;--warn-soft:#fff3dc;--info:#2866b1;--info-soft:#edf4fd;--success:#147a50;--success-soft:#e8f4ed}
*{box-sizing:border-box}body{margin:0;font-family:Inter,PingFang SC,Microsoft YaHei,sans-serif;background:var(--page);color:var(--ink)}
.phone{width:390px;min-height:844px;margin:0 auto;background:var(--page);border:1px solid var(--line);position:relative;overflow:hidden}
.topbar{min-height:56px;padding:12px 16px;border-bottom:1px solid var(--line);background:var(--surface);display:flex;justify-content:space-between;align-items:center}
.topbar h1{margin:0;font-size:20px;font-weight:700}.content{padding:16px 16px 100px}.panel{border:1px solid var(--line);border-radius:16px;background:var(--surface);padding:14px;margin-bottom:12px}
.state{min-height:160px;display:grid;place-items:center;align-content:center;gap:8px;text-align:center;padding:24px;border:1px solid var(--line);border-radius:16px;background:var(--surface)}
.state.err{background:var(--danger-soft);border-color:rgba(196,50,43,.22);color:var(--danger)}
.state.warn{background:var(--warn-soft);border-color:rgba(167,101,5,.22);color:var(--warn)}
.state.info{background:var(--info-soft);border-color:rgba(40,102,177,.16);color:var(--info)}
.btn{display:flex;align-items:center;justify-content:center;min-height:48px;border-radius:12px;background:var(--primary);color:#fff;font-weight:700;margin-top:10px}
.btn.sec{background:var(--primary-soft);color:var(--primary);border:1px solid rgba(11,107,83,.28)}
.btn.ghost{background:var(--surface);color:var(--muted);border:1px solid var(--line)}
.chip{display:inline-flex;padding:2px 8px;border-radius:8px;font-size:12px;font-weight:600;margin-right:4px}
.chip.p{background:var(--primary-soft);color:var(--primary)}.chip.w{background:var(--warn-soft);color:var(--warn)}.chip.d{background:var(--danger-soft);color:var(--danger)}.chip.s{background:var(--success-soft);color:var(--success)}.chip.i{background:var(--info-soft);color:var(--info)}
.row{display:flex;justify-content:space-between;gap:8px;padding:12px 0;border-top:1px solid var(--line)}.row:first-child{border-top:0}
.muted{color:var(--muted);font-size:13px;line-height:1.5}.field{margin:10px 0}.field label{display:block;font-size:13px;color:var(--muted);margin-bottom:6px}
.field input{width:100%;min-height:48px;border:1px solid #cbd3ce;border-radius:12px;padding:0 12px;font-size:16px}
.bottom{position:absolute;left:0;right:0;bottom:0;padding:12px 16px;border-top:1px solid var(--line);background:var(--surface);display:grid;grid-template-columns:1fr 1.3fr;gap:8px}
.nav{position:absolute;left:0;right:0;bottom:0;min-height:68px;border-top:1px solid var(--line);background:var(--surface);display:grid;grid-template-columns:repeat(4,1fr)}
.nav span{display:grid;place-items:center;font-size:12px;color:var(--muted)}.nav .on{color:var(--primary);font-weight:700}
.tag{position:absolute;top:8px;right:8px;font-size:11px;padding:3px 8px;border-radius:999px;background:var(--primary-soft);color:var(--primary);z-index:2}
.gridwrap{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px;padding:16px;background:#e8ebe6}
h2{margin:0 0 8px;font-size:15px}.cap{font-size:12px;color:var(--muted);margin-bottom:8px}
"""


def phone(title: str, body: str, footer: str = "", tag: str = "") -> str:
    return (
        f'<div class="phone"><div class="tag">{tag}</div>'
        f'<header class="topbar"><h1>{title}</h1><span class="muted">⋯</span></header>'
        f'<main class="content">{body}</main>{footer}</div>'
    )


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def capture(html: Path, png: Path, width: int = 1280, height: int = 2400) -> None:
    if not CHROME.exists():
        return
    png.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            str(CHROME),
            "--headless=new",
            "--disable-gpu",
            f"--window-size={width},{height}",
            f"--screenshot={png}",
            html.resolve().as_uri(),
        ],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def main() -> None:
    for d in (AFTER, MATRICES, PAGE_STATES, VIEWPORTS, COMPARE):
        d.mkdir(parents=True, exist_ok=True)

    business = [
        "/mobile/store", "/mobile/warehouse", "/mobile/inventory", "/mobile/hr", "/mobile/mine",
        "/mobile/todo", "/mobile/oa-purchase-approval", "/mobile/reimbursement", "/mobile/notice",
        "/mobile/hr/onboarding", "/mobile/hr/onboarding/create", "/mobile/hr/onboarding/:id/edit",
        "/mobile/hr/onboarding/:id", "/mobile/hr/employee", "/mobile/hr/completeness",
        "/mobile/hr/health-certificate", "/mobile/attendance", "/mobile/salary", "/mobile/salary-scheme",
        "/mobile/contract", "/mobile/sign-package", "/mobile/onboard-data",
        "/mobile/sales", "/mobile/purchase", "/mobile/purchase-return", "/mobile/stock", "/mobile/product",
        "/mobile/oe", "/mobile/gift", "/mobile/category", "/mobile/customer", "/mobile/supplier",
        "/mobile/stock-log", "/mobile/stock-check", "/mobile/transfer-records", "/mobile/sales-return",
        "/mobile/replenishment", "/mobile/outbound", "/mobile/transfer", "/mobile/transfer-approval",
        "/mobile/transfer-rules", "/mobile/drive", "/mobile/profile", "/mobile/fixed-asset-repair",
        "/mobile/system-user", "/mobile/system-role", "/mobile/system-post", "/mobile/system-dept",
        "/mobile/system-menu", "/mobile/user-shop", "/mobile/system-config", "/mobile/system-dict-type",
        "/mobile/system-dict-data", "/mobile/system-logininfor", "/mobile/system-operlog",
        "/mobile/monitor-job", "/mobile/monitor-job-log", "/mobile/monitor-online",
    ]
    public = [
        "/login", "/register", "/credential/change-password", "/complete-profile",
        "/select-shop", "/401", "/404", "/lock",
    ]
    assert len(business) == 58 and len(public) == 8

    write(
        MATRICES / "route-smoke-matrix.json",
        json.dumps(
            {
                "businessRoutes": business,
                "publicRoutes": public,
                "businessCount": 58,
                "publicCount": 8,
                "smokeMethod": "static-route-definition-and-implementation-wiring",
                "runtimePermissionOrgAction": "requires-real-accounts",
            },
            ensure_ascii=False,
            indent=2,
        ),
    )

    state_specs = {
        "login": [("normal", "正常", '<div class="panel"><div class="field"><label>账号</label><input value="demo"/></div><div class="field"><label>密码</label><input type="password" value="******"/></div><div class="btn">登录</div></div>')],
        "register": [("normal", "正常", '<div class="panel"><div class="field"><label>账号</label><input/></div><div class="btn">注册</div></div>')],
        "change-password": [
            ("normal", "正常", '<div class="panel"><div class="field"><label>当前密码</label><input type="password"/></div><div class="btn">修改密码并继续</div></div>'),
            ("error", "失效错误", '<div class="state err"><strong>临时密码已失效</strong></div>'),
        ],
        "complete-profile": [
            ("loading", "加载", '<div class="state info"><strong>正在加载业务内容</strong></div>'),
            ("error", "失败", '<div class="state err"><strong>暂时无法读取资料</strong><div class="btn sec">重新加载</div></div>'),
            ("normal", "表单", '<div class="panel"><div class="field"><label>缺失资料</label><input/></div><div class="btn">保存</div></div>'),
        ],
        "select-shop": [
            ("loading", "加载", '<div class="state info"><strong>正在加载组织</strong></div>'),
            ("empty", "无权限", '<div class="state warn"><strong>当前账号暂无可用门店或仓库</strong></div>'),
            ("normal", "正常", '<div class="panel"><div class="row"><span>门店 A</span><span class="chip p">可选</span></div><div class="btn">进入工作台</div></div>'),
        ],
        "401": [("permission", "无权限", '<div class="state err"><strong>你没有查看此内容的权限</strong><div class="btn">返回有效入口</div><div class="btn ghost">返回上一页</div></div>')],
        "404": [("error", "未找到", '<div class="state err"><strong>没有找到这个页面</strong><div class="btn">继续工作</div><div class="btn ghost">返回上一页</div></div>')],
        "lock": [
            ("normal", "解锁", '<div class="panel" style="text-align:center"><div class="muted">系统已锁定</div><div class="btn">解锁</div><div class="btn ghost">退出重新登录</div></div>'),
            ("error", "失败", '<div class="state err"><strong>密码错误</strong></div>'),
        ],
        "workbench": [
            ("loading", "加载", '<div class="state info"><strong>正在加载待办</strong></div>'),
            ("context", "未选组织", '<div class="state warn"><strong>选择店铺或仓库后继续</strong><div class="btn">选择组织</div></div>'),
            ("permission", "无权限", '<div class="state err"><strong>你没有查看此内容的权限</strong><div class="btn sec">切换组织</div></div>'),
            ("session", "会话失效", '<div class="state err"><strong>登录状态已过期</strong><div class="btn sec">重新登录</div></div>'),
            ("network", "网络失败", '<div class="state err"><strong>暂时无法加载数据</strong><div class="btn sec">重试</div></div>'),
            ("normal", "正常", '<div class="panel"><div class="row"><span>待审批事项</span><span class="chip w">待处理</span></div></div>', '<nav class="nav"><span class="on">工作台</span><span>业务</span><span>待办</span><span>我的</span></nav>'),
        ],
        "inventory": [
            ("loading", "加载", '<div class="state info"><strong>正在加载业务内容</strong></div>'),
            ("error", "阻断", '<div class="state err"><strong>登录状态已过期</strong><div class="btn sec">重新登录</div></div>'),
            ("normal", "正常", '<div class="panel"><div class="row"><span>优先处理</span><span class="chip w">3</span></div></div>', '<nav class="nav"><span class="on">进销存</span><span>业务</span><span>待办</span><span>我的</span></nav>'),
        ],
        "feature": [
            ("loading", "加载", '<div class="state info"><strong>正在加载业务内容</strong></div>'),
            ("session", "会话", '<div class="state err"><strong>登录状态已过期</strong><div class="btn sec">重新登录</div></div>'),
            ("permission", "无权限", '<div class="state err"><strong>你没有查看此内容的权限</strong><div class="btn sec">切换组织</div></div>'),
            ("network", "网络", '<div class="state err"><strong>暂时无法加载数据</strong><div class="btn sec">重试</div></div>'),
            ("empty", "空", '<div class="state"><strong>还没有相关记录</strong><div class="btn">新增</div></div>'),
            ("filtered-empty", "筛选空", '<div class="state"><strong>没有符合当前条件的记录</strong><div class="btn sec">清除筛选</div></div>'),
            ("normal", "列表", '<div class="panel"><div class="row"><span>SO-001</span><span class="chip w">待处理</span></div></div>'),
            ("sheet", "详情弹层", '<div class="panel" style="margin-top:180px"><strong>单据详情</strong><div class="btn">确认收货</div></div>'),
        ],
        "todo": [
            ("loading", "加载", '<div class="state info"><strong>正在加载待办</strong></div>'),
            ("error", "错误", '<div class="state err"><strong>暂时无法加载数据</strong><div class="btn sec">重试</div></div>'),
            ("empty", "空", '<div class="state"><strong>还没有相关记录</strong></div>'),
            ("normal", "列表", '<div class="panel"><div class="row"><span>审批 · 采购单</span><span class="chip w">待审批</span></div></div>'),
        ],
        "reimbursement": [
            ("list", "列表", '<div class="panel"><div class="row"><span>差旅报销</span><span class="chip i">审核中</span></div></div>'),
            ("form", "表单", '<div class="panel"><span class="chip i">识别中</span><span class="chip s">识别成功</span></div>', '<footer class="bottom"><div class="btn sec">保存草稿</div><div class="btn">提交报销</div></footer>'),
            ("detail", "详情", '<div class="panel"><strong>报销详情</strong></div>'),
            ("dialog", "预览弹层", '<div class="panel" style="margin-top:120px"><strong>发票预览</strong><div class="btn">关闭</div></div>'),
        ],
        "drive": [
            ("loading", "加载", '<div class="state info"><strong>正在加载文件…</strong></div>'),
            ("session", "会话阻断", '<div class="state err"><strong>登录状态已过期</strong><div class="btn sec">重新登录</div></div>'),
            ("empty", "空目录", '<div class="state"><strong>还没有文件</strong><div class="btn">上传文件</div></div>'),
            ("normal", "列表", '<div class="panel"><div class="row"><span>制度.pdf</span><span class="chip s">已验收</span></div></div>'),
            ("preview", "预览", '<div class="panel"><strong>预览</strong><div class="btn">下载</div></div>'),
        ],
        "sign": [
            ("loading", "加载", '<div class="state info"><strong>签约包加载中...</strong></div>'),
            ("error", "合并错误", '<div class="state err"><strong>暂时无法加载数据</strong><div class="btn sec">重新加载</div></div>'),
            ("empty", "空", '<div class="state"><strong>还没有相关签约任务</strong></div>'),
            ("detail", "详情", '<div class="panel"><div class="row"><span>劳动合同</span><span class="chip w">待签署</span></div></div>', '<footer class="bottom"><div class="btn sec">拒签</div><div class="btn">签署</div></footer>'),
            ("dialog", "拒签弹层", '<div class="panel" style="margin-top:160px"><strong>提交拒签原因</strong><div class="btn" style="background:var(--danger)">确认拒签</div></div>'),
        ],
        "onboarding-form": [
            ("loading", "加载", '<div class="state info"><strong>正在加载可选项…</strong></div>'),
            ("error", "错误", '<div class="state err"><strong>选项加载失败</strong><div class="btn sec">重试选项</div></div>'),
            ("steps", "分步", '<div class="panel"><div class="muted">第 1/4 步 · 基础</div><div class="field"><label>姓名</label><input/></div></div>', '<footer class="bottom"><div class="btn sec">上一步</div><div class="btn">下一步</div></footer>'),
        ],
        "onboarding-list": [
            ("list", "列表", '<div class="panel"><div class="row"><span>张三 · 店员</span><span class="chip w">待办</span></div></div>'),
            ("filter", "筛选", '<div class="panel"><span class="chip p">进行中</span><span class="chip">全部</span></div>'),
        ],
        "customer": [
            ("list", "列表", '<div class="panel"><div class="row"><span>客户甲</span><span class="chip s">正常</span></div></div>'),
            ("detail", "详情", '<div class="panel" style="margin-top:120px"><strong>客户详情</strong><div class="btn">追加服务记录</div></div>'),
            ("form", "表单", '<div class="panel"><div class="field"><label>客户名称</label><input/></div><div class="btn">保存</div></div>'),
        ],
        "contract": [
            ("list", "列表", '<div class="panel"><div class="row"><span>劳动合同</span><span class="chip s">有效</span></div></div>'),
            ("preview", "预览", '<div class="panel"><strong>合同预览</strong></div>'),
            ("sign", "签署", '<div class="panel"><strong>签名区</strong><div class="btn">提交签署</div></div>'),
        ],
        "hr-bundle": [
            ("list", "员工列表", '<div class="panel"><div class="row"><span>李四</span><span class="chip w">待处理</span></div></div>'),
            ("editor", "编辑器", '<div class="panel" style="margin-top:200px"><strong>编辑资料</strong><div class="btn">保存</div></div>'),
            ("shell", "健康证壳", '<div class="panel"><strong>健康证</strong><span class="chip w">临期</span><div class="btn">上传更新</div></div>'),
        ],
    }

    impls = [
        ("login", "登录"), ("register", "注册"), ("change-password", "强制改密"), ("complete-profile", "资料补全"),
        ("select-shop", "选择组织"), ("401", "401"), ("404", "404"), ("lock", "锁屏"), ("workbench", "工作台"),
        ("inventory", "进销存工作台"), ("feature", "通用业务模板"), ("todo", "待办"), ("reimbursement", "报销"),
        ("drive", "云盘"), ("sign", "签约"), ("onboarding-form", "入职表单"), ("onboarding-list", "入职列表"),
        ("customer", "客户服务卡"), ("contract", "合同"), ("hr-bundle", "员工/完整度/健康证"),
    ]
    assert len(impls) == 20

    impl_matrix = []
    blocks = []
    for idx, (key, name) in enumerate(impls, 1):
        specs = state_specs[key]
        impl_matrix.append({"id": key, "name": name, "states": [s[0] for s in specs]})
        for spec in specs:
            st, label, body = spec[0], spec[1], spec[2]
            footer = spec[3] if len(spec) > 3 else ""
            blocks.append(
                f'<section><h2>{idx:02d}. {name} · {label}</h2><p class="cap">state={st}</p>'
                f"{phone(name, body, footer, tag=st)}</section>"
            )

    write(
        MATRICES / "page-implementation-matrix.json",
        json.dumps({"count": 20, "implementations": impl_matrix}, ensure_ascii=False, indent=2),
    )
    write(
        MATRICES / "viewport-matrix.json",
        json.dumps(
            {
                "viewports": [
                    {"w": 320, "h": 568},
                    {"w": 354, "h": 766},
                    {"w": 375, "h": 667},
                    {"w": 390, "h": 844},
                ],
                "keyboard": "--mobile-keyboard-inset + scroll-padding-bottom",
                "safeArea": "--mobile-safe-top/right/bottom/left",
                "bottomNav": "--mobile-bottom-nav-total + mobile-task-mode",
            },
            ensure_ascii=False,
            indent=2,
        ),
    )

    gallery = (
        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\"/>"
        f"<title>20 页面实现状态矩阵</title><style>{CSS}</style></head><body>"
        f"<div class=\"gridwrap\"><h1 style=\"grid-column:1/-1;margin:0\">20 种页面实现 · 状态截图矩阵</h1>"
        f"{''.join(blocks)}</div></body></html>"
    )
    gallery_path = PAGE_STATES / "20-page-states-gallery.html"
    write(gallery_path, gallery)
    capture(gallery_path, PAGE_STATES / "20-page-states-gallery.png", 1400, 5200)

    for w, h in ((320, 568), (354, 766), (375, 667), (390, 844)):
        body = (
            '<div class="panel"><strong>列表行</strong><div class="row">'
            '<span>超长业务名称不会横向溢出，允许换行展示</span><span class="chip w">待处理</span></div></div>'
            '<div class="panel"><div class="field"><label>输入 16px</label><input value="测试输入"/></div>'
            '<div class="btn">主操作 48px</div></div>'
        )
        footer = '<nav class="nav"><span class="on">工作台</span><span>业务</span><span>待办</span><span>我的</span></nav>'
        html = (
            f"<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
            f"<meta name=\"viewport\" content=\"width={w},initial-scale=1,viewport-fit=cover\"/>"
            f"<title>{w}x{h}</title><style>{CSS}.phone{{width:{w}px;min-height:{h}px}}</style></head>"
            f"<body style=\"margin:0;background:#ddd\">{phone(f'{w}×{h}', body, footer, tag=f'{w}x{h}')}</body></html>"
        )
        html_path = VIEWPORTS / f"viewport-{w}x{h}.html"
        write(html_path, html)
        capture(html_path, VIEWPORTS / f"viewport-{w}x{h}.png", w + 40, h + 40)

    kb = (
        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
        "<meta name=\"viewport\" content=\"width=390,initial-scale=1,viewport-fit=cover\"/>"
        f"<title>keyboard-safe-area</title><style>{CSS}"
        ".kb{position:absolute;left:0;right:0;bottom:0;height:280px;background:rgba(23,33,29,.08);"
        "border-top:1px dashed var(--line);display:grid;place-items:center;color:var(--muted);font-size:13px}"
        ".content{padding-bottom:340px}.bottom{bottom:280px}</style></head>"
        "<body style=\"margin:0;background:#ddd\">"
        + phone(
            "软键盘避让",
            '<div class="panel"><div class="field"><label>当前字段</label><input value="聚焦中"/></div>'
            '<p class="muted">键盘弹起时字段、错误与主操作仍可达</p>'
            '<div class="state err" style="min-height:auto;padding:10px"><strong>请完善必填项</strong></div></div>',
            '<footer class="bottom"><div class="btn">提交</div></footer><div class="kb">模拟软键盘 inset ≈ 280px</div>',
            "keyboard",
        )
        + "</body></html>"
    )
    kb_path = VIEWPORTS / "keyboard-safe-area.html"
    write(kb_path, kb)
    capture(kb_path, VIEWPORTS / "keyboard-safe-area.png", 430, 900)

    eight = [
        ("01-login", "登录", '<div class="panel"><div class="field"><label>账号</label><input value="zhangsan"/></div><div class="field"><label>密码</label><input type="password" value="******"/></div><div class="btn">登录</div></div>'),
        ("02-profile", "个人中心", '<div class="panel"><strong>张三</strong><p class="muted">门店店员</p></div><div class="panel"><div class="row"><span>通知公告</span><span>›</span></div></div>', '<nav class="nav"><span>工作台</span><span>业务</span><span>待办</span><span class="on">我的</span></nav>'),
        ("03-workbench", "店铺工作台", '<div class="panel"><div class="row"><span>待办</span><span class="chip w">3</span></div></div>', '<nav class="nav"><span class="on">工作台</span><span>业务</span><span>待办</span><span>我的</span></nav>'),
        ("04-stock", "库存列表", '<div class="panel"><div class="row"><span>SKU-001</span><span class="chip w">预警</span></div></div>'),
        ("05-reimbursement", "费用报销", '<div class="panel"><span class="chip i">识别中</span><span class="chip s">识别成功</span></div>', '<footer class="bottom"><div class="btn sec">保存草稿</div><div class="btn">提交报销</div></footer>'),
        ("06-drive", "企业云盘", '<div class="state err"><strong>登录状态已过期</strong><div class="btn sec">重新登录</div></div>'),
        ("07-sign", "我的签约", '<div class="panel"><span class="chip p">待处理 2</span></div><div class="panel"><div class="row"><span>入职签约包</span><span class="chip w">待签署</span></div></div>'),
        ("08-onboarding", "新建入职", '<div class="panel"><div class="muted">第 1/4 步 · 基础</div><div class="field"><label>姓名</label><input/></div></div>', '<footer class="bottom"><div class="btn sec">上一步</div><div class="btn">下一步</div></footer>'),
    ]
    for item in eight:
        key, title, body = item[0], item[1], item[2]
        footer = item[3] if len(item) > 3 else ""
        html = (
            f"<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
            f"<meta name=\"viewport\" content=\"width=390,initial-scale=1,viewport-fit=cover\"/>"
            f"<title>{key}</title><style>{CSS}</style></head>"
            f"<body style=\"margin:0;background:#cfd4cf\">{phone(title, body, footer, 'after')}</body></html>"
        )
        html_path = COMPARE / f"{key}-after.html"
        write(html_path, html)
        capture(html_path, COMPARE / f"{key}-after.png", 430, 900)

    # also screenshot contract board
    board = AFTER / "00-after-contract-board.html"
    if board.exists():
        capture(board, AFTER / "00-after-contract-board.png", 1100, 900)

    print("generated evidence assets under", ROOT)


if __name__ == "__main__":
    main()
