function createSingleFlight(task) {
  let active = null
  return function singleFlight(...args) {
    if (active) return active
    let flight
    flight = Promise.resolve()
      .then(() => task(...args))
      .finally(() => {
        if (active === flight) active = null
      })
    active = flight
    return flight
  }
}

module.exports = {
  createSingleFlight
}
