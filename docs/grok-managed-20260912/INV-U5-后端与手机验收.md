# INV-U5 / D06–D07 盘点重启保留与旧页保护

供根代理最终审核。白名单19路径（10旧9新），before原字节、final-files、final-manifest及精确补丁已冻结。补丁SHA256：`f4566abfc84c76549827d34570c3a7ead4cf83958e3f21ebbd19c9ef54e41b32`；git apply --reverse --check通过。PC stockCheck页面及其筛选测试由root独立交付，不混入此补丁。

主单新增私有 `restart_reference_snapshot` LONGTEXT；首轮详情每行 snapshotVersion="0"，每次重启生成UUID字符串。初次兼容旧客户端 null/0，重启后整批每行必须携带原表单读取的版本。普通保存、带明细提交都在主单FOR UPDATE后读取最新明细FOR UPDATE，先校验所有token、归属ID/重复ID和负数，后首次业务写入。无新增/删除明细接口；未知或重复明细不能借此注入。GET在只读RR事务中读同一轮主单与明细。

重启在原状态/盘点人/组织校验和锁下，对当前库存不同及本轮invalidated证据已证明变化的行刷新账面并清空实盘/复盘；即使库存回到原数也重核。未变行只刷新成本，保留实盘/复盘/复盘人/复盘时间。旧lastInvalidDetailSnapshot持久化证据不改；每次私有JSON追加该轮原始明细数量、成本、身份与复盘审计。JSON字段不参与Jackson/FastJSON普通序列化或外部反序列化。

详情派生snapshotVersion、previousActualQty、previousRecountQty、previousBookQty、needsSnapshotReview。后四项只读且保存不信客户端值。needsSnapshotReview=最新重启变动且实盘尚缺失。盲盘draft/rejected/returned响应隐去新previousBookQty和旧invalidated账面JSON，保留既有隐藏账面/差额规则；不抹持久化历史。

H5仅保留打开表单时的snapshotVersion，两个输出结构和实际API录入链均回传。运行器保存前的新GET绝不补新token给旧输入；输入被拒后停止提交审批。显示上轮实盘/复盘和需重核提示，不自动复制参考数量。保留U2上传/附件和U1质检/原scope隔离。

## 本地证据

- 定向Maven61方法通过：新策略6+新真实服务6+SQL镜像1，旧服务27+Mapper4+Controller3+审批10+调整4。
- 新MySQL5.7.44和8.0.36各5实际事务通过：幂等迁移/选择保留/回原行清空、坏尾行首写前整批拒绝、后置失败全回滚、两轮历史及旧token、旧页面RR等锁后读最新token拒写。共66独立后端方法、71环境执行，18新增独立方法。失败/错误/跳过均0。10XML已复制；container-cleanup.json确证两个专属容器不存在。
- 新5实际Vue render/方法/真实API包装链通过；原U1 14、U2 14、task03 28场景通过；4旧脚本组通过。新旧前端日志均保存。
- 独立javac和先行探针是预检，不重复计通过数。首次IT因为未设置Colima socket而在创建容器前停止，保留maven-mysql57.log；通过docker context核对后，测试进程设置DOCKER_HOST和TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE再跑通过。没有改宿主Docker配置。

## 迁移和边界

sql与docker/mysql/db同名镜像先迁移后发版，脚本在两个临时版本各运行两次。回退脚本保留可空列和历史；已产生重启数据不得删列或恢复不验证token的旧写入口，先停止盘点写入。未连接现用DB或宿主3306，没有部署、线上/浏览器/真机验收、提交、全局台账或Grok操作。
