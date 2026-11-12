import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
const source=p=>readFile(new URL(`../${p}`,import.meta.url),'utf8')
test('unified system page polls without inventing a last-success timestamp',async()=>{
 const page=await source('src/components/admin/AdminSystemManagement.vue')
 for(const pattern of [/const REFRESH_INTERVAL = 1_000/,/window\.setInterval\(scheduleMonitoringRefresh, REFRESH_INTERVAL\)/,/const lastUpdatedAt = ref\(null\)/,/lastUpdatedAt\.value = data\?\.checkedAt \|\| lastUpdatedAt\.value/,/if \(pollingInFlight\.value\) return/,/pollingInFlight\.value = false/,/NOT_CONFIGURED: '未配置'/,/window\.clearInterval\(refreshTimer\)/])assert.match(page,pattern)
})
test('monitoring exposes actual application services and persists state changes',async()=>{
 const service=await source('../backend/demo1/src/main/java/com/agent/admin/config/SystemMonitoringService.java')
 for(const name of ['api-service','mysql','ai-service','topology-service','file-storage'])assert.ok(service.includes(`"${name}"`),name)
 assert.match(service,/if \(statusChanged\)/)
 assert.match(service,/PERSIST_TTL = Duration\.ofSeconds\(30\)/)
 assert.match(service,/NOT_CONFIGURED/)
})
