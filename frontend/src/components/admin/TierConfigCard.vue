<script setup>
import { Save } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

const props = defineProps({
  tier: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['save'])

function cloneTier(tier) {
  return {
    id: tier.id,
    name: tier.name,
    description: tier.description,
    monthlyTokenLimit: tier.monthlyTokenLimit,
    maxMonthlyCalls: tier.maxMonthlyCalls,
    maxContextWindow: tier.maxContextWindow,
    concurrentRequestsQps: tier.concurrentRequestsQps,
    knowledgeBaseStorageMb: tier.knowledgeBaseStorageMb,
    dailyBasicConversations: tier.dailyBasicConversations,
    dailyPremiumModelCalls: tier.dailyPremiumModelCalls,
    enabled: tier.enabled
  }
}

const draft = ref(cloneTier(props.tier))
const editableFields = [
  'monthlyTokenLimit',
  'maxMonthlyCalls',
  'maxContextWindow',
  'concurrentRequestsQps',
  'knowledgeBaseStorageMb',
  'dailyBasicConversations',
  'dailyPremiumModelCalls',
  'enabled'
]
const isDirty = computed(() => editableFields.some((field) => draft.value[field] !== props.tier[field]))

watch(() => props.tier, (tier) => {
  draft.value = cloneTier(tier)
}, { deep: true })

function normalize(value, minimum = 0) {
  return Math.max(minimum, Number(value) || 0)
}

function formatContext(value) {
  if (value >= 1000000) return `${value / 1000000}M`
  return `${value / 1000}k`
}

function save() {
  emit('save', {
    id: draft.value.id,
    values: {
      ...draft.value,
      monthlyTokenLimit: normalize(draft.value.monthlyTokenLimit),
      maxMonthlyCalls: normalize(draft.value.maxMonthlyCalls),
      maxContextWindow: normalize(draft.value.maxContextWindow, 1024),
      concurrentRequestsQps: normalize(draft.value.concurrentRequestsQps, 1),
      knowledgeBaseStorageMb: normalize(draft.value.knowledgeBaseStorageMb),
      dailyBasicConversations: normalize(draft.value.dailyBasicConversations),
      dailyPremiumModelCalls: normalize(draft.value.dailyPremiumModelCalls)
    }
  })
}
</script>

<template>
  <article class="admin-tier-config-card" :class="`is-${draft.id}`">
    <header class="admin-tier-card-header">
      <div class="admin-tier-card-heading">
        <h3>{{ draft.name }}</h3>
        <p>{{ draft.description }}</p>
      </div>
      <div class="admin-tier-card-actions">
        <button v-if="isDirty" type="button" class="admin-tier-save-button" @click="save">
          <Save :size="13" /> 保存
        </button>
        <button
          type="button"
          class="admin-switch"
          :class="{ active: draft.enabled }"
          role="switch"
          :aria-checked="draft.enabled"
          :aria-label="`切换 ${draft.name} 套餐`"
          @click="draft.enabled = !draft.enabled"
        ><i /></button>
      </div>
    </header>

    <div class="admin-tier-settings">
      <section class="admin-tier-settings-group">
        <h4>网络工程能力</h4>
        <label class="admin-tier-setting-row">
          <span>每月 Token 总量</span>
          <span class="admin-tier-editable-value"><input v-model.number="draft.monthlyTokenLimit" min="0" type="number" aria-label="每月 Token 总量" /><small>Tokens</small></span>
        </label>
        <label class="admin-tier-setting-row">
          <span>AI 拓扑生成次数</span>
          <span class="admin-tier-editable-value"><input v-model.number="draft.maxMonthlyCalls" min="0" type="number" aria-label="每月最大 API 调用" /><small>次</small></span>
        </label>
        <label class="admin-tier-setting-row">
          <span>AI 需求分析次数</span>
          <span class="admin-tier-editable-value"><input v-model.number="draft.maxContextWindow" min="1024" type="number" aria-label="最大上下文长度" /><small>{{ formatContext(draft.maxContextWindow) }}</small></span>
        </label>
        <label class="admin-tier-setting-row">
          <span>联网搜索并发</span>
          <span class="admin-tier-editable-value"><input v-model.number="draft.concurrentRequestsQps" min="1" type="number" aria-label="最高并发请求 QPS" /><small>QPS</small></span>
        </label>
        <label class="admin-tier-setting-row">
          <span>知识库存储容量</span>
          <span class="admin-tier-editable-value"><input v-model.number="draft.knowledgeBaseStorageMb" min="0" type="number" aria-label="知识库存储容量" /><small>MB</small></span>
        </label>
      </section>

      <section class="admin-tier-settings-group">
        <h4>用户端资源配额</h4>
        <label class="admin-tier-setting-row">
          <span>AI 问答次数</span>
          <span class="admin-tier-editable-value"><input v-model.number="draft.dailyBasicConversations" min="0" type="number" aria-label="单日基础对话" /><small>次 / 日</small></span>
        </label>
        <label class="admin-tier-setting-row">
          <span>Word / PDF 文档导出</span>
          <span class="admin-tier-editable-value"><input v-model.number="draft.dailyPremiumModelCalls" min="0" type="number" aria-label="单日高级或绘画模型" /><small>次 / 日</small></span>
        </label>
      </section>
    </div>
  </article>
</template>
