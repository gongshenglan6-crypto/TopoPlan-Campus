<script setup>
import { ref } from 'vue'
import { Download, Eye, Trash2 } from '@lucide/vue'
import ModalShell from './ModalShell.vue'

const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close', 'open-projects'])

const plans = ref([
  { id: 1, title: '三层办公楼网络规划', updatedAt: '今天 10:24', devices: 28, status: '已完成' },
  { id: 2, title: '园区无线覆盖与 VLAN 方案', updatedAt: '昨天 16:40', devices: 46, status: '待完善' },
  { id: 3, title: '总部与分支机构互联设计', updatedAt: '7 天前', devices: 33, status: '已完成' }
])

function downloadPlan(plan) {
  const content = `TopoPlan 网络工程方案\n\n项目：${plan.title}\n设备数量：${plan.devices}\n状态：${plan.status}\n更新时间：${plan.updatedAt}`
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${plan.title}.txt`
  link.click()
  URL.revokeObjectURL(url)
}

function deletePlan(planId) {
  plans.value = plans.value.filter((plan) => plan.id !== planId)
}

function viewPlan() {
  emit('close')
  emit('open-projects')
}
</script>

<template>
  <ModalShell :open="open" wide title="我的项目与方案" description="这里展示当前账号保存过的网络拓扑方案。" @close="$emit('close')">
    <div class="profile-plan-list">
      <article v-for="plan in plans" :key="plan.id" class="profile-plan-row">
        <div class="profile-plan-copy">
          <div>
            <h3>{{ plan.title }}</h3>
            <span class="profile-plan-status" :class="{ pending: plan.status === '待完善' }">{{ plan.status }}</span>
          </div>
          <p>{{ plan.devices }} 台设备 · 更新于{{ plan.updatedAt }}</p>
        </div>
        <div class="profile-row-actions">
          <button type="button" title="查看方案" @click="viewPlan"><Eye :size="16" /> 查看</button>
          <button type="button" title="下载导出" @click="downloadPlan(plan)"><Download :size="16" /> 导出</button>
          <button class="is-danger" type="button" title="删除方案" @click="deletePlan(plan.id)"><Trash2 :size="16" /> 删除</button>
        </div>
      </article>
      <p v-if="plans.length === 0" class="profile-empty-state">暂时没有保存的网络方案。</p>
    </div>
  </ModalShell>
</template>
