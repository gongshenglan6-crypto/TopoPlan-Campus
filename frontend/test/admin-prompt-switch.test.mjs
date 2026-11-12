import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('智能路由开关的机器人图标不会继承说明文字的偏移样式', async () => {
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')

  assert.match(styles, /\.admin-prompt-switch-row > div > span\s*\{[^}]*margin-top:\s*4px;/)
  assert.match(styles, /\.admin-prompt-switch > span\s*\{[^}]*margin:\s*0;/)
})
