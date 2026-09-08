const assert = require('assert')

const {
  captureLiveFrame,
  liveCameraConstraints,
  requireEnvironmentStream,
  requireLiveCamera,
  stopMediaStream
} = require('../src/views/mobile/attendance/mobileAttendanceLiveCamera')

assert.deepStrictEqual(liveCameraConstraints(), {
  audio: false,
  video: {
    facingMode: { exact: 'environment' },
    width: { ideal: 1920 },
    height: { ideal: 1920 }
  }
})
assert.throws(() => requireLiveCamera(null, true), /不支持实时相机/)
assert.throws(() => requireLiveCamera({ getUserMedia() {} }, false), /HTTPS/)

const rearStream = {
  getVideoTracks: () => [{ getSettings: () => ({ facingMode: 'environment' }) }],
  getTracks: () => []
}
assert.strictEqual(requireEnvironmentStream(rearStream), rearStream)

let frontStopped = 0
const frontStream = {
  getVideoTracks: () => [{ getSettings: () => ({ facingMode: 'user' }) }],
  getTracks: () => [{ stop() { frontStopped += 1 } }]
}
assert.throws(() => requireEnvironmentStream(frontStream), /后置实时相机/)
assert.strictEqual(frontStopped, 1)

let stopped = 0
stopMediaStream({ getTracks: () => [{ stop() { stopped += 1 } }, { stop() { stopped += 1 } }] })
assert.strictEqual(stopped, 2)

class FakeFile {
  constructor(parts, name, options) {
    this.parts = parts
    this.name = name
    this.type = options.type
    this.lastModified = options.lastModified
    this.size = parts.reduce((total, part) => total + Number(part.size || 0), 0)
  }
}

const drawCalls = []
const fakeDocument = {
  createElement(name) {
    assert.strictEqual(name, 'canvas')
    return {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage: (...args) => drawCalls.push(args) }),
      toBlob(callback, type, quality) {
        assert.strictEqual(type, 'image/jpeg')
        assert.strictEqual(quality, 0.82)
        callback({ size: 1024, type })
      }
    }
  }
}

;(async () => {
  const video = { videoWidth: 2560, videoHeight: 1440 }
  const file = await captureLiveFrame(video, {
    document: fakeDocument,
    File: FakeFile,
    now: 1788141600000
  })
  assert.strictEqual(file.name, 'attendance-live-1788141600000.jpg')
  assert.strictEqual(file.type, 'image/jpeg')
  assert.strictEqual(file.lastModified, 1788141600000)
  assert.strictEqual(drawCalls.length, 1)
  assert.deepStrictEqual(drawCalls[0].slice(1), [0, 0, 1920, 1080])
  await assert.rejects(
    () => captureLiveFrame({ videoWidth: 0, videoHeight: 0 }, { document: fakeDocument, File: FakeFile }),
    /尚未就绪/
  )
  console.log('mobile attendance live camera tests passed')
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
