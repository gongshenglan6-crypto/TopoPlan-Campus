import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
const source=p=>readFile(new URL(`../${p}`,import.meta.url),'utf8')
test('system navigation and route mount the unified management console',async()=>{
 const [app,workspace,page]=await Promise.all([source('src/App.vue'),source('src/components/AdminWorkspace.vue'),source('src/components/admin/AdminSystemManagement.vue')])
 assert.match(app,/key: 'system-management',\s*label: '系统管理'/)
 assert.doesNotMatch(app,/label: '系统日志'|label: '服务监控'/)
 assert.match(workspace,/<AdminSystemManagement v-else-if="activePage === 'system-management'"/)
 for(const text of ['系统规则','服务状态','最近系统日志','系统运行概览','管理规则','查看全部'])assert.ok(page.includes(text),text)
})
test('monitoring endpoint delegates to real cached health probes',async()=>{
 const [controller,service]=await Promise.all([source('../backend/demo1/src/main/java/com/agent/admin/config/AdminModuleController.java'),source('../backend/demo1/src/main/java/com/agent/admin/config/SystemMonitoringService.java')])
 assert.match(controller,/monitoring\.snapshot/)
 for(const check of ['probeApi','probeMysql','probeAi','probeInternalModule','probeFileStorage','countTodayLogs','countTodayErrors'])assert.ok(service.includes(check),check)
 assert.match(service,/synchronized \(snapshotLock\)/)
 assert.match(service,/SNAPSHOT_TTL = Duration\.ofSeconds\(1\)/)
 assert.match(service,/result\.put\("metrics"/)
 assert.doesNotMatch(service,/result\.put\("recentEvents"/)
})
