const SQL_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/
const ISO_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})$/i
const NUMERIC_TIMESTAMP_PATTERN = /^[+-]?(?:\d+\.?\d*|\.\d+)$/
const SECOND_TIMESTAMP_LIMIT = 100000000000
const MILLISECONDS_PER_MINUTE = 60 * 1000
const SHANGHAI_OFFSET_MINUTES = 8 * 60

let shanghaiDateTimeFormatter
let shanghaiDateTimeFormatterInitialized = false

function daysInMonth(year, month) {
  if (month === 2) {
    const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
    return leapYear ? 29 : 28
  }
  return [4, 6, 9, 11].includes(month) ? 30 : 31
}

function validCalendarParts(year, month, day, hour, minute, second) {
  if (!Number.isInteger(year) || year < 0 || year > 9999 ||
    !Number.isInteger(month) || month < 1 || month > 12 ||
    !Number.isInteger(day) || day < 1 ||
    !Number.isInteger(hour) || hour < 0 || hour > 23 ||
    !Number.isInteger(minute) || minute < 0 || minute > 59 ||
    !Number.isInteger(second) || second < 0 || second > 59) return false

  return day <= daysInMonth(year, month)
}

function utcTimestamp(year, month, day, hour, minute, second, millisecond) {
  const date = new Date(0)
  date.setUTCFullYear(year, month - 1, day)
  date.setUTCHours(hour, minute, second, millisecond)
  return date.getTime()
}

function fractionalMilliseconds(value) {
  if (!value) return 0
  return Number(`${value}000`.slice(0, 3))
}

function numericTimestamp(value) {
  if (!Number.isFinite(value)) return NaN
  const milliseconds = Math.abs(value) < SECOND_TIMESTAMP_LIMIT
    ? value * 1000
    : value
  const timestamp = new Date(milliseconds).getTime()
  return Number.isFinite(timestamp) ? timestamp : NaN
}

function parseSqlDateTime(text) {
  const match = SQL_DATE_TIME_PATTERN.exec(text)
  if (!match) return NaN
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const second = Number(match[6] || 0)
  const millisecond = fractionalMilliseconds(match[7])
  if (!validCalendarParts(year, month, day, hour, minute, second)) return NaN
  return utcTimestamp(year, month, day, hour, minute, second, millisecond) -
    SHANGHAI_OFFSET_MINUTES * MILLISECONDS_PER_MINUTE
}

function parseIsoDateTime(text) {
  const match = ISO_DATE_TIME_PATTERN.exec(text)
  if (!match) return NaN
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const hour = Number(match[4])
  const minute = Number(match[5])
  const second = Number(match[6] || 0)
  const millisecond = fractionalMilliseconds(match[7])
  if (!validCalendarParts(year, month, day, hour, minute, second)) return NaN

  const zone = match[8].toUpperCase()
  let offsetMinutes = 0
  if (zone !== "Z") {
    const offset = /^([+-])(\d{2}):?(\d{2})$/.exec(zone)
    const offsetHour = Number(offset && offset[2])
    const offsetMinute = Number(offset && offset[3])
    if (!offset || offsetHour > 23 || offsetMinute > 59) return NaN
    offsetMinutes = (offsetHour * 60 + offsetMinute) * (offset[1] === "+" ? 1 : -1)
  }

  return utcTimestamp(year, month, day, hour, minute, second, millisecond) -
    offsetMinutes * MILLISECONDS_PER_MINUTE
}

function dateTimeValue(value) {
  if (value instanceof Date) {
    const timestamp = value.getTime()
    return Number.isFinite(timestamp) ? timestamp : NaN
  }
  if (typeof value === "number") return numericTimestamp(value)
  if (typeof value !== "string") return NaN

  const text = value.trim()
  if (!text) return NaN
  if (NUMERIC_TIMESTAMP_PATTERN.test(text)) return numericTimestamp(Number(text))

  const sqlTimestamp = parseSqlDateTime(text)
  if (Number.isFinite(sqlTimestamp)) return sqlTimestamp
  return parseIsoDateTime(text)
}

function getShanghaiDateTimeFormatter() {
  if (!shanghaiDateTimeFormatterInitialized) {
    shanghaiDateTimeFormatterInitialized = true
    if (typeof Intl !== "undefined" && Intl.DateTimeFormat) {
      try {
        shanghaiDateTimeFormatter = new Intl.DateTimeFormat("zh-CN", {
          timeZone: "Asia/Shanghai",
          year: "numeric",
          month: "2-digit",
          day: "2-digit",
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          hour12: false,
          hourCycle: "h23"
        })
      } catch (_) {
        shanghaiDateTimeFormatter = null
      }
    }
  }
  return shanghaiDateTimeFormatter
}

function pad(value) {
  return String(value).padStart(2, "0")
}

function fallbackShanghaiParts(timestamp) {
  const date = new Date(timestamp + SHANGHAI_OFFSET_MINUTES * MILLISECONDS_PER_MINUTE)
  return {
    year: String(date.getUTCFullYear()).padStart(4, "0"),
    month: pad(date.getUTCMonth() + 1),
    day: pad(date.getUTCDate()),
    hour: pad(date.getUTCHours()),
    minute: pad(date.getUTCMinutes()),
    second: pad(date.getUTCSeconds())
  }
}

function shanghaiParts(timestamp) {
  const formatter = getShanghaiDateTimeFormatter()
  if (!formatter || typeof formatter.formatToParts !== "function") {
    return fallbackShanghaiParts(timestamp)
  }
  const parts = {}
  formatter.formatToParts(new Date(timestamp)).forEach(part => {
    if (part.type !== "literal") parts[part.type] = part.value
  })
  if (!parts.year || !parts.month || !parts.day || !parts.hour || !parts.minute || !parts.second) {
    return fallbackShanghaiParts(timestamp)
  }
  if (parts.hour === "24") parts.hour = "00"
  return parts
}

function formatSignDateTime(value) {
  const timestamp = dateTimeValue(value)
  if (!Number.isFinite(timestamp)) return "-"
  try {
    const parts = shanghaiParts(timestamp)
    return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
  } catch (_) {
    return "-"
  }
}

function formatSignDateTimeWithSeconds(value) {
  const timestamp = dateTimeValue(value)
  if (!Number.isFinite(timestamp)) return "-"
  try {
    const parts = shanghaiParts(timestamp)
    return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
  } catch (_) {
    return "-"
  }
}

module.exports = {
  dateTimeValue,
  formatSignDateTime,
  formatSignDateTimeWithSeconds
}
