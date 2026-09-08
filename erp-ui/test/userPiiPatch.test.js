const assert = require("assert")
const {
  buildUserPiiPatch,
  captureUserPiiSnapshot
} = require("../src/utils/userPiiPatch")

const loadedForm = {
  userId: "1029",
  email: "employee@example.com",
  phonenumber: "15900000000",
  sex: "1",
  roleIds: [2, 3],
  profile: {
    birthDate: "1998-01-02",
    idNumber: "masked-id",
    emergencyContact: "家属",
    bankAccount: "masked-bank"
  }
}

const baseline = captureUserPiiSnapshot(loadedForm)

assert.strictEqual(
  buildUserPiiPatch({ ...loadedForm, roleIds: [3] }, baseline),
  null,
  "role-only edits must not write the PII endpoint"
)

assert.deepStrictEqual(
  buildUserPiiPatch({
    ...loadedForm,
    profile: { ...loadedForm.profile, emergencyContact: "新联系人" }
  }, baseline),
  { emergencyContact: "新联系人" },
  "PII edits should patch only the field changed by the administrator"
)

assert.deepStrictEqual(
  buildUserPiiPatch({
    ...loadedForm,
    profile: { ...loadedForm.profile, bankAccount: "" }
  }, baseline),
  { bankAccount: "" },
  "explicitly clearing a populated PII field must remain supported"
)

assert.strictEqual(
  buildUserPiiPatch({ userId: "1029", roleIds: [2], profile: {} }, {}, {}),
  null,
  "an incomplete PII read must not turn a role edit into a mass-clear request"
)

assert.deepStrictEqual(
  buildUserPiiPatch({
    userName: "new-user",
    phonenumber: "15900000001",
    profile: { birthDate: "2000-05-06" }
  }, null, { fullSnapshot: true }),
  { phonenumber: "15900000001", birthDate: "2000-05-06" },
  "new-user creation should still send the provided PII snapshot"
)

assert.strictEqual(
  buildUserPiiPatch({ userId: "1029", email: "", profile: {} }, { email: null }),
  null,
  "equivalent empty values should not create a meaningless PII patch"
)

console.log("userPiiPatch tests passed")
