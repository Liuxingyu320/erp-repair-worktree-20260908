const USER_PII_BASE_FIELDS = ["email", "phonenumber", "sex"]

const USER_PII_PROFILE_FIELDS = [
  "birthDate", "idType", "idNumber", "bloodType", "registeredResidence", "currentAddress",
  "firstEducation", "firstDegree", "firstGraduationDate", "firstGraduationSchool", "firstMajor",
  "highestEducation", "highestDegree", "highestGraduationDate", "highestGraduationSchool", "highestMajor",
  "politicalStatus", "maritalStatus", "nationality", "foreignNationalFlag", "ethnicity", "healthStatus",
  "emergencyContact", "emergencyContactRelation", "emergencyContactPhone", "officePhone", "workLocation",
  "householdType", "socialSecurityLocation", "housingFundLocation", "bankName", "bankAccount"
]

function collectUserPiiValues(form) {
  const source = form || {}
  const profile = source.profile || {}
  const values = {}

  USER_PII_BASE_FIELDS.forEach(field => {
    if (source[field] !== undefined) values[field] = source[field]
  })
  USER_PII_PROFILE_FIELDS.forEach(field => {
    if (profile[field] !== undefined) values[field] = profile[field]
  })
  return values
}

function comparablePiiValue(value) {
  return value === undefined || value === null || value === "" ? null : value
}

function buildUserPiiPatch(form, baseline, options = {}) {
  const current = collectUserPiiValues(form)
  if (options.fullSnapshot === true) {
    return Object.keys(current).length ? current : null
  }
  if (!baseline || typeof baseline !== "object") return null

  const patch = {}
  Object.keys(current).forEach(field => {
    if (comparablePiiValue(current[field]) !== comparablePiiValue(baseline[field])) {
      patch[field] = current[field]
    }
  })
  return Object.keys(patch).length ? patch : null
}

function captureUserPiiSnapshot(form) {
  return { ...collectUserPiiValues(form) }
}

module.exports = {
  USER_PII_BASE_FIELDS,
  USER_PII_PROFILE_FIELDS,
  buildUserPiiPatch,
  captureUserPiiSnapshot,
  collectUserPiiValues
}
