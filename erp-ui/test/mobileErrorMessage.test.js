const assert = require("assert")

const { mobileErrorMessage } = require("../src/views/mobile/mobileErrorMessage")

assert.strictEqual(mobileErrorMessage(null, "加载失败"), "加载失败")
assert.strictEqual(
  mobileErrorMessage({ response: { status: 401, data: { msg: "令牌不能为空" } } }, "加载失败"),
  "登录状态已失效，请重新登录"
)
assert.strictEqual(
  mobileErrorMessage({ response: { status: 403, data: { msg: "Forbidden" } } }, "加载失败"),
  "当前账号无权执行此操作"
)
assert.strictEqual(
  mobileErrorMessage({ response: { status: 500, data: { msg: "无权调拨该物料" } } }, "保存失败"),
  "所选商品不属于当前门店可要货范围，请重新选择"
)
assert.strictEqual(
  mobileErrorMessage({ response: { status: 500, data: { msg: "当前门店不允许向该仓库要货" } } }, "来源库存加载失败"),
  "当前门店不允许向该仓库要货"
)
assert.strictEqual(
  mobileErrorMessage({ response: { status: 403, data: { msg: "补货来源仓库物料范围无效" } } }, "来源库存加载失败"),
  "补货来源仓库物料范围无效"
)
assert.strictEqual(
  mobileErrorMessage({ response: { status: 500, data: { msg: "补货供货关系无效" } } }, "来源库存加载失败"),
  "补货供货关系无效"
)
assert.strictEqual(
  mobileErrorMessage({ response: { status: 500, data: { msg: "SQLSyntaxErrorException" } } }, "签约包加载失败"),
  "签约包加载失败"
)
assert.strictEqual(
  mobileErrorMessage(new Error("java.lang.IllegalStateException at com.erp.UserService.java:42"), "资料保存失败"),
  "资料保存失败"
)
assert.strictEqual(mobileErrorMessage(new Error("请选择实际入职日期"), "保存失败"), "请选择实际入职日期")

console.log("mobile error-message tests passed")
