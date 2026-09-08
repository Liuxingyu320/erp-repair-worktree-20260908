# 系统管理整改实施基线

> 采集时间：2026-07-14（Asia/Shanghai）  
> 分支：`7月13号`  
> HEAD：`b694659dd2c19dc87377d93eaed6bbbf9a5ac0b7`  
> 用途：M0 只读开发基线；不是发布验收结果

## 工作区事实

- 开始实施前 `git status --short`：814 项（`M=387`、`D=6`、`??=421`）。这些内容属于既有项目基线，不纳入本整改的自动暂存或提交。
- 仓库位于桌面备份目录，当前只用于开发和审查；发布构建必须在非同步目录的原生干净检出中执行。
- 开始实施时发现 1131 个编号冲突副本或 AppleDouble 候选；当前目录预期不能通过干净构建门禁。
- 不使用虚拟机、Docker、Testcontainers；原生工具版本为 JDK 17.0.18、Maven 3.9.14、MySQL 客户端 8.0.45。
- Homebrew Node 25.8.0 缺少 `libsimdjson.30.dylib`，本轮改用 Codex 本机原生 Node 24.14.0；不修改系统 Node 环境。

## 开始实施时的制品指纹

| 制品 | SHA-256 |
| --- | --- |
| `docker/erp/modules/system/jar/erp-modules-system.jar` | `faaa523a85e8c765a51945720bc3972b89c4391c7d75fa887c16e89f147f260c` |
| `erp-modules/erp-system/target/erp-modules-system.jar` | `faaa523a85e8c765a51945720bc3972b89c4391c7d75fa887c16e89f147f260c` |
| `docker/erp/auth/jar/erp-auth.jar` | `fcc4c23772f968e18c04e2dbe73209026613951a57362f1142ceef4641769532` |
| `erp-auth/target/erp-auth.jar` | `fcc4c23772f968e18c04e2dbe73209026613951a57362f1142ceef4641769532` |

这些指纹仅用于证明开发起点。最终发布必须从最终提交重新构建并由 Release A manifest 记录，不能沿用本表旧制品。

## A0 首轮验证记录

- 新增测试后先观察到预期红灯：缺少安全导出 VO、详情接口和日志策略实现。
- 完成第一轮 A0 实现后，system 专项 4 项、OA 专项 1 项通过。
- `erp-ui/test/systemManagementUx.test.js` 使用本机原生 Node 24.14.0 单独执行通过。
- Maven 测试会改写仓库中被跟踪的 `target/` 产物；这些生成物不进入本次显式源码清单，也不能作为发布物。

## 隔离规则

1. 本整改只修改 `scripts/system-management-release-files-20260714.list` 明确登记的文件或其中的明确 hunk。
2. 禁止 `git add .`、禁止清理或覆盖用户已有未提交改动。
3. 每个里程碑结束都执行登记文件范围内的 `git diff --check`、专项测试和新增文件核对。
4. 最终构建前必须把已确认的完整源码基线固定到提交，再在非同步原生目录做干净检出；当前旧 HEAD 的独立 worktree 会丢失既有 1.5 万余行改动，因此现在不创建失真的 worktree。
