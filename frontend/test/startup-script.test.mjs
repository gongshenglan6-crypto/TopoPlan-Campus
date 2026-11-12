import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import test from 'node:test'

 test('one-click entry resolves its companion script relative to its own location', async () => {
 const batch = await readFile(new URL('../../启动TopoPlan.bat', import.meta.url), 'utf8')
 assert.match(batch, /-File "%~dp0start-topoplan\.ps1"/)
 })
 test('PowerShell launcher parses without executing services', () => {
 const file = new URL('../../start-topoplan.ps1', import.meta.url)
 const command = "$errorsFound=$null; [System.Management.Automation.Language.Parser]::ParseFile($args[0],[ref]$null,[ref]$errorsFound) | Out-Null; if($errorsFound.Count){$errorsFound | Out-String | Write-Error; exit 1}"
 // Read the path through an environment variable to preserve spaces and Unicode.
 const r = spawnSync('powershell.exe', ['-NoProfile', '-Command', command.replace('$args[0]', '$env:TOPO_TEST_LAUNCHER')], { encoding: 'utf8', env: { ...process.env, TOPO_TEST_LAUNCHER: decodeURIComponent(file.pathname).replace(/^\//, '') } })
 assert.equal(r.status, 0, r.stderr || r.error?.message)
 })
