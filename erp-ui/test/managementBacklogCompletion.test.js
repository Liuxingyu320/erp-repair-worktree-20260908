const assert = require('assert');
const fs = require('fs');
const path = require('path');

const uiRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(uiRoot, '..');

function read(...segments) {
  return fs.readFileSync(path.join(...segments), 'utf8');
}

const systemSalaryPage = read(uiRoot, 'src/views/system/salary/index.vue');
const oaSalaryApi = read(uiRoot, 'src/api/oa/salary.js');
const oaSalaryPage = read(uiRoot, 'src/views/oa/salary/index.vue');
const attendancePage = read(uiRoot, 'src/views/oa/attendance/index.vue');
const attendanceV2Api = read(uiRoot, 'src/api/oa/attendanceV2.js');
const attendanceSitePage = read(uiRoot, 'src/views/oa/attendance/components/AttendanceSiteManagement.vue');
const attendanceShiftPage = read(uiRoot, 'src/views/oa/attendance/components/ShiftManagement.vue');
const attendanceSchedulePage = read(uiRoot, 'src/views/oa/attendance/components/WeeklySchedule.vue');
const attendanceDayResultPage = read(uiRoot, 'src/views/oa/attendance/components/DayResultManagement.vue');
const mobileAttendancePage = read(uiRoot, 'src/views/mobile/attendance/index.vue');
const purchaseApi = read(uiRoot, 'src/api/inventory/purchase.js');
const purchasePage = read(uiRoot, 'src/views/inventory/purchase/index.vue');
const oaSalaryController = read(
  repoRoot,
  'erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java'
);

assert(systemSalaryPage.includes('薪资方案'), 'system salary page should expose salary scheme management tab');
assert(systemSalaryPage.includes('档位明细'), 'system salary page should expose salary item management tab');
assert(systemSalaryPage.includes('员工绑定'), 'system salary page should expose employee salary binding management tab');
assert(systemSalaryPage.includes('listSalaryScheme'), 'system salary page should load salary schemes');
assert(systemSalaryPage.includes('addSalaryScheme'), 'system salary page should support adding salary schemes');
assert(systemSalaryPage.includes('updateSalaryScheme'), 'system salary page should support editing salary schemes');
assert(systemSalaryPage.includes('delSalaryScheme'), 'system salary page should support deleting salary schemes');
assert(systemSalaryPage.includes('delSalaryItem'), 'system salary page should support deleting salary items');
assert(systemSalaryPage.includes('system/salaryConfig/export'), 'system salary page should expose salary scheme export');
assert(systemSalaryPage.includes('salaryUserOptions'), 'system salary page should load employees that can be bound to salary schemes');
assert(systemSalaryPage.includes('salaryShopTree'), 'system salary page should load shop context for employee salary binding');
assert(systemSalaryPage.includes('getUserSalaryBatch'), 'system salary page should keep batched employee salary loading');
assert(systemSalaryPage.includes('saveUserSalary'), 'system salary page should save employee salary binding');
assert(!systemSalaryPage.includes('getRoleSalaryBatch'), 'system salary page should not keep role salary loading after switching binding to employees');

assert(oaSalaryApi.includes('getSalaryConfig'), 'OA salary API should expose salary config getter');
assert(oaSalaryApi.includes('saveSalaryConfig'), 'OA salary API should expose salary config saver');
assert(oaSalaryApi.includes("url: '/oa/salary/config'"), 'OA salary API should call /oa/salary/config');
assert(oaSalaryApi.includes("method: 'get'"), 'OA salary config getter should use GET');
assert(oaSalaryApi.includes("method: 'put'"), 'OA salary config saver should use PUT');

assert(oaSalaryPage.includes('工资参数'), 'OA salary page should expose salary config entry');
assert(oaSalaryPage.includes('getSalaryConfig'), 'OA salary page should load salary config');
assert(oaSalaryPage.includes('saveSalaryConfig'), 'OA salary page should save salary config');

assert(oaSalaryController.includes('@GetMapping("/config")'), 'OA salary controller should expose GET /config');
assert(oaSalaryController.includes('@PutMapping("/config")'), 'OA salary controller should expose PUT /config');
assert(oaSalaryController.includes('OaSalaryConfig'), 'OA salary controller should use OaSalaryConfig');
assert(oaSalaryController.includes('salaryService.getConfig'), 'OA salary controller should delegate config reads');
assert(oaSalaryController.includes('salaryService.saveConfig'), 'OA salary controller should delegate config saves');

assert(attendancePage.includes('<shift-management'), 'attendance center should expose shift management');
assert(attendancePage.includes('<attendance-site-management'), 'attendance center should expose attendance site configuration');
assert(attendancePage.includes('<weekly-schedule'), 'attendance center should expose weekly scheduling');
assert(attendancePage.includes('<day-result-management'), 'attendance center should expose real daily result settlement');
assert(attendanceShiftPage.includes('createAttendanceShift'), 'shift management should persist V2 shifts');
assert(attendanceSchedulePage.includes('publishAttendanceSchedules') && attendanceSchedulePage.includes('businessDate'), 'weekly scheduling should publish V2 business-date schedules');
assert(attendanceSchedulePage.includes('siteId: value.siteId') && attendanceSchedulePage.includes('listAttendanceSites'), 'weekly scheduling should require and submit an enabled attendance site');
assert(attendanceSitePage.includes('createAttendanceSite') && attendanceSitePage.includes('updateAttendanceSite') && attendanceSitePage.includes('changeAttendanceSiteStatus') && attendanceSitePage.includes('deleteAttendanceSite'), 'attendance site page should restore CRUD and explicit status management');
assert(attendanceDayResultPage.includes('getAttendanceDayResultPreflight') && attendanceDayResultPage.includes('settleAttendanceDayResults'), 'daily results should preflight before server settlement');
assert(attendanceV2Api.includes("'/oa/attendance-v2'"), 'attendance V2 API should use the OA gateway route');
assert(attendanceV2Api.includes('/sites'), 'attendance V2 frontend API should expose scoped site management');
assert(mobileAttendancePage.includes('createPunchChallenge') && mobileAttendancePage.includes('navigator.geolocation.getCurrentPosition') && mobileAttendancePage.includes('getUserMedia(') && mobileAttendancePage.includes('ref="liveCameraVideo"') && !mobileAttendancePage.includes('capture="environment"'), 'mobile attendance should require challenge, geolocation and a live camera stream');
assert(!attendancePage.includes('handleCheckIn') && !attendancePage.includes('handleCheckOut'), 'desktop WEB punch actions must remain retired');

assert(purchaseApi.includes('deleteDraftPurchase'), 'purchase API should expose draft deletion helper');
assert(purchaseApi.includes('/inventory/purchase/delete/'), 'purchase draft deletion should call the dedicated delete URL');
assert(purchasePage.includes('删除草稿'), 'purchase page should expose draft deletion action');
assert(purchasePage.includes('deleteDraftPurchase'), 'purchase page should call draft deletion helper');

console.log('managementBacklogCompletion tests passed');
