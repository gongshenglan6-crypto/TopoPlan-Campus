import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('the admin sidebar uses a stable header and decorative active indicator', async () => {
  const [styles, sidebar] = await Promise.all([
    readFile(new URL('../src/styles.css', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/SidebarNav.vue', import.meta.url), 'utf8')
  ])

  assert.match(styles, /Admin sidebar header alignment and horizontal collapse contract/)
  assert.match(styles, /\.admin-shell \.sidebar::before\s*\{\s*content: none;/)
  assert.match(styles, /\.admin-shell \.sidebar-brand\s*\{[\s\S]*?height: 64px;[\s\S]*?min-height: 64px;/)
  assert.match(styles, /\.admin-shell \.sidebar-nav-icon\s*\{[\s\S]*?flex: 0 0 28px;[\s\S]*?width: 28px;[\s\S]*?min-width: 28px;/)
  assert.match(styles, /\.admin-shell \.nav-item\.active::before\s*\{[\s\S]*?transform: translateY\(-50%\) scaleY\(1\);/)
  assert.match(sidebar, /class="menu-item-left"[\s\S]*?sidebar-nav-icon[\s\S]*?sidebar-label/)
  assert.match(styles, /\.admin-shell \.menu-item-left\s*\{[\s\S]*?display: flex;[\s\S]*?gap: 10px;[\s\S]*?text-align: left;/)
})
