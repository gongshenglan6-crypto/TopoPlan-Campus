<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { BookOpen, Bot, Network, RotateCcw, Save, ShieldCheck, SlidersHorizontal, X } from '@lucide/vue'
import { getSystemRules, saveSystemRules } from '../../api/admin'

const thresholdTypes = [
  { key: 'count', label: '次数', unit: '次', inputType: 'number' },
  { key: 'percentage', label: '百分比', unit: '%', inputType: 'number' },
  { key: 'duration', label: '时间', unit: '秒', inputType: 'number' },
  { key: 'quantity', label: '数量上限', unit: '个', inputType: 'number' }
]

const defaultRuleGroups = [
  {
    id: 'ai-service', label: 'AI服务规则', description: '控制模型调用、失败恢复和资源使用边界。', icon: Bot, tone: 'blue', rules: [
      { id: 'ai-token-warning', label: 'Token使用达到80%时预警', description: '接近当期 Token 配额时提醒管理员关注消耗趋势。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'percentage', value: 80 } },
      { id: 'ai-fallback-model', label: 'AI连续调用失败3次后自动切换备用模型', description: '连续调用失败后切换已配置的备用模型，避免任务中断。', enabled: true, severity: 'critical', configurable: true, threshold: { type: 'count', value: 3 } },
      { id: 'ai-timeout-retry', label: 'AI请求超时自动重试', description: '请求超过响应时间阈值后，按当前策略自动重试。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'duration', value: 60 } },
      { id: 'ai-user-concurrency', label: '单用户AI并发请求限制', description: '限制单个账号同时处理的 AI 请求数量。', enabled: true, severity: 'success', configurable: true, threshold: { type: 'quantity', value: 5 } },
      { id: 'ai-daily-anomaly', label: '单日AI调用异常增长预警', description: '调用量较近期基线异常增长时通知管理员。', enabled: false, severity: 'warning', configurable: true, threshold: { type: 'percentage', value: 150 } }
    ]
  },
  {
    id: 'topology-service', label: '拓扑服务规则', description: '保障拓扑生成任务的稳定性与可控性。', icon: Network, tone: 'indigo', rules: [
      { id: 'topology-retry', label: 'AI拓扑生成失败自动重试', description: '生成失败时自动再次提交任务，减少偶发失败。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'count', value: 2 } },
      { id: 'topology-timeout', label: '拓扑生成超时预警', description: '任务运行超过时限后提示管理员检查服务状态。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'duration', value: 180 } },
      { id: 'topology-node-limit', label: '拓扑节点数量超过限制时提醒', description: '避免过大的生成任务拖慢平台处理队列。', enabled: true, severity: 'success', configurable: true, threshold: { type: 'quantity', value: 300 } },
      { id: 'topology-empty-result', label: 'AI生成结果为空时记录异常', description: '记录无有效结果的任务，便于排查模型与输入问题。', enabled: true, severity: 'critical', configurable: false, threshold: null },
      { id: 'topology-service-stop', label: '拓扑生成服务不可用时自动停止新任务', description: '服务异常期间暂停新任务进入队列，恢复后自动解除。', enabled: true, severity: 'critical', configurable: false, threshold: null }
    ]
  },
  {
    id: 'knowledge-base', label: '知识库规则', description: '管理文档解析、向量化与检索质量保护。', icon: BookOpen, tone: 'teal', rules: [
      { id: 'knowledge-parse-retry', label: '文档解析失败自动重试', description: '解析任务失败后按限定次数再次处理。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'count', value: 3 } },
      { id: 'knowledge-vector-warning', label: '向量化失败预警', description: '向量化异常时提醒管理员检查索引服务。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'count', value: 5 } },
      { id: 'knowledge-rag-rate', label: 'RAG检索失败率超过阈值提醒', description: '检索错误率持续升高时触发质量预警。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'percentage', value: 5 } },
      { id: 'knowledge-file-size', label: '单个文档大小限制', description: '限制上传文件体积，保护解析任务资源。', enabled: true, severity: 'success', configurable: true, threshold: { type: 'quantity', value: 50 } },
      { id: 'knowledge-file-type', label: '不支持的文件类型自动拦截', description: '上传阶段拦截不在解析白名单内的文件类型。', enabled: true, severity: 'critical', configurable: false, threshold: null }
    ]
  },
  {
    id: 'security-system', label: '安全与系统规则', description: '保护账号、接口、审计与数据导出行为。', icon: ShieldCheck, tone: 'amber', rules: [
      { id: 'security-login-failure', label: '连续登录失败5次触发风险提示', description: '短时间内连续失败时标记风险并提示管理员。', enabled: true, severity: 'warning', configurable: true, threshold: { type: 'count', value: 5 } },
      { id: 'security-ip-rate', label: '单IP高频请求自动限流', description: '同一 IP 请求频率异常时自动降低请求速率。', enabled: true, severity: 'critical', configurable: true, threshold: { type: 'quantity', value: 120 } },
      { id: 'security-audit', label: '高风险操作记录审计日志', description: '记录关键配置变更和敏感操作，支持后续追溯。', enabled: true, severity: 'success', configurable: false, threshold: null },
      { id: 'security-service-event', label: '系统服务异常自动记录事件', description: '服务健康检查异常时生成系统事件。', enabled: true, severity: 'warning', configurable: false, threshold: null },
      { id: 'security-export-limit', label: '数据导出频率限制', description: '控制短时间内的数据导出次数，降低滥用风险。', enabled: true, severity: 'critical', configurable: true, threshold: { type: 'count', value: 10 } }
    ]
  }
]

const cloneRules = (groups) => groups.map((group) => ({ ...group, rules: group.rules.map((rule) => ({ ...rule, threshold: rule.threshold ? { ...rule.threshold } : null })) }))
const ruleGroups = ref(cloneRules(defaultRuleGroups))
const toast = ref('')
const thresholdDrawerOpen = ref(false)
const thresholdRule = ref(null)
const thresholdDraft = ref({ type: 'count', value: 1 })
const isDirty = ref(false)
const isSaving = ref(false)
let toastTimer = 0

const enabledCount = computed(() => ruleGroups.value.reduce((total, group) => total + group.rules.filter((rule) => rule.enabled).length, 0))

function showToast(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2800)
}

function ruleType(rule) {
  return thresholdTypes.find((type) => type.key === rule.threshold?.type) || thresholdTypes[0]
}

function formatThreshold(rule) {
  if (!rule.threshold) return ''
  const type = ruleType(rule)
  return `${rule.threshold.value}${type.unit}`
}

function toggleRule(rule) {
  rule.enabled = !rule.enabled
  isDirty.value = true
}

function openThresholdDrawer(rule) {
  thresholdRule.value = rule
  thresholdDraft.value = { ...(rule.threshold || { type: 'count', value: 1 }) }
  thresholdDrawerOpen.value = true
}

function closeThresholdDrawer() {
  thresholdDrawerOpen.value = false
  thresholdRule.value = null
}

function saveThreshold() {
  if (!thresholdRule.value) return
  const value = Math.max(0, Number(thresholdDraft.value.value) || 0)
  thresholdRule.value.threshold = { type: thresholdDraft.value.type, value }
  isDirty.value = true
  closeThresholdDrawer()
  showToast('阈值草稿已更新，请保存规则后生效')
}

function resetDefaults() {
  ruleGroups.value = cloneRules(defaultRuleGroups)
  isDirty.value = true
  showToast('已恢复默认规则，请保存后生效')
}

async function saveSettings() {
  isSaving.value = true
  try {
    await saveSystemRules({ ruleGroups: ruleGroups.value })
    isDirty.value = false
    showToast('规则配置已更新')
  } catch (error) {
    showToast(error.message || '规则配置保存失败')
  } finally {
    isSaving.value = false
  }
}

async function loadSystemRules() {
  try {
    const stored = await getSystemRules()
    if (Array.isArray(stored?.ruleGroups)) {
      ruleGroups.value = cloneRules(stored.ruleGroups)
      isDirty.value = false
    }
  } catch (error) {
    showToast(error.message || '规则配置加载失败')
  }
}

onMounted(loadSystemRules)
onBeforeUnmount(() => window.clearTimeout(toastTimer))
</script>

<template>
  <section class="admin-section-page system-rules-page">
    <header class="system-rules-header">
      <div><p class="system-rules-kicker">平台自动化与资源保护</p><h2>规则设置</h2><p>配置平台运行过程中的自动化规则、异常处理规则和资源保护策略。</p></div>
      <div class="system-rules-engine"><i></i><div><strong>规则引擎运行正常</strong><small>{{ enabledCount }} 条规则已启用</small></div></div>
    </header>

    <section class="system-rules-grid" aria-label="规则分类">
      <article v-for="group in ruleGroups" :key="group.id" class="rule-category-card">
        <header class="rule-category-header"><div class="rule-category-title"><span class="rule-category-icon" :class="`is-${group.tone}`"><component :is="group.icon" :size="17" /></span><div><h3>{{ group.label }}</h3><p>{{ group.description }}</p></div></div><span class="rule-category-count">{{ group.rules.filter((rule) => rule.enabled).length }}/{{ group.rules.length }}</span></header>
        <div class="rule-list">
          <div v-for="rule in group.rules" :key="rule.id" class="rule-row">
            <span class="rule-status" :class="[{ 'is-disabled': !rule.enabled }, `is-${rule.severity}`]" aria-hidden="true"></span>
            <div class="rule-copy"><strong>{{ rule.label }}</strong><small>{{ rule.description }}</small><span v-if="rule.threshold" class="rule-threshold">当前阈值：{{ formatThreshold(rule) }}</span></div>
            <div class="rule-actions"><button v-if="rule.configurable" type="button" class="rule-config-button" @click="openThresholdDrawer(rule)"><SlidersHorizontal :size="13" />配置</button><span class="rule-state">{{ rule.enabled ? '启用' : '停用' }}</span><button type="button" class="rule-switch" :class="{ 'is-active': rule.enabled }" role="switch" :aria-checked="rule.enabled" :aria-label="`切换${rule.label}`" @click="toggleRule(rule)"><span class="rule-switch-thumb" aria-hidden="true"></span></button></div>
          </div>
        </div>
      </article>
    </section>

    <footer class="system-rules-footer"><span v-if="isDirty" class="system-rules-dirty">有未保存的规则变更</span><button type="button" class="system-rules-secondary" @click="resetDefaults"><RotateCcw :size="14" />恢复默认</button><button type="button" class="system-rules-primary" :disabled="!isDirty || isSaving" @click="saveSettings"><Save :size="14" />{{ isSaving ? '保存中…' : '保存规则' }}</button></footer>

    <Teleport to="body">
      <Transition name="system-rules-overlay"><div v-if="thresholdDrawerOpen" class="system-rules-overlay" @pointerdown.self="closeThresholdDrawer"><aside class="threshold-drawer" role="dialog" aria-modal="true" aria-label="配置规则阈值"><header><div><span>规则阈值</span><h3>配置{{ thresholdRule?.label }}</h3></div><button type="button" aria-label="关闭" @click="closeThresholdDrawer"><X :size="18" /></button></header><form @submit.prevent="saveThreshold"><label><span>阈值类型</span><select v-model="thresholdDraft.type"><option v-for="type in thresholdTypes" :key="type.key" :value="type.key">{{ type.label }}</option></select></label><label><span>阈值数值</span><div class="threshold-input"><input v-model.number="thresholdDraft.value" type="number" min="0" step="1" required /><em>{{ ruleType({ threshold: thresholdDraft }).unit }}</em></div></label><p class="threshold-help">保存后会更新当前规则草稿，点击页面底部“保存规则”后正式生效。</p><footer><button type="button" class="system-rules-secondary" @click="closeThresholdDrawer">取消</button><button type="submit" class="system-rules-primary">保存配置</button></footer></form></aside></div></Transition>
    </Teleport>
    <Transition name="system-rules-toast"><p v-if="toast" class="system-rules-toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
.system-rules-page { width: min(100%, 1280px); margin: 0 auto; padding: 28px 32px 34px; }
.system-rules-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }.system-rules-kicker { margin: 0 0 7px; color: #6b7d93; font-size: 12px; }.system-rules-header h2 { margin: 0; color: #172b43; font-size: 28px; line-height: 1.2; font-weight: 750; }.system-rules-header > div > p:last-child { margin: 8px 0 0; color: #6b7c91; font-size: 13px; line-height: 1.6; }.system-rules-engine { display: inline-flex; align-items: center; gap: 10px; min-width: 172px; padding: 10px 13px; border: 1px solid #bfead5; border-radius: 8px; background: #f4fcf8; color: #1d8059; }.system-rules-engine i { width: 8px; height: 8px; border-radius: 50%; background: #20a66b; box-shadow: 0 0 0 4px rgba(32, 166, 107, .12); }.system-rules-engine div { display: grid; gap: 3px; }.system-rules-engine strong { font-size: 12px; }.system-rules-engine small { color: #6c9a84; font-size: 11px; }
.system-rules-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }.rule-category-card { min-width: 0; overflow: hidden; border: 1px solid #dce6f0; border-radius: 10px; background: #fff; box-shadow: 0 6px 20px rgba(22, 49, 83, .05); }.rule-category-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 18px 18px 15px; border-bottom: 1px solid #edf2f6; background: #fcfdff; }.rule-category-title { display: flex; min-width: 0; align-items: flex-start; gap: 10px; }.rule-category-title > div { min-width: 0; }.rule-category-icon { display: grid; width: 30px; height: 30px; flex: 0 0 auto; place-items: center; border-radius: 7px; }.rule-category-icon.is-blue { background: #eaf3ff; color: #3178c7; }.rule-category-icon.is-indigo { background: #eef0ff; color: #596bd1; }.rule-category-icon.is-teal { background: #e8f8f6; color: #238c85; }.rule-category-icon.is-amber { background: #fff4e3; color: #b77b24; }.rule-category-header h3 { margin: 1px 0 4px; color: #233850; font-size: 16px; font-weight: 720; }.rule-category-header p { margin: 0; color: #78899c; font-size: 12px; line-height: 1.45; }.rule-category-count { flex: 0 0 auto; padding: 3px 7px; border: 1px solid #dce6ef; border-radius: 5px; color: #7890a6; font-size: 11px; }
.rule-list { min-width: 0; padding: 0 18px; }.rule-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 82px; padding: 13px 0; border-bottom: 1px solid #edf2f6; }.rule-row:last-child { border-bottom: 0; }.rule-status { width: 7px; height: 7px; flex: 0 0 auto; border-radius: 50%; background: #94a3b8; box-shadow: 0 0 0 3px rgba(148, 163, 184, .12); }.rule-status.is-success { background: #20a66b; box-shadow: 0 0 0 3px rgba(32, 166, 107, .12); }.rule-status.is-warning { background: #e39a2f; box-shadow: 0 0 0 3px rgba(227, 154, 47, .12); }.rule-status.is-critical { background: #db5a54; box-shadow: 0 0 0 3px rgba(219, 90, 84, .12); }.rule-status.is-disabled { background: #a7b2bf; box-shadow: none; }.rule-copy { display: grid; min-width: 0; flex: 1 1 auto; gap: 4px; }.rule-copy strong { color: #344962; font-size: 13px; line-height: 1.4; font-weight: 650; }.rule-copy small { color: #8392a3; font-size: 11px; line-height: 1.45; }.rule-threshold { color: #537aa4; font-size: 11px; }.rule-actions { display: flex; align-items: center; flex: 0 0 auto; gap: 7px; }.rule-config-button { display: inline-flex; align-items: center; gap: 3px; padding: 4px 5px; border: 0; border-radius: 4px; background: transparent; color: #5080b0; font-size: 11px; }.rule-config-button:hover { background: #edf5ff; color: #2367a8; }.rule-state { width: 26px; color: #8695a5; font-size: 10px; text-align: right; }.rule-switch { position: relative; width: 36px; height: 22px; flex: 0 0 auto; padding: 2px; border: 0; border-radius: 999px; background: #d9e1e9; cursor: pointer; transition: background-color 160ms ease; }.rule-switch:hover { background: #c7d2dd; }.rule-switch:focus-visible { outline: 3px solid rgba(37, 99, 235, .18); outline-offset: 2px; }.rule-switch.is-active { background: #1f2937; }.rule-switch.is-active:hover { background: #111827; }.rule-switch-thumb { display: block; width: 18px; height: 18px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(17, 24, 39, .22); transform: translateX(0); transition: transform 160ms ease; }.rule-switch.is-active .rule-switch-thumb { transform: translateX(14px); }
.system-rules-footer { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 18px; }.system-rules-dirty { margin-right: auto; color: #9a6e2c; font-size: 12px; }.system-rules-secondary,.system-rules-primary { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 36px; padding: 0 14px; border-radius: 7px; font-size: 13px; font-weight: 650; cursor: pointer; }.system-rules-secondary { border: 1px solid #d7e2ed; background: #fff; color: #526a83; }.system-rules-secondary:hover { background: #f5f8fb; }.system-rules-primary { border: 1px solid #1f5fa9; background: #236abd; color: #fff; }.system-rules-primary:hover:not(:disabled) { background: #1d5a9f; }.system-rules-primary:disabled { cursor: not-allowed; opacity: .5; }.system-rules-toast { position: fixed; right: 24px; bottom: 24px; z-index: 130; margin: 0; padding: 11px 15px; border: 1px solid #314b69; border-radius: 8px; background: #173864; color: #fff; font-size: 13px; box-shadow: 0 10px 24px rgba(17, 38, 65, .2); }
.system-rules-overlay { position: fixed; z-index: 120; inset: 0; display: flex; justify-content: flex-end; background: rgba(20, 35, 55, .32); }.threshold-drawer { display: flex; width: min(420px, 100vw); flex-direction: column; padding: 22px; background: #fff; box-shadow: -16px 0 36px rgba(17, 37, 63, .16); }.threshold-drawer > header { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 18px; border-bottom: 1px solid #e5edf4; }.threshold-drawer > header span { color: #7d8da0; font-size: 12px; }.threshold-drawer h3 { margin: 5px 0 0; color: #263b55; font-size: 18px; line-height: 1.35; }.threshold-drawer > header button { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: #68798e; }.threshold-drawer form { display: grid; gap: 18px; padding-top: 22px; }.threshold-drawer label { display: grid; gap: 7px; }.threshold-drawer label > span { color: #52647b; font-size: 12px; font-weight: 700; }.threshold-drawer select,.threshold-drawer input { width: 100%; height: 40px; box-sizing: border-box; border: 1px solid #d5e0ea; border-radius: 6px; outline: 0; background: #fff; color: #263b55; font-size: 13px; }.threshold-drawer select,.threshold-drawer input { padding: 0 10px; }.threshold-drawer select:focus,.threshold-drawer input:focus { border-color: #3e85d8; box-shadow: 0 0 0 3px rgba(62, 133, 216, .12); }.threshold-input { display: flex; align-items: center; gap: 8px; }.threshold-input input { flex: 1; }.threshold-input em { min-width: 34px; color: #8392a3; font-size: 12px; font-style: normal; }.threshold-help { margin: 0; color: #8190a1; font-size: 12px; line-height: 1.55; }.threshold-drawer footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: auto; padding-top: 22px; }.system-rules-overlay-enter-active,.system-rules-overlay-leave-active { transition: opacity 180ms ease; }.system-rules-overlay-enter-active .threshold-drawer,.system-rules-overlay-leave-active .threshold-drawer { transition: transform 220ms ease; }.system-rules-overlay-enter-from,.system-rules-overlay-leave-to { opacity: 0; }.system-rules-overlay-enter-from .threshold-drawer,.system-rules-overlay-leave-to .threshold-drawer { transform: translateX(100%); }
:global(html[data-theme='dark']) .system-rules-page,:global(html[data-theme='dark']) .threshold-drawer { color: #dce7f3; }:global(html[data-theme='dark']) .system-rules-header h2,:global(html[data-theme='dark']) .rule-category-header h3,:global(html[data-theme='dark']) .rule-copy strong,:global(html[data-theme='dark']) .threshold-drawer h3 { color: #edf3fb; }:global(html[data-theme='dark']) .system-rules-header > div > p:last-child,:global(html[data-theme='dark']) .rule-category-header p,:global(html[data-theme='dark']) .rule-copy small,:global(html[data-theme='dark']) .threshold-help { color: #9caec2; }:global(html[data-theme='dark']) .rule-category-card,:global(html[data-theme='dark']) .threshold-drawer { border-color: #304158; background: #172333; }:global(html[data-theme='dark']) .rule-category-header { border-color: #2c3b4f; background: #152232; }:global(html[data-theme='dark']) .rule-row { border-color: #2c3b4f; }:global(html[data-theme='dark']) .rule-config-button:hover { background: #1b365b; }:global(html[data-theme='dark']) .rule-category-count,:global(html[data-theme='dark']) .threshold-drawer select,:global(html[data-theme='dark']) .threshold-drawer input { border-color: #35475d; background: #1b2939; color: #e5edf7; }
@media (max-width: 980px) { .system-rules-grid { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .system-rules-page { padding: 22px 14px 28px; }.system-rules-header { display: grid; gap: 14px; }.system-rules-engine { width: max-content; }.rule-category-card { border-radius: 8px; }.rule-category-header,.rule-list { padding-inline: 14px; }.rule-row { align-items: flex-start; }.rule-actions { flex-direction: column; gap: 5px; }.rule-state { width: auto; }.system-rules-footer { flex-wrap: wrap; }.system-rules-dirty { width: 100%; margin-right: 0; }.system-rules-secondary,.system-rules-primary { flex: 1; }.threshold-drawer { width: 100%; } }
</style>
