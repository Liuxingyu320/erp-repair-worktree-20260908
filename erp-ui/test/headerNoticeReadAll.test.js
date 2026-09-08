const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.resolve(root, file), "utf8")
const apiSource = read("src/api/system/notice.js")
const componentSource = read("src/layout/components/HeaderNotice/index.vue")

;["业务待办", "todoTotal", "checkPermi"].forEach(forbidden => {
  assert.ok(!componentSource.includes(forbidden), `HeaderNotice should not own ${forbidden}`)
})
assert.ok(
  !/["']todo\/refresh["']/.test(componentSource),
  "HeaderNotice should not dispatch the legacy todo/refresh action"
)
assert.ok(
  /<button\s+type="button"\s+class="notice-mark-all"\s+:disabled="[^"]*noticeMarkingAll[^"]*noticeLoading[^"]*noticeUnreadCount <= 0"\s+@click="markAllRead"\s*>\s*全部已读\s*<\/button>/.test(componentSource),
  "mark-all should be a semantic button with loading and unread guards"
)

const apiFunction = apiSource.match(/export function markNoticeReadAll\s*\(([^)]*)\)\s*\{([\s\S]*?)\n\}/)
assert.ok(apiFunction, "notice API should expose markNoticeReadAll")
assert.strictEqual(apiFunction[1].trim(), "", "markNoticeReadAll should not accept top-notice ids")
assert.ok(
  apiFunction[2].includes("url: '/system/notice/markReadAll'") &&
    apiFunction[2].includes("method: 'post'"),
  "markNoticeReadAll should POST to the all-notices endpoint"
)
assert.ok(
  !/\bparams\s*:/.test(apiFunction[2]) && !/\bdata\s*:/.test(apiFunction[2]),
  "markNoticeReadAll should send neither ids nor a request body"
)

const markAllMethod = componentSource.match(/async\s+markAllRead\s*\(\)\s*\{([\s\S]*?)\n\s{4}\}\n\s{2}\}\n\}\n<\/script>/)
assert.ok(markAllMethod, "HeaderNotice should await its mark-all workflow")
assert.ok(
  /await\s+markNoticeReadAll\s*\(\s*\)/.test(markAllMethod[1]),
  "HeaderNotice should await the parameterless mark-all request"
)
assert.ok(
  /await\s+this\.loadNoticeTop\s*\(\s*\)/.test(markAllMethod[1]),
  "HeaderNotice should reload the authoritative list and unread count after mark-all succeeds"
)
assert.ok(
  componentSource.includes("return listNoticeTop()"),
  "loadNoticeTop should return its promise so mark-all can await the refresh"
)
assert.ok(
  !markAllMethod[1].includes("this.noticeUnreadCount = 0") &&
    !markAllMethod[1].includes("this.noticeList = this.noticeList.map"),
  "HeaderNotice must not pretend success by clearing local state"
)
assert.ok(
  componentSource.includes("noticeMarkingAll") &&
    markAllMethod[1].includes("this.noticeMarkingAll || this.noticeLoading"),
  "HeaderNotice should prevent duplicate mark-all clicks while loading"
)
assert.ok(
  /try\s*\{[\s\S]*await\s+markNoticeReadAll[\s\S]*await\s+this\.loadNoticeTop[\s\S]*\}\s*catch/.test(markAllMethod[1]),
  "HeaderNotice should preserve current list/count when either request fails"
)

const refreshHeaderCounts = componentSource.match(/refreshHeaderCounts\s*\(\)\s*\{([\s\S]*?)\n\s{4}\},/)
assert.ok(refreshHeaderCounts, "HeaderNotice should retain its personal-message timer callback")
assert.ok(refreshHeaderCounts[1].includes("this.loadMessageUnreadCount()"))
assert.ok(!refreshHeaderCounts[1].includes("loadNoticeTop") && !refreshHeaderCounts[1].includes("$store.dispatch"),
  "the periodic timer should refresh only personal-message unread count")
assert.ok(/mounted\s*\(\)\s*\{[\s\S]*this\.loadNoticeTop\s*\(\)/.test(componentSource),
  "HeaderNotice should load announcements once when mounted")
assert.ok(
  /onNoticeEnter\s*\(\)\s*\{[\s\S]*this\.loadNoticeTop\s*\(\)/.test(componentSource),
  "opening the announcement popover should retain an authoritative refresh path"
)

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function flushAsync() {
  return new Promise(resolve => setImmediate(resolve))
}

function loadHeaderNotice(dependencies = {}) {
  const scriptMatch = componentSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "HeaderNotice should expose a script block")
  const sandbox = {
    componentDefinition: null,
    NoticeDetailView: {},
    listNoticeTop: dependencies.listNoticeTop || (() => Promise.resolve({ data: [], unreadCount: 0 })),
    markNoticeRead: dependencies.markNoticeRead || (() => Promise.resolve()),
    markNoticeReadAll: dependencies.markNoticeReadAll || (() => Promise.resolve()),
    getUserNotificationUnreadCount: () => Promise.resolve({ data: 0 }),
    setInterval,
    clearInterval,
    setTimeout,
    clearTimeout,
    window: undefined
  }
  vm.createContext(sandbox)
  const runnableScript = scriptMatch[1]
    .replace(/^import .*$/gm, "")
    .replace("export default", "componentDefinition =")
  vm.runInContext(runnableScript, sandbox)
  return sandbox.componentDefinition
}

function createHeaderNotice(dependencies = {}, overrides = {}) {
  const definition = loadHeaderNotice(dependencies)
  const instance = {
    ...definition.data(),
    $nextTick(callback) { callback() },
    $set(list, index, value) { list.splice(index, 1, value) },
    $refs: {
      noticePopover: null,
      noticeViewRef: { open() {} }
    },
    ...overrides
  }
  Object.entries(definition.methods).forEach(([name, method]) => {
    instance[name] = method.bind(instance)
  })
  instance.beforeDestroy = definition.beforeDestroy.bind(instance)
  return instance
}

const asyncBehaviorTests = [
  ["pending single-read blocks open refresh until success settles", async () => {
    const markReadRequest = deferred()
    let listCalls = 0
    const instance = createHeaderNotice({
      markNoticeRead: () => markReadRequest.promise,
      listNoticeTop: () => {
        listCalls += 1
        return Promise.resolve({ data: [{ noticeId: 1, isRead: true }], unreadCount: 0 })
      }
    })
    const item = { noticeId: 1, isRead: false }
    instance.noticeList = [item]
    instance.noticeUnreadCount = 1

    instance.previewNotice(item)
    instance.onNoticeEnter()
    assert.strictEqual(listCalls, 0, "opening must not issue a stale GET while mark-read is pending")

    markReadRequest.resolve()
    await flushAsync()
    assert.strictEqual(listCalls, 1, "successful mark-read settlement should trigger one authoritative GET")
  }],
  ["failed single-read settles with an authoritative rollback refresh", async () => {
    const markReadRequest = deferred()
    let listCalls = 0
    const instance = createHeaderNotice({
      markNoticeRead: () => markReadRequest.promise,
      listNoticeTop: () => {
        listCalls += 1
        return Promise.resolve({ data: [{ noticeId: 2, isRead: false }], unreadCount: 1 })
      }
    })
    const item = { noticeId: 2, isRead: false }
    instance.noticeList = [item]
    instance.noticeUnreadCount = 1

    instance.previewNotice(item)
    assert.strictEqual(instance.noticeList[0].isRead, true, "single read may update the UI optimistically")
    markReadRequest.reject(new Error("mark read failed"))
    await flushAsync()

    assert.strictEqual(listCalls, 1, "failed mark-read settlement should still trigger one authoritative GET")
    assert.strictEqual(instance.noticeList[0].isRead, false, "authoritative GET should roll back failed optimistic state")
    assert.strictEqual(instance.noticeUnreadCount, 1, "authoritative GET should restore the unread count")
  }],
  ["one open cycle performs only one authoritative refresh", async () => {
    let listCalls = 0
    const instance = createHeaderNotice({
      listNoticeTop: () => {
        listCalls += 1
        return Promise.resolve({ data: [], unreadCount: 0 })
      }
    })

    instance.onNoticeEnter()
    await flushAsync()
    instance.onNoticeEnter()
    await flushAsync()
    assert.strictEqual(listCalls, 1, "repeated mouseenter in one open cycle must not repeat the GET")

    instance.noticeVisible = false
    instance.onNoticeEnter()
    await flushAsync()
    assert.strictEqual(listCalls, 2, "a later closed-to-open transition should refresh again")
  }],
  ["multiple single-read mutations refresh once after all settle", async () => {
    const firstRequest = deferred()
    const secondRequest = deferred()
    let listCalls = 0
    const instance = createHeaderNotice({
      markNoticeRead: noticeId => noticeId === 3 ? firstRequest.promise : secondRequest.promise,
      listNoticeTop: () => {
        listCalls += 1
        return Promise.resolve({ data: [], unreadCount: 0 })
      }
    })
    const firstItem = { noticeId: 3, isRead: false }
    const secondItem = { noticeId: 4, isRead: false }
    instance.noticeList = [firstItem, secondItem]
    instance.noticeUnreadCount = 2

    instance.previewNotice(firstItem)
    instance.previewNotice(secondItem)
    assert.strictEqual(instance.noticeReadPendingCount, 2, "each pending mark-read should be tracked")

    firstRequest.resolve()
    await flushAsync()
    assert.strictEqual(listCalls, 0, "the first settlement must not refresh while another mutation is pending")

    secondRequest.reject(new Error("second mark read failed"))
    await flushAsync()
    assert.strictEqual(instance.noticeReadPendingCount, 0, "all settled mutations should release the pending counter")
    assert.strictEqual(listCalls, 1, "the final settlement should trigger exactly one authoritative GET")
  }],
  ["pending single-read blocks mark-all in UI and method guard", async () => {
    let markAllCalls = 0
    const instance = createHeaderNotice({
      markNoticeReadAll: () => {
        markAllCalls += 1
        return Promise.resolve()
      }
    }, {
      noticeReadPendingCount: 1,
      noticeUnreadCount: 1
    })

    await instance.markAllRead()
    assert.strictEqual(markAllCalls, 0, "mark-all must not overlap a pending single-read mutation")
    assert.ok(
      componentSource.includes(':disabled="noticeMarkingAll || noticeLoading || noticeReadPendingCount > 0 || noticeUnreadCount <= 0"'),
      "the mark-all button should expose the same pending-read guard"
    )
  }],
  ["pending mark-all blocks a single-read mutation but still opens detail", async () => {
    const markAllRequest = deferred()
    let markReadCalls = 0
    const openedNoticeIds = []
    const instance = createHeaderNotice({
      markNoticeRead: () => {
        markReadCalls += 1
        return Promise.resolve()
      },
      markNoticeReadAll: () => markAllRequest.promise
    }, {
      $refs: {
        noticePopover: null,
        noticeViewRef: { open: noticeId => openedNoticeIds.push(noticeId) }
      }
    })
    const item = { noticeId: 5, isRead: false }
    instance.noticeList = [item]
    instance.noticeUnreadCount = 1

    const markAllPromise = instance.markAllRead()
    instance.previewNotice(item)

    assert.strictEqual(markReadCalls, 0, "single-read POST must not overlap a pending mark-all POST")
    assert.strictEqual(openedNoticeIds.length, 1, "mark-all should not block opening announcement detail")
    assert.strictEqual(openedNoticeIds[0], 5, "the selected announcement detail should still open")

    markAllRequest.resolve()
    await markAllPromise
  }],
  ["a stale GET resolving last cannot overwrite the latest response", async () => {
    const firstListRequest = deferred()
    const secondListRequest = deferred()
    let listCalls = 0
    const instance = createHeaderNotice({
      listNoticeTop: () => {
        listCalls += 1
        return listCalls === 1 ? firstListRequest.promise : secondListRequest.promise
      }
    })
    instance.noticeList = [{ noticeId: 6, noticeTitle: "initial", isRead: false }]
    instance.noticeUnreadCount = 1

    const firstLoad = instance.loadNoticeTop()
    const secondLoad = instance.loadNoticeTop()
    secondListRequest.resolve({ data: [{ noticeId: 7, noticeTitle: "latest", isRead: true }], unreadCount: 0 })
    await secondLoad
    firstListRequest.resolve({ data: [{ noticeId: 8, noticeTitle: "stale", isRead: false }], unreadCount: 9 })
    await firstLoad

    assert.strictEqual(instance.noticeList[0].noticeTitle, "latest", "only the newest GET may commit its list")
    assert.strictEqual(instance.noticeUnreadCount, 0, "only the newest GET may commit its unread count")
    assert.strictEqual(instance.noticeLoading, false, "the newest completed GET should release loading")
  }],
  ["a stale GET cannot clear loading while the latest request is pending", async () => {
    const firstListRequest = deferred()
    const secondListRequest = deferred()
    let listCalls = 0
    const instance = createHeaderNotice({
      listNoticeTop: () => {
        listCalls += 1
        return listCalls === 1 ? firstListRequest.promise : secondListRequest.promise
      }
    })
    const originalList = [{ noticeId: 9, noticeTitle: "original", isRead: false }]
    instance.noticeList = originalList
    instance.noticeUnreadCount = 1

    const firstLoad = instance.loadNoticeTop()
    const secondLoad = instance.loadNoticeTop()
    firstListRequest.resolve({ data: [{ noticeId: 10, noticeTitle: "stale", isRead: true }], unreadCount: 0 })
    await firstLoad

    assert.strictEqual(instance.noticeList, originalList, "a stale GET must not commit while the newest GET is pending")
    assert.strictEqual(instance.noticeUnreadCount, 1, "a stale GET must not change the unread count")
    assert.strictEqual(instance.noticeLoading, true, "a stale finally must not clear loading for the newest GET")

    secondListRequest.resolve({ data: [{ noticeId: 11, noticeTitle: "latest", isRead: true }], unreadCount: 0 })
    await secondLoad
    assert.strictEqual(instance.noticeLoading, false, "the newest GET should release loading when it settles")
  }],
  ["destroyed instances ignore resolved and rejected in-flight GETs", async () => {
    const resolvedListRequest = deferred()
    const rejectedListRequest = deferred()
    let listCalls = 0
    const instance = createHeaderNotice({
      listNoticeTop: () => {
        listCalls += 1
        return listCalls === 1 ? resolvedListRequest.promise : rejectedListRequest.promise
      }
    })
    const originalList = [{ noticeId: 12, isRead: false }]
    instance.noticeList = originalList
    instance.noticeUnreadCount = 1

    const resolvedLoad = instance.loadNoticeTop()
    const rejectedLoad = instance.loadNoticeTop()
    instance.beforeDestroy()
    const loadingAtDestroy = instance.noticeLoading

    resolvedListRequest.resolve({ data: [{ noticeId: 13, isRead: true }], unreadCount: 0 })
    rejectedListRequest.reject(new Error("list failed after destroy"))
    await Promise.all([resolvedLoad, rejectedLoad])

    assert.strictEqual(listCalls, 2, "destroy settlement must not start another GET")
    assert.strictEqual(instance.noticeList, originalList, "destroyed instances must not accept resolved list data")
    assert.strictEqual(instance.noticeUnreadCount, 1, "destroyed instances must not update unread count")
    assert.strictEqual(instance.noticeLoading, loadingAtDestroy, "destroyed instances must not update loading")
  }],
  ["destroyed instances do not refresh after single-read settlements", async () => {
    const resolvedMarkRead = deferred()
    const rejectedMarkRead = deferred()
    let listCalls = 0
    const instance = createHeaderNotice({
      markNoticeRead: noticeId => noticeId === 14 ? resolvedMarkRead.promise : rejectedMarkRead.promise,
      listNoticeTop: () => {
        listCalls += 1
        return Promise.resolve({ data: [], unreadCount: 0 })
      }
    })
    const firstItem = { noticeId: 14, isRead: false }
    const secondItem = { noticeId: 15, isRead: false }
    instance.noticeList = [firstItem, secondItem]
    instance.noticeUnreadCount = 2
    instance.previewNotice(firstItem)
    instance.previewNotice(secondItem)
    instance.beforeDestroy()
    const listAtDestroy = instance.noticeList
    const unreadAtDestroy = instance.noticeUnreadCount
    const loadingAtDestroy = instance.noticeLoading

    resolvedMarkRead.resolve()
    rejectedMarkRead.reject(new Error("mark read failed after destroy"))
    await flushAsync()

    assert.strictEqual(listCalls, 0, "settled single-read mutations must not refresh after destroy")
    assert.strictEqual(instance.noticeList, listAtDestroy, "settled single-read mutations must not rewrite the list after destroy")
    assert.strictEqual(instance.noticeUnreadCount, unreadAtDestroy, "settled single-read mutations must not rewrite count after destroy")
    assert.strictEqual(instance.noticeLoading, loadingAtDestroy, "settled single-read mutations must not rewrite loading after destroy")
  }],
  ["destroyed instances do not refresh after mark-all settlements", async () => {
    const resolvedMarkAll = deferred()
    const rejectedMarkAll = deferred()
    let resolvedListCalls = 0
    let rejectedListCalls = 0
    const resolvedInstance = createHeaderNotice({
      markNoticeReadAll: () => resolvedMarkAll.promise,
      listNoticeTop: () => {
        resolvedListCalls += 1
        return Promise.resolve({ data: [], unreadCount: 0 })
      }
    })
    const rejectedInstance = createHeaderNotice({
      markNoticeReadAll: () => rejectedMarkAll.promise,
      listNoticeTop: () => {
        rejectedListCalls += 1
        return Promise.resolve({ data: [], unreadCount: 0 })
      }
    })
    resolvedInstance.noticeList = [{ noticeId: 16, isRead: false }]
    resolvedInstance.noticeUnreadCount = 1
    rejectedInstance.noticeList = [{ noticeId: 17, isRead: false }]
    rejectedInstance.noticeUnreadCount = 1

    const resolvedWorkflow = resolvedInstance.markAllRead()
    const rejectedWorkflow = rejectedInstance.markAllRead()
    resolvedInstance.beforeDestroy()
    rejectedInstance.beforeDestroy()
    const resolvedListAtDestroy = resolvedInstance.noticeList
    const rejectedListAtDestroy = rejectedInstance.noticeList

    resolvedMarkAll.resolve()
    rejectedMarkAll.reject(new Error("mark all failed after destroy"))
    await Promise.all([resolvedWorkflow, rejectedWorkflow])

    assert.strictEqual(resolvedListCalls, 0, "resolved mark-all must not refresh after destroy")
    assert.strictEqual(rejectedListCalls, 0, "rejected mark-all must remain handled without a refresh")
    assert.strictEqual(resolvedInstance.noticeList, resolvedListAtDestroy, "resolved mark-all must not rewrite list after destroy")
    assert.strictEqual(rejectedInstance.noticeList, rejectedListAtDestroy, "rejected mark-all must not rewrite list after destroy")
  }]
]

async function runAsyncBehaviorTests() {
  let failures = 0
  for (const [name, test] of asyncBehaviorTests) {
    try {
      await test()
    } catch (error) {
      failures += 1
      console.error(`${name}: ${error.message}`)
    }
  }
  if (failures > 0) throw new Error(`${failures} HeaderNotice async behavior test(s) failed`)
  console.log("headerNoticeReadAll tests passed")
}

runAsyncBehaviorTests().catch(error => {
  console.error(error)
  process.exitCode = 1
})
