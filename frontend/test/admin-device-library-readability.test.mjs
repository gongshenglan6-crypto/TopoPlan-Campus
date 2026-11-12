import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
const source=()=>readFile(new URL('../src/components/admin/AdminDeviceLibrary.vue',import.meta.url),'utf8')
test('拓扑资源页格式化 ISO 更新时间并保留表格时间语义',async()=>{
 const page=await source()
 for(const p of [/function formatUpdatedAt\(value\)/,/\$\{match\[1\]\} \$\{match\[2\]\}/,/class="topology-assets-updated-at"/,/formatUpdatedAt\(device\.updatedAt\)/])assert.match(page,p)
})
test('拓扑资源页使用清晰的亮色文字与表格配色',async()=>{
 const page=await source()
 for(const p of [/\.topology-assets-intro h2\s*\{[^}]*color: #111827;/,/\.topology-assets-stats strong\s*\{[^}]*font-size: 28px;/,/\.topology-assets-table th\s*\{[^}]*color: #475569;/,/\.topology-assets-tabs button\s*\{[^}]*font-weight: 600;/,/\.topology-assets-table \.admin-table-action\s*\{[^}]*color: #2563eb;/])assert.match(page,p)
})
test('亮色统计不被深色主题覆盖，深色文字有独立限定',async()=>{
 const page=await source()
 assert.doesNotMatch(page,/(?:^|[;}])\s*\.topology-assets-stats article strong,\s*:global\(html\[data-theme='dark'\]\)/)
 assert.match(page,/:global\(html\[data-theme='dark'\]\) \.topology-assets-stats article strong,\s*:global\(html\[data-theme='dark'\]\) \.topology-assets-table td:nth-child\(2\) strong\s*\{ color: #f1f5f9;/)
})
