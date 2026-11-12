import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('AI 引擎的其余子页面复用 Prompt 管理的固定视口与局部滚动边界', async () => {
  const [app, workspace, styles] = await Promise.all([
    source('src/App.vue'),
    source('src/components/AdminWorkspace.vue'),
    source('src/styles.css')
  ])

  assert.match(app, /const isAdminAiEnginePage = computed/)
  assert.match(app, /\['ai-models', 'prompts'\]/)
  assert.match(app, /classList\.toggle\('is-admin-ai-engine-page', isLocked\)/)
  assert.match(app, /'ai-engine-page-active': isAdminAiEnginePage/)
  assert.match(workspace, /'is-ai-engine-page': modelPages\.includes\(activePage\)/)
  assert.match(styles, /html\.is-admin-ai-engine-page,[\s\S]*?overflow:\s*hidden;/)
  assert.match(styles, /\.admin-workspace\.is-ai-engine-page\s*\{[\s\S]*?height:\s*calc\(100vh - 68px\);/)
  assert.match(styles, /\.admin-model-api-table-wrap\s*\{[\s\S]*?overflow:\s*auto;/)
})
