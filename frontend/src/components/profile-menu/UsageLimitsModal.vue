<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowUpRight, CircleAlert, LoaderCircle, Sparkles } from '@lucide/vue'
import ModalShell from './ModalShell.vue'
import { getUserSubscription } from '../../api/user'

const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close', 'upgrade'])
const subscription = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const quotaKeys = [
  ['aiChat', 'AI对话', '次'],
  ['tokens', 'Token使用', 'Token'],
  ['topologyGeneration', 'AI拓扑生成', '次'],
  ['knowledgeQa', '知识库AI问答', '次'],
  ['networkPlan', 'AI方案生成', '次']
]
const usage = computed(() => quotaKeys.map(([key, label, unit]) => {
  const total = Number(subscription.value?.quota?.[key]?.total ?? 0)
  const used = Number(subscription.value?.usage?.[key]?.used ?? 0)
  const remaining = Number(subscription.value?.remaining?.[key]?.value ?? Math.max(0, total - used))
  return { key, label, total, used, remaining, unit }
}).filter((item) => item.total > 0))
function percent(item) {
  return `${item.total > 0 ? Math.min(100, Math.round((item.used / item.total) * 100)) : 0}%`
}

function upgrade() {
  emit('close')
  emit('upgrade')
}

function formatValue(value) {
  const number = Number(value || 0)
  if (number >= 1000000) return `${(number / 1000000).toFixed(number % 1000000 ? 1 : 0)}M`
  if (number >= 1000) return `${(number / 1000).toFixed(number % 1000 ? 1 : 0)}K`
  return number.toLocaleString('zh-CN')
}

function formatReset(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

async function loadUsage() {
  loading.value = true
  errorMessage.value = ''
  try { subscription.value = await getUserSubscription() } catch (error) { subscription.value = null; errorMessage.value = error.message || '额度信息加载失败' } finally { loading.value = false }
}

watch(() => props.open, (open) => { if (open) loadUsage() })
</script>

<template>
  <ModalShell :open="open" title="AI 使用额度" description="已用、总额度和重置时间均来自当前套餐与真实调用记录。" @close="$emit('close')">
    <div v-if="loading" class="profile-usage-state"><LoaderCircle :size="18" /> 正在读取额度...</div>
    <div v-else-if="errorMessage" class="profile-usage-state is-error"><CircleAlert :size="18" /> {{ errorMessage }}</div>
    <div v-else class="profile-usage-list">
      <article v-for="item in usage" :key="item.label" class="profile-usage-row">
        <div class="profile-usage-heading"><strong>{{ item.label }}</strong><span>{{ formatValue(item.used) }} / {{ formatValue(item.total) }} {{ item.unit }}</span></div>
        <div class="profile-usage-meter"><span :style="{ width: percent(item) }"></span></div>
        <p>本周期剩余 {{ formatValue(item.remaining) }} {{ item.unit }}</p>
      </article>
      <p v-if="!usage.length" class="profile-usage-empty">当前套餐尚未配置可用的 AI 额度。</p>
    </div>
    <p v-if="subscription" class="profile-usage-reset">下次额度重置：{{ formatReset(subscription.nextResetAt) }}</p>
    <button class="profile-upgrade-action" type="button" @click="upgrade"><Sparkles :size="17" /> 查看套餐 <ArrowUpRight :size="16" /></button>
  </ModalShell>
</template>

<style scoped>
.profile-usage-state { display:flex; align-items:center; justify-content:center; gap:8px; min-height:150px; color:#64748b; font-size:13px; }.profile-usage-state :deep(svg) { animation:spin .8s linear infinite; }.profile-usage-state.is-error { color:#b45309; }.profile-usage-state.is-error :deep(svg) { animation:none; }.profile-usage-reset,.profile-usage-empty { margin:12px 0 0; color:#64748b; font-size:12px; }.profile-usage-empty { text-align:center; }@keyframes spin { to { transform:rotate(360deg); } }
</style>
