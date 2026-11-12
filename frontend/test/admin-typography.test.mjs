import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('admin typography system is scoped and preserves the approved hierarchy', async () => {
  const [styles, main] = await Promise.all([
    source('src/styles/typography.css'),
    source('src/main.js')
  ])

  assert.match(main, /import '\.\/styles\/typography\.css'/)
  assert.match(styles, /\.admin-shell\s*\{[\s\S]*?font-family:/)
  assert.match(styles, /\.admin-type-page-title\s*\{[\s\S]*?font-size: 24px;[\s\S]*?font-weight: 600;[\s\S]*?color: #111827;/)
  assert.match(styles, /\.admin-type-card-title\s*\{[\s\S]*?font-size: 18px;[\s\S]*?font-weight: 600;[\s\S]*?color: #111827;/)
  assert.match(styles, /\.admin-type-label\s*\{[\s\S]*?font-size: 14px;[\s\S]*?font-weight: 500;[\s\S]*?color: #374151;/)
  assert.match(styles, /\.admin-type-table-header\s*\{[\s\S]*?font-size: 12px;[\s\S]*?font-weight: 600;[\s\S]*?color: #6b7280;/)
  assert.match(styles, /\.admin-shell input,[\s\S]*?\.admin-shell select\s*\{[\s\S]*?font-size: 14px;[\s\S]*?color: #111827;/)
  assert.doesNotMatch(styles, /font-size:\s*(?:[0-9]|1[01])px;/)
})
