import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('billing and shared admin tables preserve high contrast in light mode', async () => {
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')

  assert.match(styles, /Admin billing and table contrast normalization/)
  assert.match(styles, /html:not\(\[data-theme='dark'\]\) \.admin-billing-table th[\s\S]*?background: #f9fafb;/)
  assert.match(styles, /html:not\(\[data-theme='dark'\]\) \.admin-billing-table th[\s\S]*?color: #374151;/)
  assert.match(styles, /html:not\(\[data-theme='dark'\]\) \.admin-tier\.is-standard[\s\S]*?border-color: rgba\(187, 247, 208, 0\.9\);/)
  assert.match(styles, /html:not\(\[data-theme='dark'\]\) \.admin-billing-search:focus-within[\s\S]*?border-color: #18181b;/)
})
