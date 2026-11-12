import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('Prompt 编辑器正文在所有主题下保持深色编辑器的高对比度文字', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminPromptStudio.vue', import.meta.url), 'utf8')

  assert.match(source, /\.admin-prompt-editor-frame textarea\s*\{[\s\S]*?color:\s*#f8fafc !important;/)
  assert.match(source, /caret-color:\s*#f8fafc;/)
})
