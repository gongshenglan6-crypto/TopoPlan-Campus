import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('admin dashboard has a final dark-theme layer for new platform surfaces', async () => {
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')

  assert.match(styles, /Admin dark theme finalization/)
  assert.match(styles, /html\[data-theme='dark'\] \.app-shell\.admin-shell \.platform-metric-card[\s\S]*?background: #18181b !important;/)
  assert.match(styles, /html\[data-theme='dark'\] \.app-shell\.admin-shell \.platform-panel[\s\S]*?background: #18181b !important;/)
  assert.match(styles, /html\[data-theme='dark'\] \.app-shell\.admin-shell \.platform-metric-card strong[\s\S]*?color: #f4f4f5 !important;/)
})

test('admin theme toggle remains discoverable in dark mode', async () => {
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')

  assert.match(styles, /html\[data-theme='dark'\] \.app-shell\.admin-shell \.admin-theme-toggle:hover[\s\S]*?background: #27272a;/)
  assert.match(styles, /html\[data-theme='dark'\] \.app-shell\.admin-shell \.admin-theme-toggle:focus-visible[\s\S]*?box-shadow: 0 0 0 3px rgba\(59, 130, 246, 0\.28\);/)
  assert.match(styles, /html\[data-theme='dark'\] \.app-shell\.admin-shell \.platform-segmented-control button\.active[\s\S]*?background: #f4f4f5;/)
})
