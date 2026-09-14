const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const componentPath = path.resolve(__dirname, "../src/views/oa/signPackage/index.vue")
const componentSource = fs.readFileSync(componentPath, "utf8")
const { validatePdfBlob } = require("../src/utils/signPackageFileGate")

assert.ok(componentSource.includes(">导出签章展示版</el-button>") &&
  componentSource.includes("签章展示版用于查看签名和盖章位置，原最终归档不变"),
"desktop export UI should identify a display derivative and preserve the immutable archive wording")

function loadComponent() {
  const scriptMatch = componentSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "desktop sign package component should expose a script block")
  const script = scriptMatch[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace(/import\s+["'][^"']+["']\s*/g, "")
    .replace(/export default/, "module.exports =")

  const calls = {
    exports: [],
    savedFiles: [],
    saveCompleted: false,
    failExport: false,
    error: null,
    savePending: null
  }
  const sandbox = {
    module: { exports: {} },
    exports: {},
    process: { env: {} },
    Promise,
    Blob,
    Uint8Array,
    Array,
    Object,
    String,
    Number,
    Boolean,
    Math,
    Date,
    RegExp,
    TypeError,
    CompanySealManagement: {},
    SignPackageExceptionPanel: {},
    SignPackageRecordPanel: {},
    SignScopeSelector: {},
    SIGN_PACKAGE_STATUS_LABELS: { signed: "已签署", draft: "草稿" },
    SIGN_PACKAGE_SCENARIO_OPTIONS: [{ value: "onboard", label: "入职" }],
    DEFAULT_TEMPLATE_TYPE_OPTIONS: [],
    downloadFinalSignPackageDocumentExport(packageId, documentId) {
      calls.exports.push([packageId, documentId])
      if (calls.failExport) return Promise.reject(new Error("network"))
      return Promise.resolve(new Blob(["%PDF-1.7\n%%EOF\n"], { type: "application/pdf" }))
    },
    validatePdfBlob,
    signDictionaryLabel: value => value,
    signPackageStatusLabel: value => value,
    require(request) {
      if (request === "@/utils/uiOperationScope") return require("../src/utils/uiOperationScope")
      if (request === "@/utils/signDisplayText") return { signVersionLabel: value => value }
      if (request === "@/utils/signDateTime") {
        return { formatSignDateTimeWithSeconds: value => String(value == null ? "-" : value) }
      }
      if (request === "@/utils/signPlacementPolicy") {
        return {
          isValidSignImagePlacementJson: () => true,
          normalizeSignImagePlacementJson: value => value
        }
      }
      if (request === "@/utils/signPackageFileGate") return { validatePdfBlob }
      if (request === "@/utils/signScenario") {
        return {
          SIGN_PACKAGE_SCENARIO_OPTIONS: [{ value: "onboard", label: "入职" }],
          packageScenarioHasContractDates: () => false,
          packageScenarioHasEntryDate: () => false,
          packageScenarioHasProbationDates: () => false,
          packageScenarioHasSalary: () => false,
          normalizePackageScenario: value => value,
          signScenarioLabel: value => value
        }
      }
      if (request === "./signTemplateCatalog") {
        return {
          DEFAULT_TEMPLATE_TYPE_OPTIONS: [],
          decorateTemplateTypeOption: value => value,
          isInternalTemplateType: () => false,
          isLaborEmploymentTemplateType: () => false,
          isPostLevelScopedTemplateType: () => false,
          isSalaryVersionTemplateType: () => false,
          isServiceEmploymentTemplateType: () => false
        }
      }
      throw new Error(`Unexpected require: ${request}`)
    }
  }

  vm.runInNewContext(script, sandbox, { filename: componentPath })
  const component = sandbox.module.exports
  const dataContext = Object.assign({}, component.methods)
  const state = component.data.call(dataContext)
  return { component, state, calls }
}

function createContext(harness) {
  const context = Object.assign(harness.state, harness.component.methods, {
    detail: {
      packageId: 24,
      packageNo: "SP/24\n",
      employeeNameSnapshot: "员工:甲",
      status: "signed",
      finalConfirmationStatus: "CONFIRMED"
    },
    $download: {
      saveAs(file, name) {
        harness.calls.savePending = {
          file,
          name,
          resolve() {
            harness.calls.savedFiles.push({ file, name })
            harness.calls.saveCompleted = true
          }
        }
        return new Promise(resolve => {
          harness.calls.savePending.resolvePromise = resolve
        })
      }
    },
    $message: {
      error(message) {
        harness.calls.error = message
      }
    }
  })
  return context
}

function waitForSave(harness) {
  return new Promise(resolve => {
    const poll = () => harness.calls.savePending ? resolve() : setTimeout(poll, 0)
    poll()
  })
}

;(async () => {
  const harness = loadComponent()
  const context = createContext(harness)
  const document = {
    documentId: 104,
    documentName: "劳动/合同",
    templateType: "ONBOARD_LABOR_CONTRACT",
    finalFileAvailable: true
  }

  assert.strictEqual(context.canExportFinalDocument(document), true)
  assert.strictEqual(context.canExportFinalDocument({
    ...document,
    templateType: "ONBOARD_COMMITMENT"
  }), false, "desktop commitment documents must not expose the labor display export")
  assert.strictEqual(context.canExportFinalDocument({
    ...document,
    templateType: "ONBOARD_HANDBOOK_RECEIPT"
  }), false, "desktop handbook documents must not expose the labor display export")
  assert.strictEqual(context.canExportFinalDocument({
    ...document,
    finalFileAvailable: false
  }), false, "desktop labor documents without a final archive must not expose export")
  context.detail.finalConfirmationStatus = "PENDING"
  assert.strictEqual(context.canExportFinalDocument(document), false,
    "desktop unconfirmed packages must not expose export")
  context.detail.finalConfirmationStatus = "CONFIRMED"
  const firstExport = context.exportFinalDocument(document)
  const duplicateExport = context.exportFinalDocument(document)
  assert.strictEqual(await duplicateExport, false,
    "desktop duplicate export clicks must be ignored while the save is pending")
  await waitForSave(harness)
  assert.strictEqual(context.finalExportingDocumentId, "104",
    "desktop export loading must remain active until saveAs resolves")
  harness.calls.savePending.resolve()
  harness.calls.savePending.resolvePromise()
  assert.strictEqual(await firstExport, true)
  assert.strictEqual(context.finalExportingDocumentId, null)
  assert.deepStrictEqual(harness.calls.exports, [[24, 104]])
  assert.strictEqual(harness.calls.savedFiles.length, 1)
  assert.strictEqual(harness.calls.savedFiles[0].name,
    "SP_24-员工_甲-劳动_合同-签章展示版.pdf")

  harness.calls.failExport = true
  assert.strictEqual(await context.exportFinalDocument(document), false)
  assert.strictEqual(context.finalExportingDocumentId, null)
  assert.strictEqual(harness.calls.error, "最终合同导出失败，请稍后重试")

  console.log("desktop sign package export tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
