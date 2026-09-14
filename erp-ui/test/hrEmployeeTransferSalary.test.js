const assert = require("node:assert/strict")
const { test } = require("node:test")
const fs = require("node:fs")
const path = require("node:path")
const Vue = require("vue")
const compiler = require("vue-template-compiler")
Vue.config.silent = true

const descriptor = compiler.parseComponent(fs.readFileSync(path.resolve(__dirname, "../src/views/hr/components/HrEmployeeTransferDialog.vue"), "utf8"))
assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
const script = descriptor.script.content.replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "").replace("export default", "return")
const salaryKeys = ["baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal"]

function deferred() {
  let resolve
  const promise = new Promise(yes => { resolve = yes })
  return { promise, resolve }
}

async function harness(permissions = [], submit = async () => ({ data: { actionId: 800 } })) {
  const calls = []
  const definition = new Function("getHrTransferBusinessDate", "confirmHrEmployeeTransfer", script)(
    async () => ({ data: { businessDate: "2026-09-12" } }),
    async (userId, request) => { calls.push({ userId, request }); return submit(userId, request) }
  )
  const store = Vue.observable({ getters: { permissions } })
  const model = new Vue({ ...definition, beforeCreate() { this.$store = store }, propsData: {
    visible: true,
    employee: { userId: 9, employeeName: "合成员工", deptId: 20, departmentName: "合成一店", postIds: [40], fields: {
      jobGrade: "P3", workLocation: "合成地点", workCityLevel: "一线", legalEntityId: 30,
      legalEntityCode: "TEST", legalEntity: "合成主体", salaryVersion: "VERSION-OLD",
      ...Object.fromEntries(salaryKeys.map(key => [key, "******"]))
    } },
    options: { departments: [{ deptId: 20, deptName: "合成一店" }, { deptId: 21, deptName: "合成二店" }],
      posts: [{ postId: 40, postName: "合成岗位", postCode: "TEST_POST" }], supervisors: [] }
  } })
  await model.openDialog()
  model.model.targetDeptId = 21
  return { model, calls }
}

function setSalary(model) {
  Object.assign(model.model, { adjustSalary: true, baseSalary: 5000, postSalary: 1000, fieldAllowance: 200, performanceSalary: 800 })
  model.syncSalaryTotal()
}

test("ordinary transfer submits without reading or filling masked salary", async () => {
  const { model, calls } = await harness(["hr:employee:transfer"])
  assert.equal(model.canAdjustSalary, false)
  assert.equal(model.canSubmit, true)
  for (const key of salaryKeys) assert.equal(model.model[key], null)
  await model.submit()
  assert.equal(calls.length, 1)
  assert.equal(calls[0].request.adjustSalary, false)
  for (const key of [...salaryKeys, "salaryVersion"]) assert.equal(Object.hasOwn(calls[0].request, key), false)
  assert.equal(descriptor.template.content.includes("工资不变"), false)
  model.$destroy()
})

for (const permission of ["hr:employee:sensitive:view", "system:salary:edit", "oa:signTask:send"]) {
  test(`${permission} does not authorize salary adjustment`, async () => {
    const { model, calls } = await harness(["hr:employee:transfer", permission])
    setSalary(model)
    assert.equal(model.canAdjustSalary, false)
    assert.equal(model.canSubmit, false)
    await model.submitPayload(null)
    assert.equal(calls.length, 0)
    model.$destroy()
  })
}

test("dedicated salary editor explicitly sends valid five amounts without a version input", async () => {
  const { model, calls } = await harness(["hr:employee:transfer", "hr:employee:salary:edit"])
  assert.equal(model.model.adjustSalary, false)
  setSalary(model)
  assert.equal(model.salaryValid, true)
  await model.submit()
  assert.equal(calls.length, 1)
  assert.equal(calls[0].request.adjustSalary, true)
  assert.equal(calls[0].request.salaryTotal, 7000)
  assert.equal(Object.hasOwn(calls[0].request, "salaryVersion"), false)
  assert.equal(descriptor.template.content.includes('prop="salaryVersion"'), false)
  model.$destroy()
})

test("invalid or missing salary cannot be coerced from a mask or non-finite value", async () => {
  const { model, calls } = await harness(["hr:employee:salary:edit"])
  for (const value of [null, "", "******", NaN, Infinity, -1]) {
    setSalary(model)
    model.model.baseSalary = value
    model.syncSalaryTotal()
    assert.equal(model.salaryValid, false)
    await model.submit()
  }
  assert.equal(calls.length, 0)
  model.$destroy()
})

test("permission removal collapses salary editing before the next submission", async () => {
  const { model } = await harness(["hr:employee:salary:edit"])
  setSalary(model)
  model.$store.getters.permissions = ["hr:employee:sensitive:view"]
  await Vue.nextTick()
  assert.equal(model.canAdjustSalary, false)
  assert.equal(model.model.adjustSalary, false)
  assert.equal(Object.hasOwn(model.buildPayload(null), "baseSalary"), false)
  model.$destroy()
})

test("duplicate click and late response cannot confirm a reopened employee dialog", async () => {
  const pending = deferred()
  const { model, calls } = await harness(["hr:employee:transfer"], () => pending.promise)
  const emitted = []
  model.$on("confirmed", result => emitted.push(result))
  const running = model.submit()
  await model.submit()
  assert.equal(calls.length, 1)
  model.invalidate()
  await model.openDialog()
  model.errorMessage = "新窗口提示"
  pending.resolve({ data: { actionId: 800 } })
  await running
  assert.equal(emitted.length, 0)
  assert.equal(model.errorMessage, "新窗口提示")
  assert.equal(model.submitting, false)
  model.$destroy()
})

test("salary permission migration registers an action without role or user grants", () => {
  const sql = fs.readFileSync(path.resolve(__dirname, "../../sql/erp_hr_employee_salary_edit_permission_20260912.sql"), "utf8")
    .replace(/^\s*--.*$/gm, "")
  assert.match(sql, /INSERT INTO sys_menu/)
  assert.match(sql, /hr:employee:salary:edit/)
  assert.match(sql, /parent_count <> 1/)
  assert.match(sql, /IF permission_count = 0 THEN/)
  assert.doesNotMatch(sql, /(?:INSERT(?:\s+IGNORE)?\s+INTO|UPDATE|DELETE\s+FROM)\s+sys_(?:role|user)/i)
  assert.doesNotMatch(sql, /sync_sign_hr_permissions/i)
})
