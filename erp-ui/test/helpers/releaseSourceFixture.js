"use strict"

const fs = require("fs")
const os = require("os")
const path = require("path")
const { execFileSync } = require("child_process")

// Release tests also run from source archives. Git-dependent checks belong in
// an isolated test repository, never in a fabricated .git beside user sources.
function withReleaseSourceFixture(sourceRoot, relativePaths, run) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "erp-release-contract-"))
  const paths = [...new Set(relativePaths)]
  const env = Object.fromEntries(Object.entries(process.env)
    .filter(([key]) => !key.startsWith("GIT_")))
  env.GIT_CONFIG_NOSYSTEM = "1"
  env.GIT_CONFIG_GLOBAL = os.devNull
  env.GIT_TERMINAL_PROMPT = "0"
  const git = (...args) => execFileSync("git", ["-C", root, ...args], {
    encoding: "utf8", env, stdio: "pipe"
  })
  try {
    for (const relative of paths) {
      if (path.isAbsolute(relative) || relative.split(/[\\/]/).some(part => part === ".." || part === ".git")) {
        throw new Error(`unsafe fixture path: ${relative}`)
      }
      const target = path.join(root, relative)
      fs.mkdirSync(path.dirname(target), { recursive: true })
      fs.copyFileSync(path.join(sourceRoot, relative), target)
    }
    git("init", "--quiet", "--template=")
    git("config", "core.hooksPath", os.devNull)
    const commit = () => {
      git("add", "--force", "--", ...paths)
      git("-c", "user.name=Release contract fixture", "-c", "user.email=fixture@example.invalid",
        "-c", "commit.gpgSign=false", "commit", "--quiet", "-m", "Synthetic release test fixture")
    }
    return run({ root, env, git, commit })
  } finally {
    fs.rmSync(root, { recursive: true, force: true })
  }
}

module.exports = { withReleaseSourceFixture }
