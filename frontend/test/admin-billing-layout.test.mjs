import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('resource overview keeps statistics and charts in a centered responsive page', async () => {
 const page=await readFile(new URL('../src/components/admin/AdminBillingQuota.vue',import.meta.url),'utf8')
 // The redesigned resource overview replaced the old cost dashboard.
 for(const name of ['resource-statistics-page','resource-statistics-intro','resource-statistics-grid','resource-statistics-main-grid','resource-statistics-bottom-grid']) assert.ok(page.includes(name),name)
 assert.match(page,/\.admin-billing-page\s*\{[^}]*width:\s*min\(100%, 1280px\);[^}]*margin:\s*0 auto;/)
 assert.match(page,/@media \(max-width: 900px\)[\s\S]*\.resource-statistics-main-grid,\.resource-statistics-bottom-grid\s*\{\s*grid-template-columns:\s*1fr;/)
})
