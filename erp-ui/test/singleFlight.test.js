const assert = require("assert")
const { createSingleFlight } = require("../src/utils/singleFlight")

async function run() {
  let calls = 0
  let release
  const gate = new Promise(resolve => { release = resolve })
  const recover = createSingleFlight(async () => {
    calls += 1
    await gate
    return "rotated"
  })

  const first = recover()
  const second = recover()
  const third = recover()
  assert.strictEqual(first, second)
  assert.strictEqual(second, third)
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(calls, 1, "concurrent CSRF failures must start one recovery request")
  release()
  assert.deepStrictEqual(await Promise.all([first, second, third]), [
    "rotated", "rotated", "rotated"
  ])

  await recover()
  assert.strictEqual(calls, 2, "a later CSRF expiry may start a new recovery flight")
  console.log("singleFlight tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
