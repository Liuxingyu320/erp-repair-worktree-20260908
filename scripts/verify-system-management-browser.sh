#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${ERP_SYSTEM_BROWSER_BASE_URL:-http://127.0.0.1:19528}"
API_PREFIX="${ERP_SYSTEM_BROWSER_API_PREFIX:-/prod-api}"
NODE_BIN="${NODE_BIN:-$(command -v node || true)}"
PLAYWRIGHT_CLI_VERSION="0.1.17"
SESSION="erp-system-management-$PPID-$$"
OUTPUT_DIR="$ROOT_DIR/output/playwright/system-management"
BROWSER_PHASE="${ERP_SYSTEM_BROWSER_PHASE:-full}"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

[[ "$BASE_URL" =~ ^http://(127\.0\.0\.1|localhost)(:[0-9]+)?(/.*)?$ ]] \
    || fail "browser gate only accepts a loopback frontend URL"
[[ "$API_PREFIX" =~ ^/[A-Za-z0-9_-]+$ ]] || fail "unsafe browser API prefix"
[[ -n "$NODE_BIN" && -x "$NODE_BIN" ]] || fail "a working native Node.js executable is required"
case "$BROWSER_PHASE" in
    full|management|sort|notice) ;;
    *) fail "unsupported browser phase: $BROWSER_PHASE" ;;
esac
mkdir -p "$OUTPUT_DIR"

NPX_CLI_JS="${NPX_CLI_JS:-}"
if [[ -z "$NPX_CLI_JS" ]]; then
    for candidate in \
        /opt/homebrew/lib/node_modules/npm/bin/npx-cli.js \
        /usr/local/lib/node_modules/npm/bin/npx-cli.js; do
        if [[ -f "$candidate" ]]; then
            NPX_CLI_JS="$candidate"
            break
        fi
    done
fi

if [[ -n "$NPX_CLI_JS" && -f "$NPX_CLI_JS" ]]; then
    PW_PREFIX=("$NODE_BIN" "$NPX_CLI_JS" --yes --package "@playwright/cli@$PLAYWRIGHT_CLI_VERSION" playwright-cli)
else
    NPX_BIN="${NPX_BIN:-$(command -v npx || true)}"
    [[ -n "$NPX_BIN" ]] || fail "npx is required for Playwright CLI"
    PW_PREFIX=("$NPX_BIN" --yes --package "@playwright/cli@$PLAYWRIGHT_CLI_VERSION" playwright-cli)
fi

pw()
{
    (cd "$OUTPUT_DIR" && PATH="$(dirname "$NODE_BIN"):$PATH" \
        "${PW_PREFIX[@]}" --session "$SESSION" "$@")
}

cleanup()
{
    pw close >/dev/null 2>&1 || true
}
trap cleanup EXIT HUP INT TERM

extract_ref()
{
    local snapshot="$1"
    local pattern="$2"
    local line
    local ref
    line="$(printf '%s\n' "$snapshot" | grep -E "$pattern" | head -1 || true)"
    ref="$(printf '%s\n' "$line" | sed -n 's/.*\[ref=\([^]]*\)\].*/\1/p')"
    [[ -n "$ref" ]] || fail "snapshot element not found: $pattern"
    printf '%s' "$ref"
}

assert_snapshot()
{
    local snapshot="$1"
    local expected="$2"
    printf '%s\n' "$snapshot" | grep -Fq -- "$expected" \
        || fail "browser snapshot misses: $expected"
}

assert_eval_true()
{
    local expression="$1"
    local message="$2"
    local actual
    actual="$(pw eval "$expression")"
    printf '%s\n' "$actual" | grep -qx 'true' || fail "$message: $actual"
}

arm_message_capture()
{
    assert_eval_true '() => { if (window.__systemGateMessageObserver) window.__systemGateMessageObserver.disconnect(); window.__systemGateMessages = []; const capture = () => Array.from(document.querySelectorAll(".el-message")).forEach(message => { const value = message.textContent.trim(); if (value && !window.__systemGateMessages.includes(value)) window.__systemGateMessages.push(value); }); window.__systemGateMessageObserver = new MutationObserver(capture); window.__systemGateMessageObserver.observe(document.body, { childList: true, subtree: true, characterData: true }); capture(); return true; }' "browser gate could not arm transient message capture"
}

pw open about:blank --browser chrome >/dev/null
pw resize 1366 768 >/dev/null
pw route "**${API_PREFIX}/**" --status 200 --content-type application/json \
    --body '{"code":200,"data":[],"rows":[],"total":0}' >/dev/null
pw route "**${API_PREFIX}/code" --status 200 --content-type application/json \
    --body '{"code":200,"captchaEnabled":false}' >/dev/null
pw route "**${API_PREFIX}/auth/login" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"access_token":"browser-smoke-token","expires_in":3600}}' >/dev/null
pw route "**${API_PREFIX}/system/user/getInfo" --status 200 --content-type application/json \
    --body '{"code":200,"user":{"userId":1,"deptId":100,"userName":"release_admin","nickName":"发布验收员","avatar":""},"roles":["admin"],"permissions":["system:user:list","system:user:query","system:user:add","system:user:edit","system:user:remove","system:user:export","system:user:import","system:user:resetPwd","system:user:pii:read","system:user:pii:edit","system:user:pii:export","system:userShop:list","system:userShop:query","system:userShop:edit","system:role:list","system:role:query","system:role:add","system:role:edit","system:role:remove","system:role:export","system:dept:list","system:dept:query","system:dept:add","system:dept:edit","system:dept:remove","system:menu:list","system:menu:query","system:menu:add","system:menu:edit","system:menu:remove","system:config:list","system:config:query","system:config:edit","system:config:remove","system:config:refresh","system:operlog:list","system:operlog:query","system:notice:list","system:notice:query","system:notice:add","system:notice:edit","system:notice:remove","system:notice:publish"],"credentialState":"ACTIVE","temporaryPasswordExpiresAt":null,"profileCompletionRequired":false,"profileMissingFields":[],"driveEnabled":false,"businessFeatures":{"systemManagementUxV2":true,"noticeWorkflow":true},"pwdChrtype":"0","systemBuild":{"commit":"1111111111111111111111111111111111111111","buildTime":"2026-07-14T11:30:00Z","version":"3.6.8"}}' >/dev/null
pw route "**${API_PREFIX}/system/menu/getRouters" --status 200 --content-type application/json \
    --body '{"code":200,"data":[{"name":"System","path":"/system","hidden":false,"redirect":"noRedirect","component":"Layout","alwaysShow":true,"meta":{"title":"系统管理","icon":"system","noCache":false,"link":null},"children":[{"name":"User","path":"user","hidden":false,"component":"system/user/index","meta":{"title":"用户管理","icon":"user","noCache":false,"link":null}},{"name":"Shop","path":"shop","hidden":false,"component":"system/shop/index","meta":{"title":"组织授权","icon":"tree","noCache":false,"link":null}},{"name":"Role","path":"role","hidden":false,"component":"system/role/index","meta":{"title":"角色管理","icon":"peoples","noCache":false,"link":null}},{"name":"Dept","path":"dept","hidden":false,"component":"system/dept/index","meta":{"title":"部门管理","icon":"tree","noCache":false,"link":null}},{"name":"Menu","path":"menu","hidden":false,"component":"system/menu/index","meta":{"title":"菜单管理","icon":"tree-table","noCache":false,"link":null}},{"name":"Notice","path":"notice","hidden":false,"component":"system/notice/index","meta":{"title":"通知公告","icon":"message","noCache":false,"link":null}},{"name":"Config","path":"config","hidden":false,"component":"system/config/index","meta":{"title":"参数设置","icon":"edit","noCache":false,"link":null}},{"name":"Operlog","path":"operlog","hidden":false,"component":"system/operlog/index","meta":{"title":"操作日志","icon":"form","noCache":false,"link":null}}]}]}' >/dev/null
pw route "**${API_PREFIX}/system/dept/shop-tree" --status 200 --content-type application/json \
    --body '{"code":200,"data":[{"deptId":100,"deptName":"发布验收门店","deptType":"STORE","children":[]}]}' >/dev/null
pw route "**${API_PREFIX}/system/user/list**" --status 200 --content-type application/json \
    --body '{"code":200,"rows":[{"userId":20,"deptId":100,"userName":"release_admin","nickName":"发布验收员","phonenumber":"138****8000","status":"0","createTime":"2026-07-14 12:00:00","postNames":"系统管理员","roleCount":1,"shopScopeCount":1,"shopScopeNames":"华南公司","setupStatus":"complete","dept":{"deptId":100,"deptName":"华南公司"},"profile":{"employeeNo":"E020","employeeStatus":"正式"}}],"total":1}' >/dev/null
pw route "**${API_PREFIX}/system/user/setup-summary**" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"totalCount":7,"activeCount":6,"disabledCount":1,"missingRoleCount":2,"missingShopScopeCount":1,"completeCount":4}}' >/dev/null
pw route "**${API_PREFIX}/system/user/shop/tree" --status 200 --content-type application/json \
    --body '{"code":200,"data":[{"deptId":100,"parentId":0,"ancestors":"0","deptName":"华南公司","deptType":"COMPANY","children":[{"deptId":201,"parentId":100,"ancestors":"0,100","deptName":"南山门店","deptType":"STORE","children":[]},{"deptId":202,"parentId":100,"ancestors":"0,100","deptName":"福田门店","deptType":"STORE","children":[]}]}]}' >/dev/null
pw route "**${API_PREFIX}/system/user/shop/batch**" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"20":[100]}}' >/dev/null
pw route "**${API_PREFIX}/system/user/shop/20" --status 200 --content-type application/json \
    --body '{"code":200,"shopIds":[100,999],"editableShopIds":[100],"preservedShopIds":[999],"outOfScopeCount":1}' >/dev/null
pw route "**${API_PREFIX}/system/user/shop/20/preview" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"normalizedShopIds":[100,202],"directAddedIds":[202],"directRemovedIds":[],"directAddedCount":1,"directRemovedCount":0,"effectiveAddedCount":0,"effectiveRemovedCount":0,"preservedShopIds":[999],"preservedCount":1,"warnings":["有1项当前管理范围外授权会原样保留"],"scopeVersion":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}}' >/dev/null
pw route "**${API_PREFIX}/system/role/list**" --status 200 --content-type application/json \
    --body '{"code":200,"rows":[{"roleId":2,"roleName":"门店查看员","roleKey":"store_viewer","roleSort":2,"status":"0","createTime":"2026-07-14 12:00:00"}],"total":1}' >/dev/null
pw route "**${API_PREFIX}/system/menu/treeselect" --status 200 --content-type application/json \
    --body '{"code":200,"data":[{"id":101,"label":"系统管理","menuType":"M","children":[{"id":102,"label":"角色管理","menuType":"C","children":[{"id":103,"label":"角色新增","menuType":"F","children":[]}]}]}]}' >/dev/null
pw route "**${API_PREFIX}/system/role/deptTree" --status 200 --content-type application/json \
    --body '{"code":200,"data":[{"id":100,"label":"华南公司","children":[{"id":201,"label":"南山门店","children":[]}]}]}' >/dev/null
pw route "**${API_PREFIX}/system/role" --status 200 --content-type application/json \
    --body '{"code":200,"data":88,"msg":"新增成功"}' >/dev/null
pw route "**${API_PREFIX}/system/dept/list**" --status 200 --content-type application/json \
    --body '{"code":200,"data":[{"deptId":100,"parentId":0,"ancestors":"0","deptName":"华南公司","deptType":"COMPANY","orderNum":5,"status":"0","leader":"发布验收员","leaderUserId":1,"createTime":"2026-07-14 12:00:00"}]}' >/dev/null
pw route "**${API_PREFIX}/system/legalEntity/options" --status 200 --content-type application/json \
    --body '{"code":200,"data":[]}' >/dev/null
pw route "**${API_PREFIX}/system/dept/updateSort" --status 200 --content-type application/json \
    --body '{"code":200,"msg":"排序保存成功"}' >/dev/null
pw route "**${API_PREFIX}/system/menu/list**" --status 200 --content-type application/json \
    --body '{"code":200,"data":[{"menuId":301,"parentId":0,"menuName":"系统管理","menuType":"M","orderNum":6,"path":"system","component":"Layout","perms":"","icon":"system","isFrame":"1","status":"0"}]}' >/dev/null
pw route "**${API_PREFIX}/system/menu/updateSort" --status 200 --content-type application/json \
    --body '{"code":409,"msg":"排序基线冲突","businessCode":"SORT_CONFLICT","conflictIds":[301]}' >/dev/null
pw route "**${API_PREFIX}/system/notice/list**" --status 200 --content-type application/json \
    --body '{"code":200,"rows":[{"noticeId":41,"noticeTitle":"七月门店经营提醒","noticeType":"2","status":"1","lifecycleStatus":"DRAFT","audienceType":"ALL","scheduledPublishTime":null,"publishedTime":null,"expireTime":null,"version":2,"previousNoticeId":null,"recipientCount":0,"createBy":"release_admin","createTime":"2026-07-14 12:00:00"}],"total":1}' >/dev/null
pw route "**${API_PREFIX}/system/notice/41" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"noticeId":41,"noticeTitle":"七月门店经营提醒","noticeType":"2","noticeContent":"<p><strong>请各门店核对经营数据。</strong></p>","status":"1","lifecycleStatus":"DRAFT","audienceType":"ALL","scheduledPublishTime":null,"publishedTime":null,"expireTime":null,"version":2,"previousNoticeId":null,"recipientCount":0,"createBy":"release_admin","audiences":[{"noticeId":41,"targetType":"ALL","targetId":0,"includeChildren":false}]}}' >/dev/null
pw route "**${API_PREFIX}/system/notice/audience-options**" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"departments":[{"id":100,"label":"华南公司","description":"部门 / COMPANY"}],"roles":[{"id":2,"label":"门店查看员","description":"角色 / store_viewer"}],"users":[{"id":20,"label":"发布验收员","description":"华南公司 / release_admin"}]}}' >/dev/null
pw route "**${API_PREFIX}/system/notice/audience-preview" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"recipientCount":3,"ruleCount":1,"organizationCounts":{"华南公司":2,"总部":1},"warnings":[]}}' >/dev/null
pw route "**${API_PREFIX}/system/notice/41/publish" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"noticeId":41,"lifecycleStatus":"PUBLISHED","status":"0","version":3,"recipientCount":3}}' >/dev/null
pw route "**${API_PREFIX}/system/notice" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"noticeId":42,"noticeTitle":"浏览器验收草稿","noticeType":"1","noticeContent":"<p>仅保存草稿，不广播。</p>","status":"1","lifecycleStatus":"DRAFT","audienceType":"ALL","version":1,"audiences":[{"targetType":"ALL","targetId":0,"includeChildren":false}]}}' >/dev/null

pw goto "$BASE_URL/system/user" >/dev/null
login_snapshot="$(pw snapshot)"
assert_snapshot "$login_snapshot" 'main "ERP登录"'
assert_eval_true '() => { const account = document.querySelector("label[for=desktop-login-username]"); const password = document.querySelector("label[for=desktop-login-password]"); return Boolean(account && password && account.textContent.trim() === "账号" && password.textContent.trim() === "密码" && account.offsetParent && password.offsetParent); }' "desktop login labels are not visibly associated with their fields"
account_ref="$(extract_ref "$login_snapshot" 'textbox "账号"')"
password_ref="$(extract_ref "$login_snapshot" 'textbox "密码"')"
login_ref="$(extract_ref "$login_snapshot" 'button "登录"')"
pw fill "$account_ref" release_admin >/dev/null
pw fill "$password_ref" 'NativeOnly-Smoke-2026!' >/dev/null
pw click "$login_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { if (document.body.textContent.includes("1 个业务组织可选")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "organization entry selection did not appear within 8 seconds after login"

shop_snapshot="$(pw snapshot)"
assert_snapshot "$shop_snapshot" '1 个业务组织可选'
enter_ref="$(extract_ref "$shop_snapshot" 'button "进入系统"')"
pw click "$enter_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { if (document.querySelector(".user-table-card")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "user management page did not become ready within 8 seconds"

if [[ "$BROWSER_PHASE" == "full" || "$BROWSER_PHASE" == "management" ]]; then
user_snapshot="$(pw snapshot)"
assert_snapshot "$user_snapshot" '用户管理'
assert_snapshot "$user_snapshot" '导出完整个人信息'
assert_snapshot "$user_snapshot" '全部账号'
assert_snapshot "$user_snapshot" '未分配角色'
assert_snapshot "$user_snapshot" '未授权管理范围'
assert_snapshot "$user_snapshot" '高级筛选'
assert_snapshot "$user_snapshot" '列预设'

for viewport in "1018 768" "1366 768" "1440 900" "1920 1080"; do
    viewport_width="${viewport%% *}"
    viewport_height="${viewport##* }"
    pw resize "$viewport_width" "$viewport_height" >/dev/null
    assert_eval_true '() => { const root = document.querySelector(".user-table-card"); const left = root && root.querySelector(".el-table__fixed"); const right = root && root.querySelector(".el-table__fixed-right"); if (!root || !left || !right) return false; const rr = root.getBoundingClientRect(); const lr = left.getBoundingClientRect(); const xr = right.getBoundingClientRect(); return lr.left >= rr.left - 2 && xr.right <= rr.right + 2 && lr.right < xr.left; }' "fixed identity/status/action columns overlap or leave the user table at viewport $viewport"
done
pw resize 1366 768 >/dev/null

avatar_ref="$(extract_ref "$user_snapshot" 'button "发布验收员"')"
pw click "$avatar_ref" >/dev/null
menu_snapshot="$(pw snapshot)"
version_ref="$(extract_ref "$menu_snapshot" 'listitem.*版本信息')"
pw click "$version_ref" >/dev/null

version_snapshot="$(pw snapshot)"
assert_snapshot "$version_snapshot" 'dialog "版本信息"'
assert_snapshot "$version_snapshot" 'system 版本'
assert_snapshot "$version_snapshot" '3.6.8'
close_ref="$(extract_ref "$version_snapshot" 'button "关 闭"')"
pw click "$close_ref" >/dev/null

user_snapshot="$(pw snapshot)"
export_ref="$(extract_ref "$user_snapshot" 'button.*导出完整个人信息')"
pw click "$export_ref" >/dev/null
export_snapshot="$(pw snapshot)"
assert_snapshot "$export_snapshot" 'dialog "导出完整个人信息"'
assert_snapshot "$export_snapshot" '这是高敏感数据导出'
assert_snapshot "$export_snapshot" 'checkbox "我确认本次导出有明确业务需要，并会按敏感文件要求保管和删除"'
assert_snapshot "$export_snapshot" 'button "确认导出" [disabled]'

pw goto "$BASE_URL/system/shop?userId=20&userName=release_admin" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const root = document.querySelector(".resizable-split-pane"); if (root && document.body.textContent.includes("用户组织授权")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "organization authorization page did not become ready within 8 seconds"
shop_scope_snapshot="$(pw snapshot)"
assert_snapshot "$shop_scope_snapshot" '用户组织授权'
assert_snapshot "$shop_scope_snapshot" 'separator "调整组织授权面板宽度"'
assert_snapshot "$shop_scope_snapshot" '仅显示已选'
assert_snapshot "$shop_scope_snapshot" '展开到匹配项'
assert_snapshot "$shop_scope_snapshot" '清空本次可编辑项'
assert_snapshot "$shop_scope_snapshot" '直接授权'
assert_snapshot "$shop_scope_snapshot" '上级继承'
assert_snapshot "$shop_scope_snapshot" '范围外保留 1'
assert_snapshot "$shop_scope_snapshot" '华南公司'
assert_snapshot "$shop_scope_snapshot" '南山门店'
assert_eval_true '() => { const pane = document.querySelector(".resizable-split-pane"); const side = document.querySelector(".resizable-split-pane__side"); return Boolean(pane && side && Math.round(side.getBoundingClientRect().width) >= 320); }' "organization authorization side pane is narrower than 320px"
preview_ref="$(extract_ref "$shop_scope_snapshot" 'button.*预览并保存')"

pw eval '() => { const boxes = Array.from(document.querySelectorAll(".shop-tree-card .el-tree-node__content .el-checkbox__input")); if (boxes.length < 3) return false; boxes[2].click(); return true; }' >/dev/null
pw click "$preview_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const dialog = Array.from(document.querySelectorAll(".el-dialog__wrapper")).find(wrapper => getComputedStyle(wrapper).display !== "none" && wrapper.textContent.includes("确认组织授权变更")); if (dialog) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "organization authorization preview did not appear within 8 seconds"
preview_snapshot="$(pw snapshot)"
assert_snapshot "$preview_snapshot" 'dialog "确认组织授权变更"'
assert_snapshot "$preview_snapshot" '直接新增'
assert_snapshot "$preview_snapshot" '生效范围增加'
assert_snapshot "$preview_snapshot" '范围外保留'
assert_snapshot "$preview_snapshot" '有1项当前管理范围外授权会原样保留'
assert_snapshot "$preview_snapshot" '保存时会再次校验授权版本'

pw unroute "**${API_PREFIX}/system/user/shop/20" >/dev/null
pw route "**${API_PREFIX}/system/user/shop/20" --status 200 --content-type application/json \
    --body '{"code":200,"data":{"normalizedShopIds":[100,202],"directAddedIds":[],"directRemovedIds":[],"directAddedCount":0,"directRemovedCount":0,"effectiveAddedCount":0,"effectiveRemovedCount":0,"preservedShopIds":[999],"preservedCount":1,"warnings":[],"scopeVersion":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}' >/dev/null
confirm_save_ref="$(extract_ref "$preview_snapshot" 'button "确认保存"')"
arm_message_capture
pw click "$confirm_save_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const success = (window.__systemGateMessages || []).some(message => message.includes("组织授权保存成功")); if (success) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "organization authorization success feedback did not appear within 8 seconds"

pw goto "$BASE_URL/system/role" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { if (document.querySelector(".role-table-card")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "role management page did not become ready within 8 seconds"
role_snapshot="$(pw snapshot)"
assert_snapshot "$role_snapshot" '角色管理'
assert_eval_true '() => Boolean(document.querySelector(".role-table-card .el-switch[aria-label=\"停用角色：门店查看员\"]"))' "role status switch has no actionable accessible name"
role_add_ref="$(extract_ref "$role_snapshot" 'button.*新增')"
pw click "$role_add_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const dialog = Array.from(document.querySelectorAll(".el-dialog__wrapper")).find(wrapper => getComputedStyle(wrapper).display !== "none" && wrapper.textContent.includes("新增角色向导")); if (dialog) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "role creation wizard did not appear within 8 seconds"
wizard_snapshot="$(pw snapshot)"
assert_snapshot "$wizard_snapshot" 'dialog "新增角色向导"'
assert_snapshot "$wizard_snapshot" '权限字符会被接口和代码长期引用'
role_name_ref="$(extract_ref "$wizard_snapshot" 'textbox "例如：门店负责人"')"
role_key_ref="$(extract_ref "$wizard_snapshot" 'textbox "例如：store_manager"')"
pw fill "$role_name_ref" 门店负责人 >/dev/null
pw fill "$role_key_ref" store_manager >/dev/null
next_ref="$(extract_ref "$wizard_snapshot" 'button "下一步"')"
pw click "$next_ref" >/dev/null
menu_step_snapshot="$(pw snapshot)"
assert_snapshot "$menu_step_snapshot" '父子联动开启时'
assert_eval_true '() => { const box = document.querySelector(".role-wizard__tree .el-checkbox__input"); if (!box) return false; box.click(); return true; }' "role wizard menu tree is not keyboard/click operable"
menu_count_snapshot="$(pw snapshot)"
assert_snapshot "$menu_count_snapshot" '目录 1'
assert_snapshot "$menu_count_snapshot" '菜单 1'
assert_snapshot "$menu_count_snapshot" '按钮 1'
next_ref="$(extract_ref "$menu_count_snapshot" 'button "下一步"')"
pw click "$next_ref" >/dev/null
scope_snapshot="$(pw snapshot)"
assert_snapshot "$scope_snapshot" '默认使用“本部门及以下”'
assert_snapshot "$scope_snapshot" 'radio "本部门及以下" [checked]'
next_ref="$(extract_ref "$scope_snapshot" 'button "下一步"')"
pw click "$next_ref" >/dev/null
confirm_role_snapshot="$(pw snapshot)"
assert_snapshot "$confirm_role_snapshot" '确认权限和数据范围'
assert_snapshot "$confirm_role_snapshot" '目录 1、菜单 1、按钮 1，共 3 项'
assert_snapshot "$confirm_role_snapshot" '本部门及以下'
create_role_ref="$(extract_ref "$confirm_role_snapshot" 'button "确认创建"')"
pw click "$create_role_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { if (document.body.textContent.includes("角色「门店负责人」已创建")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "role creation result did not appear within 8 seconds"
created_role_snapshot="$(pw snapshot)"
assert_snapshot "$created_role_snapshot" 'dialog "角色创建完成"'
assert_snapshot "$created_role_snapshot" '角色「门店负责人」已创建'
assert_snapshot "$created_role_snapshot" 'button "去添加成员"'
later_ref="$(extract_ref "$created_role_snapshot" 'button "稍后处理"')"
pw click "$later_ref" >/dev/null
fi

if [[ "$BROWSER_PHASE" == "full" || "$BROWSER_PHASE" == "sort" ]]; then
pw goto "$BASE_URL/system/dept" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { if (document.querySelector(".dept-table-card .el-input-number input")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "department sort page did not become ready within 8 seconds"
dept_snapshot="$(pw snapshot)"
assert_snapshot "$dept_snapshot" '部门管理'
assert_eval_true '() => { const button = Array.from(document.querySelectorAll("button")).find(item => item.textContent.trim() === "保存排序"); return Boolean(button && button.disabled); }' "unchanged department sort must keep save disabled"
assert_eval_true '() => { const input = document.querySelector(".dept-table-card .el-input-number input"); if (!input) return false; input.value = "9"; input.dispatchEvent(new Event("input", { bubbles: true })); input.dispatchEvent(new Event("change", { bubbles: true })); return true; }' "department sort input could not be changed"
dept_dirty_snapshot="$(pw snapshot)"
assert_snapshot "$dept_dirty_snapshot" '保存排序（已修改 1 项）'
assert_eval_true '() => { const button = Array.from(document.querySelectorAll("button")).find(item => item.textContent.trim() === "搜索"); if (!button) return false; button.click(); return true; }' "department search button could not be activated"
dept_discard_snapshot="$(pw snapshot)"
assert_snapshot "$dept_discard_snapshot" '搜索将丢失尚未保存的部门排序'
cancel_discard_ref="$(extract_ref "$dept_discard_snapshot" 'button "取消"')"
pw click "$cancel_discard_ref" >/dev/null
dept_dirty_snapshot="$(pw snapshot)"
arm_message_capture
assert_eval_true '() => { const button = Array.from(document.querySelectorAll("button")).find(item => item.textContent.replace(/\s/g, "").includes("保存排序（已修改1项）")); if (!button) return false; button.click(); return true; }' "department sort save button could not be activated"
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const success = (window.__systemGateMessages || []).some(message => message.includes("排序保存成功")); const button = Array.from(document.querySelectorAll("button")).find(item => item.textContent.trim() === "保存排序"); if (success && button && button.disabled) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "department sort success feedback and clean baseline did not appear within 8 seconds"
assert_eval_true '() => { const button = Array.from(document.querySelectorAll("button")).find(item => item.textContent.trim() === "保存排序"); return Boolean(button && button.disabled); }' "saved department sort must reset the baseline and disable save"

pw goto "$BASE_URL/system/menu" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { if (document.querySelector(".menu-table-card .el-input-number input")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "menu sort page did not become ready within 8 seconds"
menu_snapshot="$(pw snapshot)"
assert_snapshot "$menu_snapshot" '菜单管理'
assert_eval_true '() => { const input = document.querySelector(".menu-table-card .el-input-number input"); if (!input) return false; input.value = "8"; input.dispatchEvent(new Event("input", { bubbles: true })); input.dispatchEvent(new Event("change", { bubbles: true })); return true; }' "menu sort input could not be changed"
menu_dirty_snapshot="$(pw snapshot)"
assert_snapshot "$menu_dirty_snapshot" '保存排序（已修改 1 项）'
assert_eval_true '() => { const button = Array.from(document.querySelectorAll("button")).find(item => item.textContent.replace(/\s/g, "").includes("保存排序（已修改1项）")); if (!button) return false; button.click(); return true; }' "menu sort save button could not be activated"
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { if (document.body.textContent.includes("服务器排序基线已变化")) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "menu sort conflict feedback did not appear within 8 seconds"
menu_conflict_snapshot="$(pw snapshot)"
assert_snapshot "$menu_conflict_snapshot" '服务器排序基线已变化'
assert_snapshot "$menu_conflict_snapshot" '冲突 ID：301'
assert_snapshot "$menu_conflict_snapshot" '保存排序（已修改 1 项）'
assert_eval_true '() => { const input = document.querySelector(".menu-table-card .el-input-number input"); const localValue = input ? Number(input.value) : NaN; return localValue === 8; }' "menu conflict overwrote the local sort value"
fi

if [[ "$BROWSER_PHASE" == "full" || "$BROWSER_PHASE" == "notice" ]]; then
if [[ "$BROWSER_PHASE" == "full" ]]; then
    # The preceding menu conflict intentionally preserves a dirty local sort.
    # A full-page navigation must raise beforeunload; accepting it is part of
    # the cross-page dirty-state guard verification.
    pw goto "$BASE_URL/system/notice" >/dev/null
    pw dialog-accept >/dev/null
    pw goto "$BASE_URL/system/notice" >/dev/null
else
    pw goto "$BASE_URL/system/notice" >/dev/null
fi
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const root = document.querySelector(".notice-workflow-page"); const ready = root && root.textContent.includes("新建草稿") && root.textContent.includes("七月门店经营提醒"); if (ready) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "notice workflow page did not become ready within 8 seconds"
notice_snapshot="$(pw snapshot)"
assert_snapshot "$notice_snapshot" '通知公告'
assert_snapshot "$notice_snapshot" '新建草稿'
assert_snapshot "$notice_snapshot" '七月门店经营提醒'
assert_snapshot "$notice_snapshot" '版本 2 · 全员'
assert_snapshot "$notice_snapshot" '草稿'
assert_snapshot "$notice_snapshot" '尚未发布'
assert_snapshot "$notice_snapshot" 'button "隐藏搜索条件"'
assert_snapshot "$notice_snapshot" 'button "刷新列表"'
notice_add_ref="$(extract_ref "$notice_snapshot" 'button.*新建草稿')"
pw click "$notice_add_ref" >/dev/null
notice_editor_snapshot="$(pw snapshot)"
assert_snapshot "$notice_editor_snapshot" 'dialog "新建公告草稿"'
assert_snapshot "$notice_editor_snapshot" '发布时只选取启用账号并固化接收人快照'
assert_snapshot "$notice_editor_snapshot" '保存只会生成草稿，不会向任何人广播'
assert_snapshot "$notice_editor_snapshot" 'toolbar "公告内容工具栏"'
assert_snapshot "$notice_editor_snapshot" 'textbox "公告内容"'
assert_snapshot "$notice_editor_snapshot" 'button "加粗"'
assert_snapshot "$notice_editor_snapshot" 'textbox "公告草稿标题"'
assert_snapshot "$notice_editor_snapshot" 'radio "桌面" [checked]'
assert_snapshot "$notice_editor_snapshot" 'radio "移动"'
assert_eval_true '() => { const input = document.querySelector(".el-dialog[aria-label=\"新建公告草稿\"] input[placeholder=\"请输入公告标题\"]"); return Boolean(input && document.activeElement === input); }' "new notice dialog did not focus the title field"
pw press Escape >/dev/null
notice_escape_snapshot="$(pw snapshot)"
assert_snapshot "$notice_escape_snapshot" '新建草稿'
assert_eval_true '() => document.activeElement === document.querySelector(".notice-toolbar-card button")' "closing the notice editor with Escape did not restore the trigger focus"

notice_add_ref="$(extract_ref "$notice_escape_snapshot" 'button.*新建草稿')"
pw click "$notice_add_ref" >/dev/null
notice_editor_snapshot="$(pw snapshot)"
notice_title_ref="$(extract_ref "$notice_editor_snapshot" 'textbox "公告草稿标题"')"
notice_content_ref="$(extract_ref "$notice_editor_snapshot" 'textbox "公告内容"')"
notice_preview_ref="$(extract_ref "$notice_editor_snapshot" 'button.*预览接收人数')"
pw fill "$notice_title_ref" 浏览器验收草稿 >/dev/null
pw fill "$notice_content_ref" 仅保存草稿，不广播。 >/dev/null
assert_eval_true '() => { const editor = document.querySelector(".el-dialog[aria-label=\"新建公告草稿\"] .editor"); const component = editor && editor.parentElement && editor.parentElement.__vue__; if (!component || !component.Quill) return false; component.Quill.setText("仅保存草稿，不广播。"); return component.Quill.getText().includes("仅保存草稿"); }' "notice rich-text editor could not accept content"
pw click "$notice_preview_ref" >/dev/null
audience_preview_snapshot="$(pw snapshot)"
assert_snapshot "$audience_preview_snapshot" '预计接收 3 人（已跨规则去重）'
assert_snapshot "$audience_preview_snapshot" '华南公司 2 人'
assert_snapshot "$audience_preview_snapshot" '总部 1 人'
save_draft_ref="$(extract_ref "$audience_preview_snapshot" 'button.*保存草稿')"
pw click "$save_draft_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const visible = Array.from(document.querySelectorAll(".el-dialog__wrapper")).some(wrapper => getComputedStyle(wrapper).display !== "none" && wrapper.querySelector(".el-dialog[aria-label=\"新建公告草稿\"]")); if (!visible) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "saving a draft did not close the editor within 8 seconds"
draft_saved_snapshot="$(pw snapshot)"
assert_snapshot "$draft_saved_snapshot" '新建草稿'
assert_eval_true '() => document.activeElement === document.querySelector(".notice-toolbar-card button")' "saving a draft did not restore the trigger focus"

pw goto "$BASE_URL/system/notice" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const publish = Array.from(document.querySelectorAll(".notice-table-card button")).some(button => button.textContent.trim() === "发布"); if (publish) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "notice publish action did not become available within 8 seconds"
notice_snapshot="$(pw snapshot)"
publish_ref="$(extract_ref "$notice_snapshot" 'button.*发布公告：七月门店经营提醒')"
pw click "$publish_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const dialog = Array.from(document.querySelectorAll(".el-dialog__wrapper")).find(wrapper => getComputedStyle(wrapper).display !== "none" && wrapper.querySelector(".el-dialog[aria-label=\"发布确认\"]")); const radio = dialog && dialog.querySelector(".publish-form input[type=radio]"); if (radio && document.activeElement === radio) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "publish dialog did not open and focus the publish mode within 8 seconds"
publish_snapshot="$(pw snapshot)"
assert_snapshot "$publish_snapshot" 'dialog "发布确认"'
assert_snapshot "$publish_snapshot" '发布后将固化接收人快照'
assert_snapshot "$publish_snapshot" '七月门店经营提醒'
assert_snapshot "$publish_snapshot" '预计接收'
assert_snapshot "$publish_snapshot" '3 人'
assert_snapshot "$publish_snapshot" '当前版本'
assert_snapshot "$publish_snapshot" '2'
assert_snapshot "$publish_snapshot" 'radio "立即发布" [checked]'
assert_snapshot "$publish_snapshot" 'radio "计划发布"'
assert_eval_true '() => { const radio = document.querySelector(".publish-form input[type=radio]"); return Boolean(radio && document.activeElement === radio); }' "publish dialog did not focus the publish mode"
confirm_publish_ref="$(extract_ref "$publish_snapshot" 'button.*确认发布')"
arm_message_capture
pw click "$confirm_publish_ref" >/dev/null
assert_eval_true '() => new Promise(resolve => { const deadline = Date.now() + 8000; const check = () => { const success = (window.__systemGateMessages || []).some(message => message.includes("公告发布成功")); if (success) return resolve(true); if (Date.now() >= deadline) return resolve(false); setTimeout(check, 50); }; check(); })' "notice publish success feedback did not appear within 8 seconds"
fi

user_agent="$(pw eval '() => navigator.userAgent' | sed -n 's/^"\(.*\)"$/\1/p' | head -1)"
expected_browser="${ERP_SYSTEM_EXPECTED_BROWSER:-UNSET}"
if [[ "$expected_browser" != UNSET && "$user_agent" != *"Chrome/$expected_browser"* ]]; then
    fail "browser version differs from release manifest: expected Chrome/$expected_browser, actual $user_agent"
fi
printf 'SYSTEM_MANAGEMENT_BROWSER_OK cli=%s phase=%s browser=%s\n' \
    "$PLAYWRIGHT_CLI_VERSION" "$BROWSER_PHASE" "${user_agent:-chrome}"
