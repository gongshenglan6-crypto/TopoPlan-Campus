import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('the admin navigation does not expose the project engineering module', async () => {
  const appSource = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
  const workspaceSource = await readFile(new URL('../src/components/AdminWorkspace.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(appSource, /key: 'project-engineering'/)
  assert.doesNotMatch(appSource, /key: 'data-cleanup'/)
  assert.doesNotMatch(workspaceSource, /AdminProjectReview/)
})
