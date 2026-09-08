const assert = require("assert")

const {
  isValidSignImagePlacementJson,
  normalizeSignImagePlacementJson
} = require("../src/utils/signPlacementPolicy")

assert.strictEqual(
  normalizeSignImagePlacementJson('{ "mode" : "APPENDED_CONFIRMATION_PAGE" }'),
  '{"mode":"APPENDED_CONFIRMATION_PAGE"}',
  "APPENDED_CONFIRMATION_PAGE should normalize to its exact explicit policy"
)
assert.strictEqual(
  normalizeSignImagePlacementJson(
    '{"height":"80","x":"114","mode":"LAST_PAGE","width":120,"y":375}'
  ),
  '{"mode":"LAST_PAGE","x":114,"y":375,"width":120,"height":80}',
  "LAST_PAGE should normalize coordinates and preserve no implicit page number"
)
assert.strictEqual(
  normalizeSignImagePlacementJson(
    '{"height":80,"x":114,"mode":"PLACED","width":120,"pageNumber":2,"y":375}'
  ),
  '{"mode":"PLACED","pageNumber":2,"x":114,"y":375,"width":120,"height":80}',
  "PLACED should retain its explicit positive page number"
)

;[
  '{"mode":"APPENDED_CONFIRMATION_PAGE","x":0}',
  '{"mode":"LAST_PAGE","pageNumber":1,"x":114,"y":375,"width":120,"height":80}',
  '{"mode":"LAST_PAGE","x":114,"y":375,"width":120,"height":80,"rotation":0}',
  '{"mode":"LAST_PAGE","x":114,"y":375,"width":0,"height":80}',
  '{"mode":"LAST_PAGE","x":null,"y":375,"width":120,"height":80}',
  '{"mode":"PLACED","pageNumber":0,"x":114,"y":375,"width":120,"height":80}',
  '{"mode":"UNKNOWN","x":114,"y":375,"width":120,"height":80}'
].forEach(value => {
  assert.strictEqual(normalizeSignImagePlacementJson(value), null,
    `invalid or ambiguous placement must fail closed: ${value}`)
  assert.strictEqual(isValidSignImagePlacementJson(value), false)
})

assert.strictEqual(isValidSignImagePlacementJson(
  '{"mode":"LAST_PAGE","x":114,"y":375,"width":120,"height":80}'
), true)

console.log("sign placement policy tests passed")
