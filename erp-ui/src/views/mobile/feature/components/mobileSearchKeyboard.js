function runMobileSearchEnter(event, search) {
  if (event && typeof event.preventDefault === "function") {
    event.preventDefault()
  }
  if (event && typeof event.stopPropagation === "function") {
    event.stopPropagation()
  }
  return typeof search === "function" ? search() : undefined
}

module.exports = {
  runMobileSearchEnter
}
