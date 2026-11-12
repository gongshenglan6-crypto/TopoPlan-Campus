import assert from 'node:assert/strict'
import { access,readFile } from 'node:fs/promises'
import test from 'node:test'
test('topology resource icons use persisted uploads and retain local editor assets',async()=>{
 const page=await readFile(new URL('../src/components/admin/AdminDeviceLibrary.vue',import.meta.url),'utf8')
 for(const icon of ['switch','router','firewall','server'])await access(new URL(`../src/assets/topology/${icon}.svg`,import.meta.url))
 assert.match(page,/\/uploads\/topology-icons\//)
 assert.match(page,/asset: rawIconPath/)
 assert.match(page,/<img v-if="device\.asset"/)
 assert.match(page,/iconPath: device\.iconPath/)
 assert.match(page,/iconHash: device\.iconHash/)
})
