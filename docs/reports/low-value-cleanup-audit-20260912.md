# 低价值代码审计与最小清理 — 2026-09-12

本轮修改 10 个原本干净的代码/测试文件，新增 1 行、删除 223 行，净减少 222 行；另新增本报告。没有提交、推送或部署。

基线：`codex/review-fixes-20260908`，HEAD `76b69e3dd808e4f9c526474e2ddaab84e12bd5d1`。开始时已有 106 条修改/未跟踪状态，均避开。对 5,174 个 Git 跟踪及非忽略未跟踪文件记录 SHA-256；清理后仅批准的 10 个文件变化，其余 5,164 个内容不变，暂存区不变。

## 每项删除及安全依据

| 文件/范围 | 本轮变化 | 删除依据及保留的行为 |
| --- | --- | --- |
| `scripts/test_employee_importer.py` | 删除重复的 `test_store_level_user_keeps_official_main_department_with_store_scope`，36 行 | 与同一类中的 `test_region_detail_scope_does_not_override_official_main_department` 输入、断言及 AST body 完全相同；无自定义 setup/teardown 或方法名分支。两者单独运行通过，实际覆盖 importer 的同一组 199 行。保留区域明细不覆盖主部门的完整测试场景，套件由 33 项变为 32 项。 |
| `erp-ui/src/utils/common.js` | 删除 `sprintf` 与注释，14 行 | 全仓除声明外无调用；505 个 JS/Vue 脚本的 AST 核对、显式具名导入、main.js 注册和动态加载扫描均未发现引用。其他代码逐字节保留。 |
| `erp-ui/src/utils/validate.js` | 删除 `validUsername`、`validURL`、`validLowerCase`、`validUpperCase`、`validAlphabets`、`validEmail` 及注释，54 行 | 均为未使用的模板校验函数；无命名空间/动态工具函数加载。保留正在使用的路径、空值和外链判断；保留其余函数和混合 CRLF 字节。 |
| `erp-ui/scripts/run-node-tests.cjs` | 将重复匹配规则简化为 `/\.test\.[cm]?js$/`，1 行替换 | 旧表达式的两个 `.js` 分支均已被 `[cm]?js` 覆盖。137,271 个生成文件名和实际全部 310 个目录项比较一致，前后均发现同一组 308 个测试文件；原复制文件拒绝检查保留。 |
| `erp-common/erp-common-log/.../PropertyPreExcludeFilter.java` | 删除 24 行旧 Fastjson 过滤器 | 全仓仅类/构造器自身引用，无注解、SPI、自动配置或反射名称注册。实际 LogAspect 使用 AuditPayloadSanitizer，日志脱敏与自动配置测试通过；clean 重编译后旧 class 文件不存在。 |
| `erp-common/erp-common-core/.../EscapeUtil.java` | 删除硬编码演示 `main()` 和陈旧样例注释，12 行 | 无脚本/构建入口调用；保留 clean/escape/unescape 所有实现，HTML 过滤回归通过。 |
| `erp-modules/erp-inventory/.../InvPurchaseServiceImpl.java` | 删除未调用的 `selectProductsByDetailIds` 及其专属 Mapper 字段/import，28 行 | 方法为未注解的 private 方法，全文/字符串/反射/配置搜索仅有声明；实际保存路径使用 InventoryItemResolver。保留仓库范围检查和事务。 |
| `erp-modules/erp-inventory/.../InvSalesServiceImpl.java` | 删除同类死方法、专属 Mapper 注入及孤立的 `productScopeError(InvProduct)` 重载，41 行 | 保留实际使用的 `productScopeError(InventoryItemSnapshot)` 和组织权限校验；该死代码不是公共接口、Mapper statement 或框架入口。 |
| `erp-modules/erp-inventory/.../InvPurchaseServiceImplTest.java` | 删除已失效的服务字段注入，1 行 | 商品 Mapper 仍注入实际 InventoryItemResolver，全部 28 项测试和断言保留。 |
| `erp-modules/erp-inventory/.../InvSalesServiceImplTest.java` | 删除失效服务字段注入及由此孤立的 `injectIfPresent` 转发方法，12 行 | 该辅助方法只有被删的一处调用；实际 resolver 注入保留，全部 9 项测试和断言保留。 |

精确文件列表和补丁见 [cleanup-paths.json](../../output/low-value-audit-20260912/cleanup-paths.json)、[cleanup.patch](../../output/low-value-audit-20260912/cleanup.patch)。独立复核未发现可操作问题：[复核记录](../../output/low-value-audit-20260912/foundation-independent-review.md)。

## 主要目录覆盖

完成了每个主要目录的盘点、候选扫描和定向内容核对；这不表示逐行验证全部业务行为。

| 主要目录 | 检查与处理 |
| --- | --- |
| `erp-common` | 全部 9 个子模块；工具函数、反射、自动配置、序列化、日志与测试。仅删除旧过滤器和演示入口；保留兼容门面、DTO、注解和依赖聚合模块。 |
| `erp-api` | system/oa/approval 全部 3 个子模块；Feign 契约、降级工厂、DTO 和配置。公共接口及动态注册保留。 |
| `erp-auth` | 登录、令牌、浏览器会话和测试；双模式认证有独立职责，保留。重复 return 属可选后续小改，本次未扩展。 |
| `erp-gateway` | 路由、过滤器、验证码、配置及类路径测试；框架注册使少静态引用代码仍在工作，保留。 |
| `erp-visual` | monitor 入口、安全配置和 JWT 类路径测试；相似测试验证不同模块依赖图，保留。 |
| `erp-modules` | file/job/approval/oa/system/inventory 全部 6 个模块。检查 service/mapper/controller/resources/tests；库存仅上述删除。Spring 存储实现、字符串调用定时任务、审批回调注册、事务/锁包装器、被反射测试的隐私方法均保留。 |
| `erp-ui` | src 全部主要子目录、16 个 views 业务目录，以及 test/scripts/build/bin/patches/public/docs/android/ios 和根配置。505 个 JS/Vue 脚本完成 AST 分析；只删除已证明无消费者的导出和重复测试匹配。 |
| `scripts` | 根脚本及 ci/contract/erp-new-2/fixtures/qa/release 全部子目录；Python AST 重复方法扫描、Shell 语法、发布清单及测试入口。只删除一项完全重复测试；相同实现但有不同领域命名/调用的 clean_name/phone 保留。 |
| `docker` | compose、服务启动/打包、MySQL/Nacos/Redis/Nginx 配置及测试。SQL 镜像受有序清单和哈希契约控制，保留。 |
| `sql` | 184 个非忽略 SQL 文件的盘点、重复哈希及迁移清单关系检查。scripts/docker/sql 范围发现的 113 组 SQL 重复均未删除；静态门禁核对其中 110 个自动迁移与 1 个手工迁移的 Docker 镜像。 |
| `docs` | 814 个非忽略文件及全部一级子目录盘点，定向阅读部署/云盘手册和历史修复说明。历史证据、截图、决策和迁移记录不以日期旧为由删除。 |
| `bin` | 控制器权限检查入口及其调用/语法；保留。 |
| `ops` | monitoring 对账告警规则及业务用途；保留。 |
| `.mvn`、根文件 | Wrapper/POM/工具版本、忽略规则、来源 SHA256SUMS 和交接说明；均有构建/溯源用途，保留。 |
| `output`、`备份校验` | 四组既有输出目录、三组备份证据目录、哈希校验脚本及其索引；属于原始证据和回退材料，不作为运行代码删除。 |

详细记录：[前端](../../output/low-value-audit-20260912/frontend-audit.md)、[业务模块](../../output/low-value-audit-20260912/modules-audit.md)、[基础设施](../../output/low-value-audit-20260912/foundation-audit.md)。其中“候选”不等于本轮已实施；实施范围以本报告和 cleanup.patch 为准。

保留的典型低价值候选：原生 Android 示例测试、iOS 空生命周期回调、job 全注释配置和更大范围模板工具函数。它们需要各自工具链/使用边界验证或会扩大补丁，未在缺少相应验证时删除。安全、权限、失败分支和不同模块类路径测试不会因为简单或相似而删除。

## 验证结果

| 检查 | 修改前 | 修改后 | 证据 |
| --- | --- | --- | --- |
| Java 专注测试 | 4 套件 43 项通过 | clean 重编译后 8 套件 53 项通过，0 失败/错误/跳过 | [日志](../../output/low-value-audit-20260912/backend-after.log)、[逐套件计数](../../output/low-value-audit-20260912/backend-after-counts.json) |
| Python 导入测试 | 33 项通过 | 32 项通过，0 失败/跳过；只少重复项 | [日志](../../output/low-value-audit-20260912/python-after.log)、[AST 与执行覆盖证据](../../output/low-value-audit-20260912/duplicate-test-proof.json) |
| 前端专注测试 | 3 个文件通过 | 4 个文件通过，0 失败；增加验证现有测试门禁 | [日志](../../output/low-value-audit-20260912/frontend-after.log)、[等价性证据](../../output/low-value-audit-20260912/frontend-removal-proof.md) |
| ESLint | — | 3 个修改的 JS/CJS 文件通过 7 条正确性规则 | [命令](../../output/low-value-audit-20260912/commands.txt)、[日志](../../output/low-value-audit-20260912/eslint.log) |
| Ruff | — | 修改的 Python 测试文件通过全部 F 规则 | [日志](../../output/low-value-audit-20260912/ruff.log) |
| 仓库静态门禁 | 通过 | hygiene、Docker SQL 顺序/逐字节一致性、集成测试配置检查通过 | [日志](../../output/low-value-audit-20260912/repository-after.log) |
| 支持脚本语法盘点 | 125 个 Python AST、163 个 Shell 语法检查无语法错误 | 未作为全仓 lint 结论；Python 原有转义写法仍有 SyntaxWarning | [扫描清单](../../output/low-value-audit-20260912/support-syntax-scan.json) |
| 改动范围与回退 | 记录基线及 10 文件原件 | 定向 git diff --check、其余文件哈希、暂存区及反向补丁适用性均通过 | [范围证据](../../output/low-value-audit-20260912/scope-verification.json)、[验证汇总](../../output/low-value-audit-20260912/verification-summary.json) |

Java 测试按本轮新生成的 Surefire XML 唯一套件计数，不叠加旧报告。前端数字是测试文件数，未与 Java/Python 用例数混算。

项目没有配置 npm lint 或 Java Checkstyle/Spotless/PMD。本轮使用临时目录中的 ESLint 8.57.1（no-unused-vars、no-undef、no-unreachable、no-dupe-args、no-dupe-keys、no-duplicate-case、valid-typeof）和 Ruff 0.12.12（F）进行专注 lint，未添加项目依赖或改动锁文件。Java 证据为 JDK 17 + Maven Wrapper 3.9.16 的 clean compile/test 和 diff 检查；没有将其称为 Java 专用 lint。前端使用项目支持的 Node 22.23.1。

## 证据和回退

全部本轮日志、原始文件、基线哈希及独立补丁存放在 `output/low-value-audit-20260912/`（Git 忽略的本地证据）。[commands.txt](../../output/low-value-audit-20260912/commands.txt) 给出实际检查命令。10 个文件的原件在其 `before/` 目录；已验证 `git apply --reverse --check output/low-value-audit-20260912/cleanup.patch` 通过，仅检查可回退性，未撤销本轮清理。日后回退应先重新检查补丁适用性，避免覆盖新增工作。

验证范围为本地静态检查和专注回归；没有进行全仓测试、生产构建、浏览器/原生设备验收或线上验证，也没有执行数据库变更。
