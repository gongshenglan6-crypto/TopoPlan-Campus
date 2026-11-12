import { onBeforeUnmount, onMounted, ref } from 'vue'

const SIDEBAR_EXPAND_DELAY = 600
const INTRO_COMPLETE_DELAY = 1500
const INTRO_STORAGE_KEY = 'hasPlayedIntroAnimation'

function hasPlayedIntroAnimation() {
  try {
    return window.sessionStorage.getItem(INTRO_STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function saveIntroAnimationState() {
  try {
    window.sessionStorage.setItem(INTRO_STORAGE_KEY, 'true')
  } catch {
    // Storage can be unavailable in privacy-restricted browsing contexts.
  }
}

export function useInitialEntrance() {
  const shouldPlayIntro = !hasPlayedIntroAnimation()
  const sidebarCollapsed = ref(shouldPlayIntro)
  const introPlaying = ref(shouldPlayIntro)
  let sidebarTimer = null
  let finishTimer = null

  onMounted(() => {
    // This sequence belongs to the app mount, never to route or chat changes.
    if (!introPlaying.value) return

    sidebarTimer = window.setTimeout(() => {
      sidebarCollapsed.value = false
    }, SIDEBAR_EXPAND_DELAY)
    finishTimer = window.setTimeout(() => {
      introPlaying.value = false
      saveIntroAnimationState()
    }, INTRO_COMPLETE_DELAY)
  })

  onBeforeUnmount(() => {
    window.clearTimeout(sidebarTimer)
    window.clearTimeout(finishTimer)
  })

  return { introPlaying, sidebarCollapsed }
}
