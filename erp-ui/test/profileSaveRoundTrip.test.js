const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("@babel/core")
const Vue = require("vue")

const uiRoot = path.resolve(__dirname, "..")
const filename = path.join(uiRoot, "src/views/system/user/profile/userInfo.vue")

function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

async function flush() {
  await Promise.resolve()
  await new Promise(resolve => setImmediate(resolve))
  await Vue.nextTick()
  await Vue.nextTick()
}

function loadUserInfo(updateUserProfile) {
  const script = fs.readFileSync(filename, "utf8").match(/<script>([\s\S]*?)<\/script>/)[1]
  const code = babel.transformSync(script, {
    filename,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
  }).code
  const componentModule = { exports: {} }
  vm.runInNewContext(code, {
    module: componentModule,
    exports: componentModule.exports,
    require(id) {
      if (id === "@/api/system/user") return { updateUserProfile }
      if (id === "@/utils/profileDisplayDate") return require("../src/utils/profileDisplayDate")
      throw new Error("unexpected dependency: " + id)
    },
    Object,
    Array,
    String,
    Number,
    Promise
  }, { filename })
  return componentModule.exports.default
}

function createUser(overrides) {
  const extra = Object.assign({}, overrides || {})
  const profileOverrides = extra.profile || {}
  delete extra.profile
  const profile = Object.assign({
    currentAddress: "旧地址",
    emergencyContact: "旧联系人",
    emergencyContactRelation: "配偶",
    emergencyContactPhone: "13900000000",
    maritalStatus: "已婚",
    ethnicity: "汉",
    politicalStatus: "群众",
    firstEducation: "本科",
    firstDegree: "学士",
    firstGraduationDate: "2010-06-01",
    firstGraduationSchool: "旧学校",
    firstMajor: "旧专业",
    highestEducation: "本科",
    highestDegree: "学士",
    highestGraduationDate: "2010-06-01",
    highestGraduationSchool: "旧学校",
    highestMajor: "旧专业",
    bankName: "旧银行",
    bankAccountMasked: "****1234",
    birthDate: "1990-01-01",
    idType: "身份证",
    idNumberMasked: "**************1234",
    registeredResidenceMasked: "**省**市",
    householdType: "城镇",
    nationality: "中国",
    socialType: "城职",
    socialSecurityType: "正常",
    socialSecurityLocation: "上海",
    housingFundLocation: "上海",
    employeeNo: "E-1",
    positionNo: "P-1",
    companyName: "公司",
    deptLevel1Name: "一部",
    contractType: "固定期限",
    legalEntity: "主体"
  }, profileOverrides)
  return Object.assign({
    userName: "zhangsan",
    nickName: "旧昵称",
    phonenumber: "13800000000",
    email: "user@example.com",
    sex: "2",
    currentAddress: "旧地址",
    profile
  }, extra)
}

function mockApi() {
  const sent = []
  const options = []
  let impl = (payload, opts) => {
    sent.push(Object.assign({}, payload))
    options.push(opts)
    return Promise.resolve({})
  }
  return {
    sent,
    options,
    updateUserProfile(payload, opts) {
      return impl(payload, opts)
    },
    use(factory) {
      impl = (payload, opts) => {
        sent.push(Object.assign({}, payload))
        options.push(opts)
        return factory(payload, opts)
      }
    }
  }
}

function mount(user, api) {
  const messages = []
  const definition = loadUserInfo((payload, opts) => api.updateUserProfile(payload, opts))
  const info = new Vue(Object.assign({}, definition, { propsData: { user } }))
  info.$modal = {
    msgSuccess(msg) { messages.push({ type: "success", msg }) },
    msgError(msg) { messages.push({ type: "error", msg }) }
  }
  info.$refs.form = { validate(callback) { callback(true) } }
  return { info, messages, user }
}

function assertNoPlainBank(user, profile) {
  assert.ok(!Object.prototype.hasOwnProperty.call(user, "bankAccount") || !user.bankAccount)
  assert.ok(!user.profile || !user.profile.bankAccount)
  assert.ok(!profile.bankAccount)
}

function assertReadonlyKept(profile) {
  assert.strictEqual(profile.employeeNo, "E-1")
  assert.strictEqual(profile.idNumberMasked, "**************1234")
  assert.strictEqual(profile.contractType, "固定期限")
  assert.strictEqual(profile.bankAccountMasked, "****1234")
}

async function scenarioNickNameAndContact() {
  const api = mockApi()
  const { info, user } = mount(createUser(), api)
  info.form.nickName = "新昵称"
  info.form.emergencyContact = "新联系人"
  info.submit()
  await flush()
  assert.strictEqual(info.form.nickName, "新昵称")
  assert.strictEqual(info.form.emergencyContact, "新联系人")
  assert.strictEqual(user.nickName, "新昵称")
  assert.strictEqual(user.profile.emergencyContact, "新联系人")
  assert.strictEqual(info.profile.emergencyContact, "新联系人")
  info.submit()
  await flush()
  assert.strictEqual(api.sent.length, 2)
  assert.strictEqual(api.sent[1].nickName, "新昵称")
  assert.strictEqual(api.sent[1].emergencyContact, "新联系人")
}

async function scenarioAddressAndContactCounterexamples() {
  const addressApi = mockApi()
  const addressMount = mount(createUser(), addressApi)
  addressMount.info.form.currentAddress = "新地址"
  addressMount.info.submit()
  await flush()
  assert.strictEqual(addressApi.sent[0].currentAddress, "新地址")
  assert.strictEqual(addressMount.info.form.currentAddress, "新地址")
  assert.strictEqual(addressMount.user.currentAddress, "新地址")
  assert.strictEqual(addressMount.user.profile.currentAddress, "新地址")
  assert.strictEqual(addressMount.info.profile.currentAddress, "新地址")

  const nullEmailApi = mockApi()
  const nullEmailMount = mount(createUser({ email: null }), nullEmailApi)
  assert.strictEqual(nullEmailMount.info.form.email, "")
  nullEmailMount.info.form.emergencyContact = "空邮联系人"
  nullEmailMount.info.submit()
  await flush()
  assert.strictEqual(nullEmailMount.info.form.emergencyContact, "空邮联系人")
  assert.strictEqual(nullEmailMount.info.form.email, "")
  assert.strictEqual(nullEmailApi.sent[0].emergencyContact, "空邮联系人")
  assert.strictEqual(nullEmailApi.sent[0].email, "")

  const normalizedApi = mockApi()
  const normalized = mount(createUser({ email: "" }), normalizedApi)
  assert.strictEqual(normalized.info.form.email, "")
  normalized.info.form.emergencyContact = "仅联系人"
  normalized.info.submit()
  await flush()
  assert.strictEqual(normalized.info.form.emergencyContact, "仅联系人")
  assert.strictEqual(normalized.info.form.nickName, "旧昵称")
  assert.strictEqual(normalizedApi.sent[0].emergencyContact, "仅联系人")
}

async function scenarioExternalAuthoritativeRefill() {
  const api = mockApi()
  const { info } = mount(createUser(), api)
  assert.strictEqual(info.form.nickName, "旧昵称")
  assert.strictEqual(info.form.emergencyContact, "旧联系人")
  info.user = createUser({
    nickName: "权威昵称",
    email: null,
    profile: {
      emergencyContact: "权威联系人",
      currentAddress: "权威地址",
      employeeNo: "E-9",
      idNumberMasked: "**************9999",
      contractType: "无固定期限"
    }
  })
  await flush()
  assert.strictEqual(info.form.nickName, "权威昵称")
  assert.strictEqual(info.form.emergencyContact, "权威联系人")
  assert.strictEqual(info.form.currentAddress, "权威地址")
  assert.strictEqual(info.form.email, "")
  assert.strictEqual(info.profile.employeeNo, "E-9")
  assert.strictEqual(info.profile.contractType, "无固定期限")
}

async function scenarioSaveInFlightAndFailure() {
  const leaked = []
  const onUnhandled = reason => { leaked.push(reason) }
  process.on("unhandledRejection", onUnhandled)
  try {
    const api = mockApi()
    const first = deferred()
    api.use(() => first.promise)
    const { info, messages, user } = mount(createUser(), api)
    info.form.nickName = "保存中昵称"
    info.form.emergencyContact = "保存中联系人"
    info.submit()
    await flush()
    info.form.nickName = "保存后新昵称"
    info.form.emergencyContact = "保存后新联系人"
    first.resolve({})
    await flush()
    assert.strictEqual(info.form.nickName, "保存后新昵称")
    assert.strictEqual(info.form.emergencyContact, "保存后新联系人")
    assert.strictEqual(user.nickName, "保存中昵称")
    assert.strictEqual(user.profile.emergencyContact, "保存中联系人")
    assert.strictEqual(info.profile.emergencyContact, "保存中联系人")
    assert.strictEqual(messages.filter(item => item.type === "success").length, 1)

    const failApi = mockApi()
    const fail = deferred()
    failApi.use(() => fail.promise)
    const failed = mount(createUser(), failApi)
    failed.info.form.emergencyContact = "失败仍保留"
    failed.info.form.currentAddress = "失败地址"
    failed.info.submit()
    fail.reject(new Error("save failed"))
    await flush()
    assert.strictEqual(failed.info.form.emergencyContact, "失败仍保留")
    assert.strictEqual(failed.info.form.currentAddress, "失败地址")
    assert.strictEqual(failed.info.saving, false)
    assert.strictEqual(failed.messages.filter(item => item.type === "success").length, 0)
    assert.ok(failed.messages.some(item => item.type === "error"))
    assert.ok(failApi.options[0] && failApi.options[0].silentError === true)
    assert.strictEqual(failed.user.profile.emergencyContact, "旧联系人")
    failApi.use(() => Promise.resolve({}))
    failed.info.submit()
    await flush()
    assert.strictEqual(failApi.sent.length, 2)
    assert.strictEqual(failApi.sent[1].emergencyContact, "失败仍保留")
    assert.strictEqual(failed.user.profile.emergencyContact, "失败仍保留")
    assert.strictEqual(leaked.length, 0)
  } finally {
    process.removeListener("unhandledRejection", onUnhandled)
  }
}

async function scenarioBankAndReadonlyBoundaries() {
  const keepApi = mockApi()
  const keep = mount(createUser(), keepApi)
  keep.info.form.bankAccount = ""
  keep.info.form.bankName = "新银行"
  keep.info.submit()
  await flush()
  assert.ok(!Object.prototype.hasOwnProperty.call(keepApi.sent[0], "bankAccount"))
  assert.strictEqual(keep.info.form.bankAccount, "")
  assert.strictEqual(keep.user.profile.bankName, "新银行")
  assertNoPlainBank(keep.user, keep.info.profile)
  assertReadonlyKept(keep.info.profile)
  assert.strictEqual(keep.info.display(keep.info.profile.bankAccountMasked), "****1234")

  const clearApi = mockApi()
  const clear = mount(createUser(), clearApi)
  clear.info.form.bankAccount = "6222021234567890"
  clear.info.submit()
  await flush()
  assert.strictEqual(clearApi.sent[0].bankAccount, "6222021234567890")
  assert.strictEqual(clear.info.form.bankAccount, "")
  assertNoPlainBank(clear.user, clear.info.profile)
  assert.ok(!JSON.stringify(clear.user).includes("6222021234567890"))
  assert.ok(!JSON.stringify(clear.info.profile).includes("6222021234567890"))
  assertReadonlyKept(clear.info.profile)

  const inflightApi = mockApi()
  const pending = deferred()
  inflightApi.use(() => pending.promise)
  const inflight = mount(createUser(), inflightApi)
  inflight.info.form.bankAccount = "1111222233334444"
  inflight.info.submit()
  await flush()
  inflight.info.form.bankAccount = "5555666677778888"
  pending.resolve({})
  await flush()
  assert.strictEqual(inflight.info.form.bankAccount, "5555666677778888")
  assertNoPlainBank(inflight.user, inflight.info.profile)
  assert.ok(!JSON.stringify(inflight.user).includes("1111222233334444"))
  assert.ok(!JSON.stringify(inflight.info.profile).includes("1111222233334444"))
  assert.ok(!JSON.stringify(inflight.user).includes("5555666677778888"))
  inflightApi.use(() => Promise.resolve({}))
  inflight.info.submit()
  await flush()
  assert.strictEqual(inflightApi.sent[1].bankAccount, "5555666677778888")
  assert.strictEqual(inflight.info.form.bankAccount, "")
}

async function scenarioInPlaceAuthoritativeWatcher() {
  const api = mockApi()
  const { info, user } = mount(createUser(), api)
  user.nickName = "外部昵称"
  Vue.set(user.profile, "emergencyContact", "外部联系人")
  await flush()
  assert.strictEqual(info.form.nickName, "外部昵称")
  assert.strictEqual(info.form.emergencyContact, "外部联系人")
  info.form.currentAddress = "本地地址"
  info.submit()
  await flush()
  user.nickName = "保存后权威昵称"
  Vue.set(user.profile, "emergencyContact", "保存后权威联系人")
  await flush()
  assert.strictEqual(info.form.nickName, "保存后权威昵称")
  assert.strictEqual(info.form.emergencyContact, "保存后权威联系人")
  assert.strictEqual(info.form.currentAddress, "本地地址")
  info.user = createUser({
    nickName: "新对象昵称",
    profile: { emergencyContact: "新对象联系人", currentAddress: "新对象地址" }
  })
  await flush()
  assert.strictEqual(info.form.nickName, "新对象昵称")
  assert.strictEqual(info.form.emergencyContact, "新对象联系人")
  assert.strictEqual(info.form.currentAddress, "新对象地址")
}

async function scenarioStaleSaveDoesNotOverrideNewAuthority() {
  const leaked = []
  const onUnhandled = reason => { leaked.push(reason) }
  process.on("unhandledRejection", onUnhandled)
  try {
    const api = mockApi()
    const pending = deferred()
    api.use(() => pending.promise)
    const { info, messages, user } = mount(createUser(), api)
    info.form.nickName = "提交昵称"
    info.form.emergencyContact = "提交联系人"
    info.submit()
    await flush()
    assert.strictEqual(info.saving, true)
    user.nickName = "权威昵称"
    Vue.set(user.profile, "emergencyContact", "权威联系人")
    await flush()
    assert.strictEqual(info.form.nickName, "权威昵称")
    assert.strictEqual(info.form.emergencyContact, "权威联系人")
    assert.strictEqual(info.saving, false)
    info.form.emergencyContact = "再改联系人"
    pending.resolve({})
    await flush()
    assert.strictEqual(info.form.emergencyContact, "再改联系人")
    assert.strictEqual(user.nickName, "权威昵称")
    assert.strictEqual(user.profile.emergencyContact, "权威联系人")
    assert.strictEqual(messages.filter(item => item.type === "success").length, 0)

    const failApi = mockApi()
    const failPending = deferred()
    failApi.use(() => failPending.promise)
    const replaced = mount(createUser(), failApi)
    replaced.info.form.nickName = "旧提交"
    replaced.info.submit()
    await flush()
    replaced.info.user = createUser({ nickName: "替换权威", profile: { emergencyContact: "替换联系人" } })
    await flush()
    replaced.info.form.nickName = "替换后输入"
    failPending.reject(new Error("fail"))
    await flush()
    assert.strictEqual(replaced.info.form.nickName, "替换后输入")
    assert.strictEqual(replaced.info.saving, false)
    assert.strictEqual(replaced.messages.filter(item => item.type === "success").length, 0)
    assert.strictEqual(replaced.messages.filter(item => item.type === "error").length, 0)
    assert.strictEqual(leaked.length, 0)

    const goneApi = mockApi()
    const gonePending = deferred()
    goneApi.use(() => gonePending.promise)
    const gone = mount(createUser(), goneApi)
    gone.info.$tab = { closePage() {} }
    gone.info.form.nickName = "离开前提交"
    gone.info.submit()
    await flush()
    gone.info.close()
    gonePending.resolve({})
    await flush()
    assert.strictEqual(gone.messages.filter(item => item.type === "success").length, 0)
    assert.strictEqual(gone.messages.filter(item => item.type === "error").length, 0)

    const deadApi = mockApi()
    const deadPending = deferred()
    deadApi.use(() => deadPending.promise)
    const dead = mount(createUser(), deadApi)
    dead.info.form.nickName = "销毁前提交"
    dead.info.submit()
    await flush()
    dead.info.$destroy()
    deadPending.resolve({})
    await flush()
    assert.strictEqual(dead.messages.filter(item => item.type === "success").length, 0)
    assert.strictEqual(dead.messages.filter(item => item.type === "error").length, 0)
  } finally {
    process.removeListener("unhandledRejection", onUnhandled)
  }
}

async function scenarioMissingProfileKeysBecomeReactive() {
  const api = mockApi()
  const user = {
    nickName: "旧昵称",
    phonenumber: "13800000000",
    email: "user@example.com",
    sex: "2",
    profile: {
      employeeNo: "E-1",
      idNumberMasked: "**************1234",
      contractType: "固定期限",
      bankAccountMasked: "****1234",
      legalEntity: "主体"
    }
  }
  const parent = new Vue({ data() { return { user } } })
  const definition = loadUserInfo((payload, opts) => api.updateUserProfile(payload, opts))
  const info = new Vue(Object.assign({}, definition, { propsData: { user: parent.user } }))
  info.$modal = { msgSuccess() {}, msgError() {} }
  info.$refs.form = { validate(callback) { callback(true) } }
  assert.strictEqual(info.form.currentAddress, "")
  assert.strictEqual(info.form.emergencyContact, "")
  assert.ok(!Object.prototype.hasOwnProperty.call(parent.user.profile, "currentAddress"))
  assert.ok(!Object.prototype.hasOwnProperty.call(parent.user.profile, "emergencyContact"))
  info.form.nickName = "补齐昵称"
  info.form.currentAddress = "补齐地址"
  info.form.emergencyContact = "补齐联系人"
  info.submit()
  await flush()
  assert.strictEqual(parent.user.nickName, "补齐昵称")
  assert.strictEqual(parent.user.profile.currentAddress, "补齐地址")
  assert.strictEqual(parent.user.profile.emergencyContact, "补齐联系人")
  assert.strictEqual(info.profile.currentAddress, "补齐地址")
  assert.strictEqual(info.profile.employeeNo, "E-1")
  assert.strictEqual(info.profile.contractType, "固定期限")
  const addressDesc = Object.getOwnPropertyDescriptor(parent.user.profile, "currentAddress")
  const contactDesc = Object.getOwnPropertyDescriptor(parent.user.profile, "emergencyContact")
  assert.ok(addressDesc && typeof addressDesc.get === "function")
  assert.ok(contactDesc && typeof contactDesc.get === "function")
  parent.$destroy()
  info.$destroy()
}

async function scenarioSingleFlightAndUnsavedHint() {
  const api = mockApi()
  const { info } = mount(createUser(), api)
  let validate
  info.$refs.form = { validate(callback) { validate = callback } }
  info.form.nickName = "校验中"
  info.submit()
  info.submit()
  assert.strictEqual(typeof validate, "function")
  validate(true)
  await flush()
  assert.strictEqual(api.sent.length, 1)
  assert.strictEqual(api.sent[0].nickName, "校验中")

  const saveApi = mockApi()
  const pending = deferred()
  saveApi.use(() => pending.promise)
  const saving = mount(createUser(), saveApi)
  saving.info.form.emergencyContact = "第一次"
  saving.info.submit()
  await flush()
  saving.info.form.emergencyContact = "保存后继续改"
  saving.info.submit()
  assert.strictEqual(saveApi.sent.length, 1)
  pending.resolve({})
  await flush()
  assert.strictEqual(saving.info.form.emergencyContact, "保存后继续改")
  assert.strictEqual(saving.info.hasUnsavedChanges, true)
  assert.ok(fs.readFileSync(filename, "utf8").includes("还有未保存的修改"))
  saveApi.use(() => Promise.resolve({}))
  saving.info.submit()
  await flush()
  assert.strictEqual(saveApi.sent.length, 2)
  assert.strictEqual(saveApi.sent[1].emergencyContact, "保存后继续改")
  assert.strictEqual(saving.info.hasUnsavedChanges, false)
}

async function scenarioAuthoritativeProfileDeletionClearsReadonlyDisplay() {
  const api = mockApi()
  const { info, user } = mount(createUser(), api)
  assert.strictEqual(info.profile.employeeNo, "E-1")
  assert.strictEqual(info.profile.contractType, "固定期限")
  info.form.nickName = "本地未保存昵称"
  Vue.delete(user.profile, "employeeNo")
  await flush()
  assert.ok(!Object.prototype.hasOwnProperty.call(info.profile, "employeeNo"))
  assert.strictEqual(info.display(info.profile.employeeNo), "暂无")
  assert.strictEqual(info.form.nickName, "本地未保存昵称")
  Vue.set(user.profile, "contractType", "")
  await flush()
  assert.strictEqual(info.profile.contractType, "")
  assert.strictEqual(info.display(info.profile.contractType), "暂无")
  assert.strictEqual(info.form.nickName, "本地未保存昵称")
  assert.strictEqual(info.profile.bankAccountMasked, "****1234")
}

function loadUserApi() {
  const calls = []
  const file = path.join(uiRoot, "src/api/system/user.js")
  const code = babel.transformSync(fs.readFileSync(file, "utf8"), {
    filename: file,
    babelrc: false,
    configFile: false,
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
  }).code
  const apiModule = { exports: {} }
  vm.runInNewContext(code, {
    module: apiModule,
    exports: apiModule.exports,
    require(name) {
      if (name === "@/utils/request") {
        return function request(config) {
          calls.push(config)
          return Promise.resolve({ data: {} })
        }
      }
      if (name === "@/utils/common") {
        return {
          parseStrEmpty(value) {
            if (!value || value === "undefined" || value === "null") return ""
            return value
          }
        }
      }
      throw new Error("unexpected dependency: " + name)
    }
  }, { filename: file })
  return { api: apiModule.exports, calls }
}

async function scenarioUpdateUserProfileSilentErrorContract() {
  const { api, calls } = loadUserApi()
  const payload = { nickName: "甲", emergencyContact: "联系人" }
  const hijack = {
    silentError: true,
    url: "/hacked",
    method: "delete",
    data: { x: 1 },
    params: { reasonCode: "HACK" },
    headers: { Authorization: "no" }
  }
  api.updateUserProfile(payload)
  api.updateUserProfile(payload, hijack)
  api.updateUserProfile(payload, { silentError: false })
  assert.strictEqual(calls.length, 3)
  calls.forEach((actual, index) => {
    assert.strictEqual(actual.url, "/system/user/profile", "call " + index + " url must stay on profile")
    assert.strictEqual(actual.method, "put", "call " + index + " method must stay put")
    assert.strictEqual(actual.headers, undefined)
    assert.strictEqual(actual.params, undefined)
    assert.strictEqual(actual.data, payload)
  })
  assert.strictEqual(calls[0].silentError, false)
  assert.strictEqual(calls[1].silentError, true)
  assert.strictEqual(calls[2].silentError, false)

  const pageApi = mockApi()
  const fail = deferred()
  pageApi.use(() => fail.promise)
  const { info, messages } = mount(createUser(), pageApi)
  info.form.emergencyContact = "页面失败保留"
  info.submit()
  await flush()
  assert.ok(pageApi.options[0] && pageApi.options[0].silentError === true)
  fail.reject(new Error("当前保存失败"))
  await flush()
  assert.strictEqual(info.form.emergencyContact, "页面失败保留")
  assert.ok(messages.some(item => item.type === "error" && item.msg === "当前保存失败"))
  assert.strictEqual(messages.filter(item => item.type === "success").length, 0)
}

async function run() {
  let scenarios = 0
  const done = async fn => {
    await fn()
    scenarios += 1
  }
  await done(scenarioNickNameAndContact)
  await done(scenarioAddressAndContactCounterexamples)
  await done(scenarioExternalAuthoritativeRefill)
  await done(scenarioSaveInFlightAndFailure)
  await done(scenarioBankAndReadonlyBoundaries)
  await done(scenarioInPlaceAuthoritativeWatcher)
  await done(scenarioStaleSaveDoesNotOverrideNewAuthority)
  await done(scenarioMissingProfileKeysBecomeReactive)
  await done(scenarioSingleFlightAndUnsavedHint)
  await done(scenarioAuthoritativeProfileDeletionClearsReadonlyDisplay)
  await done(scenarioUpdateUserProfileSilentErrorContract)
  console.log("profileSaveRoundTrip: " + scenarios + " scenarios")
}

run().catch(error => {
  console.error(error)
  process.exitCode = 1
})
