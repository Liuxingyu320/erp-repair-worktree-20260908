const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const componentPath = path.resolve(__dirname, '../src/layout/components/TagsView/index.vue')

function loadComponent() {
  const source = fs.readFileSync(componentPath, 'utf8')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, 'TagsView component script must exist')

  const executable = script[1]
    .replace(/import ScrollPane from ['"].*?['"]/, 'const ScrollPane = {}')
    .replace(/import path from ['"]path['"]/, "const path = require('path')")
    .replace('export default', 'module.exports =')

  const context = {
    module: { exports: {} },
    exports: {},
    require,
    document: { body: { addEventListener() {}, removeEventListener() {} } },
    window: { addEventListener() {}, removeEventListener() {} }
  }
  vm.runInNewContext(executable, context, { filename: componentPath })
  return context.module.exports
}

function createHarness() {
  const nextTicks = []
  const moved = []
  const component = loadComponent()
  const harness = {
    $refs: {
      scrollPane: {
        moveToTarget(tag) {
          moved.push(tag)
        }
      }
    },
    $route: {
      path: '/workbench/todo',
      fullPath: '/workbench/todo?category=all'
    },
    $store: {
      dispatch() {}
    },
    $nextTick(callback) {
      nextTicks.push(callback)
    }
  }
  return { component, harness, moved, nextTicks }
}

function testMissingTagRefsAreSafe() {
  const { component, harness, moved, nextTicks } = createHarness()
  component.methods.moveToCurrentTag.call(harness)
  assert.strictEqual(nextTicks.length, 1)
  assert.doesNotThrow(() => nextTicks.shift()(), 'route changes must tolerate a render tick with no tag refs')
  assert.deepStrictEqual(moved, [])
}

function testRefsAreReadAfterRenderTick() {
  const { component, harness, moved, nextTicks } = createHarness()
  component.methods.moveToCurrentTag.call(harness)
  const todoTag = {
    to: {
      path: '/workbench/todo',
      fullPath: '/workbench/todo?category=all'
    }
  }
  harness.$refs.tag = [todoTag]
  nextTicks.shift()()
  assert.deepStrictEqual(moved, [todoTag], 'the current tag should be resolved from refs created by the render tick')
}

testMissingTagRefsAreSafe()
testRefsAreReadAfterRenderTick()

console.log('todoTagsViewNavigation tests passed')
