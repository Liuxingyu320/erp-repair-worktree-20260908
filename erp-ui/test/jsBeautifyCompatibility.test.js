const assert = require("assert")

const beautifier = require("js-beautify")

assert.strictEqual(typeof beautifier.html, "function", "the form builder requires the CommonJS html formatter")
assert.strictEqual(typeof beautifier.js, "function")
assert.strictEqual(typeof beautifier.css, "function")

const formatted = beautifier.html("<section><strong>safe</strong></section>", {
  indent_size: 2,
  wrap_line_length: 120
})
assert.ok(formatted.includes("<section>") && formatted.includes("<strong>safe</strong>"))

console.log("jsBeautifyCompatibility tests passed")
