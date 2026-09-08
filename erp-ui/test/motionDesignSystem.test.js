const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const indexSource = read("src/assets/styles/index.scss")
const motionSource = read("src/assets/styles/motion.scss")
const transitionSource = read("src/assets/styles/transition.scss")
const appMainSource = read("src/layout/components/AppMain.vue")
const accessibilityShellSource = read("src/directive/accessibility/shell.js")
const mobileSystemSource = read("src/views/mobile/styles/mobileSystem.scss")
const homeSource = read("src/views/index.vue")
const rightToolbarSource = read("src/components/RightToolbar/index.vue")
const treePanelSource = read("src/components/TreePanel/index.vue")
const runtimeEvidenceSource = read("scripts/capture-mobile-runtime-evidence.cjs")

function listStyleSources(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolutePath = path.join(directory, entry.name)
    if (entry.isDirectory()) return listStyleSources(absolutePath)
    if (!/\.(?:vue|scss|css)$/.test(entry.name)) return []
    return [[path.relative(rootDir, absolutePath), fs.readFileSync(absolutePath, "utf8")]]
  })
}

assert.ok(
  indexSource.includes("@import './motion.scss';") &&
    indexSource.indexOf("@import './motion.scss';") > indexSource.indexOf("mobileSystem.scss"),
  "the global motion layer should load last so it can normalize legacy and Element UI transitions"
)

;[
  "--motion-duration-fast: 120ms",
  "--motion-duration-base: 180ms",
  "--motion-duration-slow: 240ms",
  "--motion-ease-enter: cubic-bezier(0.16, 1, 0.3, 1)",
  "--motion-ease-exit: cubic-bezier(0.4, 0, 1, 1)",
  "--motion-ease-standard: cubic-bezier(0.2, 0, 0, 1)"
].forEach(contract => {
  assert.ok(motionSource.includes(contract), `motion system should define ${contract}`)
})

assert.ok(
  motionSource.includes('html[data-input-modality="pointer"]') &&
    motionSource.includes("transform: scale(var(--motion-press-scale))") &&
    accessibilityShellSource.includes("window.addEventListener('pointerdown', setPointerModality, true)") &&
    accessibilityShellSource.includes("window.addEventListener('keydown', setKeyboardModality, true)"),
  "press feedback should be pointer-only so keyboard activation remains instant"
)

assert.ok(
  /@media \(prefers-reduced-motion: reduce\)[\s\S]*animation-duration: 0\.01ms !important;[\s\S]*transition-duration: 0\.01ms !important;/.test(motionSource) &&
    motionSource.includes("animation-iteration-count: 1 !important") &&
    motionSource.includes("scroll-behavior: auto !important"),
  "desktop, mobile, and third-party motion should share one global reduced-motion path"
)

assert.ok(
  runtimeEvidenceSource.includes('name: "prefers-reduced-motion", value: "reduce"') &&
    runtimeEvidenceSource.includes("reducedMotionValid") &&
    runtimeEvidenceSource.includes("maxTransitionMilliseconds <= 0.01") &&
    runtimeEvidenceSource.includes("maxAnimationMilliseconds <= 0.01"),
  "runtime evidence should verify the compiled app under emulated reduced-motion preferences"
)

assert.ok(
  motionSource.includes(".dialog-fade-enter-active") &&
    motionSource.includes(".dialog-fade-leave-active") &&
    motionSource.includes("animation: none !important") &&
    motionSource.includes("var(--motion-duration-base) var(--motion-ease-enter)") &&
    motionSource.includes("var(--motion-duration-fast) var(--motion-ease-exit)"),
  "overlays should use interruptible short enters and quieter exits"
)

assert.ok(
  motionSource.includes("Safari can retain the dialog-fade-enter class") &&
    /\.el-dialog__wrapper\.dialog-fade-enter\s*\{[\s\S]*?opacity:\s*1\s*!important;[\s\S]*?\}/.test(motionSource) &&
    /\.el-dialog__wrapper\.dialog-fade-enter\s+\.el-dialog\s*\{[\s\S]*?opacity:\s*1\s*!important;[\s\S]*?transform:\s*none\s*!important;[\s\S]*?\}/.test(motionSource),
  "append-to-body dialogs must remain visible if Safari retains Vue 2 enter classes"
)

assert.ok(
  motionSource.includes("Safari can leave both el-drawer-fade-enter classes attached") &&
    /\.el-drawer__wrapper\.el-drawer-fade-enter,\s*\.el-drawer__wrapper\.el-drawer-fade-enter-active\s*\{[\s\S]*?opacity:\s*1\s*!important;[\s\S]*?animation:\s*none\s*!important;[\s\S]*?\}/.test(motionSource),
  "append-to-body drawers must remain painted if Safari retains Vue 2 enter classes"
)

assert.ok(
  !/transition\s*:[^;]*(?:width|height|top|right|bottom|left|margin|padding)/.test(motionSource),
  "the generated motion layer must not transition layout properties"
)

const transitionAllHits = listStyleSources(path.join(rootDir, "src"))
  .filter(([, source]) => /transition\s*:[^;]*\ball\b/.test(source))
  .map(([file]) => file)

assert.deepStrictEqual(
  transitionAllHits,
  [],
  "front-end source should name animated properties instead of using transition: all"
)

assert.ok(
  !/calc\(var\([^)]*\)\s*\*/.test(motionSource + transitionSource),
  "motion distances should use broadly supported calc subtraction instead of CSS level-4 multiplication"
)

assert.ok(
  !/transition\s*:\s*all\b/.test(transitionSource) &&
    transitionSource.includes("transform: translateY(var(--motion-distance-enter))") &&
    transitionSource.includes("filter: blur(var(--motion-enter-blur))"),
  "shared Vue transitions should animate only purposeful visual properties"
)

assert.ok(
  transitionSource.includes("Breadcrumbs are high-frequency navigation") &&
    /\.breadcrumb-enter-active,[\s\S]*transition:\s*none;/.test(transitionSource) &&
    !appMainSource.includes('<transition name="fade-transform" mode="out-in">'),
  "high-frequency breadcrumb and route navigation should remain instant"
)

assert.ok(
  !rightToolbarSource.includes("max-height 0.25s") &&
    !rightToolbarSource.includes("_animateSearch") &&
    rightToolbarSource.includes("high-frequency task control"),
  "the frequently used search toolbar should update without layout animation"
)

assert.ok(
  !/transition:\s*width/.test(treePanelSource) &&
    !treePanelSource.includes("will-change: width") &&
    motionSource.includes(".tree-action-icon"),
  "tree resizing and collapse should avoid layout animation while pointer buttons retain tactile feedback"
)

assert.ok(
  mobileSystemSource.includes("--mobile-duration: var(--motion-duration-base, 180ms)") &&
    mobileSystemSource.includes("--mobile-ease-enter: var(--motion-ease-enter") &&
    mobileSystemSource.includes("--mobile-ease-exit: var(--motion-ease-exit"),
  "the mobile design system should consume the global motion semantics"
)

assert.ok(
  homeSource.includes("animation: home-rise var(--motion-duration-base) var(--motion-ease-enter) both") &&
    homeSource.includes("filter: blur(var(--motion-enter-blur))") &&
    !/animation:\s*home-rise\s+(?:4|5)\d{2}ms/.test(homeSource),
  "the dashboard entrance should stay subtle and under the productivity motion budget"
)

console.log("motion design system tests passed")
