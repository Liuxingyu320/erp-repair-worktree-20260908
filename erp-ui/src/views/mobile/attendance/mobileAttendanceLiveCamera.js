function liveCameraConstraints() {
  return {
    audio: false,
    video: {
      facingMode: { exact: 'environment' },
      width: { ideal: 1920 },
      height: { ideal: 1920 }
    }
  }
}

function requireEnvironmentStream(stream) {
  const tracks = stream && typeof stream.getVideoTracks === 'function' ? stream.getVideoTracks() : []
  const track = tracks.length === 1 ? tracks[0] : null
  const settings = track && typeof track.getSettings === 'function' ? track.getSettings() : null
  if (!settings || String(settings.facingMode || '').toLowerCase() !== 'environment') {
    stopMediaStream(stream)
    throw new Error('无法确认已启用后置实时相机，本次不能打卡。')
  }
  return stream
}

function requireLiveCamera(mediaDevices, secureContext) {
  if (secureContext === false) throw new Error('手机网页端实时相机必须使用 HTTPS。')
  if (!mediaDevices || typeof mediaDevices.getUserMedia !== 'function') {
    throw new Error('当前浏览器不支持实时相机，本次不能打卡。请使用新版 Safari、微信或已安装应用。')
  }
  return mediaDevices
}

function stopMediaStream(stream) {
  if (!stream || typeof stream.getTracks !== 'function') return
  stream.getTracks().forEach(track => {
    if (track && typeof track.stop === 'function') track.stop()
  })
}

function canvasBlob(canvas, type, quality) {
  return new Promise((resolve, reject) => {
    if (!canvas || typeof canvas.toBlob !== 'function') {
      reject(new Error('当前浏览器不能生成现场照片'))
      return
    }
    canvas.toBlob(blob => {
      if (!blob || blob.size <= 0) reject(new Error('现场照片生成失败，请重新拍摄'))
      else resolve(blob)
    }, type, quality)
  })
}

async function captureLiveFrame(video, options = {}) {
  const width = Number(video && video.videoWidth)
  const height = Number(video && video.videoHeight)
  if (!video || !Number.isFinite(width) || !Number.isFinite(height) || width < 320 || height < 240) {
    throw new Error('实时相机画面尚未就绪，请稍候再拍')
  }
  const maximum = Number(options.maxDimension || 1920)
  const scale = Math.min(1, maximum / Math.max(width, height))
  const targetWidth = Math.max(320, Math.round(width * scale))
  const targetHeight = Math.max(240, Math.round(height * scale))
  const documentRef = options.document || (typeof document !== 'undefined' ? document : null)
  if (!documentRef || typeof documentRef.createElement !== 'function') throw new Error('当前页面不能生成现场照片')
  const canvas = documentRef.createElement('canvas')
  canvas.width = targetWidth
  canvas.height = targetHeight
  const context = canvas.getContext && canvas.getContext('2d')
  if (!context || typeof context.drawImage !== 'function') throw new Error('当前浏览器不能读取实时相机画面')
  context.drawImage(video, 0, 0, targetWidth, targetHeight)
  const blob = await canvasBlob(canvas, 'image/jpeg', Number(options.quality || 0.82))
  const now = Number(typeof options.now === 'function' ? options.now() : options.now || Date.now())
  const FileCtor = options.File || (typeof File !== 'undefined' ? File : null)
  if (!FileCtor) throw new Error('当前浏览器不能封装现场照片')
  return new FileCtor([blob], `attendance-live-${now}.jpg`, {
    type: 'image/jpeg',
    lastModified: now
  })
}

module.exports = {
  captureLiveFrame,
  liveCameraConstraints,
  requireEnvironmentStream,
  requireLiveCamera,
  stopMediaStream
}
