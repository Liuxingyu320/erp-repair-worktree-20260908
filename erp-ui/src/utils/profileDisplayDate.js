// Display the calendar date only for a complete, recognizable ISO datetime.
// Names containing T are ordinary text; never parse dates through a timezone.
function displayProfileDate(value) {
  if (typeof value !== 'string') return value
  const match = /^(\d{4})-(\d{2})-(\d{2})T(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d+)?)?(?:Z|[+-](?:0\d|1[0-4]):[0-5]\d)?$/.exec(value)
  if (!match) return value
  const year = Number(match[1]), month = Number(match[2]), day = Number(match[3])
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  return month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1] ? value.slice(0, 10) : value
}

module.exports = { displayProfileDate }
