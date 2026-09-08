# 合同自动化阶段2验收记录

**日期：** 2026-07-12

**分支：** `codex/contract-signing-automation`

**已验证代码：** `e7db446d`

**结论：** 开发环境自动化验收通过；真实HR账号全流程、服务中断恢复和Android/iOS真机推送保留为部署环境上线门禁。自动发送未启用，后续阶段生成的草稿仍必须由唯一HR一次确认。

## 已交付范围

- `oa_sign_task`、任务事件hash链、乐观锁状态机和幂等请求。
- 唯一HR配置、独立受管角色、任务迁移和用户/角色/配置生命周期保护。
- 单HR任务中心、七项业务指标、合同详情、补资料、重新校验、一次确认、发送和失败重试。
- HR业务接口裁剪原始hash；审计/技术用户仅保留只读验真证据，不能参与业务审批。
- 定向站内消息、OA outbox、FCM/APNs适配、设备注册和Header三类独立计数。
- 短时确认令牌绑定任务、用户、文档版本和服务端快照；成功请求可安全幂等重放。

## 自动化验证

### 后端

```bash
mvn -pl erp-api/erp-api-system -am -DskipTests install
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-modules/erp-oa -am test
```

结果：

- `erp-api-system` reactor 安装成功。
- System reactor：251项测试通过，0失败、0错误。
- OA reactor：320项测试通过，0失败、0错误。

覆盖包括唯一HR配置异常、权限同步、HR变更任务迁移、确认/发送requestId重放、签署requestId重放、通知业务键去重、outbox抢占/恢复、60秒起步退避、死信处理和技术证据隔离。

### 数据库兼容与并发

- 两份迁移SQL逐字一致，`git diff --check`通过。
- MySQL 5.7.44和8.0.46均完成迁移幂等验证。
- REPEATABLE READ双连接验证覆盖HR配置切换、角色/用户并发、旧快照回迁、任务改派、确认与迁移竞争；最终任务只迁移一次、审计只写一次、未发生死锁。

### 前端与移动壳同步

```bash
cd erp-ui
npm test
npm run build:prod
npm run app:verify
```

结果：

- 前端Node测试：106个测试文件通过，0失败。
- 生产构建成功。
- Capacitor同步校验通过。
- 本机没有 `google-services.json` 和 `GoogleService-Info.plist`，因此未声称完成真实推送；验证脚本已明确报告凭据缺失。

## 配置和安全状态

- `sign.hr.user-id` 必须在目标环境指向一个真实、启用且未删除的用户；缺失、非法或失效时返回 `CONFIG_HR_MISSING`，不会回退通知任意管理员。
- 当前阶段没有自动发送执行路径，任务只能由唯一HR确认后发送。
- FCM/APNs关闭或缺少凭据时，业务任务、待办和站内消息不依赖移动推送而回滚。
- 用户工作区原有的未跟踪Android/iOS生成目录未被暂存或提交。

## 部署环境上线门禁

以下项目需要真实环境、账号或设备，开发机无法替代：

- [ ] 配置真实 `sign.hr.user-id`，按“缺资料 → 补资料 → 校验 → 生成草稿 → HR确认 → 发送 → 员工阅读 → 签署”走完一条业务记录，并核对每次状态转换只产生一条事件。
- [ ] 在目标数据库重放相同业务键、确认requestId、发送requestId和通知键，确认行数不增加。
- [ ] 停止System服务，确认OA outbox重试且主任务不回滚；恢复后站内消息恰好一条。
- [ ] 使用测试推送凭据验证失败退避，再用Android和iOS真机各发送一条不含敏感信息的消息。
- [ ] 在推送关闭、OA通知调度器关闭两种条件下，确认人工任务处理和站内待办仍正常。

在这些门禁完成前，不开启阶段4自动发送开关。
