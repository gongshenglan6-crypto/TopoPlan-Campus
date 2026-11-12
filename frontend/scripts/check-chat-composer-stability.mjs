import { readFile } from 'node:fs/promises'

const appSource = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')
const workspaceTag = appSource.match(/<UserWorkspace\b[\s\S]*?\/>/)

if (!workspaceTag) {
  throw new Error('UserWorkspace must remain mounted by App.vue.')
}

if (/:key="[^"]*activeChatId/.test(workspaceTag[0])) {
  throw new Error('UserWorkspace must not be keyed by activeChatId because it remounts ChatComposer on session switches.')
}

console.log('ChatComposer mount-stability check passed.')
