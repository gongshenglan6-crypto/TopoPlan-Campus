import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('knowledge base status filter uses the polished shared dropdown', async () => {
  const [knowledge, dropdown, styles] = await Promise.all([
    source('src/components/admin/AdminKnowledgeDocuments.vue'),
    source('src/components/admin/LogFilterDropdown.vue'),
    source('src/styles.css')
  ])

  assert.match(knowledge, /import LogFilterDropdown/)
  assert.match(knowledge, /<LogFilterDropdown[^>]*v-model="statusFilter"/)
  assert.match(knowledge, /const statusOptions = \[/)
  assert.doesNotMatch(knowledge, /<select v-model="statusFilter"/)
  assert.match(dropdown, /option\.tone/)
  assert.match(dropdown, /admin-log-filter-dot/)
  assert.match(styles, /\.admin-log-filter-menu\s*\{[^}]*padding: 4px 0;/s)
  assert.match(styles, /\.admin-log-filter-menu button\s*\{[^}]*margin: 2px 4px;/s)
  assert.match(styles, /\.admin-log-filter-menu button\s*\{[^}]*border-radius: 6px;/s)
  assert.match(styles, /\.admin-knowledge-filter \.admin-log-filter-trigger\s*\{[^}]*display: flex;/s)
  assert.match(styles, /\.admin-knowledge-filter \.admin-log-filter-menu\s*\{[^}]*position: absolute;/s)
  assert.match(styles, /\.admin-log-filter-dot\.is-ready[^\{]*\{[^}]*background: #22c55e/s)
  assert.match(styles, /\.admin-log-filter-dot\.is-processing[^\{]*\{[^}]*background: #fbbf24/s)
  assert.match(styles, /\.admin-log-filter-dot\.is-failed[^\{]*\{[^}]*background: #ef4444/s)
})
