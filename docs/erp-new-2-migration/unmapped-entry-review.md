# ERP-NEW_2 未映射入口复核

> 日期：2026-08-02
> 状态：阶段 1 人工复核初稿；全部保留，未授权下线
> 明细来源：`functional-migration-matrix.csv` 中 `source_kind=unmapped-page-candidate` 的 57 条记录

## 1. 结论

“未映射入口”只表示当前静态路由和当前工作树 SQL 中没有定位到直接入口，不表示页面无效。动态菜单依赖数据库运行态，而本阶段按隔离要求没有连接任何数据库，因此 57 条候选全部进入迁移矩阵并保持“待确认”。

其中 51 条仍有 API、权限或自动化测试证据，不能因菜单不可见而下线；其余 6 条只有源码证据，也必须等待产品负责人逐项确认。

## 2. 证据分级

| 证据组合 | 数量 | 初步判定 |
|---|---:|---|
| API + 权限 + 自动化测试 | 27 | 强活动证据，优先按现行业务能力核验 |
| API + 自动化测试 | 13 | 强活动证据，补查权限和入口 |
| API + 权限 | 2 | 有运行能力证据，补查测试和入口 |
| API | 7 | 有后端交互证据，补查权限、测试和入口 |
| 权限 + 自动化测试 | 1 | 有业务约束与回归证据，补查 API 和入口 |
| 自动化测试 | 1 | 有回归证据，补查 API、权限和入口 |
| 仅源码 | 6 | 不能自动判废，需逐项人工确认 |
| **合计** | **57** | **全部保留** |

## 3. 仅源码证据的 6 条候选

| 页面源码 | 当前判断 | 核验重点 |
|---|---|---|
| `erp-ui/src/views/index_v1.vue` | 可能是旧工作台版本 | 与现工作台比较能力差异，确认保留、合并或下线 |
| `erp-ui/src/views/monitor/job/detail.vue` | 可能是被父页面调用的详情组件 | 查父组件动态引用和任务详情交互 |
| `erp-ui/src/views/system/operlog/detail.vue` | 可能是被父页面调用的详情组件 | 查操作日志列表中的弹层/组件引用 |
| `erp-ui/src/views/tool/build/index.vue` | 可能是低频开发工具页面 | 确认生产角色是否可见及是否纳入新前端 |
| `erp-ui/src/views/tool/gen/basicInfoForm.vue` | 可能是代码生成器内部步骤组件 | 与生成器编辑流程一起核验，不单独下线 |
| `erp-ui/src/views/tool/gen/genInfoForm.vue` | 可能是代码生成器内部步骤组件 | 与生成器编辑流程一起核验，不单独下线 |

## 4. 当前缺少自动化测试证据的直接入口页面

以下 11 个 `route-page` 条目尚未关联到自动化测试；这只是源码级严格路径匹配结果，不等同于测试一定不存在：

```text
erp-ui/src/views/credential/change-password.vue
erp-ui/src/views/error/401.vue
erp-ui/src/views/error/404.vue
erp-ui/src/views/lock.vue
erp-ui/src/views/mobile/hr/onboarding/form.vue
erp-ui/src/views/mobile/hr/team/index.vue
erp-ui/src/views/mobile/inventory/oeReplenishment/index.vue
erp-ui/src/views/mobile/store/index.vue
erp-ui/src/views/mobile/warehouse/index.vue
erp-ui/src/views/redirect.vue
erp-ui/src/views/tool/gen/editTable.vue
```

在对应能力迁移前，应为业务入口补齐最小回归场景；401、404 等系统页可以采用路由级集成测试。

## 5. 检查点规则

1. 本文不授权删除、合并、下线或改写任何旧页面。
2. 页面是否下线必须同时说明角色、入口、API、权限、数据和替代流程。
3. 详情组件或流程子组件不能因为没有独立路由而被判定为无效。
4. 数据库菜单核验只能在 ERP-NEW_2 专属运行环境建立后进行，禁止连接原项目或共享数据库补证。
5. 用户逐项确认前，迁移矩阵中的 `target_disposition` 和 `confirmation_status` 继续保持“待确认”。
