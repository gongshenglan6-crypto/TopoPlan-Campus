import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('../src/components/admin/AdminModelApi.vue', import.meta.url), 'utf8')
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8')

test('model API editor exposes four focused configuration tabs', () => {
  for (const label of ['基础配置', '模型参数', '速率与并发', '计费与成本']) {
    assert.match(source, new RegExp(label))
  }
  assert.match(source, /activeModalTab/)
})

test('model API draft keeps identity, parameter, concurrency, and billing fields', () => {
  for (const field of [
    'modelId',
    'contextLimit',
    'maxTokens',
    'temperature',
    'topP',
    'maxConcurrency',
    'timeout',
    'inputPrice',
    'outputPrice',
    'quotaMultiplier'
  ]) {
    assert.match(source, new RegExp(`${field}:`))
  }
})

test('model API editor supports key visibility, copy, URL validation, and saving state', () => {
  assert.match(source, /apiKeyVisible/)
  assert.match(source, /copyApiKey/)
  assert.match(source, /isValidBaseUrl/)
  assert.match(source, /isSaving/)
  assert.match(source, /<form ref="modelApiModal" novalidate/)
  assert.match(source, /function showFieldError\(tab, message\)[\s\S]*showToast\(message\)/)
  assert.match(source, /type="submit"[^>]*:disabled="isSaving"/)
  assert.match(source, /showToast\('Model channel configuration saved\.'\)\s+isSaving\.value = false\s+closeModal\(\)/)
})

test('model API modal keeps its shared rounded clipping on all four corners', () => {
  assert.match(styles, /\.admin-modal-card\.admin-model-api-modal[^}]*overflow: hidden[^}]*border-radius: 16px/)
  assert.doesNotMatch(styles, /\.admin-modal-card\.admin-model-api-modal,\.admin-model-api-form \{ overflow: visible; \}/)
})

test('model type control uses the custom dropdown instead of the native select', () => {
  assert.doesNotMatch(source, /<select v-model="draft\.modelType"/)
  assert.match(source, /class="admin-model-type-select"/)
  assert.match(source, /class="admin-model-type-trigger"/)
  assert.match(source, /role="listbox"/)
  assert.match(source, /role="option"/)
  assert.match(source, /<ChevronDown/)
  assert.match(source, /<Check v-if="draft\.modelType === type\.key"/)
  assert.match(source, /handleDocumentPointerDown/)
  assert.match(styles, /\.admin-model-type-menu[^}]*top: calc\(100% \+ 5px\)/)
  assert.match(styles, /\.admin-model-type-option\[aria-selected='true'\][^}]*background: #eff6ff/)
})

test('model API tooltips are hover-only, delayed, and positioned within the modal portal', () => {
  for (const copy of [
    '模型所属的服务提供商，例如 OpenAI、DeepSeek、阿里云等。',
    '调用 API 时实际使用的模型标识，例如 gpt-4.1。',
    '仅用于管理后台展示，不影响实际 API 调用。',
    '模型服务的 HTTP 或 HTTPS API 根地址。',
    '用于调用模型服务的身份认证密钥，请妥善保管。'
  ]) {
    assert.match(source, new RegExp(copy))
  }
  assert.match(source, /<Teleport to="body">/)
  assert.match(source, /function positionTooltip\(button\)/)
  assert.match(source, /const placement = canShowAbove \|\| !canShowBelow \? 'top' : 'bottom'/)
  assert.match(source, /const left = clamp\(targetRect\.left, modalRect\.left \+ margin, modalRect\.right - popoverRect\.width - margin\)/)
  assert.match(source, /window\.setTimeout\(\(\) => showTooltip\(button\), 240\)/)
  assert.match(source, /@pointerover="handleTooltipPointerOver"/)
  assert.match(source, /@pointerout="handleTooltipPointerOut"/)
  assert.match(source, /maxWidth: Math\.min\(260, modalRect\.width - 16\)/)
  assert.match(styles, /\.admin-model-tooltip-popover \{ position: fixed; z-index: 1600/)
  assert.match(styles, /\.admin-model-tooltip-popover\.is-bottom::after/)
  assert.doesNotMatch(styles, /\.admin-model-tooltip:hover::after/)
})

test('model category tabs keep the active tab readable in the light theme', () => {
  assert.match(source, /class="admin-model-category-tabs"/)
  assert.match(source, /:aria-selected="activeModelCategory === tab\.key"/)
  assert.match(styles, /\.admin-model-category-tabs button\.active[^}]*background: #f3f4f6/)
  assert.match(styles, /\.admin-model-category-tabs button\.active[^}]*color: #111827/)
  assert.match(styles, /\.admin-model-category-tabs button\.active[^}]*font-weight: 600/)
  assert.match(styles, /\.admin-model-category-tabs button\.active[^}]*border: 0/)
  assert.match(styles, /\.admin-model-category-tabs button:hover:not\(\.active\)[^}]*background: #f4f4f5/)
  assert.match(styles, /\.admin-model-category-tabs button:hover:not\(\.active\)[^}]*color: #111827/)
})

test('model status updates keep a fixed table and status slot while the request is in flight', () => {
  assert.match(source, /<colgroup>[\s\S]*admin-model-api-col-status[\s\S]*admin-model-api-col-actions[\s\S]*<\/colgroup>/)
  assert.match(source, /class="admin-model-status-cell"/)
  assert.match(source, /class="admin-model-switch-track"/)
  assert.match(source, /class="admin-model-switch-spinner-slot"/)
  assert.match(source, /class="admin-model-switch-label"/)
  assert.match(styles, /\.admin-model-api-table \{[^}]*table-layout: fixed/)
  assert.match(styles, /\.admin-model-api-table \.admin-model-api-col-status \{ width: 156px/)
  assert.match(styles, /\.admin-model-switch \{[^}]*grid-template-columns: 28px 42px[^}]*width: 98px/)
  assert.match(styles, /\.admin-model-switch-spinner-slot \{[^}]*position: absolute[^}]*right: 0/)
  assert.match(styles, /\.app-shell\.admin-shell\.ai-engine-page-active \.admin-model-api-table-wrap \{[\s\S]*?scrollbar-gutter: stable/)
})

test('model tests expose persisted state and a detailed success or failure dialog', () => {
  for (const expectation of [
    'lastTestStatus',
    'lastTestLatency',
    'lastTestErrorCode',
    '连接成功',
    '连接失败',
    '错误类型',
    '输入 Token',
    '输出 Token',
    'function testStatusLabel',
    'function closeTestResult'
  ]) assert.match(source, new RegExp(expectation))

  assert.match(source, /modelId\.includes\('embedding'\).*return 'embedding'/s)
  assert.match(source, /if \(type === 'embedding'\) return \['knowledge-embedding'\]/)
  assert.match(source, /if \(type === 'search'\) return \['web-search'\]/)
})
