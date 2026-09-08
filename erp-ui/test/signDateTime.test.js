const assert = require("assert")

const {
  dateTimeValue,
  formatSignDateTime,
  formatSignDateTimeWithSeconds
} = require("../src/utils/signDateTime")

const expectedTimestamp = Date.parse("2026-07-19T10:01:09.125Z")

assert.strictEqual(
  dateTimeValue("2026-07-19 18:01:09.125"),
  expectedTimestamp,
  "SQL-style values without a zone must be interpreted as Asia/Shanghai (+08:00)"
)
assert.strictEqual(
  dateTimeValue("2026-07-19T18:01:09.125"),
  expectedTimestamp,
  "zone-less database timestamps using T should follow the same Shanghai rule"
)
assert.strictEqual(
  dateTimeValue("2026-07-19T10:01:09.125Z"),
  expectedTimestamp,
  "ISO timestamps with Z must preserve their instant"
)
assert.strictEqual(
  dateTimeValue("2026-07-19T19:01:09.125+09:00"),
  expectedTimestamp,
  "ISO timestamps with an explicit offset must preserve their instant"
)
assert.strictEqual(
  dateTimeValue("2026-07-19T05:31:09.125-0430"),
  expectedTimestamp,
  "compact ISO offsets should be accepted without relying on browser-specific Date parsing"
)
assert.strictEqual(
  dateTimeValue(new Date(expectedTimestamp)),
  expectedTimestamp,
  "valid Date instances must be supported"
)
assert.strictEqual(
  dateTimeValue(expectedTimestamp),
  expectedTimestamp,
  "finite millisecond timestamps must be supported"
)
assert.strictEqual(
  dateTimeValue(expectedTimestamp / 1000),
  expectedTimestamp,
  "finite Unix timestamps in seconds must be supported"
)
assert.strictEqual(
  dateTimeValue(String(expectedTimestamp)),
  expectedTimestamp,
  "numeric timestamp strings must follow the same deterministic conversion"
)

assert.strictEqual(formatSignDateTime("2026-07-19T10:01:09.125Z"), "2026-07-19 18:01")
assert.strictEqual(formatSignDateTime("2026-07-19T19:01:09.125+09:00"), "2026-07-19 18:01")
assert.strictEqual(formatSignDateTime("2026-07-19 18:01"), "2026-07-19 18:01")
assert.strictEqual(formatSignDateTime("2026-07-19T16:00:00Z"), "2026-07-20 00:00",
  "formatting must use Asia/Shanghai rather than the machine's local timezone")
assert.strictEqual(
  formatSignDateTimeWithSeconds("2026-07-19T10:01:09.125Z"),
  "2026-07-19 18:01:09",
  "audit-oriented desktop times should preserve seconds"
)

const invalidValues = [
  undefined,
  null,
  "",
  "   ",
  NaN,
  Infinity,
  -Infinity,
  new Date(NaN),
  "not-a-date",
  "2026-02-30 18:01:09",
  "2026-07-19 24:01:09",
  "2026-07-19T18:01:09+24:00"
]

invalidValues.forEach(value => {
  assert.ok(Number.isNaN(dateTimeValue(value)), `invalid input should not yield a timestamp: ${String(value)}`)
  assert.strictEqual(formatSignDateTime(value), "-",
    `invalid input must render a dash instead of a zero-filled date: ${String(value)}`)
  assert.strictEqual(formatSignDateTimeWithSeconds(value), "-",
    `invalid input with seconds must render a dash: ${String(value)}`)
})

console.log("sign date-time tests passed")
