# 手机网页端考勤闭环补完计划

## 目标与范围

- 以干净候选提交 `9d8f011e0d0cc718de4a3dc63fdf7f34402d056c` 为基线，完成手机网页端 Attendance V2 的发布前闭环。
- 修复排班地点/围栏、连续分段打卡、弱网未知结果、前后端总开关与入口一致性。
- 保留旧主工作区 `/Users/liuxingyu/Desktop/备份/ERP-NEW` 原状；不部署、不修改生产或本地业务数据。
- 真机相机和定位是独立发布门禁；源码、模拟器或桌面浏览器结果不能替代。

## 主要改动范围

- 后端：`erp-modules/erp-oa/src/main/java/com/erp/oa/attendance/**`、`AttendanceV2Mapper.xml` 及对应测试。
- 前端：`erp-ui/src/views/mobile/attendance/**`、`erp-ui/src/api/oa/attendanceV2.js`、移动路由/导航、用户 feature state 及对应测试。
- 迁移：三份 Attendance V2 SQL 副本与 `docker/mysql/bootstrap-files.list`，保持内容一致和默认关闭。

## 实施顺序

1. 恢复排班对 `siteId` 的显式绑定；发布时冻结地点、经纬度、坐标系、半径和最大精度快照。
2. 打卡时按排班快照转换坐标并执行精度/围栏判断；保存真实距离和 `INSIDE`，超范围、低精度或缺少围栏快照均失败关闭。SQL 保持 nullable 只用于 expand/旧数据兼容，不允许新发布排班或 V2 打卡静默进入 `NOT_APPLICABLE`。
3. 将 parity 候选的持久化 punch attempt 状态机移入完整页面：一次 attempt 固定 request ID，未知结果冻结写入并通过今日上下文精确核对。
4. 成功或已确认恢复后释放当前 attempt，并在重新加载今日上下文时允许下一分段卡位；保持最新水印证据可恢复。
5. 手机 H5 使用 HTTPS `getUserMedia` 建立实时后摄会话并从视频帧生成 JPEG；不再把任意文件选择时间伪装成拍摄时间，不支持实时相机时失败关闭。Capacitor 继续使用原生相机。
6. 增加 `attendanceV2` 前端 feature state；总开关关闭时隐藏入口并保持后端失败关闭，旧打卡动作/API 不再出现在 V2 路由链中。
7. 补齐迁移副本、权限和 bootstrap 清单，执行迁移一致性检查，但不连接或写入现有业务数据库。

## 验证门禁

- 后端：围栏内、超围栏、低精度、无地点、challenge 重放、分段下一卡位、未知结果核对测试。
- 前端：attempt store、超时冻结、刷新恢复、成功后下一卡位、H5 实时相机生命周期、开关隐藏、证据恢复测试。
- 运行现有 Attendance V2 相关 Maven 测试、前端聚焦测试、生产构建、`git diff --check` 和精确路径审计。
- 本地 HTTPS H5 验证加载、开关关闭态、无排班态和接口失败态；真实 Safari/微信定位、相机、multipart、水印、弱网和分段打卡留作真机门禁。

## 风险与回滚

- 围栏属于业务规则恢复；若产品确认“仅地址审计、永久取消围栏”，应以单独产品决策覆盖本计划，而不是继续保留失效字段和测试。
- 所有改动只在 `codex/attendance-h5-completion-20260831`；回滚可按独立提交撤销，不触碰原脏工作区。
- 不进行合并、推送、部署、迁移执行或测试数据写入，除非用户另行授权。
