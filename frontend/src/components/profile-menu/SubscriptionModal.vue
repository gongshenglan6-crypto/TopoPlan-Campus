<script setup>
import { computed, ref, watch } from 'vue'
import { Check, CircleAlert, LoaderCircle, Sparkles } from '@lucide/vue'
import ModalShell from './ModalShell.vue'
import { getUserSubscription, listUserPlans } from '../../api/user'

const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
const subscription = ref(null)
const plans = ref([])
const loading = ref(false)
const errorMessage = ref('')
const notice = ref('')

const currentLevel = computed(() => subscription.value?.level || '')
const isFreePlan = computed(() => currentLevel.value === 'FREE')

function formatDate(value) {
  if (!value) return '长期有效'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString('zh-CN')
}

function planBenefits(plan) {
  return plan?.benefits?.items ?? []
}

function requestUpgrade() {
  notice.value = '订阅升级功能暂未开放，请联系管理员处理。'
}

async function loadSubscription() {
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const [nextSubscription, nextPlans] = await Promise.all([getUserSubscription(), listUserPlans()])
    subscription.value = nextSubscription
    plans.value = nextPlans
  } catch (error) {
    subscription.value = null
    plans.value = []
    errorMessage.value = error.message || '订阅信息加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) loadSubscription()
  }
)
</script>

<template>
  <ModalShell :open="open" wide title="我的订阅" description="套餐权益和 AI 额度由后台套餐配置实时提供。" @close="$emit('close')">
    <div v-if="loading" class="profile-subscription-state"><LoaderCircle :size="18" /> 正在读取订阅信息...</div>
    <div v-else-if="errorMessage" class="profile-subscription-state is-error"><CircleAlert :size="18" /> {{ errorMessage }}</div>
    <template v-else-if="subscription">
      <section class="profile-current-subscription">
        <div><span>当前套餐</span><strong>{{ subscription.planName }}</strong><p :class="{ suspended: subscription.status !== 'ACTIVE' }">● {{ subscription.status === 'ACTIVE' ? '生效中' : '已暂停' }}</p></div>
        <dl><div><dt>订阅开始</dt><dd>{{ formatDate(subscription.startAt) }}</dd></div><div><dt>有效期至</dt><dd>{{ isFreePlan ? '长期有效' : formatDate(subscription.expireAt) }}</dd></div><div><dt>额度周期</dt><dd>{{ subscription.period === 'MONTHLY' ? '每月' : subscription.period || '-' }}</dd></div><div><dt>下次重置</dt><dd>{{ formatDate(subscription.nextResetAt) }}</dd></div></dl>
      </section>
      <p v-if="notice" class="profile-subscription-notice">{{ notice }}</p>
      <section class="profile-subscription-grid" aria-label="套餐对比">
        <article v-for="plan in plans" :key="plan.code" class="profile-plan-card" :class="{ featured: plan.code === 'PRO', current: plan.code === currentLevel }">
          <span class="profile-plan-tag">{{ plan.code === currentLevel ? '当前套餐' : plan.code === 'PRO' ? '推荐' : plan.code }}</span>
          <h3>{{ plan.userName }}</h3>
          <p>{{ plan.description }}</p>
          <ul><li v-for="benefit in planBenefits(plan).slice(0, 5)" :key="benefit.label"><Check :size="15" /> {{ benefit.label }} {{ Number(benefit.value).toLocaleString('zh-CN') }} {{ benefit.unit }}</li></ul>
          <button v-if="plan.code !== currentLevel && plan.code !== 'FREE'" type="button" @click="requestUpgrade"><Sparkles :size="16" /> {{ plan.code === 'STANDARD' ? '升级普通版' : '升级专业版' }}</button>
          <button v-else-if="plan.code === currentLevel" type="button" disabled>{{ plan.code === 'PRO' ? '当前已是专业版' : '当前套餐' }}</button>
        </article>
      </section>
    </template>
  </ModalShell>
</template>

<style scoped>
.profile-subscription-state { display:flex; align-items:center; justify-content:center; gap:8px; min-height:180px; color:#64748b; font-size:13px; }.profile-subscription-state :deep(svg) { animation:spin .8s linear infinite; }.profile-subscription-state.is-error { color:#b45309; }.profile-subscription-state.is-error :deep(svg) { animation:none; }.profile-current-subscription { display:flex; align-items:flex-start; justify-content:space-between; gap:22px; margin-bottom:16px; padding:16px; border:1px solid #dbe4ee; border-radius:8px; background:#fff; }.profile-current-subscription>div { display:grid; gap:5px; }.profile-current-subscription span { color:#6b7280; font-size:12px; }.profile-current-subscription strong { color:#1f2937; font-size:22px; }.profile-current-subscription p { margin:0; color:#047857; font-size:12px; font-weight:650; }.profile-current-subscription p.suspended { color:#b45309; }.profile-current-subscription dl { display:grid; grid-template-columns:repeat(2,minmax(115px,1fr)); gap:10px 20px; margin:0; }.profile-current-subscription dl div { display:grid; gap:3px; }.profile-current-subscription dt { color:#94a3b8; font-size:11px; }.profile-current-subscription dd { margin:0; color:#475569; font-size:12px; font-weight:600; }.profile-subscription-notice { margin:0 0 14px; padding:10px 12px; border:1px solid #dbeafe; border-radius:7px; background:#eff6ff; color:#1d4ed8; font-size:12px; }.profile-plan-card.current { border-color:#bfdbfe; }.profile-plan-card button:disabled { cursor:default; }@keyframes spin { to { transform:rotate(360deg); } }@media (max-width:640px) { .profile-current-subscription { display:grid; }.profile-current-subscription dl { grid-template-columns:1fr 1fr; } }
</style>
