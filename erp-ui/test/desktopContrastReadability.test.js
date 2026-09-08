const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const refinement = read("src/assets/styles/desktop-apple-refinement.scss")
const elementUi = read("src/assets/styles/element-ui.scss")
const headerNotice = read("src/layout/components/HeaderNotice/index.vue")
const customer = read("src/views/inventory/customer/index.vue")
const customerSummary = read("src/views/inventory/customer/components/CustomerOpsSummary.vue")
const selectShop = read("src/views/select-shop/index.vue")

;[
  "--erp-canvas: #ffffff",
  "--erp-text-secondary: #555b61",
  "--erp-text-muted: #686f76",
  ".el-input__inner::placeholder",
  "color: #6f737a !important",
  ".el-empty__image svg",
  "filter: grayscale(0.82) brightness(0.94)",
  ".el-empty__description p",
  ".el-table__empty-text",
  ".el-message,",
  ".el-notification",
  ".notice-popover:focus-visible"
].forEach(contract => {
  assert.ok(refinement.includes(contract), `desktop contrast layer should include ${contract}`)
})

assert.ok(
  refinement.includes("-webkit-text-fill-color: #747981") &&
    refinement.includes(".el-button.is-disabled") &&
    refinement.includes("opacity: 1"),
  "disabled form controls and buttons should remain visibly inactive without fading their labels away"
)

assert.ok(
  headerNotice.includes("color: #56595f") &&
    headerNotice.includes("background: #eef3f6") &&
    headerNotice.includes("color: #5f636a") &&
    headerNotice.includes("rgba(63, 111, 143, .24)"),
  "header actions and the notice popover should stay legible on the bright desktop toolbar"
)

assert.ok(
  customer.includes("var(--erp-text-muted, #686f76)") &&
    customer.includes("::v-deep .el-alert__description") &&
    customer.includes("grid-column: 1 / -1") &&
    customer.includes("background: rgba(237, 244, 248, .58)") &&
    customerSummary.includes("border: 1px solid #d4dae0") &&
    customerSummary.includes("color: #555b61"),
  "the reported customer page should strengthen supporting text and summary-card separation"
)

assert.ok(
  refinement.includes("--inv-accent: var(--erp-interactive)") &&
    refinement.includes("--inv-accent-soft: var(--erp-interactive-soft)"),
  "inventory pages should use restrained mist-blue accents instead of a flat graphite-only treatment"
)

assert.ok(
  selectShop.includes("background: #ffffff") &&
    selectShop.includes(".el-input__inner::placeholder") &&
    selectShop.includes("-webkit-text-fill-color: #6f737a"),
  "organization selection should share the brighter canvas and readable placeholder treatment"
)

assert.ok(
  elementUi.includes("@media screen and (min-width: 769px)") &&
    elementUi.includes("background: rgba(255, 255, 255, 0.985)") &&
    elementUi.includes(".el-message__content"),
  "desktop entry pages should keep feedback toasts on the same bright cool-neutral surface"
)

console.log("desktop contrast readability tests passed")
