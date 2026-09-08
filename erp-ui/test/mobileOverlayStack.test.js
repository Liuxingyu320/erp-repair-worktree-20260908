const assert = require("assert")

const {
  mountMobileOverlay,
  releaseMobileOverlay,
  releaseAllMobileOverlays,
  getMobileOverlayLockState,
  resetMobileOverlayLockForTest
} = require("../src/views/mobile/feature/components/mobileOverlayStack")

function createClassList() {
  const values = new Set()
  return {
    add(name) {
      values.add(name)
    },
    remove(name) {
      values.delete(name)
    },
    contains(name) {
      return values.has(name)
    },
    toArray() {
      return Array.from(values)
    }
  }
}

function createElement(name) {
  return {
    name,
    parentNode: null
  }
}

const body = {
  classList: createClassList(),
  style: {
    position: "",
    top: "",
    left: "",
    right: "",
    width: "",
    overflow: "",
    touchAction: ""
  },
  appendChild() {
    throw new Error("Vue-owned overlays must not be reparented")
  }
}

let restoredScroll = null
global.document = {
  body,
  documentElement: { scrollTop: 37 }
}
global.window = {
  pageYOffset: 37,
  scrollTo(x, y) {
    restoredScroll = [x, y]
  }
}

resetMobileOverlayLockForTest()

const vueParent = { name: "vue-parent" }
const detailMask = createElement("detail")
const formMask = createElement("form")
detailMask.parentNode = vueParent
formMask.parentNode = vueParent

mountMobileOverlay("mobile-detail-sheet-open")
mountMobileOverlay("mobile-form-sheet-open")

assert.strictEqual(detailMask.parentNode, vueParent, "detail overlay should remain under its Vue-owned parent")
assert.strictEqual(formMask.parentNode, vueParent, "form overlay should remain under its Vue-owned parent")
assert.ok(body.classList.contains("mobile-overlay-open"), "opening any mobile overlay should lock the page body")
assert.ok(body.classList.contains("mobile-detail-sheet-open"), "detail sheet body class should be tracked")
assert.ok(body.classList.contains("mobile-form-sheet-open"), "form sheet body class should be tracked")
assert.strictEqual(body.style.position, "fixed", "mobile body lock should preserve viewport position while sheets are open")
assert.strictEqual(body.style.top, "-37px", "mobile body lock should remember the scroll offset")
assert.deepStrictEqual(
  getMobileOverlayLockState().activeClasses,
  ["mobile-detail-sheet-open", "mobile-form-sheet-open"],
  "mobile overlay stack should remember nested sheet order"
)

releaseMobileOverlay("mobile-detail-sheet-open")

assert.ok(body.classList.contains("mobile-overlay-open"), "closing one nested sheet should keep the body locked")
assert.ok(!body.classList.contains("mobile-detail-sheet-open"), "closed sheet class should be removed")
assert.ok(body.classList.contains("mobile-form-sheet-open"), "remaining sheet class should stay active")
assert.deepStrictEqual(restoredScroll, null, "scroll position should not restore while another sheet is still open")

releaseMobileOverlay("mobile-form-sheet-open")

assert.ok(!body.classList.contains("mobile-overlay-open"), "body lock should clear after the last sheet closes")
assert.strictEqual(body.style.position, "", "body position style should be restored after the last sheet closes")
assert.deepStrictEqual(restoredScroll, [0, 37], "body unlock should restore the original scroll position")

releaseAllMobileOverlays()
assert.deepStrictEqual(restoredScroll, [0, 37], "releasing an already empty stack should not change scroll position")

global.window.pageYOffset = 19
mountMobileOverlay("mobile-detail-sheet-open")
mountMobileOverlay("mobile-form-sheet-open")
releaseAllMobileOverlays()

assert.ok(!body.classList.contains("mobile-overlay-open"), "full release should clear the shared body lock")
assert.ok(!body.classList.contains("mobile-detail-sheet-open"), "full release should clear detail sheet state")
assert.ok(!body.classList.contains("mobile-form-sheet-open"), "full release should clear form sheet state")
assert.deepStrictEqual(restoredScroll, [0, 19], "full release should restore the scroll position captured by the active stack")

delete global.document
delete global.window
