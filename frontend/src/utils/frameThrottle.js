export function createFrameThrottle(callback) {
  let frameId = null

  const throttled = (...args) => {
    if (frameId !== null) return
    frameId = window.requestAnimationFrame(() => {
      frameId = null
      callback(...args)
    })
  }

  throttled.cancel = () => {
    if (frameId === null) return
    window.cancelAnimationFrame(frameId)
    frameId = null
  }

  return throttled
}
