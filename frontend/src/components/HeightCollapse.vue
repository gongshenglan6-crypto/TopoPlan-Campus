<script setup>
const props = defineProps({
  duration: {
    type: Number,
    default: 280
  }
})

const easing = 'cubic-bezier(0.22, 0.9, 0.3, 1)'

function scrollAnchors(element) {
  const containers = []
  let parent = element.parentElement

  while (parent && parent !== document.body) {
    const style = window.getComputedStyle(parent)
    const verticallyScrollable = /auto|scroll|overlay/.test(style.overflowY)

    if (verticallyScrollable && parent.scrollHeight > parent.clientHeight) {
      containers.push({ element: parent, top: parent.scrollTop, left: parent.scrollLeft })
    }

    parent = parent.parentElement
  }

  return {
    viewport: { left: window.scrollX, top: window.scrollY },
    containers
  }
}

function restoreScrollAnchors(anchors) {
  window.scrollTo(anchors.viewport.left, anchors.viewport.top)
  anchors.containers.forEach(({ element, top, left }) => {
    element.scrollTo(left, top)
  })
}

function keepScrollAnchored(anchors) {
  let frameId = 0

  const keepPosition = () => {
    restoreScrollAnchors(anchors)
    frameId = requestAnimationFrame(keepPosition)
  }

  frameId = requestAnimationFrame(keepPosition)
  return () => cancelAnimationFrame(frameId)
}

function transitionDuration() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 180 : props.duration
}

function animateHeight(element, targetHeight, done) {
  const anchors = scrollAnchors(element)
  const releaseScrollAnchor = keepScrollAnchored(anchors)
  const duration = transitionDuration()
  let completed = false

  const finish = () => {
    if (completed) return
    completed = true
    element.removeEventListener('transitionend', onTransitionEnd)
    window.clearTimeout(fallbackTimer)
    releaseScrollAnchor()
    restoreScrollAnchors(anchors)
    done()
  }

  const onTransitionEnd = (event) => {
    if (event.target === element && event.propertyName === 'height') finish()
  }

  element.addEventListener('transitionend', onTransitionEnd)
  const fallbackTimer = window.setTimeout(finish, duration + 80)
  requestAnimationFrame(() => {
    element.style.height = `${targetHeight}px`
  })
}

function beforeEnter(element) {
  element.style.boxSizing = 'border-box'
  element.style.height = '0px'
  element.style.overflow = 'hidden'
  element.style.willChange = 'height'
  element.style.transition = 'none'
}

function enter(element, done) {
  const style = window.getComputedStyle(element)
  const targetHeight = element.scrollHeight
    + Number.parseFloat(style.borderTopWidth)
    + Number.parseFloat(style.borderBottomWidth)
  element.style.transition = `height ${transitionDuration()}ms ${easing}`
  void element.offsetHeight
  animateHeight(element, targetHeight, done)
}

function afterEnter(element) {
  element.style.height = ''
  element.style.overflow = ''
  element.style.willChange = ''
  element.style.transition = ''
}

function beforeLeave(element) {
  element.style.boxSizing = 'border-box'
  element.style.height = `${element.scrollHeight}px`
  element.style.overflow = 'hidden'
  element.style.willChange = 'height'
  element.style.transition = `height ${transitionDuration()}ms ${easing}`
}

function leave(element, done) {
  void element.offsetHeight
  animateHeight(element, 0, done)
}

function afterLeave(element) {
  element.style.height = ''
  element.style.overflow = ''
  element.style.willChange = ''
  element.style.transition = ''
}
</script>

<template>
  <Transition
    :css="false"
    @before-enter="beforeEnter"
    @enter="enter"
    @after-enter="afterEnter"
    @before-leave="beforeLeave"
    @leave="leave"
    @after-leave="afterLeave"
  >
    <slot />
  </Transition>
</template>
