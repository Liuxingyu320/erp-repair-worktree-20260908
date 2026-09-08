const assert = require("assert")
const fs = require("fs")
const path = require("path")

const componentDir = path.resolve(__dirname, "../src/views/mobile/feature/components")
const lineItemsSource = fs.readFileSync(path.join(componentDir, "MobileLineItemsEditor.vue"), "utf8")
const formSheetSource = fs.readFileSync(path.join(componentDir, "MobileFormSheet.vue"), "utf8")
const sheetStyles = fs.readFileSync(path.join(componentDir, "mobileSheet.scss"), "utf8")

assert.ok(
  lineItemsSource.includes("portalStockPickerToBody") &&
    lineItemsSource.includes("document.body.appendChild(this.$refs.stockPickerMask)"),
  "the replenishment stock picker must escape the clipped form-sheet stacking context"
)

assert.ok(
  /\.stock-picker-mask\s*\{[\s\S]*?position:\s*fixed;[\s\S]*?inset:\s*0;[\s\S]*?z-index:\s*10040;/.test(lineItemsSource) &&
    /\.stock-picker-sheet\s*\{[\s\S]*?max-height:\s*calc\(100dvh[\s\S]*?overflow:\s*hidden;/.test(lineItemsSource),
  "the portalled picker must occupy the dynamic viewport and clip only its own internal layout"
)

assert.ok(
  /\.stock-picker-list\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;/.test(lineItemsSource) &&
    /\.stock-picker-footer\s*\{[\s\S]*?position:\s*sticky;[\s\S]*?bottom:\s*0;/.test(lineItemsSource),
  "the inventory results must be the only picker scroller and the confirm action must remain reachable"
)

assert.ok(
  /body\.mobile-form-sheet-open\s+\.bottom-nav[\s\S]*?visibility:\s*hidden;[\s\S]*?pointer-events:\s*none;/.test(formSheetSource) &&
    /body\.mobile-stock-picker-open\s+\.bottom-nav[\s\S]*?visibility:\s*hidden;[\s\S]*?pointer-events:\s*none;/.test(lineItemsSource),
  "the application bottom navigation must not cover or receive taps through either replenishment overlay"
)

assert.ok(
  formSheetSource.includes("scrollTargetWithinFormBody") &&
    formSheetSource.includes("this.$refs.formBody") &&
    formSheetSource.includes("target.getBoundingClientRect()") &&
    !formSheetSource.includes("target.scrollIntoView"),
  "validation and keyboard focus must scroll the form body instead of an arbitrary ancestor"
)

assert.ok(
  /\.stock-option\s*\{[\s\S]*?grid-template-columns:\s*56px minmax\(0,\s*1fr\);/.test(lineItemsSource) &&
    /\.stock-option-main strong,[\s\S]*?overflow-wrap:\s*anywhere;/.test(lineItemsSource) &&
    /\.stock-option-side\s*\{[\s\S]*?min-width:\s*0;/.test(lineItemsSource),
  "long replenishment item codes must wrap without pushing quantity controls beyond narrow screens"
)

assert.ok(
  /\.form-body\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;/.test(sheetStyles) &&
    /\.form-footer\s*\{[\s\S]*?position:\s*sticky;[\s\S]*?bottom:\s*0;/.test(sheetStyles),
  "the parent form must retain one content scroller and a reachable action row"
)

assert.ok(
  /\.detail-action-list\s*\{[\s\S]*?grid-template-columns:\s*repeat\(auto-fit,\s*minmax\(88px,\s*1fr\)\);/.test(sheetStyles) &&
    /@media \(max-width:\s*320px\)[\s\S]*?\.detail-action-list\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\);/.test(sheetStyles),
  "detail actions should use available width before adding rows while retaining a single-column fallback"
)

console.log("mobile replenishment overlay regression checks passed")
