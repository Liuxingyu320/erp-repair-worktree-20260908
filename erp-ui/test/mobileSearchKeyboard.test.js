const assert = require("assert")
const fs = require("fs")
const path = require("path")

let keyboardHelpers = {}
try {
  keyboardHelpers = require("../src/views/mobile/feature/components/mobileSearchKeyboard")
} catch (error) {
  if (!error || error.code !== "MODULE_NOT_FOUND") throw error
}

const { runMobileSearchEnter } = keyboardHelpers

assert.strictEqual(typeof runMobileSearchEnter, "function", "mobile search Enter helper should be exported")

const calls = []
const result = runMobileSearchEnter({
  preventDefault() {
    calls.push("preventDefault")
  },
  stopPropagation() {
    calls.push("stopPropagation")
  }
}, () => {
  calls.push("search")
  return "searched"
})

assert.deepStrictEqual(
  calls,
  ["preventDefault", "stopPropagation", "search"],
  "Enter should cancel implicit submission and bubbling before starting search"
)
assert.strictEqual(result, "searched", "the helper should return the search result")

let searchWithoutEventCalls = 0
assert.strictEqual(
  runMobileSearchEnter(null, () => {
    searchWithoutEventCalls += 1
    return "safe-search"
  }),
  "safe-search",
  "a missing event should still allow the explicit search"
)
assert.strictEqual(searchWithoutEventCalls, 1)

const noSearchCalls = []
assert.doesNotThrow(() => runMobileSearchEnter({
  preventDefault() {
    noSearchCalls.push("preventDefault")
  },
  stopPropagation() {
    noSearchCalls.push("stopPropagation")
  }
}))
assert.deepStrictEqual(noSearchCalls, ["preventDefault", "stopPropagation"])
assert.doesNotThrow(() => runMobileSearchEnter())

const entityPickerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileEntityPicker.vue"),
  "utf8"
)
const lineItemsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileLineItemsEditor.vue"),
  "utf8"
)

;[entityPickerSource, lineItemsSource].forEach((source, index) => {
  assert.ok(
    source.includes('@keydown.enter="handleSearchEnter"') &&
      source.includes("runMobileSearchEnter(event") &&
      !source.includes("@keyup.enter"),
    `${index === 0 ? "entity picker" : "stock picker"} should call the shared keydown search handler before form submit`
  )
})
