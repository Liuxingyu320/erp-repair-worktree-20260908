const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '../..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

const companyUi = read('erp-ui/src/views/oa/signPackage/CompanySealManagement.vue')
const packageUi = read('erp-ui/src/views/oa/signPackage/index.vue')
const legalController = read('erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLegalEntityController.java')
const packageController = read('erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java')

;['oa:signCompany:list', 'oa:signCompany:edit', 'oa:signSeal:list', 'oa:signSeal:edit']
  .forEach(permission => assert.ok(
    companyUi.includes(permission) || packageUi.includes(permission) ||
      legalController.includes(permission) || packageController.includes(permission),
    `independent permission ${permission} should be enforced`
  ))
assert.ok(legalController.includes('@GetMapping("/options")') &&
  /system:dept:list[\s\S]*oa:signCompany:list/.test(legalController),
'legal-entity options must require department or signing-company view permission')
assert.ok(packageController.includes('@RequiresPermissions("oa:signSeal:list")') &&
  packageController.includes('@RequiresPermissions("oa:signSeal:edit")'),
'seal read and write endpoints must enforce separate backend permissions')
assert.ok(packageUi.includes('canViewCompanySealManagement') &&
  packageUi.includes('oa:signCompany:list'),
'the company/seal tab must be hidden without company view permission')

console.log('sign company and seal management tests passed')
