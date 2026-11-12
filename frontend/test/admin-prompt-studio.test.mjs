import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('Prompt 编辑器以 currentPrompt 的标题和版本驱动可编辑 Header', async () => {
  const studio = await source('src/components/admin/AdminPromptStudio.vue')

  assert.match(studio, /const currentPrompt = computed/)
  assert.match(studio, /v-model="currentPrompt\.title"/)
  assert.match(studio, /v-model="currentPrompt\.version"/)
  assert.match(studio, /class="admin-prompt-version-input"/)
  assert.match(studio, /<style scoped>/)
  assert.match(studio, /font-size:\s*24px/)
  assert.match(studio, /border-bottom-color:\s*#d1d5db/)
})

test('Prompt 左侧列表展示共享标题并提高激活态与 Draft 标签的对比度', async () => {
  const sidebar = await source('src/components/admin/PromptSidebar.vue')

  assert.match(sidebar, /template\.title \?\? template\.name/)
  assert.match(sidebar, /'is-draft':\s*String\(template\.version\)\.toLowerCase\(\) === 'draft'/)
  assert.match(sidebar, /<style scoped>/)
  assert.match(sidebar, /background-color:\s*#f3f4f6/)
  assert.match(sidebar, /color:\s*#111827/)
  assert.match(sidebar, /color:\s*#4b5563/)
  assert.match(sidebar, /background:\s*#e0f2fe/)
  assert.match(sidebar, /color:\s*#0369a1/)
})
