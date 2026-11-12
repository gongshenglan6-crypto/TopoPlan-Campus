import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('topology resources model public assets and AI eligibility', async () => {
  const source = await readFile(new URL('../src/components/admin/AdminDeviceLibrary.vue', import.meta.url), 'utf8')

  for (const field of ['englishName', 'allowAi', '排序', '启用', '停用', '允许 AI 使用', '网络设备', '安全设备', '服务器与存储']) {
    assert.match(source, new RegExp(field))
  }
  assert.match(source, /enabled && .*allowAi|allowAi && .*enabled/)
  assert.doesNotMatch(source, /用户拓扑|查看项目|查看方案/)
})
